// EMA secure chat gateway — Supabase Edge Function
// Provider credentials are read only from Supabase Function Secrets.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: corsHeaders });

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST" || new URL(request.url).pathname.endsWith("/")) {
    return reply({ error: { code: "NOT_FOUND", message: "مسیر درخواست معتبر نیست." } }, 404);
  }
  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) {
    return reply({ error: { code: "UNAUTHORIZED", message: "برای استفاده از چت وارد حساب شو." } }, 401);
  }
  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_PUBLISHABLE_KEY") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!supabaseUrl || !anonKey || !serviceKey) {
    return reply({ error: { code: "SERVER_CONFIG_MISSING", message: "پیکربندی امن سرور کامل نیست." } }, 503);
  }

  // Verify the user's access token with Supabase Auth; never trust a client-supplied user id.
  const identityResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, Authorization: auth },
  }).catch(() => null);
  if (!identityResponse?.ok) {
    return reply({ error: { code: "UNAUTHORIZED", message: "نشست ورود معتبر نیست؛ دوباره وارد حساب شو." } }, 401);
  }
  const identity = await identityResponse.json().catch(() => ({}));
  if (typeof identity.id !== "string") {
    return reply({ error: { code: "UNAUTHORIZED", message: "حساب کاربری معتبر نیست." } }, 401);
  }

  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > 1_500_000) {
    return reply({ error: { code: "REQUEST_TOO_LARGE", message: "حجم درخواست بیش از حد مجاز است." } }, 413);
  }
  let body: any;
  try { body = JSON.parse(raw); } catch {
    return reply({ error: { code: "INVALID_JSON", message: "ساختار درخواست معتبر نیست." } }, 400);
  }
  if (!Array.isArray(body?.messages) || body.messages.length < 1 || body.messages.length > 30) {
    return reply({ error: { code: "INVALID_REQUEST", message: "ساختار گفتگو معتبر نیست." } }, 400);
  }
  const containsImage = JSON.stringify(body.messages).includes("data:image/");
  if (containsImage) {
    return reply({ error: { code: "IMAGE_NOT_ENABLED", message: "تحلیل تصویر هنوز در درگاه امن فعال نشده است." } }, 503);
  }

  // Atomic daily quota, enforced in Postgres rather than in the Android client.
  const quotaResponse = await fetch(`${supabaseUrl}/rest/v1/rpc/ema_consume_chat_quota`, {
    method: "POST",
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ p_user_id: identity.id, p_daily_limit: 50 }),
  }).catch(() => null);
  if (!quotaResponse?.ok) {
    return reply({ error: { code: "QUOTA_SERVICE_UNAVAILABLE", message: "سرویس سهمیه چت آماده نیست؛ مهاجرت پایگاه داده را اجرا کن." } }, 503);
  }
  const quota = await quotaResponse.json().catch(() => null);
  if (quota !== true) {
    return reply({ error: { code: "DAILY_FREE_LIMIT_REACHED", message: "سهمیه رایگان امروز تمام شده است؛ فردا دوباره امتحان کن." } }, 429);
  }

  const primaryKey = Deno.env.get("CHAT_PRIMARY_API_KEY") || "";
  const secondaryKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || "";
  const primaryBase = (Deno.env.get("CHAT_PRIMARY_BASE_URL") || "https://netarz.ir/api/ai/v1").replace(/\/+$/, "");
  const secondaryBase = (Deno.env.get("CHAT_SECONDARY_BASE_URL") || "https://api.gapgpt.app/v1").replace(/\/+$/, "");
  const primaryModel = Deno.env.get("CHAT_PRIMARY_MODEL") || "gpt-4o-mini";
  const secondaryModel = Deno.env.get("CHAT_SECONDARY_MODEL") || primaryModel;
  if (!primaryKey || !primaryBase.startsWith("https://")) {
    return reply({ error: { code: "CHAT_GATEWAY_NOT_CONFIGURED", message: "سرویس اصلی گفتگو روی سرور تنظیم نشده است." } }, 503);
  }

  const payload = {
    model: primaryModel,
    messages: body.messages,
    stream: false,
    max_tokens: Math.min(1200, Math.max(1, Number(body.max_tokens) || 1200)),
    temperature: typeof body.temperature === "number" ? Math.min(1, Math.max(0, body.temperature)) : 0.7,
  };
  const providers = [
    { name: "NetArz", base: primaryBase, key: primaryKey, model: primaryModel },
    ...(secondaryKey ? [{ name: "GapGPT", base: secondaryBase, key: secondaryKey, model: secondaryModel }] : []),
  ];
  let lastStatus = 502;
  let lastBody = "";
  for (const provider of providers) {
    try {
      const response = await fetch(`${provider.base}/chat/completions`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${provider.key}`,
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ ...payload, model: provider.model }),
        signal: AbortSignal.timeout(85000),
      });
      const text = await response.text();
      if (response.ok) {
        const result = JSON.parse(text);
        if (!Array.isArray(result.choices) || !result.choices.length) throw new Error("Provider returned no choices");
        return reply(result);
      }
      lastStatus = response.status;
      lastBody = text.slice(0, 600);
      // Do not fall back on a client/model error; fallback only for provider availability/auth/rate failures.
      if (![401, 403, 408, 429, 500, 502, 503, 504].includes(response.status)) break;
    } catch (error) {
      lastStatus = 502;
      lastBody = error instanceof Error ? error.message : "provider request failed";
    }
  }
  console.error("EMA chat providers failed", { status: lastStatus, detail: lastBody.slice(0, 200) });
  return reply({ error: { code: "CHAT_PROVIDERS_UNAVAILABLE", message: "سرویس گفتگو موقتاً پاسخگو نیست؛ لطفاً کمی بعد دوباره تلاش کن." } }, 503);
});
