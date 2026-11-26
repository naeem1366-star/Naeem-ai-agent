interface Env {
  DB: D1Database;
  SUPABASE_URL: string;
  SUPABASE_PUBLISHABLE_KEY: string;
  ALLOWED_ORIGINS?: string;
  GISOO_MANAGEMENT_KEY?: string;
  GISOO_BASE_URL?: string;
  KEY_ENCRYPTION_SECRET?: string;
  ADMIN_EMAILS?: string;
  DEFAULT_KEY_LIMIT_USD?: string;
  CHAT_PRIMARY_BASE_URL?: string;
  CHAT_PRIMARY_MODEL?: string;
  CHAT_PRIMARY_API_KEY?: string;
  CHAT_SECONDARY_BASE_URL?: string;
  CHAT_SECONDARY_MODEL?: string;
  CHAT_SECONDARY_API_KEY?: string;
  CHAT_FREE_MONTHLY_REQUESTS?: string;
  CHAT_GLOBAL_MONTHLY_REQUESTS?: string;
  ASSETS?: Fetcher;
}

type Json = Record<string, unknown>;

const encoder = new TextEncoder();
const json = (data: Json, status = 200, headers: HeadersInit = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  });

function cors(request: Request, env: Env): Headers {
  const headers = new Headers();
  const origin = request.headers.get("Origin");
  const allowed = (env.ALLOWED_ORIGINS || "").split(",").map(x => x.trim()).filter(Boolean);
  if (origin && (allowed.includes("*") || allowed.includes(origin))) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Vary", "Origin");
  }
  headers.set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
  headers.set("Access-Control-Max-Age", "86400");
  return headers;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

type SupabaseIdentity = { id: string; email: string };

async function supabaseIdentity(request: Request, env: Env): Promise<SupabaseIdentity | null> {
  const authorization = request.headers.get("Authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!token || !env.SUPABASE_URL || !env.SUPABASE_PUBLISHABLE_KEY) return null;
  try {
    const response = await fetch(env.SUPABASE_URL.replace(/\/+$/, "") + "/auth/v1/user", {
      headers: { "apikey": env.SUPABASE_PUBLISHABLE_KEY, "Authorization": "Bearer " + token, "Accept": "application/json" },
      signal: AbortSignal.timeout(12000)
    });
    if (!response.ok) return null;
    const user = await response.json() as any;
    if (typeof user?.id !== "string" || typeof user?.email !== "string" || !user.email.includes("@")) return null;
    return { id: user.id, email: user.email.toLowerCase() };
  } catch { return null; }
}

type AuthUser = { id: string; phone: string; email: string; status: string };

async function authenticatedUser(request: Request, env: Env): Promise<AuthUser | null> {
  const identity = await supabaseIdentity(request, env);
  if (!identity) return null;
  const now = Math.floor(Date.now() / 1000);
  const internalMarker = "supabase:" + identity.id;
  await env.DB.prepare("INSERT INTO users (id, phone, email, created_at, last_login_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET email = excluded.email, last_login_at = excluded.last_login_at")
    .bind(identity.id, internalMarker, identity.email, now, now).run();
  const account = await env.DB.prepare("SELECT id, phone, email, status FROM users WHERE id = ?").bind(identity.id).first<AuthUser>();
  return account;
}

async function adminUser(request: Request, env: Env): Promise<AuthUser | null> {
  const user = await authenticatedUser(request, env);
  if (!user || user.status !== "active") return null;
  const allowed = (env.ADMIN_EMAILS || "").split(",").map(x => x.trim().toLowerCase()).filter(Boolean);
  return allowed.includes(user.email.toLowerCase()) ? user : null;
}

async function me(request: Request, env: Env): Promise<Response> {
  const user = await authenticatedUser(request, env);
  if (!user || user.status !== "active") return json({ error: "UNAUTHORIZED" }, 401);
  return json({ user: { id: user.id, email: user.email } });
}
async function encryptProviderSecret(secret: string, master: string): Promise<{ cipher: string; iv: string }> {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(master));
  const key = await crypto.subtle.importKey("raw", digest, "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, encoder.encode(secret));
  return { cipher: base64Url(new Uint8Array(encrypted)), iv: base64Url(iv) };
}

async function gisooRequest(env: Env, method: string, path: string, body?: unknown, idempotencyKey?: string): Promise<any> {
  if (!env.GISOO_MANAGEMENT_KEY) throw new Error("GISOO_NOT_CONFIGURED");
  const base = (env.GISOO_BASE_URL || "https://gisoo.pro/api/v1/manage").replace(/\/+$/, "");
  const headers: Record<string, string> = { "Authorization": "Bearer " + env.GISOO_MANAGEMENT_KEY, "Accept": "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
  const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000) });
  const data = await response.json().catch(() => ({})) as any;
  if (!response.ok) { const error = new Error(data?.error?.code || "GISOO_REQUEST_FAILED"); (error as any).status = response.status; throw error; }
  return data;
}

