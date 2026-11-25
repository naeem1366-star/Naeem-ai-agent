interface Env {
  DB: D1Database;
  GHASEDAK_API_KEY: string;
  GHASEDAK_TEMPLATE_NAME: string;
  AUTH_SECRET: string;
  ALLOWED_ORIGINS?: string;
  GISOO_MANAGEMENT_KEY?: string;
  GISOO_BASE_URL?: string;
  KEY_ENCRYPTION_SECRET?: string;
  ADMIN_PHONES?: string;
  DEFAULT_KEY_LIMIT_USD?: string;
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
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
  headers.set("Access-Control-Max-Age", "86400");
  return headers;
}

function normalizeIranPhone(input: unknown): string | null {
  if (typeof input !== "string") return null;
  let value = input.replace(/[\s()-]/g, "");
  if (value.startsWith("+98")) value = "0" + value.slice(3);
  else if (value.startsWith("0098")) value = "0" + value.slice(4);
  else if (value.startsWith("98") && value.length === 12) value = "0" + value.slice(2);
  return /^09\d{9}$/.test(value) ? value : null;
}

function randomCode(): string {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(bytes[0] % 1_000_000).padStart(6, "0");
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function hmac(value: string, secret: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(value));
  return base64Url(new Uint8Array(signature));
}

async function issueToken(userId: string, phone: string, secret: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = base64Url(encoder.encode(JSON.stringify({ sub: userId, phone, iat: now, exp: now + 60 * 60 * 24 * 30 })));
  const body = header + "." + payload;
  return body + "." + await hmac(body, secret);
}

async function verifyToken(token: string, secret: string): Promise<{ sub: string; phone: string } | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const expected = await hmac(parts[0] + "." + parts[1], secret);
  if (expected !== parts[2]) return null;
  try {
    const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    if (!payload.sub || !payload.phone || payload.exp < Math.floor(Date.now() / 1000)) return null;
    return { sub: payload.sub, phone: payload.phone };
  } catch {
    return null;
  }
}

async function rateLimit(db: D1Database, key: string, windowSeconds: number, max: number): Promise<boolean> {
  const now = Math.floor(Date.now() / 1000);
  const start = now - windowSeconds;
  await db.prepare(`
    INSERT INTO rate_limits (bucket_key, window_start, hit_count)
    VALUES (?, ?, 1)
    ON CONFLICT(bucket_key) DO UPDATE SET
      hit_count = CASE WHEN rate_limits.window_start < ? THEN 1 ELSE rate_limits.hit_count + 1 END,
      window_start = CASE WHEN rate_limits.window_start < ? THEN excluded.window_start ELSE rate_limits.window_start END
  `).bind(key, now, start, start).run();
  const row = await db.prepare("SELECT hit_count FROM rate_limits WHERE bucket_key = ?").bind(key).first<{hit_count:number}>();
  return (row?.hit_count ?? max + 1) <= max;
}

