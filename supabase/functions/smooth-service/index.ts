// EMA secure gateway: chat + card-to-card billing.
// Provider keys and the Supabase service-role key stay in Edge Function secrets.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: corsHeaders });

const jsonBody = async (request: Request) => {
  try { return await request.json(); }
  catch { throw new Error("INVALID_JSON"); }
};

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const url = new URL(request.url);
  const pathname = url.pathname.replace(/\/+$/, "");
  const functionMarker = "/smooth-service";
  const functionIndex = pathname.indexOf(functionMarker);
  const route = functionIndex >= 0 ? pathname.slice(functionIndex + functionMarker.length) : pathname;

  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) {
    return reply({ error: { code: "UNAUTHORIZED", message: "برای استفاده از ایما وارد حساب شو." } }, 401);
  }

  const supabaseUrl = (Deno.env.get("SUPABASE_URL") || "").replace(/\/+$/, "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_PUBLISHABLE_KEY") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!supabaseUrl || !anonKey || !serviceKey) {
    return reply({ error: { code: "SERVER_CONFIG_MISSING", message: "پیکربندی امن سرور کامل نیست." } }, 503);
  }

  const identityResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, Authorization: auth },
    signal: AbortSignal.timeout(12000),
  }).catch((error) => {
    console.error("EMA auth lookup failed", error instanceof Error ? error.message : "network error");
    return null;
  });
  if (!identityResponse?.ok) {
    return reply({ error: { code: "UNAUTHORIZED", message: "نشست ورود معتبر نیست؛ دوباره وارد حساب شو." } }, 401);
  }
  const identity = await identityResponse.json().catch(() => ({}));
  if (typeof identity.id !== "string") {
    return reply({ error: { code: "UNAUTHORIZED", message: "حساب کاربری معتبر نیست." } }, 401);
  }
  const userId = identity.id as string;

  const serviceFetch = async (path: string, init: RequestInit = {}) => {
    const response = await fetch(`${supabaseUrl}/rest/v1/${path}`, {
      ...init,
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
        Accept: "application/json",
        ...(init.headers || {}),
      },
    });
    const raw = await response.text();
    let data: any = null;
    try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
    if (!response.ok) {
      console.error("EMA billing database error", response.status, raw.slice(0, 300));
      const error = new Error(data?.message || data?.hint || "DATABASE_REQUEST_FAILED");
      (error as any).status = response.status;
      throw error;
    }
    return data;
  };

  const requireAdmin = async () => {
    const rows = await serviceFetch(`ema_admin_users?select=role&user_id=eq.${userId}&limit=1`);
    if (!Array.isArray(rows) || rows.length === 0) throw new Error("ADMIN_REQUIRED");
    return rows[0].role as string;
  };

  try {
    // Product lens: identify a photographed product using the server-side vision model.
    if (request.method === "POST" && route === "/v1/vision/product") {
      const body = await jsonBody(request);
      const imageData = String(body?.image || "");
      if (!imageData.startsWith("data:image/") || imageData.length > 5_500_000) {
        return reply({ error: { code: "INVALID_PRODUCT_IMAGE", message: "عکس معتبر نیست یا حجم آن زیاد است." } }, 400);
      }
      // Free image analysis uses GapGPT only. Never route this tool to paid NetArz.
      const providerKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || Deno.env.get("CHAT_SECONDARY_APLIKEY") || "";
      const providerBase = (Deno.env.get("CHAT_SECONDARY_BASE_URL") || "https://api.gapgpt.app/v1").replace(/\/+$/, "");
      const visionModel = Deno.env.get("CHAT_SECONDARY_VISION_MODEL") || Deno.env.get("CHAT_FREE_MODEL") || "gpt-4o-mini";
      if (!providerKey || !providerBase.startsWith("https://")) {
        return reply({ error: { code: "VISION_NOT_CONFIGURED", message: "سرویس تشخیص تصویر تنظیم نشده است." } }, 503);
      }
      const prompt = "Identify the main commercial product in this photo. Return concise Persian JSON only with keys product_name, brand, model, category, visual_description, search_query, confidence. Do not invent a brand or model; use empty string if uncertain. search_query should be a useful shopping search phrase in Persian and English.";
      const upstream = await fetch(`${providerBase}/chat/completions`, {
        method: "POST",
        headers: { Authorization: `Bearer ${providerKey}`, "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          model: visionModel,
          messages: [{ role: "user", content: [
            { type: "text", text: prompt },
            { type: "image_url", image_url: { url: imageData, detail: "high" } },
          ] }],
          max_tokens: 500,
          temperature: 0.1,
        }),
        signal: AbortSignal.timeout(90000),
      });
      if (!upstream.ok) {
        const detail = (await upstream.text()).slice(0, 500);
        console.error("EMA product vision failed", { status: upstream.status, detail: detail.slice(0, 160) });
        return reply({ error: { code: "VISION_PROVIDER_ERROR", message: `تشخیص تصویر ناموفق بود (HTTP ${upstream.status}).` } }, upstream.status);
      }
      const result = await upstream.json().catch(() => ({}));
      const content = result?.choices?.[0]?.message?.content;
      if (typeof content !== "string" || !content.trim()) {
        return reply({ error: { code: "VISION_EMPTY_RESULT", message: "مدل نتوانست محصول را تشخیص دهد." } }, 502);
      }
      return reply({ identification: content });
    }

    // Free image generation uses GapGPT only; no EMA wallet debit and no NetArz fallback.
    if (request.method === "POST" && route === "/v1/images/generations") {
      const body = await jsonBody(request);
      const prompt = String(body?.prompt || "").trim();
      const model = String(body?.model || Deno.env.get("CHAT_SECONDARY_IMAGE_MODEL") || "gpt-image-2").trim();
      if (!prompt || prompt.length > 4000 || model.length > 120) {
        return reply({ error: { code: "INVALID_IMAGE_REQUEST", message: "توضیح تصویر خالی یا بیش از حد طولانی است." } }, 400);
      }
      const gapgptKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || Deno.env.get("CHAT_SECONDARY_APLIKEY") || "";
      const gapgptBase = (Deno.env.get("CHAT_SECONDARY_BASE_URL") || "https://api.gapgpt.app/v1").replace(/\/+$/, "");
      if (!gapgptKey || !gapgptBase.startsWith("https://")) {
        return reply({ error: { code: "FREE_IMAGE_NOT_CONFIGURED", message: "سرویس ساخت تصویر رایگان GapGPT روی سرور تنظیم نشده است." } }, 503);
      }
      try {
        const upstream = await fetch(gapgptBase + "/images/generations", {
          method: "POST",
          headers: { Authorization: "Bearer " + gapgptKey, "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({ model, prompt, n: 1, size: "1024x1024", output_format: "png" }),
          signal: AbortSignal.timeout(180000),
        });
        const raw = await upstream.text();
        if (!upstream.ok) {
          console.error("EMA GapGPT free image generation failed", { status: upstream.status, detail: raw.slice(0, 160) });
          return reply({ error: { code: "FREE_IMAGE_PROVIDER_ERROR", message: "ساخت تصویر با GapGPT ناموفق بود (HTTP " + upstream.status + "). اعتبار و مدل تصویر را بررسی کن." } }, upstream.status);
        }
        let result: any;
        try { result = JSON.parse(raw); } catch {
          return reply({ error: { code: "FREE_IMAGE_INVALID_RESPONSE", message: "پاسخ GapGPT برای ساخت تصویر معتبر نبود." } }, 502);
        }
        console.log("EMA free image generated", { provider: "GapGPT", model });
        return reply(result);
      } catch (error) {
        console.error("EMA GapGPT free image request failed", error instanceof Error ? error.message : "network error");
        return reply({ error: { code: "FREE_IMAGE_PROVIDER_UNAVAILABLE", message: "سرویس ساخت تصویر GapGPT موقتاً در دسترس نیست؛ هیچ انتقالی به NetArz انجام نمی‌شود." } }, 503);
      }
    }

    // Free text-to-speech uses GapGPT only. Never fall back to NetArz.
    if (request.method === "POST" && route === "/v1/audio/speech") {
      const body = await jsonBody(request);
      const input = String(body?.input || "").trim();
      if (!input || input.length > 4096) {
        return reply({ error: { code: "INVALID_SPEECH_INPUT", message: "متن گفتار خالی یا بیش از حد طولانی است." } }, 400);
      }

      const gapgptKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || Deno.env.get("CHAT_SECONDARY_APLIKEY") || "";
      let gapgptBase = Deno.env.get("CHAT_SECONDARY_BASE_URL") || "https://api.gapgpt.app/v1";
      while (gapgptBase.endsWith("/")) gapgptBase = gapgptBase.slice(0, -1);
      const speechModel = Deno.env.get("CHAT_SECONDARY_TTS_MODEL") || String(body?.model || "gpt-4o-mini-tts");
      const speechVoice = Deno.env.get("CHAT_SECONDARY_TTS_VOICE") || String(body?.voice || "nova");
      const instructions = String(body?.instructions || "Speak Persian (Farsi) naturally and clearly.");

      if (!gapgptKey || !gapgptBase.startsWith("https://")) {
        return reply({ error: { code: "FREE_SPEECH_NOT_CONFIGURED", message: "سرویس صدای رایگان GapGPT روی سرور تنظیم نشده است." } }, 503);
      }

      try {
        const upstream = await fetch(`${gapgptBase}/audio/speech`, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${gapgptKey}`,
            "Content-Type": "application/json",
            Accept: "audio/mpeg, application/octet-stream, application/json",
          },
          body: JSON.stringify({
            model: speechModel,
            voice: speechVoice,
            input,
            instructions,
            response_format: "mp3",
          }),
          signal: AbortSignal.timeout(120000),
        });
        if (!upstream.ok) {
          const detail = (await upstream.text()).slice(0, 400);
          console.error("EMA GapGPT free speech failed", { status: upstream.status, detail: detail.slice(0, 160) });
          return reply({
            error: {
              code: "FREE_SPEECH_PROVIDER_ERROR",
              message: `ساخت صدای رایگان با GapGPT ناموفق بود (HTTP ${upstream.status}). لطفاً بعداً دوباره تلاش کن.`,
            },
          }, 503);
        }

        const audio = await upstream.arrayBuffer();
        if (!audio.byteLength) {
          return reply({ error: { code: "FREE_SPEECH_EMPTY_RESPONSE", message: "GapGPT فایل صوتی خالی برگرداند." } }, 502);
        }
        console.log("EMA free speech generated", { provider: "GapGPT", status: upstream.status });
        return new Response(audio, {
          status: 200,
          headers: {
            ...corsHeaders,
            "Content-Type": upstream.headers.get("Content-Type") || "audio/mpeg",
            "Cache-Control": "no-store",
          },
        });
      } catch (error) {
        console.error("EMA GapGPT free speech request failed", error instanceof Error ? error.message : "network error");
        return reply({
          error: {
            code: "FREE_SPEECH_PROVIDER_UNAVAILABLE",
            message: "سرویس صدای رایگان GapGPT موقتاً در دسترس نیست؛ هیچ انتقالی به NetArz انجام نمی‌شود.",
          },
        }, 503);
      }
    }

    // Billing routes are served by this same function so Android can use one secure gateway URL.
    if (route.startsWith("/v1/billing/")) {
      if (request.method === "GET" && route === "/v1/billing/admin/dashboard") {
        await requireAdmin();
        const snapshot = await serviceFetch("rpc/ema_admin_dashboard_snapshot", {
          method: "POST",
          body: JSON.stringify({ p_admin_id: userId }),
        });
        const providerAccounts: any[] = [];
        const checkedAt = new Date().toISOString();

        // NetArz documents GET /me and GET /usage for live account monitoring.
        const netarzKey = Deno.env.get("CHAT_PRIMARY_API_KEY") || Deno.env.get("Netarz") || "";
        const netarzBase = (Deno.env.get("CHAT_PRIMARY_BASE_URL") || "https://netarz.ir/api/ai/v1").replace(/\/+$/, "");
        if (!netarzKey || !netarzBase.startsWith("https://")) {
          providerAccounts.push({ provider: "NetArz", status: "not_configured", checked_at: checkedAt });
        } else {
          try {
            const accountResponse = await fetch(`${netarzBase}/me`, {
              headers: { Authorization: `Bearer ${netarzKey}`, Accept: "application/json" },
              signal: AbortSignal.timeout(12000),
            });
            if (!accountResponse.ok) {
              providerAccounts.push({ provider: "NetArz", status: "error", http_status: accountResponse.status, checked_at: checkedAt });
            } else {
              const account = await accountResponse.json();
              let usage: any = null;
              let usageStatus = "unavailable";
              try {
                const usageResponse = await fetch(`${netarzBase}/usage`, {
                  headers: { Authorization: `Bearer ${netarzKey}`, Accept: "application/json" },
                  signal: AbortSignal.timeout(12000),
                });
                if (usageResponse.ok) {
                  usage = await usageResponse.json();
                  usageStatus = "ok";
                } else {
                  usageStatus = "error";
                }
              } catch { usageStatus = "error"; }
              providerAccounts.push({
                provider: "NetArz",
                status: String(account?.status || "active"),
                balance_usd: account?.balance?.usd ?? null,
                balance_display: account?.balance?.usd_display ?? null,
                balance_toman_estimate: account?.balance?.toman_estimate ?? null,
                spent_usd: account?.key?.spent_usd ?? null,
                usage_30d_usd: usage?.total_usd ?? null,
                usage_status: usageStatus,
                project_name: account?.project?.name ?? null,
                key_name: account?.key?.name ?? null,
                rpm_limit: account?.key?.rpm_limit ?? null,
                checked_at: checkedAt,
              });
            }
          } catch {
            providerAccounts.push({ provider: "NetArz", status: "unreachable", checked_at: checkedAt });
          }
        }

        // GapGPT's balance endpoint is not verified. Report configuration only;
        // never infer a provider balance from EMA's internal cost ledger.
        const gapgptKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || Deno.env.get("CHAT_SECONDARY_APLIKEY") || "";
        providerAccounts.push({
          provider: "GapGPT",
          status: gapgptKey ? "configured_balance_unavailable" : "not_configured",
          checked_at: checkedAt,
        });

        return reply({ ...(snapshot || {}), provider_accounts: providerAccounts });
      }

      if (request.method === "GET" && route === "/v1/billing/catalog") {
        const [wallets, plans, subscriptions, settings, admins] = await Promise.all([
          serviceFetch(`ema_wallets?select=balance_toman&user_id=eq.${userId}&limit=1`),
          serviceFetch("ema_plans?select=id,name,duration_days,price_toman,ai_credit_percent&enabled=eq.true&order=sort_order.asc"),
          serviceFetch(`ema_subscriptions?select=plan_id,ends_at,ema_plans(name)&user_id=eq.${userId}&ends_at=gt.${new Date().toISOString()}&order=ends_at.desc&limit=1`),
          serviceFetch("ema_payment_settings?select=instructions,min_topup_toman,max_topup_toman&singleton=eq.true&limit=1"),
          serviceFetch(`ema_admin_users?select=user_id&user_id=eq.${userId}&limit=1`),
        ]);
        const setting = settings?.[0] || {};
        const active = subscriptions?.[0] || null;
        return reply({
          balanceToman: Number(wallets?.[0]?.balance_toman || 0),
          plans: plans || [],
          subscription: active ? { plan_name: active.ema_plans?.name || active.plan_id, ends_at: active.ends_at } : null,
          payment: {
            instructions: setting.instructions || "",
            minTopupToman: Number(setting.min_topup_toman || 100000),
            maxTopupToman: Number(setting.max_topup_toman || 50000000),
          },
          isAdmin: Array.isArray(admins) && admins.length > 0,
        });
      }

      if (request.method === "GET" && route === "/v1/billing/orders") {
        const rows = await serviceFetch(`ema_payment_orders?select=id,amount_toman,order_type,status,receipt_submitted_at,receipt_note,created_at&user_id=eq.${userId}&order=created_at.desc&limit=50`);
        return reply({ orders: (rows || []).map((o: any) => ({
          id: o.id, amount_toman: Number(o.amount_toman), purpose: o.order_type,
          status: o.status, receipt_submitted_at: o.receipt_submitted_at ? 1 : 0,
          rejection_reason: o.status === "rejected" ? (o.receipt_note || "") : "",
        })) });
      }

      if (request.method === "POST" && route === "/v1/billing/orders") {
        const body = await jsonBody(request);
        const purpose = body?.purpose;
        const key = String(body?.idempotencyKey || "").trim();
        if (!["wallet_topup", "subscription"].includes(purpose) || key.length < 8 || key.length > 100) {
          return reply({ error: { code: "INVALID_ORDER", message: "اطلاعات سفارش معتبر نیست." } }, 400);
        }
        const existing = await serviceFetch(`ema_payment_orders?select=id,status,amount_toman&user_id=eq.${userId}&idempotency_key=eq.${encodeURIComponent(key)}&limit=1`);
        if (existing?.length) return reply({ order: existing[0], duplicate: true });

        let amount = 0;
        let planId: string | null = null;
        if (purpose === "wallet_topup") {
          const settings = await serviceFetch("ema_payment_settings?select=min_topup_toman,max_topup_toman&singleton=eq.true&limit=1");
          const value = Number(body.amountToman);
          const min = Number(settings?.[0]?.min_topup_toman || 100000);
          const max = Number(settings?.[0]?.max_topup_toman || 50000000);
          if (!Number.isSafeInteger(value) || value < min || value > max) {
            return reply({ error: { code: "INVALID_TOPUP_AMOUNT", message: "مبلغ شارژ خارج از محدوده مجاز است." } }, 400);
          }
          amount = value;
        } else {
          planId = String(body.planId || "");
          const plans = await serviceFetch(`ema_plans?select=id,price_toman&enabled=eq.true&id=eq.${encodeURIComponent(planId)}&limit=1`);
          if (!plans?.length) return reply({ error: { code: "PLAN_NOT_AVAILABLE", message: "این پلن در حال حاضر فعال نیست." } }, 400);
          amount = Number(plans[0].price_toman);
        }
        const inserted = await serviceFetch("ema_payment_orders?select=id,amount_toman,order_type,status,created_at", {
          method: "POST",
          headers: { Prefer: "return=representation" },
          body: JSON.stringify({
            user_id: userId, order_type: purpose, plan_id: planId,
            amount_toman: amount, status: "pending", idempotency_key: key,
          }),
        });
        return reply({ order: inserted?.[0] || null, duplicate: false }, 201);
      }

      const receiptMatch = route.match(/^\/v1\/billing\/orders\/([0-9a-f-]{36})\/receipt$/i);
      if (request.method === "POST" && receiptMatch) {
        const body = await jsonBody(request);
        const reference = String(body?.receiptReference || "").trim();
        if (reference.length < 3 || reference.length > 160) {
          return reply({ error: { code: "INVALID_RECEIPT_REFERENCE", message: "شماره پیگیری معتبر نیست." } }, 400);
        }
        const rows = await serviceFetch(
          `ema_payment_orders?id=eq.${receiptMatch[1]}&user_id=eq.${userId}&status=eq.pending&select=id,status`,
          { method: "PATCH", headers: { Prefer: "return=representation" },
            body: JSON.stringify({ status: "receipt_submitted", payment_reference: reference, receipt_note: reference, receipt_submitted_at: new Date().toISOString(), updated_at: new Date().toISOString() }) },
        );
        if (!rows?.length) return reply({ error: { code: "ORDER_NOT_RECEIVABLE", message: "سفارش پیدا نشد یا قبلاً رسید آن ثبت شده است." } }, 409);
        return reply({ ok: true, order: rows[0] });
      }

      if (route === "/v1/billing/admin/orders" && request.method === "GET") {
        await requireAdmin();
        const rows = await serviceFetch("ema_payment_orders?select=id,user_id,amount_toman,order_type,plan_id,status,payment_reference,receipt_note,receipt_submitted_at,created_at&status=eq.receipt_submitted&order=created_at.asc&limit=100");
        return reply({ orders: rows || [] });
      }

      const reviewMatch = route.match(/^\/v1\/billing\/admin\/orders\/([0-9a-f-]{36})\/review$/i);
      if (request.method === "POST" && reviewMatch) {
        await requireAdmin();
        const body = await jsonBody(request);
        const decision = body?.decision;
        if (!["approve", "reject"].includes(decision)) {
          return reply({ error: { code: "INVALID_DECISION", message: "تصمیم مدیر معتبر نیست." } }, 400);
        }
        const result = await serviceFetch("rpc/ema_admin_review_payment", {
          method: "POST",
          body: JSON.stringify({
            p_order_id: reviewMatch[1], p_admin_id: userId,
            p_decision: decision, p_rejection_reason: String(body?.reason || "").slice(0, 300),
          }),
        });
        return reply(result);
      }

      return reply({ error: { code: "NOT_FOUND", message: "مسیر پرداخت پیدا نشد." } }, 404);
    }

    // Free chat uses GapGPT only; NetArz is reserved for paid features.
    if (request.method !== "POST" || !route.endsWith("/chat/completions")) return reply({ error: { code: "NOT_FOUND", message: "مسیر درخواست معتبر نیست." } }, 404);
    const raw = await request.text();
    if (new TextEncoder().encode(raw).length > 1_500_000) return reply({ error: { code: "REQUEST_TOO_LARGE", message: "حجم درخواست بیش از حد مجاز است." } }, 413);
    let body: any;
    try { body = JSON.parse(raw); } catch { return reply({ error: { code: "INVALID_JSON", message: "ساختار درخواست معتبر نیست." } }, 400); }
    if (!Array.isArray(body?.messages) || body.messages.length < 1 || body.messages.length > 30) return reply({ error: { code: "INVALID_REQUEST", message: "ساختار گفتگو معتبر نیست." } }, 400);
    if (JSON.stringify(body.messages).includes("data:image/")) return reply({ error: { code: "IMAGE_NOT_ENABLED", message: "تحلیل تصویر هنوز در درگاه امن فعال نشده است." } }, 503);

    const freeKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || Deno.env.get("CHAT_SECONDARY_APLIKEY") || "";
    let freeBase = Deno.env.get("CHAT_SECONDARY_BASE_URL") || "https://api.gapgpt.app/v1";
    while (freeBase.endsWith("/")) freeBase = freeBase.slice(0, -1);
    const freeModel = Deno.env.get("CHAT_FREE_MODEL") || Deno.env.get("CHAT_SECONDARY_MODEL") || "gpt-4o-mini";
    if (!freeKey || !freeBase.startsWith("https://")) return reply({ error: { code: "FREE_CHAT_NOT_CONFIGURED", message: "سرویس چت رایگان هنوز روی سرور تنظیم نشده است." } }, 503);

    const quotaDate = new Date().toISOString().slice(0, 10);
    const quotaResponse = await fetch(supabaseUrl + "/rest/v1/rpc/ema_consume_chat_quota", {
      method: "POST",
      headers: { apikey: serviceKey, Authorization: "Bearer " + serviceKey, "Content-Type": "application/json" },
      body: JSON.stringify({ p_user_id: userId, p_daily_limit: 50 }),
      signal: AbortSignal.timeout(12000),
    }).catch((error) => { console.error("EMA chat quota check failed", error instanceof Error ? error.message : "network error"); return null; });
    if (!quotaResponse?.ok) return reply({ error: { code: "QUOTA_SERVICE_UNAVAILABLE", message: "سرویس سهمیه چت آماده نیست؛ migration سهمیه را اجرا کن." } }, 503);
    if (await quotaResponse.json().catch(() => null) !== true) return reply({ error: { code: "DAILY_FREE_LIMIT_REACHED", message: "سهمیه رایگان امروز تمام شده است؛ فردا دوباره امتحان کن." } }, 429);

    const refundFreeQuota = async () => {
      const response = await fetch(supabaseUrl + "/rest/v1/rpc/ema_refund_chat_quota", {
        method: "POST",
        headers: { apikey: serviceKey, Authorization: "Bearer " + serviceKey, "Content-Type": "application/json" },
        body: JSON.stringify({ p_user_id: userId, p_usage_date: quotaDate }),
        signal: AbortSignal.timeout(8000),
      }).catch(() => null);
      if (!response?.ok) console.error("EMA failed to refund free chat quota after provider error");
    };

    const payload = { model: freeModel, messages: body.messages, stream: false, max_tokens: Math.min(1200, Math.max(1, Number(body.max_tokens) || 1200)), temperature: typeof body.temperature === "number" ? Math.min(1, Math.max(0, body.temperature)) : 0.7 };
    let upstream: Response;
    try {
      upstream = await fetch(freeBase + "/chat/completions", {
        method: "POST",
        headers: { Authorization: "Bearer " + freeKey, "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(30000),
      });
    } catch (error) {
      await refundFreeQuota();
      console.error("EMA GapGPT free chat request failed", error instanceof Error ? error.message : "network error");
      return reply({ error: { code: "FREE_CHAT_PROVIDER_UNAVAILABLE", message: "سرویس چت رایگان موقتاً پاسخگو نیست؛ کمی بعد دوباره تلاش کن." } }, 503);
    }
    const providerText = await upstream.text();
    if (!upstream.ok) {
      await refundFreeQuota();
      console.error("EMA GapGPT free chat returned an error", { status: upstream.status, detail: providerText.slice(0, 180) });
      return reply({ error: { code: "FREE_CHAT_PROVIDER_ERROR", message: "سرویس چت رایگان نتوانست پاسخ بدهد؛ کمی بعد دوباره تلاش کن." } }, 503);
    }
    let result: any;
    try { result = JSON.parse(providerText); } catch {
      await refundFreeQuota();
      return reply({ error: { code: "FREE_CHAT_INVALID_RESPONSE", message: "پاسخ سرویس چت معتبر نبود؛ دوباره تلاش کن." } }, 502);
    }
    if (!Array.isArray(result?.choices) || !result.choices.length) {
      await refundFreeQuota();
      return reply({ error: { code: "FREE_CHAT_EMPTY_RESPONSE", message: "پاسخی از سرویس چت دریافت نشد؛ دوباره تلاش کن." } }, 502);
    }
    return reply(result);
  } catch (error) {
    const code = error instanceof Error ? error.message : "INTERNAL_ERROR";
    const status = code === "ADMIN_REQUIRED" ? 403 : (error as any)?.status || 500;
    const message = code === "ADMIN_REQUIRED" ? "دسترسی مدیر برای این حساب فعال نیست."
      : code === "INVALID_JSON" ? "ساختار درخواست معتبر نیست."
      : "در انجام درخواست خطایی رخ داد؛ دوباره تلاش کن.";
    return reply({ error: { code, message } }, status);
  }
});