async function provisionProviderKey(env: Env, userId: string, email: string): Promise<void> {
  if (!env.GISOO_MANAGEMENT_KEY || !env.KEY_ENCRYPTION_SECRET) return;
  const existing = await env.DB.prepare("SELECT user_id FROM provider_accounts WHERE user_id = ?").bind(userId).first();
  if (existing) return;
  const externalId = "naeem-user-" + userId;
  const limit = env.DEFAULT_KEY_LIMIT_USD || "0.01";
  let project: any;
  try {
    project = await gisooRequest(env, "POST", "/projects", {
      name: "Naeem AI " + email.split("@")[0].slice(-12), external_id: externalId,
      description: "Naeem AI customer account",
      metadata: { product: "naeem_ai", user_id: userId, email },
      key: { name: "naeem-main", spend_limit_usd: limit, expires_in_days: 365 }
    }, "naeem-signup-" + userId);
  } catch (error) {
    if ((error as any).status !== 409) throw error;
    const conflict = await gisooRequest(env, "GET", "/projects/by-external/" + encodeURIComponent(externalId));
    const key = await gisooRequest(env, "POST", "/projects/" + conflict.id + "/keys", {
      name: "naeem-main", spend_limit_usd: limit, expires_in_days: 365
    }, "naeem-signup-key-" + userId);
    project = { ...conflict, key };
  }
  const key = project.key;
  if (!project.id || !key?.id || !key?.secret) throw new Error("GISOO_INVALID_PROVISION_RESPONSE");
  const encrypted = await encryptProviderSecret(key.secret, env.KEY_ENCRYPTION_SECRET);
  const now = Math.floor(Date.now()/1000);
  await env.DB.prepare("INSERT INTO provider_accounts (user_id, project_id, key_id, key_masked, secret_cipher, secret_iv, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'active', ?, ?) ON CONFLICT(user_id) DO NOTHING")
    .bind(userId, String(project.id), String(key.id), String(key.masked || ""), encrypted.cipher, encrypted.iv, now, now).run();
}


function decodeBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(normalized + "=".repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}


