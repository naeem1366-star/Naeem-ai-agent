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

  // NetArz pays for all free AI traffic; GapGPT is a failover only for transient outages.
  const aiProviderConfigs = () => {
    const netarzKey = Deno.env.get("NETARZ_API_KEY") || Deno.env.get("CHAT_PRIMARY_API_KEY") || Deno.env.get("Netarz") || "";
    const netarzBase = (Deno.env.get("NETARZ_BASE_URL") || Deno.env.get("CHAT_PRIMARY_BASE_URL") || "https://netarz.ir/api/ai/v1").replace(/\\/+$/, "");
    const gapKey = Deno.env.get("CHAT_SECONDARY_API_KEY") || Deno.env.get("CHAT_SECONDARY_APLIKEY") || "";
    const gapBase = (Deno.env.get("CHAT_SECONDARY_BASE_URL") || "https://api.gapgpt.app/v1").replace(/\\/+$/, "");
    return [
      {
        name: "NetArz", key: netarzKey, base: netarzBase,
        model: Deno.env.get("NETARZ_CHAT_MODEL") || Deno.env.get("CHAT_PRIMARY_MODEL") || Deno.env.get("CHAT_FREE_MODEL") || "gpt-4o-mini",
        visionModel: Deno.env.get("NETARZ_VISION_MODEL") || Deno.env.get("CHAT_PRIMARY_VISION_MODEL") || Deno.env.get("CHAT_PRIMARY_MODEL") || "gpt-4o-mini",
        imageModel: Deno.env.get("NETARZ_IMAGE_MODEL") || Deno.env.get("CHAT_PRIMARY_IMAGE_MODEL") || "gpt-image-1",
        speechModel: Deno.env.get("NETARZ_TTS_MODEL") || Deno.env.get("CHAT_PRIMARY_TTS_MODEL") || "gpt-4o-mini-tts",
      },
      {
        name: "GapGPT", key: gapKey, base: gapBase,
        model: Deno.env.get("CHAT_SECONDARY_MODEL") || Deno.env.get("CHAT_FREE_MODEL") || "gpt-4o-mini",
        visionModel: Deno.env.get("CHAT_SECONDARY_VISION_MODEL") || Deno.env.get("CHAT_FREE_MODEL") || "gpt-4o-mini",
        imageModel: Deno.env.get("CHAT_SECONDARY_IMAGE_MODEL") || "gpt-image-2",
        speechModel: Deno.env.get("CHAT_SECONDARY_TTS_MODEL") || "gpt-4o-mini-tts",
      },
    ].filter((provider) => provider.key && provider.base.startsWith("https://"));
  };

  const requestAiWithFallback = async (
    path: string,
    providers: ReturnType<typeof aiProviderConfigs>,
    payloadFor: (provider: ReturnType<typeof aiProviderConfigs>[number]) => unknown,
    timeoutMs: number,
  ) => {
    let lastFailure = "provider_unavailable";
    for (const provider of providers) {
      try {
        const response = await fetch(provider.base + path, {
          method: "POST",
          headers: {
            Authorization: "Bearer " + provider.key,
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify(payloadFor(provider)),
          signal: AbortSignal.timeout(timeoutMs),
        });
        if (response.ok) return { provider, response, raw: "" };
        const raw = await response.text();
        if (response.status === 408 || response.status === 429 || response.status >= 500) {
          lastFailure = provider.name + "_http_" + response.status;
          console.warn("EMA primary AI provider transient failure; trying fallback", { provider: provider.name, status: response.status });
          continue;
        }
        return { provider, response, raw };
      } catch (error) {
        lastFailure = provider.name + "_" + (error instanceof Error ? error.message : "network_error");
        console.warn("EMA AI provider connection failed; trying fallback", { provider: provider.name });
      }
    }
    return { provider: null, response: null, raw: "", error: lastFailure };
  };

  try {
    // Account-scoped personal memory. All writes are server-side and tied to the verified Auth user.
    if (route === "/v1/memory/items" && request.method === "GET") {
      const items = await serviceFetch(
        `ema_memory_items?select=id,memory_type,content,confidence,sensitivity,pinned,updated_at&user_id=eq.${userId}&order=pinned.desc,updated_at.desc&limit=100`
      );
      return reply({ items: items || [], sync: "account" });
    }

    if (route === "/v1/memory/items" && request.method === "POST") {
      const body = await jsonBody(request);
      const content = String(body?.content || "").trim();
      const memoryType = String(body?.memoryType || "note");
      const source = String(body?.source || "user_explicit");
      const sensitivity = String(body?.sensitivity || "normal");
      if (!content || content.length > 4000 ||
          !["preference", "fact", "project", "decision", "note", "correction"].includes(memoryType) ||
          !["user_explicit", "user_correction", "assistant_suggested", "imported"].includes(source) ||
          !["normal", "sensitive"].includes(sensitivity)) {
        return reply({ error: { code: "INVALID_MEMORY", message: "اطلاعات حافظه معتبر نیست." } }, 400);
      }
      // Never promote inferred or sensitive information into durable memory without user action.
      if (source === "assistant_suggested" || sensitivity === "sensitive") {
        return reply({ error: { code: "MEMORY_REQUIRES_APPROVAL", message: "ذخیره این نوع اطلاعات به تأیید صریح کاربر نیاز دارد." } }, 403);
      }
      const normalizedKey = content.toLocaleLowerCase("fa-IR").replace(/\s+/g, " ").slice(0, 4000);
      const rows = await serviceFetch("ema_memory_items?on_conflict=user_id,normalized_key", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=representation" },
        body: JSON.stringify({
          user_id: userId, memory_type: memoryType, content, normalized_key: normalizedKey,
          confidence: source === "user_correction" ? 1 : 0.95, source, sensitivity,
          updated_at: new Date().toISOString(),
        }),
      });
      return reply({ item: rows?.[0] || null }, 201);
    }

    const memoryDelete = route.match(/^\/v1\/memory\/items\/([0-9a-f-]{36})$/i);
    if (memoryDelete && request.method === "DELETE") {
      await serviceFetch(`ema_memory_items?id=eq.${memoryDelete[1]}&user_id=eq.${userId}`, { method: "DELETE" });
      return reply({ ok: true });
    }

    if (route === "/v1/memory/profile" && request.method === "GET") {
      const rows = await serviceFetch(
        `ema_profiles?select=display_name,preferences,memory_sync_enabled&user_id=eq.${userId}&limit=1`
      );
      return reply({ profile: rows?.[0] || { display_name: "", preferences: {}, memory_sync_enabled: true } });
    }

    if (route === "/v1/memory/profile" && request.method === "PATCH") {
      const body = await jsonBody(request);
      const patch: Record<string, unknown> = { updated_at: new Date().toISOString() };
      if (typeof body?.displayName === "string") patch.display_name = body.displayName.trim().slice(0, 80);
      if (body?.preferences && typeof body.preferences === "object" && !Array.isArray(body.preferences)) {
        patch.preferences = body.preferences;
      }
      if (typeof body?.memorySyncEnabled === "boolean") patch.memory_sync_enabled = body.memorySyncEnabled;
      const rows = await serviceFetch(`ema_profiles?user_id=eq.${userId}`, {
        method: "PATCH", headers: { Prefer: "return=representation" }, body: JSON.stringify(patch),
      });
      return reply({ profile: rows?.[0] || null });
    }

    if (route === "/v1/memory/learning-events" && request.method === "GET") {
      const events = await serviceFetch(
        `ema_learning_events?select=id,event_type,summary,status,created_at&user_id=eq.${userId}&order=created_at.desc&limit=20`
      );
      return reply({ events: events || [] });
    }

    if (route === "/v1/memory/learning-events" && request.method === "POST") {
      const body = await jsonBody(request);
      const eventType = String(body?.eventType || "");
      const summary = String(body?.summary || "").trim();
      if (!["correction", "preference_signal", "task_outcome", "memory_review", "user_feedback"].includes(eventType) ||
          !summary || summary.length > 2000) {
        return reply({ error: { code: "INVALID_LEARNING_EVENT", message: "گزارش یادگیری معتبر نیست." } }, 400);
      }
      const rows = await serviceFetch("ema_learning_events?select=id,event_type,status,created_at", {
        method: "POST", headers: { Prefer: "return=representation" },
        body: JSON.stringify({ user_id: userId, event_type: eventType, summary, status: "pending" }),
      });
      return reply({ event: rows?.[0] || null }, 201);
    }

    if (route === "/v1/ai/providers" && request.method === "GET") {
      const providers = aiProviderConfigs();
      const netarz = providers.find((provider) => provider.name === "NetArz");
      const gapgpt = providers.find((provider) => provider.name === "GapGPT");
      return reply({
        chat: [
          { id: "netarz", configured: !!netarz, role: "primary" },
          { id: "gapgpt", configured: !!gapgpt, role: "fallback" },
        ],
        image: { primary: "NetArz", fallback: "GapGPT", configured: !!netarz || !!gapgpt },
        vision: { primary: "NetArz", fallback: "GapGPT", configured: !!netarz || !!gapgpt },
        speech: { primary: "NetArz", fallback: "GapGPT", configured: !!netarz || !!gapgpt },
        webSearch: { configured: !!Deno.env.get("EMA_SEARCH_API_KEY"), provider: Deno.env.get("EMA_SEARCH_PROVIDER") || "brave" },
        memory: { available: true, storage: "account_scoped" },
      });
    }

    if (route === "/v1/search/web" && request.method === "POST") {
      const body = await jsonBody(request);
      const query = String(body?.query || "").trim();
      const count = Math.min(10, Math.max(1, Number(body?.count) || 8));
      const searchKey = Deno.env.get("EMA_SEARCH_API_KEY") || "";
      const searchProvider = (Deno.env.get("EMA_SEARCH_PROVIDER") || "brave").toLowerCase();
      if (!query || query.length > 500) return reply({ error: { code: "INVALID_SEARCH_QUERY", message: "عبارت جست‌وجو معتبر نیست." } }, 400);
      if (!searchKey || searchProvider !== "brave") {
        return reply({ error: { code: "WEB_SEARCH_NOT_CONFIGURED", message: "موتور جست‌وجوی امن هنوز روی سرور تنظیم نشده است." } }, 503);
      }
      const searchUrl = new URL("https://api.search.brave.com/res/v1/web/search");
      searchUrl.searchParams.set("q", query);
      searchUrl.searchParams.set("count", String(count));
      const upstream = await fetch(searchUrl, {
        headers: { "X-Subscription-Token": searchKey, Accept: "application/json" },
        signal: AbortSignal.timeout(18000),
      });
      if (!upstream.ok) {
        console.error("EMA web search provider failed", { status: upstream.status });
        return reply({ error: { code: "WEB_SEARCH_PROVIDER_ERROR", message: "جست‌وجوی وب موقتاً ناموفق بود." } }, 502);
      }
      const data: any = await upstream.json().catch(() => ({}));
      const results = (data?.web?.results || []).slice(0, count).map((item: any) => ({
        title: String(item?.title || "").slice(0, 300),
        url: String(item?.url || ""),
        snippet: String(item?.description || "").slice(0, 1200),
        published: item?.page_age || item?.age || null,
      })).filter((item: any) => item.title && item.url.startsWith("https://"));
      return reply({ provider: "brave", searched_at: new Date().toISOString(), results });
    }

    // Free AI media uses NetArz first. GapGPT is only a transient-outage fallback.
    if (request.method === "POST" && route === "/v1/vision/product") {
      const body = await jsonBody(request);
      const imageData = String(body?.image || "");
      if (!imageData.startsWith("data:image/") || imageData.length > 5_500_000) {
        return reply({ error: { code: "INVALID_PRODUCT_IMAGE", message: "عکس معتبر نیست یا حجم آن زیاد است." } }, 400);
      }
      const providers = aiProviderConfigs();
      const available = providers.filter((provider) => provider.visionModel);
      if (!available.length) return reply({ error: { code: "VISION_NOT_CONFIGURED", message: "اتصال NetArz و مسیر پشتیبان تشخیص تصویر تنظیم نشده است." } }, 503);
      const prompt = "Identify the main commercial product in this photo. Return concise Persian JSON only with keys product_name, brand, model, category, visual_description, search_query, confidence. Do not invent a brand or model; use empty string if uncertain. search_query should be a useful shopping search phrase in Persian and English.";
      const result = await requestAiWithFallback("/chat/completions", available, (provider) => ({
        model: provider.visionModel,
        messages: [{ role: "user", content: [
          { type: "text", text: prompt },
          { type: "image_url", image_url: { url: imageData, detail: "high" } },
        ] }],
        max_tokens: 500,
        temperature: 0.1,
      }), 90000);
      if (!result.response) {
        console.error("EMA product vision providers unavailable", { reason: result.error });
        return reply({ error: { code: "VISION_PROVIDER_UNAVAILABLE", message: "تشخیص تصویر با NetArz و مسیر پشتیبان موقتاً در دسترس نیست." } }, 503);
      }
      if (!result.response.ok) {
        console.error("EMA product vision provider rejected request", { provider: result.provider?.name, status: result.response.status, detail: result.raw.slice(0, 160) });
        return reply({ error: { code: "VISION_PROVIDER_ERROR", message: "تشخیص تصویر ناموفق بود (HTTP " + result.response.status + "). مدل بینایی و اعتبار سرویس را بررسی کن." } }, result.response.status);
      }
      const data: any = await result.response.json().catch(() => ({}));
      const identification = data?.choices?.[0]?.message?.content;
      if (typeof identification !== "string" || !identification.trim()) {
        return reply({ error: { code: "VISION_EMPTY_RESULT", message: "مدل نتوانست محصول را تشخیص دهد." } }, 502);
      }
      console.log("EMA product vision completed", { provider: result.provider?.name, model: result.provider?.visionModel });
      return reply({ identification });
    }

    if (request.method === "POST" && route === "/v1/images/generations") {
      const body = await jsonBody(request);
      const prompt = String(body?.prompt || "").trim();
      const requestedModel = String(body?.model || "").trim();
      if (!prompt || prompt.length > 4000 || requestedModel.length > 120) {
        return reply({ error: { code: "INVALID_IMAGE_REQUEST", message: "توضیح تصویر خالی یا بیش از حد طولانی است." } }, 400);
      }
      const providers = aiProviderConfigs();
      const available = providers.filter((provider) => provider.imageModel);
      if (!available.length) return reply({ error: { code: "FREE_IMAGE_NOT_CONFIGURED", message: "اتصال NetArz و مسیر پشتیبان ساخت تصویر تنظیم نشده است." } }, 503);
      const result = await requestAiWithFallback("/images/generations", available, (provider) => {
        let model = requestedModel || provider.imageModel;
        // The app's former default was gpt-image-2; NetArz documents gpt-image-1 as its GPT Image model.
        if (provider.name === "NetArz" && model === "gpt-image-2") model = provider.imageModel || "gpt-image-1";
        if (provider.name === "GapGPT" && !requestedModel) model = provider.imageModel;
        return { model, prompt, n: 1, size: "1024x1024", output_format: "png" };
      }, 180000);
      if (!result.response) {
        console.error("EMA free image providers unavailable", { reason: result.error });
        return reply({ error: { code: "FREE_IMAGE_PROVIDER_UNAVAILABLE", message: "ساخت تصویر با NetArz و مسیر پشتیبان موقتاً در دسترس نیست." } }, 503);
      }
      const raw = result.response.ok ? await result.response.text() : result.raw;
      if (!result.response.ok) {
        console.error("EMA free image provider rejected request", { provider: result.provider?.name, status: result.response.status, detail: raw.slice(0, 160) });
        return reply({ error: { code: "FREE_IMAGE_PROVIDER_ERROR", message: "ساخت تصویر ناموفق بود (HTTP " + result.response.status + "). مدل و اعتبار سرویس را بررسی کن." } }, result.response.status);
      }
      let data: any;
      try { data = JSON.parse(raw); } catch {
        return reply({ error: { code: "FREE_IMAGE_INVALID_RESPONSE", message: "پاسخ سرویس ساخت تصویر در قالب معتبر نبود." } }, 502);
      }
      console.log("EMA free image generated", { provider: result.provider?.name, model: data?.model || requestedModel });
      return reply(data);
    }

    if (request.method === "POST" && route === "/v1/audio/speech") {
      const body = await jsonBody(request);
      const input = String(body?.input || "").trim();
      if (!input || input.length > 4096) {
        return reply({ error: { code: "INVALID_SPEECH_INPUT", message: "متن گفتار خالی یا بیش از حد طولانی است." } }, 400);
      }
      const providers = aiProviderConfigs();
      const available = providers.filter((provider) => provider.speechModel);
      if (!available.length) return reply({ error: { code: "FREE_SPEECH_NOT_CONFIGURED", message: "اتصال NetArz و مسیر پشتیبان گفتار تنظیم نشده است." } }, 503);
      const result = await requestAiWithFallback("/audio/speech", available, (provider) => ({
        model: provider.name === "NetArz"
          ? (Deno.env.get("NETARZ_TTS_MODEL") || Deno.env.get("CHAT_PRIMARY_TTS_MODEL") || String(body?.model || provider.speechModel))
          : (Deno.env.get("CHAT_SECONDARY_TTS_MODEL") || String(body?.model || provider.speechModel)),
        voice: String(body?.voice || Deno.env.get("NETARZ_TTS_VOICE") || Deno.env.get("CHAT_SECONDARY_TTS_VOICE") || "nova"),
        input,
        instructions: String(body?.instructions || "Speak Persian (Farsi) naturally and clearly."),
        response_format: "mp3",
      }), 120000);
      if (!result.response) {
        console.error("EMA free speech providers unavailable", { reason: result.error });
        return reply({ error: { code: "FREE_SPEECH_PROVIDER_UNAVAILABLE", message: "تبدیل متن به گفتار با NetArz و مسیر پشتیبان موقتاً در دسترس نیست." } }, 503);
      }
      if (!result.response.ok) {
        const detail = result.raw.slice(0, 180);
        console.error("EMA free speech provider rejected request", { provider: result.provider?.name, status: result.response.status, detail });
        return reply({ error: { code: "FREE_SPEECH_PROVIDER_ERROR", message: "ساخت صدای فارسی ناموفق بود (HTTP " + result.response.status + "). مدل و اعتبار سرویس را بررسی کن." } }, 503);
      }
      const audio = await result.response.arrayBuffer();
      if (!audio.byteLength) return reply({ error: { code: "FREE_SPEECH_EMPTY_RESPONSE", message: "سرویس صوتی فایل خالی برگرداند." } }, 502);
      console.log("EMA free speech generated", { provider: result.provider?.name, model: result.provider?.speechModel });
      return new Response(audio, {
        status: 200,
        headers: {
          ...corsHeaders,
          "Content-Type": result.response.headers.get("Content-Type") || "audio/mpeg",
          "Cache-Control": "no-store",
        },
      });
    }

    // Paid media tools use NetArz only. Provider secrets never leave this function.
    // Keep NetArz media credentials isolated from generic chat-provider settings.
    const mediaNetarzKey = Deno.env.get("NETARZ_API_KEY") || Deno.env.get("Netarz") || Deno.env.get("CHAT_PRIMARY_API_KEY") || "";
    const mediaNetarzBase = (Deno.env.get("NETARZ_BASE_URL") || "https://netarz.ir/api/ai/v1").replace(/\/+$/, "");
    const mediaProviderFetch = async (path: string, init: RequestInit = {}) => {
      if (!mediaNetarzKey || !mediaNetarzBase.startsWith("https://")) throw new Error("NETARZ_NOT_CONFIGURED");
      return await fetch(mediaNetarzBase + path, {
        ...init,
        headers: { Authorization: `Bearer ${mediaNetarzKey}`, Accept: "application/json", ...(init.headers || {}) },
        signal: init.signal || AbortSignal.timeout(180000),
      });
    };
    const getMediaAccess = async (feature: string, model: string, estimateUsd: number) => {
      const profiles = await serviceFetch(`ema_profiles?select=status&user_id=eq.${userId}&limit=1`);
      if (!Array.isArray(profiles) || profiles.length === 0 || profiles[0].status !== "active") {
        throw new Error("ACCOUNT_NOT_ACTIVE");
      }
      const [admins, wallets, subscriptions, settings] = await Promise.all([
        serviceFetch(`ema_admin_users?select=user_id&user_id=eq.${userId}&limit=1`),
        serviceFetch(`ema_wallets?select=balance_toman&user_id=eq.${userId}&limit=1`),
        serviceFetch(`ema_subscriptions?select=id&user_id=eq.${userId}&starts_at=lte.${encodeURIComponent(new Date().toISOString())}&ends_at=gt.${encodeURIComponent(new Date().toISOString())}&limit=1`),
        serviceFetch("ema_billing_settings?select=ai_billing_enabled,usd_to_toman,markup_bps&singleton=eq.true&limit=1"),
      ]);
      const isAdmin = Array.isArray(admins) && admins.length > 0;
      const hasSubscription = Array.isArray(subscriptions) && subscriptions.length > 0;
      const walletBalance = Number(wallets?.[0]?.balance_toman || 0);
      const billing = settings?.[0] || {};
      const rate = Number(billing.usd_to_toman || 0);
      const markup = Math.max(0, Number(billing.markup_bps || 0));
      const billingReady = billing.ai_billing_enabled === true && rate > 0;
      if (!isAdmin && !hasSubscription && walletBalance <= 0) throw new Error("PAID_ACCESS_REQUIRED");
      if (!isAdmin && !hasSubscription && !billingReady) throw new Error("AI_BILLING_NOT_ENABLED");
      let reservationId: string | null = null;
      let reserveToman = 0;
      if (!isAdmin && billingReady && walletBalance > 0) {
        if (!(estimateUsd > 0) || !Number.isFinite(estimateUsd)) throw new Error("MEDIA_MODEL_PRICE_MISSING");
        reserveToman = Math.max(1, Math.ceil(estimateUsd * rate * (1 + markup / 10000) * 1.5));
        const reserved = await serviceFetch("rpc/ema_reserve_wallet", {
          method: "POST",
          body: JSON.stringify({
            p_user_id: userId, p_amount_toman: reserveToman,
            p_reference: crypto.randomUUID(), p_feature: feature, p_model: model,
          }),
        });
        if (reserved?.ok && reserved?.reservation_id) {
          reservationId = String(reserved.reservation_id);
        } else if (!hasSubscription) {
          throw new Error("INSUFFICIENT_AI_CREDIT");
        }
      }
      return { isAdmin, hasSubscription, rate, markup, reservationId, reserveToman };
    };
    const settleMediaReservation = async (
      reservationId: string | null, feature: string, model: string, actualUsd: number,
      rate: number, markup: number, isAdmin: boolean, hasSubscription: boolean,
      metadata: Record<string, unknown> = {},
      reserveToman: number = Number.MAX_SAFE_INTEGER,
    ) => {
      const safeUsd = Math.max(0, Number.isFinite(actualUsd) ? actualUsd : 0);
      if (reservationId) {
        const actualToman = Math.min(reserveToman, Math.max(0, Math.ceil(safeUsd * rate * (1 + markup / 10000))));
        await serviceFetch("rpc/ema_settle_wallet", {
          method: "POST",
          body: JSON.stringify({
            p_reservation_id: reservationId, p_actual_toman: actualToman,
            p_provider: "NetArz", p_input_tokens: 0, p_output_tokens: 0, p_provider_cost_usd: safeUsd,
          }),
        });
        const events = await serviceFetch(`ema_usage_events?select=id&reservation_id=eq.${reservationId}&limit=1`);
        const eventId = events?.[0]?.id;
        if (eventId) await serviceFetch("ema_provider_cost_ledger", {
          method: "POST", headers: { Prefer: "return=minimal" },
          body: JSON.stringify({ user_id: userId, usage_event_id: eventId, provider: "NetArz", model, cost_usd: safeUsd }),
        }).catch((error) => console.error("EMA media cost ledger insert failed", error instanceof Error ? error.message : "unknown"));
      } else {
        const events = await serviceFetch("ema_usage_events?select=id", {
          method: "POST", headers: { Prefer: "return=representation" },
          body: JSON.stringify({
            user_id: userId, reservation_id: null, feature, provider: "NetArz", model,
            input_tokens: 0, output_tokens: 0, provider_cost_usd: safeUsd, charged_toman: 0,
            metadata: { access: isAdmin ? "admin" : hasSubscription ? "subscription" : "wallet", ...metadata },
          }),
        });
        const eventId = events?.[0]?.id;
        if (eventId) await serviceFetch("ema_provider_cost_ledger", {
          method: "POST", headers: { Prefer: "return=minimal" },
          body: JSON.stringify({ user_id: userId, usage_event_id: eventId, provider: "NetArz", model, cost_usd: safeUsd }),
        });
      }
    };
    const getMediaCatalog = async (type: "video" | "music") => {
      const response = await mediaProviderFetch(`/models?type=${type}`, { method: "GET" });
      const raw = await response.text();
      if (!response.ok) {
        // Keep the provider HTTP status in the gateway error so 401/403/404/429 can be
        // distinguished from a missing EMA route while never logging or returning credentials.
        throw new Error("NETARZ_MODEL_CATALOG_HTTP_" + response.status + ":" + raw.slice(0, 240));
      }
      return JSON.parse(raw);
    };

    if (request.method === "GET" && route === "/v1/media/models") {
      const type = url.searchParams.get("type");
      if (type !== "video" && type !== "music") return reply({ error: { code: "INVALID_MEDIA_TYPE", message: "نوع فهرست رسانه معتبر نیست." } }, 400);
      try { return reply(await getMediaCatalog(type)); }
      catch (error) {
        const detail = error instanceof Error ? error.message : "unknown";
        const upstreamStatus = detail.match(/NETARZ_MODEL_CATALOG_HTTP_(\\d{3})/)?.[1] || "";
        console.error("EMA NetArz media catalog failed", detail.slice(0, 320));
        return reply({
          error: {
            code: "NETARZ_MODEL_CATALOG_FAILED",
            upstream_status: upstreamStatus ? Number(upstreamStatus) : null,
            message: upstreamStatus
              ? "فهرست مدل‌های NetArz پاسخ HTTP " + upstreamStatus + " داد؛ کلید و نشانی اختصاصی NetArz را بررسی کن."
              : "دریافت فهرست مدل‌های NetArz ناموفق بود؛ اتصال سرور یا پاسخ JSON را بررسی کن.",
          },
        }, 502);
      }
    }

    if (request.method === "POST" && route === "/v1/media/music") {
      const body = await jsonBody(request);
      const model = String(body?.model || "").trim();
      const prompt = String(body?.prompt || "").trim();
      if (!model || model.length > 180 || !prompt || prompt.length > 4000) {
        return reply({ error: { code: "INVALID_MUSIC_REQUEST", message: "مدل یا توضیح موسیقی معتبر نیست." } }, 400);
      }
      let reservationId: string | null = null;
      try {
        const catalog = await getMediaCatalog("music");
        const item = (catalog?.data || []).find((entry: any) => entry?.id === model);
        if (!item) return reply({ error: { code: "MEDIA_MODEL_NOT_ALLOWED", message: "مدل موسیقی در فهرست فعال NetArz نیست." } }, 400);
        const pricing = item?.pricing || {};
        const seconds = Number(body?.seconds || 0);
        const perRequest = Number(pricing.per_request || 0);
        const perMinute = Number(pricing.per_minute || 0);
        const estimateUsd = perRequest + (perMinute > 0 && seconds > 0 ? perMinute * seconds / 60 : 0);
        if (!(estimateUsd > 0)) return reply({ error: { code: "MEDIA_MODEL_PRICE_MISSING", message: "قیمت این مدل موسیقی از فهرست NetArz دریافت نشد؛ تولید متوقف شد." } }, 503);
        const access = await getMediaAccess("ai_music", model, estimateUsd);
        reservationId = access.reservationId;
        const payload: Record<string, unknown> = { model, prompt };
        if (body?.seconds != null) payload.seconds = body.seconds;
        if (body?.temperature != null) payload.temperature = body.temperature;
        if (body?.seed != null) payload.seed = body.seed;
        const upstream = await mediaProviderFetch("/audio/music", {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload), signal: AbortSignal.timeout(240000),
        });
        const raw = await upstream.text();
        if (!upstream.ok) {
          if (reservationId) await serviceFetch("rpc/ema_release_wallet", {
            method: "POST", body: JSON.stringify({ p_reservation_id: reservationId, p_reason: `netarz_music_http_${upstream.status}` }),
          });
          return reply({ error: { code: "NETARZ_MUSIC_ERROR", message: `ساخت موسیقی با NetArz ناموفق بود (HTTP ${upstream.status}).` } }, 502);
        }
        let result: any;
        try { result = JSON.parse(raw); } catch { throw new Error("NETARZ_INVALID_MUSIC_RESPONSE"); }
        const reportedUsd = Number(result?.usd ?? result?.usage?.cost_usd);
        const returnedUsd = Number.isFinite(reportedUsd) && reportedUsd > 0 ? reportedUsd : estimateUsd;
        await settleMediaReservation(reservationId, "ai_music", model, returnedUsd, access.rate, access.markup, access.isAdmin, access.hasSubscription,
          { request_id: result?.id || null, seconds }, access.reserveToman);
        console.log("EMA paid music completed", { user_id: userId, model, provider: "NetArz", cost_usd: returnedUsd });
        return reply(result);
      } catch (error) {
        if (reservationId) await serviceFetch("rpc/ema_release_wallet", {
          method: "POST", body: JSON.stringify({ p_reservation_id: reservationId, p_reason: "netarz_music_request_failed" }),
        }).catch(() => null);
        const code = error instanceof Error ? error.message : "NETARZ_MUSIC_FAILED";
        const status = code === "PAID_ACCESS_REQUIRED" || code === "INSUFFICIENT_AI_CREDIT" ? 402 :
          code === "ACCOUNT_NOT_ACTIVE" ? 403 : code === "AI_BILLING_NOT_ENABLED" || code === "MEDIA_MODEL_PRICE_MISSING" ? 503 : 502;
        return reply({ error: { code, message: code === "PAID_ACCESS_REQUIRED" ? "برای ساخت موسیقی اشتراک فعال یا اعتبار کیف پول لازم است." :
          code === "INSUFFICIENT_AI_CREDIT" ? "اعتبار کیف پول برای هزینه این قطعه کافی نیست." :
          code === "AI_BILLING_NOT_ENABLED" ? "محاسبه هزینه و کسر اعتبار هنوز توسط مدیر فعال نشده است." :
          code === "ACCOUNT_NOT_ACTIVE" ? "حساب کاربری فعال نیست." : "درخواست ساخت موسیقی انجام نشد؛ تنظیمات مدل و سرویس را بررسی کن." } }, status);
      }
    }

    if (request.method === "POST" && route === "/v1/media/videos") {
      const body = await jsonBody(request);
      const model = String(body?.model || "").trim();
      const prompt = String(body?.prompt || "").trim();
      const seconds = Math.max(1, Math.min(60, Number(body?.seconds || 4)));
      const size = String(body?.size || "auto").trim();
      if (!model || model.length > 180 || !prompt || prompt.length > 4000 ||
          (!/^(\d{3,4})x(\d{3,4})$/.test(size) && size !== "auto")) {
        return reply({ error: { code: "INVALID_VIDEO_REQUEST", message: "مدل، توضیح یا ابعاد ویدیو معتبر نیست." } }, 400);
      }
      let reservationId: string | null = null;
      try {
        const catalog = await getMediaCatalog("video");
        const item = (catalog?.data || []).find((entry: any) => entry?.id === model);
        if (!item) return reply({ error: { code: "MEDIA_MODEL_NOT_ALLOWED", message: "مدل ویدیو در فهرست فعال NetArz نیست." } }, 400);
        const pricing = item?.pricing || {};
        const caps = item?.capabilities || {};
        const multiplier = Number(caps.size_multipliers?.[size] || 1);
        const perSecond = Number(pricing.per_second || 0);
        const estimateUsd = perSecond * seconds * multiplier;
        if (!(estimateUsd > 0)) return reply({ error: { code: "MEDIA_MODEL_PRICE_MISSING", message: "قیمت این مدل ویدیو از فهرست NetArz دریافت نشد؛ تولید متوقف شد." } }, 503);
        const access = await getMediaAccess("professional_video", model, estimateUsd);
        reservationId = access.reservationId;
        const payload: Record<string, unknown> = { model, prompt, seconds: String(seconds), size };
        if (typeof body?.image === "string" && body.image.length <= 8_000_000) payload.image = body.image;
        if (typeof body?.video === "string" && body.video.length <= 18_000_000) payload.video = body.video;
        if (new TextEncoder().encode(JSON.stringify(payload)).length > 19_000_000) {
          if (reservationId) await serviceFetch("rpc/ema_release_wallet", { method: "POST", body: JSON.stringify({ p_reservation_id: reservationId, p_reason: "media_payload_too_large" }) });
          return reply({ error: { code: "MEDIA_PAYLOAD_TOO_LARGE", message: "حجم فایل مرجع بیش از حد مجاز است." } }, 413);
        }
        const upstream = await mediaProviderFetch("/videos", {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload), signal: AbortSignal.timeout(120000),
        });
        const raw = await upstream.text();
        if (!upstream.ok) {
          if (reservationId) await serviceFetch("rpc/ema_release_wallet", {
            method: "POST", body: JSON.stringify({ p_reservation_id: reservationId, p_reason: `netarz_video_http_${upstream.status}` }),
          });
          return reply({ error: { code: "NETARZ_VIDEO_ERROR", message: `شروع تولید ویدیو ناموفق بود (HTTP ${upstream.status}).` } }, 502);
        }
        const job = JSON.parse(raw);
        const providerJobId = String(job?.id || job?.data?.id || "");
        if (!providerJobId || !/^[A-Za-z0-9_-]{1,160}$/.test(providerJobId)) throw new Error("NETARZ_INVALID_VIDEO_JOB");
        const reportedUsd = Number(job?.usd);
        const actualEstimate = Number.isFinite(reportedUsd) && reportedUsd > 0 ? reportedUsd : estimateUsd;
        await serviceFetch("ema_media_jobs", {
          method: "POST", headers: { Prefer: "return=minimal" },
          body: JSON.stringify({
            user_id: userId, provider_job_id: providerJobId, feature: "professional_video",
            model, reservation_id: reservationId, estimated_cost_usd: actualEstimate,
            size, seconds, reserve_toman: access.reserveToman, status: String(job?.status || "queued"),
          }),
        });
        return reply({ ...job, id: providerJobId, model, usd: actualEstimate, ema_job_id: providerJobId }, 202);
      } catch (error) {
        if (reservationId) await serviceFetch("rpc/ema_release_wallet", {
          method: "POST", body: JSON.stringify({ p_reservation_id: reservationId, p_reason: "netarz_video_create_failed" }),
        }).catch(() => null);
        const code = error instanceof Error ? error.message : "NETARZ_VIDEO_FAILED";
        const status = code === "PAID_ACCESS_REQUIRED" || code === "INSUFFICIENT_AI_CREDIT" ? 402 :
          code === "ACCOUNT_NOT_ACTIVE" ? 403 : code === "AI_BILLING_NOT_ENABLED" || code === "MEDIA_MODEL_PRICE_MISSING" ? 503 : 502;
        return reply({ error: { code, message: code === "PAID_ACCESS_REQUIRED" ? "برای تولید ویدیوی حرفه‌ای اشتراک فعال یا اعتبار کیف پول لازم است." :
          code === "INSUFFICIENT_AI_CREDIT" ? "اعتبار کیف پول برای هزینه این ویدیو کافی نیست." :
          code === "AI_BILLING_NOT_ENABLED" ? "محاسبه هزینه و کسر اعتبار هنوز توسط مدیر فعال نشده است." :
          code === "ACCOUNT_NOT_ACTIVE" ? "حساب کاربری فعال نیست." : "درخواست تولید ویدیوی حرفه‌ای انجام نشد؛ تنظیمات مدل را بررسی کن." } }, status);
      }
    }

    const mediaJobMatch = route.match(/^\/v1\/media\/videos\/([A-Za-z0-9_-]{1,160})(?:\/(content))?$/);
    if (request.method === "GET" && mediaJobMatch) {
      const providerJobId = mediaJobMatch[1];
      const isContent = mediaJobMatch[2] === "content";
      const jobs = await serviceFetch(`ema_media_jobs?select=*&provider_job_id=eq.${encodeURIComponent(providerJobId)}&user_id=eq.${userId}&limit=1`);
      const savedJob = jobs?.[0];
      if (!savedJob) return reply({ error: { code: "MEDIA_JOB_NOT_FOUND", message: "درخواست ویدیو برای این حساب پیدا نشد." } }, 404);
      if (isContent) {
        if (savedJob.status !== "completed") return reply({ error: { code: "MEDIA_NOT_READY", message: "ویدیو هنوز آماده دانلود نیست." } }, 409);
        const contentResponse = await mediaProviderFetch(`/videos/${providerJobId}/content`, {
          method: "GET", headers: { Accept: "video/mp4, application/octet-stream, */*" },
        });
        if (!contentResponse.ok) return reply({ error: { code: "NETARZ_VIDEO_DOWNLOAD_FAILED", message: "دریافت فایل ویدیوی نهایی ناموفق بود." } }, 502);
        return new Response(contentResponse.body, {
          status: 200, headers: { ...corsHeaders, "Content-Type": contentResponse.headers.get("Content-Type") || "video/mp4", "Cache-Control": "no-store" },
        });
      }
      const statusResponse = await mediaProviderFetch(`/videos/${providerJobId}`, { method: "GET" });
      const raw = await statusResponse.text();
      if (!statusResponse.ok) return reply({ error: { code: "NETARZ_VIDEO_STATUS_FAILED", message: "دریافت وضعیت ویدیو از NetArz ناموفق بود." } }, 502);
      const job = JSON.parse(raw);
      const status = String(job?.status || "unknown");
      if (!savedJob.settled && (status === "completed" || status === "failed" || status === "cancelled")) {
        const reservationId = savedJob.reservation_id ? String(savedJob.reservation_id) : null;
        if (status === "completed") {
          const reportedUsd = Number(job?.usd);
          const cost = Number.isFinite(reportedUsd) && reportedUsd > 0 ? reportedUsd : Number(savedJob.estimated_cost_usd || 0);
          const billingRows = await serviceFetch("ema_billing_settings?select=usd_to_toman,markup_bps&singleton=eq.true&limit=1");
          const billing = billingRows?.[0] || {};
          const admins = await serviceFetch(`ema_admin_users?select=user_id&user_id=eq.${userId}&limit=1`);
          const isAdmin = Array.isArray(admins) && admins.length > 0;
          const subs = await serviceFetch(`ema_subscriptions?select=id&user_id=eq.${userId}&starts_at=lte.${encodeURIComponent(new Date().toISOString())}&ends_at=gt.${encodeURIComponent(new Date().toISOString())}&limit=1`);
          await settleMediaReservation(reservationId, "professional_video", savedJob.model, cost,
            Number(billing.usd_to_toman || 0), Math.max(0, Number(billing.markup_bps || 0)), isAdmin,
            Array.isArray(subs) && subs.length > 0, { provider_job_id: providerJobId, seconds: savedJob.seconds, size: savedJob.size });
        } else if (reservationId) {
          await serviceFetch("rpc/ema_release_wallet", {
            method: "POST", body: JSON.stringify({ p_reservation_id: reservationId, p_reason: `netarz_video_${status}` }),
          });
        }
        await serviceFetch(`ema_media_jobs?id=eq.${savedJob.id}`, {
          method: "PATCH", headers: { Prefer: "return=minimal" },
          body: JSON.stringify({ status, settled: true, updated_at: new Date().toISOString() }),
        });
      } else {
        await serviceFetch(`ema_media_jobs?id=eq.${savedJob.id}`, {
          method: "PATCH", headers: { Prefer: "return=minimal" },
          body: JSON.stringify({ status, updated_at: new Date().toISOString() }),
        });
      }
      return reply({ ...job, id: providerJobId, status });
    }

    // Media billing uses live NetArz catalog pricing; do not hardcode model prices in the APK.
    // Paid AI tools use NetArz only. Wallet authorization, reservation and settlement stay server-side.
    if (request.method === "POST" && route === "/v1/ai/paid/chat/completions") {
      const body = await jsonBody(request);
      const feature = String(body?.feature || "");
      const allowedFeatures = new Set(["think_room", "future_gateway", "social_analytics"]);
      if (!allowedFeatures.has(feature)) {
        return reply({ error: { code: "PAID_FEATURE_NOT_ALLOWED", message: "این قابلیت در فهرست ابزارهای هوش مصنوعی پولی تعریف نشده است." } }, 403);
      }
      if (!Array.isArray(body?.messages) || body.messages.length < 1 || body.messages.length > 30) {
        return reply({ error: { code: "INVALID_REQUEST", message: "ساختار گفتگو معتبر نیست." } }, 400);
      }
      if (new TextEncoder().encode(JSON.stringify(body.messages)).length > 1_500_000) {
        return reply({ error: { code: "REQUEST_TOO_LARGE", message: "حجم درخواست بیش از حد مجاز است." } }, 413);
      }

      const netarzKey = Deno.env.get("CHAT_PRIMARY_API_KEY") || Deno.env.get("Netarz") || "";
      const netarzBase = (Deno.env.get("CHAT_PRIMARY_BASE_URL") || "https://netarz.ir/api/ai/v1").replace(/\/+$/, "");
      const model = String(Deno.env.get("CHAT_PRIMARY_MODEL") || body?.model || "gpt-4o-mini").trim();
      if (!netarzKey || !netarzBase.startsWith("https://")) {
        return reply({ error: { code: "NETARZ_NOT_CONFIGURED", message: "اتصال امن NetArz روی سرور تنظیم نشده است." } }, 503);
      }

      const profiles = await serviceFetch(`ema_profiles?select=status&user_id=eq.${userId}&limit=1`);
      if (!Array.isArray(profiles) || profiles.length === 0 || profiles[0].status !== "active") {
        return reply({ error: { code: "ACCOUNT_NOT_ACTIVE", message: "حساب کاربری برای استفاده از قابلیت پولی فعال نیست." } }, 403);
      }
      const admins = await serviceFetch(`ema_admin_users?select=user_id&user_id=eq.${userId}&limit=1`);
      const isAdmin = Array.isArray(admins) && admins.length > 0;
      const now = new Date().toISOString();
      const [walletRows, subscriptionRows] = await Promise.all([
        serviceFetch(`ema_wallets?select=balance_toman&user_id=eq.${userId}&limit=1`),
        serviceFetch(`ema_subscriptions?select=id&user_id=eq.${userId}&starts_at=lte.${encodeURIComponent(now)}&ends_at=gt.${encodeURIComponent(now)}&limit=1`),
      ]);
      const walletBalance = Number(walletRows?.[0]?.balance_toman || 0);
      const hasActiveSubscription = Array.isArray(subscriptionRows) && subscriptionRows.length > 0;

      const billingRows = await serviceFetch("ema_billing_settings?select=ai_billing_enabled,usd_to_toman,markup_bps&singleton=eq.true&limit=1");
      const billing = billingRows?.[0] || {};
      const pricingRows = await serviceFetch(`ema_pricing_rules?select=input_usd_per_million,output_usd_per_million&model=eq.${encodeURIComponent(model)}&enabled=eq.true&limit=1`);
      const pricing = pricingRows?.[0] || null;
      const inputRate = Number(pricing?.input_usd_per_million || 0);
      const outputRate = Number(pricing?.output_usd_per_million || 0);
      const usdToToman = Number(billing.usd_to_toman || 0);
      const markupBps = Math.max(0, Number(billing.markup_bps || 0));
      const walletBillingReady = billing.ai_billing_enabled === true && !!pricing &&
        inputRate >= 0 && outputRate >= 0 && (inputRate + outputRate) > 0 && usdToToman > 0;
      if (!isAdmin && !hasActiveSubscription && walletBalance > 0 && !walletBillingReady) {
        return reply({ error: { code: "AI_MODEL_PRICING_MISSING", message: "برای کسر امن اعتبار کیف پول، قیمت مدل NetArz و نرخ تبدیل دلار به تومان باید در پنل مدیریت تنظیم شود." } }, 503);
      }

      const maxTokens = Math.max(1, Math.min(1600, Number(body?.max_tokens) || 1200));
      const serializedMessages = JSON.stringify(body.messages);
      const estimatedInputTokens = Math.max(1, Math.ceil(serializedMessages.length / 3) + 64);
      const reserveUsd = ((estimatedInputTokens * inputRate) + (maxTokens * outputRate)) / 1_000_000;
      const reserveToman = Math.max(1, Math.ceil(reserveUsd * usdToToman * (1 + markupBps / 10000) * 1.5));
      const reservationKey = crypto.randomUUID();
      let reservationId: string | null = null;
      // Prefer per-request wallet billing when configured and sufficiently funded.
      // An active subscription remains a valid included-access path when wallet billing is not used.
      if (!isAdmin && walletBalance > 0 && !hasActiveSubscription && !walletBillingReady) {
        return reply({ error: { code: "AI_BILLING_NOT_ENABLED", message: "سامانه محاسبه و کسر اعتبار هوش مصنوعی هنوز توسط مدیر فعال نشده است." } }, 503);
      }
      if (!isAdmin && walletBalance <= 0 && !hasActiveSubscription) {
        return reply({ error: { code: "PAID_ACCESS_REQUIRED", message: "برای استفاده از این قابلیت اشتراک فعال یا اعتبار کیف پول لازم است." } }, 402);
      }
      if (!isAdmin && walletBillingReady && walletBalance >= reserveToman) {
        const reservation = await serviceFetch("rpc/ema_reserve_wallet", {
          method: "POST",
          body: JSON.stringify({
            p_user_id: userId,
            p_amount_toman: reserveToman,
            p_reference: reservationKey,
            p_feature: feature,
            p_model: model,
          }),
        });
        if (reservation?.ok) {
          reservationId = String(reservation.reservation_id || "");
          if (!reservationId) {
            return reply({ error: { code: "AI_RESERVATION_FAILED", message: "رزرو اعتبار هوش مصنوعی انجام نشد." } }, 503);
          }
        } else if (!hasActiveSubscription) {
          return reply({
            error: {
              code: reservation?.code || "INSUFFICIENT_AI_CREDIT",
              message: "اعتبار کیف پول برای اجرای این درخواست کافی نیست؛ لطفاً کیف پول را شارژ کن.",
              balance_toman: reservation?.balance_toman ?? walletBalance,
              required_toman: reservation?.required_toman ?? reserveToman,
            },
          }, 402);
        }
      } else if (!isAdmin && !hasActiveSubscription && walletBalance < reserveToman) {
        return reply({ error: { code: "INSUFFICIENT_AI_CREDIT", message: "اعتبار کیف پول برای اجرای این درخواست کافی نیست؛ لطفاً کیف پول را شارژ کن." } }, 402);
      }

      try {
        const upstream = await fetch(`${netarzBase}/chat/completions`, {
          method: "POST",
          headers: { Authorization: `Bearer ${netarzKey}`, "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify({
            model,
            messages: body.messages,
            stream: false,
            max_tokens: maxTokens,
            temperature: typeof body.temperature === "number" ? Math.min(1, Math.max(0, body.temperature)) : 0.7,
          }),
          signal: AbortSignal.timeout(90000),
        });
        const raw = await upstream.text();
        if (!upstream.ok) {
          if (reservationId) {
            await serviceFetch("rpc/ema_release_wallet", {
              method: "POST",
              body: JSON.stringify({ p_reservation_id: reservationId, p_reason: `netarz_http_${upstream.status}` }),
            }).catch((error) => console.error("EMA paid AI reservation release failed", error instanceof Error ? error.message : "unknown"));
          }
          console.error("EMA NetArz paid AI failed", { status: upstream.status, detail: raw.slice(0, 180) });
          return reply({ error: { code: "NETARZ_PROVIDER_ERROR", message: `پاسخ NetArz ناموفق بود (HTTP ${upstream.status}). اعتبار و مدل انتخابی را بررسی کن.` } }, 502);
        }

        let result: any;
        try { result = JSON.parse(raw); } catch {
          if (reservationId) {
            await serviceFetch("rpc/ema_release_wallet", {
              method: "POST",
              body: JSON.stringify({ p_reservation_id: reservationId, p_reason: "invalid_provider_json" }),
            }).catch((error) => console.error("EMA paid AI reservation release failed", error instanceof Error ? error.message : "unknown"));
          }
          return reply({ error: { code: "NETARZ_INVALID_RESPONSE", message: "پاسخ NetArz در قالب معتبر دریافت نشد." } }, 502);
        }

        const usage = result?.usage || {};
        const inputTokens = Math.max(0, Number(usage.prompt_tokens ?? estimatedInputTokens));
        const outputTokens = Math.max(0, Number(usage.completion_tokens ?? maxTokens));
        const actualUsd = ((inputTokens * inputRate) + (outputTokens * outputRate)) / 1_000_000;
        if (reservationId) {
          const actualToman = Math.min(reserveToman, Math.max(0, Math.ceil(actualUsd * usdToToman * (1 + markupBps / 10000))));
          await serviceFetch("rpc/ema_settle_wallet", {
            method: "POST",
            body: JSON.stringify({
              p_reservation_id: reservationId,
              p_actual_toman: actualToman,
              p_provider: "NetArz",
              p_input_tokens: inputTokens,
              p_output_tokens: outputTokens,
              p_provider_cost_usd: actualUsd,
            }),
          });
        } else {
          const usageEvents = await serviceFetch("ema_usage_events?select=id", {
            method: "POST",
            headers: { Prefer: "return=representation" },
            body: JSON.stringify({
              user_id: userId,
              reservation_id: null,
              feature,
              provider: "NetArz",
              model,
              input_tokens: inputTokens,
              output_tokens: outputTokens,
              provider_cost_usd: actualUsd,
              charged_toman: 0,
              metadata: { access: isAdmin ? "admin" : "subscription", pricing_configured: !!pricing },
            }),
          });
          const usageEventId = usageEvents?.[0]?.id;
          if (usageEventId) {
            await serviceFetch("ema_provider_cost_ledger", {
              method: "POST",
              headers: { Prefer: "return=minimal" },
              body: JSON.stringify({ user_id: userId, usage_event_id: usageEventId, provider: "NetArz", model, cost_usd: actualUsd }),
            });
          }
        }
        console.log("EMA paid AI request completed", { provider: "NetArz", feature, model, user_id: userId });
        return reply(result);
      } catch (error) {
        if (reservationId) {
          await serviceFetch("rpc/ema_release_wallet", {
            method: "POST",
            body: JSON.stringify({ p_reservation_id: reservationId, p_reason: "provider_request_failed" }),
          }).catch((releaseError) => console.error("EMA paid AI reservation release failed", releaseError instanceof Error ? releaseError.message : "unknown"));
        }
        console.error("EMA NetArz paid AI request failed", error instanceof Error ? error.message : "network error");
        return reply({ error: { code: "NETARZ_UNAVAILABLE", message: "سرویس NetArz موقتاً در دسترس نیست؛ اعتبار رزروشده آزاد شد." } }, 503);
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

    // Free chat: NetArz is primary, GapGPT is used only if NetArz has a transient outage.
    if (request.method !== "POST" || !route.endsWith("/chat/completions")) return reply({ error: { code: "NOT_FOUND", message: "مسیر درخواست معتبر نیست." } }, 404);
    const raw = await request.text();
    if (new TextEncoder().encode(raw).length > 1_500_000) return reply({ error: { code: "REQUEST_TOO_LARGE", message: "حجم درخواست بیش از حد مجاز است." } }, 413);
    let body: any;
    try { body = JSON.parse(raw); } catch { return reply({ error: { code: "INVALID_JSON", message: "ساختار گفتگو معتبر نیست." } }, 400); }
    if (!Array.isArray(body?.messages) || body.messages.length < 1 || body.messages.length > 30) return reply({ error: { code: "INVALID_REQUEST", message: "ساختار گفتگو معتبر نیست." } }, 400);

    const candidates = aiProviderConfigs().filter((provider) => provider.model);
    if (!candidates.length) return reply({ error: { code: "FREE_CHAT_NOT_CONFIGURED", message: "کلید NetArz و مسیر پشتیبان GapGPT روی سرور تنظیم نشده است." } }, 503);

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

    const containsImage = JSON.stringify(body.messages).includes("data:image/");
    const payloadFor = (provider: typeof candidates[number]) => ({
      model: containsImage
        ? provider.visionModel
        : provider.name === "NetArz"
          ? (String(body.model || "").trim() || provider.model)
          : provider.model,
      messages: body.messages,
      stream: false,
      max_tokens: Math.min(4096, Math.max(1, Number(body.max_tokens) || 2400)),
      temperature: typeof body.temperature === "number" ? Math.min(1, Math.max(0, body.temperature)) : 0.65,
    });

    let result: any = null;
    let usedProvider = "";
    let usedModel = "";
    let lastFailure = "provider_unavailable";
    for (const provider of candidates) {
      const providerPayload = payloadFor(provider);
      try {
        const upstream = await fetch(provider.base + "/chat/completions", {
          method: "POST",
          headers: { Authorization: "Bearer " + provider.key, "Content-Type": "application/json", Accept: "application/json" },
          body: JSON.stringify(providerPayload),
          signal: AbortSignal.timeout(45000),
        });
        const providerText = await upstream.text();
        if (!upstream.ok) {
          lastFailure = "http_" + upstream.status;
          if (upstream.status === 408 || upstream.status === 429 || upstream.status >= 500) {
            console.warn("EMA chat provider transient failure; trying next provider", { provider: provider.name, status: upstream.status });
            continue;
          }
          await refundFreeQuota();
          console.error("EMA chat provider rejected request", { provider: provider.name, status: upstream.status });
          return reply({ error: { code: "CHAT_PROVIDER_REJECTED", message: "موتور گفت‌وگوی انتخاب‌شده درخواست را نپذیرفت؛ تنظیمات مدل و دسترسی آن باید بررسی شود." } }, 502);
        }
        try { result = JSON.parse(providerText); } catch {
          lastFailure = "invalid_json";
          console.warn("EMA chat provider returned invalid JSON", { provider: provider.name });
          continue;
        }
        if (!Array.isArray(result?.choices) || !result.choices.length) {
          lastFailure = "empty_choices";
          continue;
        }
        usedProvider = provider.name;
        usedModel = providerPayload.model;
        break;
      } catch (error) {
        lastFailure = error instanceof Error ? error.message : "network_error";
        console.warn("EMA chat provider unavailable; trying next provider", { provider: provider.name });
      }
    }

    if (!result) {
      await refundFreeQuota();
      console.error("All EMA chat providers failed", { reason: lastFailure });
      return reply({ error: { code: "CHAT_PROVIDERS_UNAVAILABLE", message: "موتورهای گفت‌وگوی ایما موقتاً پاسخگو نیستند؛ سهمیه این درخواست آزاد شد." } }, 503);
    }
    console.log("EMA free chat completed", { provider: usedProvider, model: usedModel, user_id: userId });
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