async function sendGhasedakOtp(env: Env, phone: string, code: string): Promise<void> {
  const response = await fetch("https://gateway.ghasedak.me/rest/api/v1/WebService/SendOtpWithParams", {
    method: "POST",
    headers: { "ApiKey": env.GHASEDAK_API_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({
      receptors: [phone],
      templateName: env.GHASEDAK_TEMPLATE_NAME,
      param1: code
    })
  });
  const body = await response.json() as { IsSuccess?: boolean; StatusCode?: number; Message?: string };
  if (!response.ok || body.IsSuccess === false || (body.StatusCode !== undefined && body.StatusCode !== 200)) {
    throw new Error("SMS_PROVIDER_REJECTED");
  }
}

async function requestOtp(request: Request, env: Env): Promise<Response> {
  const body = await request.json().catch(() => null) as Json | null;
  const phone = normalizeIranPhone(body?.phone);
  if (!phone) return json({ error: "INVALID_PHONE", message: "شماره موبایل معتبر ایران را وارد کنید." }, 400);

  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  if (!(await rateLimit(env.DB, "ip:" + ip, 3600, 10))) {
    return json({ error: "RATE_LIMITED", message: "تعداد درخواست‌ها بیش از حد مجاز است." }, 429);
  }
  if (!(await rateLimit(env.DB, "phone:" + phone, 86400, 5))) {
    return json({ error: "RATE_LIMITED", message: "برای این شماره فعلاً امکان درخواست کد جدید وجود ندارد." }, 429);
  }

  const now = Math.floor(Date.now() / 1000);
  const recent = await env.DB.prepare(
    "SELECT created_at FROM otp_challenges WHERE phone = ? ORDER BY created_at DESC LIMIT 1"
  ).bind(phone).first<{created_at:number}>();
  if (recent && now - recent.created_at < 60) {
    return json({ error: "WAIT_BEFORE_RETRY", retryAfter: 60 - (now - recent.created_at) }, 429);
  }

  const code = randomCode();
  const challengeId = crypto.randomUUID();
  const codeHash = await hmac(phone + ":" + challengeId + ":" + code, env.AUTH_SECRET);
  await env.DB.prepare(
    "INSERT INTO otp_challenges (id, phone, code_hash, expires_at, attempts, consumed_at, created_at, request_ip) VALUES (?, ?, ?, ?, 0, NULL, ?, ?)"
  ).bind(challengeId, phone, codeHash, now + 300, now, ip).run();

  try {
    await sendGhasedakOtp(env, phone, code);
  } catch {
    await env.DB.prepare("DELETE FROM otp_challenges WHERE id = ?").bind(challengeId).run();
    return json({ error: "SMS_SEND_FAILED", message: "ارسال کد انجام نشد. کمی بعد دوباره تلاش کنید." }, 502);
  }

  return json({ success: true, expiresIn: 300, retryAfter: 60 });
}

async function verifyOtp(request: Request, env: Env): Promise<Response> {
  const body = await request.json().catch(() => null) as Json | null;
  const phone = normalizeIranPhone(body?.phone);
  const code = typeof body?.code === "string" ? body.code.trim() : "";
  if (!phone || !/^\d{6}$/.test(code)) {
    return json({ error: "INVALID_INPUT", message: "شماره موبایل یا کد تأیید معتبر نیست." }, 400);
  }

  const challenge = await env.DB.prepare(
    "SELECT id, code_hash, expires_at, attempts FROM otp_challenges WHERE phone = ? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1"
  ).bind(phone).first<{id:string;code_hash:string;expires_at:number;attempts:number}>();

  const now = Math.floor(Date.now() / 1000);
  if (!challenge || challenge.expires_at < now || challenge.attempts >= 5) {
    return json({ error: "OTP_EXPIRED", message: "کد منقضی شده است. کد جدید دریافت کنید." }, 400);
  }

  const submittedHash = await hmac(phone + ":" + challenge.id + ":" + code, env.AUTH_SECRET);
  if (submittedHash !== challenge.code_hash) {
    await env.DB.prepare("UPDATE otp_challenges SET attempts = attempts + 1 WHERE id = ? AND consumed_at IS NULL")
      .bind(challenge.id).run();
    return json({ error: "WRONG_CODE", message: "کد واردشده صحیح نیست." }, 400);
  }

  const consumed = await env.DB.prepare(
    "UPDATE otp_challenges SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL AND expires_at >= ? AND attempts < 5"
  ).bind(now, challenge.id, now).run();
  if ((consumed.meta?.changes ?? 0) !== 1) {
    return json({ error: "OTP_ALREADY_USED", message: "این کد قبلاً استفاده شده یا منقضی شده است." }, 409);
  }

  const existing = await env.DB.prepare("SELECT id FROM users WHERE phone = ?").bind(phone).first<{id:string}>();
  const userId = existing?.id || crypto.randomUUID();
  await env.DB.prepare(`
    INSERT INTO users (id, phone, created_at, last_login_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT(phone) DO UPDATE SET last_login_at = excluded.last_login_at
  `).bind(userId, phone, now, now).run();

  if (env.GISOO_MANAGEMENT_KEY && env.KEY_ENCRYPTION_SECRET) await provisionProviderKey(env, userId, phone).catch(() => undefined);
  const token = await issueToken(userId, phone, env.AUTH_SECRET);
  return json({ success: true, token, tokenType: "Bearer", expiresIn: 2592000, user: { id: userId, phone } });
}

async function me(request: Request, env: Env): Promise<Response> {
  const authorization = request.headers.get("Authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  const user = await verifyToken(token, env.AUTH_SECRET);
  if (!user) return json({ error: "UNAUTHORIZED" }, 401);
  return json({ user });
}


type AuthUser = { id: string; phone: string; status: string };

async function authenticatedUser(request: Request, env: Env): Promise<AuthUser | null> {
  const authorization = request.headers.get("Authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  const claims = await verifyToken(token, env.AUTH_SECRET);
  if (!claims) return null;
  return await env.DB.prepare("SELECT id, phone, status FROM users WHERE id = ?").bind(claims.sub).first<AuthUser>();
}

async function adminUser(request: Request, env: Env): Promise<AuthUser | null> {
  const user = await authenticatedUser(request, env);
  if (!user || user.status !== "active") return null;
  const allowed = (env.ADMIN_PHONES || "").split(",").map(normalizeIranPhone).filter(Boolean);
  return allowed.includes(user.phone) ? user : null;
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

async function provisionProviderKey(env: Env, userId: string, phone: string): Promise<void> {
  if (!env.GISOO_MANAGEMENT_KEY || !env.KEY_ENCRYPTION_SECRET) return;
  const existing = await env.DB.prepare("SELECT user_id FROM provider_accounts WHERE user_id = ?").bind(userId).first();
  if (existing) return;
  const externalId = "naeem-user-" + userId;
  const limit = env.DEFAULT_KEY_LIMIT_USD || "0.01";
  let project: any;
  try {
    project = await gisooRequest(env, "POST", "/projects", {
      name: "Naeem AI " + phone.slice(-4), external_id: externalId,
      description: "Naeem AI customer account",
      metadata: { product: "naeem_ai", user_id: userId },
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
  const rows = await env.DB.prepare("SELECT u.id, u.phone, u.status, u.wallet_balance_toman, u.created_at, p.project_id, p.key_id, p.key_masked, p.status AS provider_status FROM users u LEFT JOIN provider_accounts p ON p.user_id = u.id WHERE (? = '' OR u.phone LIKE ? OR u.id = ?) ORDER BY u.created_at DESC LIMIT ?")
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
  const user = await env.DB.prepare("SELECT id, phone FROM users WHERE id = ?").bind(userId).first<any>();
  if (!user) return json({ error: "USER_NOT_FOUND" }, 404);
  try {
    await provisionProviderKey(env, user.id, user.phone);
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
      if (request.method === "POST" && url.pathname === "/v1/auth/otp/request") return withHeaders(await requestOtp(request, env), headers);
      if (request.method === "POST" && url.pathname === "/v1/auth/otp/verify") return withHeaders(await verifyOtp(request, env), headers);
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