async function chatCompletion(request: Request, env: Env): Promise<Response> {
  const user = await authenticatedUser(request, env);
  if (!user || user.status !== "active") return json({ error: { code: "UNAUTHORIZED", message: "برای استفاده از چت ابتدا وارد حساب شو." } }, 401);

  const rawBody = await request.text();
  if (encoder.encode(rawBody).length > 5_000_000) return json({ error: { code: "REQUEST_TOO_LARGE", message: "حجم درخواست بیش از حد مجاز است." } }, 413);
  let body: any = null;
  try { body = JSON.parse(rawBody); } catch { return json({ error: { code: "INVALID_JSON", message: "ساختار JSON درخواست معتبر نیست." } }, 400); }
  if (!body || !Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 80) {
    return json({ error: { code: "INVALID_REQUEST", message: "ساختار درخواست گفتگو معتبر نیست." } }, 400);
  }
  const primaryBase = (env.CHAT_PRIMARY_BASE_URL || "https://netarz.ir/api/ai/v1").replace(/\/+$/, "");
  const primaryKey = env.CHAT_PRIMARY_API_KEY || "";
  const primaryModel = env.CHAT_PRIMARY_MODEL || "gpt-4o-mini";
  const secondaryBase = (env.CHAT_SECONDARY_BASE_URL || "https://api.gapgpt.ir/v1").replace(/\/+$/, "");
  const secondaryKey = env.CHAT_SECONDARY_API_KEY || "";
  const secondaryModel = env.CHAT_SECONDARY_MODEL || primaryModel;
  if (!primaryKey || !primaryBase.startsWith("https://")) {
    return json({ error: { code: "CHAT_GATEWAY_NOT_CONFIGURED", message: "سرویس گفتگوی ایما هنوز روی سرور پیکربندی نشده است." } }, 503);
  }

  const now = Math.floor(Date.now() / 1000);
  const period = new Date().toISOString().slice(0, 7);
  const limit = Math.max(1, Math.min(10000, Number(env.CHAT_FREE_MONTHLY_REQUESTS || "50")));
  const reserve = await env.DB.prepare(
    "UPDATE users SET chat_free_period = ?, chat_free_used = CASE WHEN chat_free_period = ? THEN chat_free_used + 1 ELSE 1 END WHERE id = ? AND status = 'active' AND (chat_free_period != ? OR chat_free_used < ?)"
  ).bind(period, period, user.id, period, limit).run();
  if ((reserve.meta?.changes || 0) !== 1) {
    const usage = await env.DB.prepare("SELECT chat_free_used, chat_free_period FROM users WHERE id = ?").bind(user.id).first<any>();
    return json({ error: { code: "CHAT_QUOTA_EXHAUSTED", message: "سهمیه رایگان این ماه تمام شده است. برای ادامه، اعتبار حساب را افزایش بده." }, quota: { limit, used: usage?.chat_free_period === period ? usage.chat_free_used : 0, period } }, 402);
  }

  const globalLimit = Math.max(1, Math.min(1000000, Number(env.CHAT_GLOBAL_MONTHLY_REQUESTS || "5000")));
  const globalReserve = await env.DB.prepare(
    "INSERT INTO chat_global_usage (period, request_count, updated_at) VALUES (?, 1, ?) ON CONFLICT(period) DO UPDATE SET request_count = request_count + 1, updated_at = excluded.updated_at WHERE request_count < ?"
  ).bind(period, now, globalLimit).run();
  if ((globalReserve.meta?.changes || 0) !== 1) {
    await env.DB.prepare("UPDATE users SET chat_free_used = CASE WHEN chat_free_period = ? AND chat_free_used > 0 THEN chat_free_used - 1 ELSE chat_free_used END WHERE id = ?").bind(period, user.id).run();
    return json({ error: { code: "CHAT_CAPACITY_REACHED", message: "ظرفیت رایگان این ماه فعلاً تکمیل شده است. بعداً دوباره تلاش کن." } }, 503);
  }
  const refundReservation = async () => {
    await env.DB.batch([
      env.DB.prepare("UPDATE users SET chat_free_used = CASE WHEN chat_free_period = ? AND chat_free_used > 0 THEN chat_free_used - 1 ELSE chat_free_used END WHERE id = ?").bind(period, user.id),
      env.DB.prepare("UPDATE chat_global_usage SET request_count = CASE WHEN request_count > 0 THEN request_count - 1 ELSE 0 END, updated_at = ? WHERE period = ?").bind(Math.floor(Date.now() / 1000), period)
    ]);
  };

  const payload = { ...body, model: primaryModel, stream: false };
  delete (payload as any).user;
  const tryProvider = async (base: string, key: string, model: string): Promise<Response> => {
    if (!key || !base.startsWith("https://")) throw new Error("PROVIDER_NOT_CONFIGURED");
    const outgoing = { ...payload, model };
    return fetch(base + "/chat/completions", {
      method: "POST",
      headers: { "Authorization": "Bearer " + key, "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify(outgoing),
      signal: AbortSignal.timeout(60000)
    });
  };

  let upstream: Response | null = null;
  let selectedModel = primaryModel;
  let primaryFailure = "";
  try {
    upstream = await tryProvider(primaryBase, primaryKey, primaryModel);
    if (upstream.status === 429 || upstream.status >= 500) {
      primaryFailure = "HTTP_" + upstream.status;
      await upstream.body?.cancel();
      upstream = null;
    }
  } catch (error) {
    primaryFailure = error instanceof Error ? error.name : "NETWORK_ERROR";
  }

  if (!upstream && secondaryKey && secondaryBase.startsWith("https://")) {
    try {
      upstream = await tryProvider(secondaryBase, secondaryKey, secondaryModel);
      selectedModel = secondaryModel;
      if (upstream.status === 429 || upstream.status >= 500) {
        await upstream.body?.cancel();
        upstream = null;
      }
    } catch { upstream = null; }
  }

  if (!upstream) {
    await refundReservation();
    return json({ error: { code: "CHAT_PROVIDERS_UNAVAILABLE", message: "در حال حاضر سرویس گفتگو پاسخگو نیست. سهمیه‌ات کسر نشد؛ کمی بعد دوباره تلاش کن.", primary: primaryFailure } }, 503);
  }

  const raw = await upstream.text();
  if (!upstream.ok) {
    await refundReservation();
    return new Response(raw || JSON.stringify({ error: { message: "خطا در سرویس گفتگو" } }), {
      status: upstream.status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
    });
  }

  const usage = (() => { try { return JSON.parse(raw).usage || {}; } catch { return {}; } })();
  await env.DB.prepare(
    "INSERT INTO usage_events (id, user_id, feature, model, input_tokens, output_tokens, provider_cost_usd, charge_toman, status, created_at) VALUES (?, ?, 'chat', ?, ?, ?, '0', 0, 'success', ?)"
  ).bind(crypto.randomUUID(), user.id, selectedModel, Number(usage.prompt_tokens || 0), Number(usage.completion_tokens || 0), now).run().catch(() => undefined);
  return new Response(raw, { status: upstream.status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "X-IMA-Chat-Provider": selectedModel === primaryModel ? "primary" : "secondary" } });
}

