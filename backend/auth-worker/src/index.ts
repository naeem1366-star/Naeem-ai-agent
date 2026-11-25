interface Env {
  DB: D1Database;
  GHASEDAK_API_KEY: string;
  GHASEDAK_TEMPLATE_NAME: string;
  AUTH_SECRET: string;
  ALLOWED_ORIGINS?: string;
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

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const headers = cors(request, env);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/health") return json({ ok: true, service: "naeem-ai-auth" }, 200, headers);
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