async function walletSummary(request: Request, env: Env): Promise<Response> {
  const user = await authenticatedUser(request, env);
  if (!user || user.status !== "active") return json({ error: "UNAUTHORIZED" }, 401);
  const balance = await env.DB.prepare("SELECT wallet_balance_toman FROM users WHERE id = ?").bind(user.id).first<{wallet_balance_toman:number}>();
  const transactions = await env.DB.prepare("SELECT id, type, amount_toman, balance_after_toman, description, created_at FROM wallet_transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT 30").bind(user.id).all();
  return json({ balanceToman: balance?.wallet_balance_toman ?? 0, transactions: transactions.results || [] });
}

async function adminOverview(request: Request, env: Env): Promise<Response> {
  if (!(await adminUser(request, env))) return json({ error: "ADMIN_ONLY" }, 403);
  const users = await env.DB.prepare("SELECT COUNT(*) AS count FROM users").first<any>();
  const active = await env.DB.prepare("SELECT COUNT(*) AS count FROM users WHERE status = 'active'").first<any>();
  const wallet = await env.DB.prepare("SELECT COALESCE(SUM(wallet_balance_toman),0) AS total FROM users").first<any>();
  const topups = await env.DB.prepare("SELECT COALESCE(SUM(amount_toman),0) AS total FROM wallet_transactions WHERE type = 'topup'").first<any>();
  const usage = await env.DB.prepare("SELECT COUNT(*) AS requests, COALESCE(SUM(charge_toman),0) AS charged, COALESCE(SUM(provider_cost_usd),0) AS provider_cost_usd FROM usage_events").first<any>();
  let provider: any = null;
  if (env.GISOO_MANAGEMENT_KEY) {
    try {
      const account = await gisooRequest(env, "GET", "/account");
      provider = { status: account.status, balanceUsd: account.balance?.usd_display || account.balance?.usd || null, spentUsd: account.totals?.spent_usd || null };
    } catch { provider = { status: "unavailable" }; }
  }
  return json({ users: users?.count || 0, activeUsers: active?.count || 0, walletLiabilityToman: wallet?.total || 0, topupsToman: topups?.total || 0, usage: { requests: usage?.requests || 0, chargedToman: usage?.charged || 0, providerCostUsd: usage?.provider_cost_usd || 0 }, provider });
}

async function adminUsers(request: Request, env: Env): Promise<Response> {
  if (!(await adminUser(request, env))) return json({ error: "ADMIN_ONLY" }, 403);
  const url = new URL(request.url);
  const q = (url.searchParams.get("q") || "").trim().slice(0, 40);
  const limit = Math.min(100, Math.max(1, Number(url.searchParams.get("limit") || 50)));
  const rows = await env.DB.prepare("SELECT u.id, u.email, u.status, u.wallet_balance_toman, u.created_at, p.project_id, p.key_id, p.key_masked, p.status AS provider_status FROM users u LEFT JOIN provider_accounts p ON p.user_id = u.id WHERE (? = '' OR u.email LIKE ? OR u.id = ?) ORDER BY u.created_at DESC LIMIT ?")
    .bind(q, "%" + q + "%", q, limit).all();
  return json({ users: rows.results || [] });
}

async function adminWalletAdjust(request: Request, env: Env, userId: string): Promise<Response> {
  if (!(await adminUser(request, env))) return json({ error: "ADMIN_ONLY" }, 403);
  const body = await request.json().catch(() => null) as any;
  const amount = Number(body?.amountToman);
  const description = typeof body?.description === "string" ? body.description.trim().slice(0, 160) : "";
  if (!Number.isSafeInteger(amount) || amount === 0 || Math.abs(amount) > 100000000 || !description) return json({ error: "INVALID_ADJUSTMENT" }, 400);
  const id = crypto.randomUUID(), now = Math.floor(Date.now()/1000);
  const results = await env.DB.batch([
    env.DB.prepare("UPDATE users SET wallet_balance_toman = wallet_balance_toman + ? WHERE id = ? AND status = 'active' AND wallet_balance_toman + ? >= 0").bind(amount, userId, amount),
    env.DB.prepare("INSERT INTO wallet_transactions (id, user_id, type, amount_toman, balance_after_toman, description, reference, created_at) SELECT ?, id, 'admin_adjustment', ?, wallet_balance_toman, ?, ?, ? FROM users WHERE id = ? AND changes() = 1").bind(id, amount, description, "admin:" + id, now, userId)
  ]);
  if ((results[0]?.meta?.changes || 0) !== 1) return json({ error: "USER_NOT_FOUND_OR_INSUFFICIENT_BALANCE" }, 409);
  const row = await env.DB.prepare("SELECT wallet_balance_toman FROM users WHERE id = ?").bind(userId).first<any>();
  return json({ success: true, balanceToman: row?.wallet_balance_toman ?? 0 });
}

async function adminSetUserStatus(request: Request, env: Env, userId: string): Promise<Response> {
  if (!(await adminUser(request, env))) return json({ error: "ADMIN_ONLY" }, 403);
  const body = await request.json().catch(() => null) as any;
  if (!["active", "suspended"].includes(body?.status)) return json({ error: "INVALID_STATUS" }, 400);
  const result = await env.DB.prepare("UPDATE users SET status = ? WHERE id = ?").bind(body.status, userId).run();
  if ((result.meta?.changes || 0) !== 1) return json({ error: "USER_NOT_FOUND" }, 404);
  return json({ success: true, status: body.status });
}

async function adminProvisionUser(request: Request, env: Env, userId: string): Promise<Response> {
  if (!(await adminUser(request, env))) return json({ error: "ADMIN_ONLY" }, 403);
  if (!env.GISOO_MANAGEMENT_KEY || !env.KEY_ENCRYPTION_SECRET) return json({ error: "GISOO_NOT_CONFIGURED" }, 503);
  const user = await env.DB.prepare("SELECT id, email FROM users WHERE id = ?").bind(userId).first<any>();
  if (!user) return json({ error: "USER_NOT_FOUND" }, 404);
  if (typeof user.email !== "string" || !user.email.includes("@")) return json({ error: "LEGACY_PHONE_ACCOUNT" }, 409);
  try {
    await provisionProviderKey(env, user.id, user.email);
    const row = await env.DB.prepare("SELECT project_id, key_id, key_masked, status FROM provider_accounts WHERE user_id = ?").bind(userId).first<any>();
    return json({ success: true, provider: row });
  } catch { return json({ error: "PROVISION_FAILED" }, 502); }
}

async function adminProviderLimit(request: Request, env: Env, userId: string): Promise<Response> {
  if (!(await adminUser(request, env))) return json({ error: "ADMIN_ONLY" }, 403);
  const body = await request.json().catch(() => null) as any;
  const amount = Number(body?.spendLimitUsd);
  if (!Number.isFinite(amount) || amount < 0.01 || amount > 1000000) return json({ error: "INVALID_LIMIT" }, 400);
  const account = await env.DB.prepare("SELECT key_id FROM provider_accounts WHERE user_id = ?").bind(userId).first<any>();
  if (!account) return json({ error: "PROVIDER_KEY_NOT_FOUND" }, 404);
  try { await gisooRequest(env, "PATCH", "/keys/" + account.key_id, { spend_limit_usd: amount.toFixed(2) }); return json({ success: true }); }
  catch { return json({ error: "GISOO_UPDATE_FAILED" }, 502); }
}


export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const headers = cors(request, env);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/health") return json({ ok: true, service: "naeem-ai-core" }, 200, headers);
      if (request.method === "GET" && (url.pathname === "/admin" || url.pathname === "/admin/")) {
        if (env.ASSETS) return env.ASSETS.fetch(new Request(new URL("/admin.html", request.url), request));
        return json({ error: "ADMIN_ASSET_NOT_CONFIGURED" }, 503, headers);
      }
      if (request.method === "POST" && url.pathname === "/v1/chat/completions") return withHeaders(await chatCompletion(request, env), headers);
      if (request.method === "GET" && url.pathname === "/v1/wallet") return withHeaders(await walletSummary(request, env), headers);
      if (request.method === "GET" && url.pathname === "/v1/admin/overview") return withHeaders(await adminOverview(request, env), headers);
      if (request.method === "GET" && url.pathname === "/v1/admin/users") return withHeaders(await adminUsers(request, env), headers);
      const walletMatch = url.pathname.match(/^\/v1\/admin\/users\/([^/]+)\/wallet$/);
      if (request.method === "POST" && walletMatch) return withHeaders(await adminWalletAdjust(request, env, walletMatch[1]), headers);
      const statusMatch = url.pathname.match(/^\/v1\/admin\/users\/([^/]+)\/status$/);
      if (request.method === "PATCH" && statusMatch) return withHeaders(await adminSetUserStatus(request, env, statusMatch[1]), headers);
      const provisionMatch = url.pathname.match(/^\/v1\/admin\/users\/([^/]+)\/provision$/);
      if (request.method === "POST" && provisionMatch) return withHeaders(await adminProvisionUser(request, env, provisionMatch[1]), headers);
      const limitMatch = url.pathname.match(/^\/v1\/admin\/users\/([^/]+)\/key-limit$/);
      if (request.method === "PATCH" && limitMatch) return withHeaders(await adminProviderLimit(request, env, limitMatch[1]), headers);
      if (request.method === "POST" && (url.pathname === "/v1/auth/otp/request" || url.pathname === "/v1/auth/otp/verify")) return withHeaders(json({ error: "PHONE_AUTH_RETIRED", message: "ورود با شماره موبایل غیرفعال شده است؛ از ورود ایمیل و رمز عبور استفاده کنید." }, 410), headers);
      if (request.method === "GET" && url.pathname === "/v1/me") return withHeaders(await me(request, env), headers);
      return json({ error: "NOT_FOUND" }, 404, headers);
    } catch {
      return json({ error: "INTERNAL_ERROR" }, 500, headers);
    }
  }
};

function withHeaders(response: Response, headers: Headers): Response {
  const merged = new Headers(response.headers);
  headers.forEach((value, key) => merged.set(key, value));
  return new Response(response.body, { status: response.status, headers: merged });
}
