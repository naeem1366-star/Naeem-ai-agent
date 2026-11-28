// EMA Social API v1. All operations are authenticated and server-mediated.
// A global feature flag is checked on every social operation, not just in the Android UI.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, PATCH, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: corsHeaders });
const jsonBody = async (request: Request) => {
  try { return await request.json(); } catch { throw new Error("INVALID_JSON"); }
};
const safeText = (value: unknown, max: number) => String(value ?? "").trim().slice(0, max);
const stringArray = (value: unknown, maxItems = 30, maxLength = 80): string[] =>
  Array.isArray(value) ? [...new Set(value.map(v => safeText(v, maxLength)).filter(Boolean))].slice(0, maxItems) : [];

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const url = new URL(request.url);
  const marker = "/ema-social";
  const at = url.pathname.indexOf(marker);
  const route = at >= 0 ? url.pathname.slice(at + marker.length).replace(/\/+$/, "") : url.pathname.replace(/\/+$/, "");
  const auth = request.headers.get("Authorization") || "";
  if (!auth.startsWith("Bearer ")) return reply({ error: { code: "UNAUTHORIZED", message: "برای استفاده از ایما وارد حساب شو." } }, 401);

  const supabaseUrl = (Deno.env.get("SUPABASE_URL") || "").replace(/\/+$/, "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") || Deno.env.get("SUPABASE_PUBLISHABLE_KEY") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!supabaseUrl || !anonKey || !serviceKey) return reply({ error: { code: "SERVER_CONFIG_MISSING", message: "پیکربندی امن سرور کامل نیست." } }, 503);

  const identityResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, Authorization: auth }, signal: AbortSignal.timeout(12000),
  }).catch(() => null);
  if (!identityResponse?.ok) return reply({ error: { code: "UNAUTHORIZED", message: "نشست ورود معتبر نیست." } }, 401);
  const identity = await identityResponse.json().catch(() => ({}));
  if (typeof identity.id !== "string") return reply({ error: { code: "UNAUTHORIZED", message: "حساب کاربری معتبر نیست." } }, 401);
  const userId = identity.id as string;

  const serviceFetch = async (path: string, init: RequestInit = {}) => {
    const response = await fetch(`${supabaseUrl}/rest/v1/${path}`, {
      ...init, headers: {
        apikey: serviceKey, Authorization: `Bearer ${serviceKey}`,
        "Content-Type": "application/json", Accept: "application/json",
        ...(init.headers || {}),
      },
    });
    const raw = await response.text();
    let data: any = null;
    try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
    if (!response.ok) {
      console.error("EMA social database error", response.status, raw.slice(0, 300));
      const error = new Error(data?.message || data?.hint || "DATABASE_REQUEST_FAILED");
      (error as any).status = response.status;
      throw error;
    }
    return data;
  };
  const isAdmin = async () => {
    const rows = await serviceFetch(`ema_admin_users?select=role&user_id=eq.${userId}&limit=1`);
    return Array.isArray(rows) && rows.length > 0;
  };
  const control = async () => {
    const rows = await serviceFetch("ema_feature_controls?select=enabled,maintenance_message,updated_at&feature_key=eq.social_network&limit=1");
    return rows?.[0] || { enabled: false, maintenance_message: "این بخش موقتاً غیرفعال است." };
  };
  const requireSocialEnabled = async () => {
    const state = await control();
    if (state.enabled !== true) {
      const error = new Error("SOCIAL_DISABLED");
      (error as any).status = 503;
      (error as any).maintenanceMessage = state.maintenance_message;
      throw error;
    }
    return state;
  };
  const audit = async (action: string, details: unknown = {}) => {
    await serviceFetch("ema_social_audit", {
      method: "POST", headers: { Prefer: "return=minimal" },
      body: JSON.stringify({ actor_user_id: userId, action, details }),
    });
  };

  try {
    // Admin control endpoints remain available even while the social network is disabled.
    if (route === "/v1/social/admin/control" && request.method === "GET") {
      if (!(await isAdmin())) throw new Error("ADMIN_REQUIRED");
      const state = await control();
      const [profiles, requests, teams, reports] = await Promise.all([
        serviceFetch("ema_social_profiles?select=user_id&limit=1"),
        serviceFetch("ema_social_connection_requests?select=id&status=eq.pending&limit=1"),
        serviceFetch("ema_social_teams?select=id&limit=1"),
        serviceFetch("ema_social_reports?select=id&status=eq.open&limit=1"),
      ]);
      return reply({ control: state, metrics: {
        profiles_sample_count: Array.isArray(profiles) ? profiles.length : 0,
        pending_requests_sample_count: Array.isArray(requests) ? requests.length : 0,
        teams_sample_count: Array.isArray(teams) ? teams.length : 0,
        open_reports_sample_count: Array.isArray(reports) ? reports.length : 0,
      }});
    }
    if (route === "/v1/social/admin/control" && request.method === "PATCH") {
      if (!(await isAdmin())) throw new Error("ADMIN_REQUIRED");
      const body = await jsonBody(request);
      if (typeof body?.enabled !== "boolean") return reply({ error: { code: "INVALID_CONTROL", message: "وضعیت فعال‌سازی معتبر نیست." } }, 400);
      const message = safeText(body?.maintenanceMessage || "این بخش موقتاً توسط مدیریت غیرفعال شده است.", 300);
      const rows = await serviceFetch("ema_feature_controls?on_conflict=feature_key", {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=representation" },
        body: JSON.stringify({ feature_key: "social_network", enabled: body.enabled, maintenance_message: message, updated_by: userId, updated_at: new Date().toISOString() }),
      });
      await audit(body.enabled ? "social_network_enabled" : "social_network_disabled", { maintenance_message: message });
      return reply({ control: rows?.[0] || await control() });
    }

    if (route === "/v1/social/status" && request.method === "GET") {
      const state = await control();
      return reply({ enabled: state.enabled === true, maintenanceMessage: state.maintenance_message || "", checkedAt: new Date().toISOString() });
    }
    await requireSocialEnabled();

    if (route === "/v1/social/profile" && request.method === "GET") {
      const rows = await serviceFetch(`ema_social_profiles?select=user_id,display_name,headline,bio,city,languages,interests,skills,goals,experience_summary,collaboration_modes,availability,visibility,discoverable,ai_matching_consent,updated_at&user_id=eq.${userId}&limit=1`);
      const consent = await serviceFetch(`ema_social_consents?select=profile_processing,semantic_matching,consent_version&user_id=eq.${userId}&limit=1`);
      return reply({ profile: rows?.[0] || null, consent: consent?.[0] || { profile_processing: false, semantic_matching: false } });
    }
    if (route === "/v1/social/profile" && request.method === "PUT") {
      const body = await jsonBody(request);
      const visibility = ["private","connections","public"].includes(body?.visibility) ? body.visibility : "private";
      const availability = ["not_set","open","limited","unavailable"].includes(body?.availability) ? body.availability : "not_set";
      const profile = {
        user_id: userId, display_name: safeText(body?.displayName, 80),
        headline: safeText(body?.headline, 140), bio: safeText(body?.bio, 2000),
        city: safeText(body?.city, 100), languages: stringArray(body?.languages),
        interests: stringArray(body?.interests), skills: stringArray(body?.skills),
        goals: stringArray(body?.goals), experience_summary: safeText(body?.experienceSummary, 2000),
        collaboration_modes: stringArray(body?.collaborationModes), availability, visibility,
        discoverable: body?.discoverable === true, ai_matching_consent: body?.aiMatchingConsent === true,
        updated_at: new Date().toISOString(),
      };
      const rows = await serviceFetch("ema_social_profiles?on_conflict=user_id", {
        method: "POST", headers: { Prefer: "resolution=merge-duplicates,return=representation" }, body: JSON.stringify(profile),
      });
      const consent = {
        user_id: userId, profile_processing: body?.profileProcessingConsent === true,
        semantic_matching: body?.semanticMatchingConsent === true, consent_version: "1.0", updated_at: new Date().toISOString(),
      };
      await serviceFetch("ema_social_consents?on_conflict=user_id", {
        method: "POST", headers: { Prefer: "resolution=merge-duplicates,return=minimal" }, body: JSON.stringify(consent),
      });
      await audit("social_profile_updated", { visibility, discoverable: profile.discoverable, matching_consent: profile.ai_matching_consent });
      return reply({ profile: rows?.[0] || profile }, 200);
    }

    if (route === "/v1/social/discover" && request.method === "GET") {
      const rows = await serviceFetch("ema_social_profiles?select=user_id,display_name,headline,bio,city,languages,interests,skills,goals,availability&discoverable=eq.true&visibility=eq.public&user_id=neq." + userId + "&order=updated_at.desc&limit=50");
      const blocks = await serviceFetch(`ema_social_blocks?select=blocked_user_id&blocker_user_id=eq.${userId}`);
      const blocked = new Set((blocks || []).map((r: any) => r.blocked_user_id));
      return reply({ people: (rows || []).filter((p: any) => !blocked.has(p.user_id)) });
    }

    if (route === "/v1/social/matches" && request.method === "GET") {
      const ownRows = await serviceFetch(`ema_social_profiles?select=interests,skills,goals,collaboration_modes,ai_matching_consent,discoverable&user_id=eq.${userId}&limit=1`);
      const own = ownRows?.[0];
      if (!own || own.ai_matching_consent !== true || own.discoverable !== true) {
        return reply({ matches: [], notice: "برای دریافت پیشنهاد، نمایش‌پذیری و رضایت تطبیق را در پروفایل فعال کن." });
      }
      const candidates = await serviceFetch("ema_social_profiles?select=user_id,display_name,headline,city,interests,skills,goals,collaboration_modes,availability&discoverable=eq.true&ai_matching_consent=eq.true&visibility=eq.public&user_id=neq." + userId + "&limit=100");
      const blocked = await serviceFetch(`ema_social_blocks?select=blocked_user_id&blocker_user_id=eq.${userId}`);
      const blockedIds = new Set((blocked || []).map((r: any) => r.blocked_user_id));
      const overlap = (a: string[] = [], b: string[] = []) => a.filter(v => b.some(w => w.toLocaleLowerCase() === v.toLocaleLowerCase()));
      const matches = (candidates || []).filter((p: any) => !blockedIds.has(p.user_id)).map((p: any) => {
        const shared = overlap(own.interests || [], p.interests || []);
        const sharedGoals = overlap(own.goals || [], p.goals || []);
        const complementary = (own.skills || []).filter((s: string) => !(p.skills || []).includes(s));
        const needed = overlap(own.collaboration_modes || [], p.collaboration_modes || []);
        const score = Math.min(98, shared.length * 18 + sharedGoals.length * 15 + needed.length * 10 + Math.min(complementary.length, 2) * 8 + 20);
        return { ...p, score, matchType: shared.length || sharedGoals.length ? "similar_minds" : "complementary_minds",
          reasons: [
            ...shared.map((v: string) => "علاقه مشترک: " + v),
            ...sharedGoals.map((v: string) => "هدف مشترک: " + v),
            ...needed.map((v: string) => "آمادگی همکاری مشترک: " + v),
            ...(complementary.length ? ["زمینه برای تکمیل مهارت‌های یکدیگر"] : []),
          ] };
      }).sort((a: any, b: any) => b.score - a.score).slice(0, 30);
      return reply({ matches, disclaimer: "امتیاز تطبیق یک تخمین توضیح‌پذیر است و موفقیت همکاری را تضمین نمی‌کند." });
    }

    if (route === "/v1/social/connections/requests" && request.method === "GET") {
      const rows = await serviceFetch(`ema_social_connection_requests?select=id,sender_user_id,recipient_user_id,message,status,created_at&or=(sender_user_id.eq.${userId},recipient_user_id.eq.${userId})&order=created_at.desc&limit=100`);
      return reply({ requests: rows || [] });
    }
    if (route === "/v1/social/connections/requests" && request.method === "POST") {
      const body = await jsonBody(request);
      const recipient = safeText(body?.recipientUserId, 50);
      if (!/^[0-9a-f-]{36}$/i.test(recipient) || recipient === userId) return reply({ error: { code: "INVALID_RECIPIENT", message: "کاربر مقصد معتبر نیست." } }, 400);
      const target = await serviceFetch(`ema_social_profiles?select=user_id,discoverable,visibility&user_id=eq.${recipient}&limit=1`);
      if (!target?.length || target[0].discoverable !== true || target[0].visibility !== "public") return reply({ error: { code: "PROFILE_NOT_AVAILABLE", message: "این پروفایل برای درخواست ارتباط در دسترس نیست." } }, 404);
      const blocked = await serviceFetch(`ema_social_blocks?select=blocker_user_id&or=(and(blocker_user_id.eq.${userId},blocked_user_id.eq.${recipient}),and(blocker_user_id.eq.${recipient},blocked_user_id.eq.${userId}))&limit=1`);
      if (blocked?.length) return reply({ error: { code: "CONNECTION_NOT_ALLOWED", message: "امکان ارسال درخواست ارتباط وجود ندارد." } }, 403);
      const rows = await serviceFetch("ema_social_connection_requests", {
        method: "POST", headers: { Prefer: "return=representation" },
        body: JSON.stringify({ sender_user_id: userId, recipient_user_id: recipient, message: safeText(body?.message, 1000) }),
      });
      return reply({ request: rows?.[0] || null }, 201);
    }

    const requestMatch = route.match(/^\/v1\/social\/connections\/requests\/([0-9a-f-]{36})$/i);
    if (requestMatch && request.method === "PATCH") {
      const body = await jsonBody(request);
      const decision = body?.decision;
      if (!["accepted","declined","cancelled"].includes(decision)) return reply({ error: { code: "INVALID_DECISION", message: "پاسخ درخواست معتبر نیست." } }, 400);
      const existing = await serviceFetch(`ema_social_connection_requests?select=id,sender_user_id,recipient_user_id,status&id=eq.${requestMatch[1]}&limit=1`);
      const item = existing?.[0];
      if (!item || (item.sender_user_id !== userId && item.recipient_user_id !== userId)) return reply({ error: { code: "REQUEST_NOT_FOUND", message: "درخواست پیدا نشد." } }, 404);
      if ((decision === "accepted" || decision === "declined") && item.recipient_user_id !== userId) return reply({ error: { code: "FORBIDDEN", message: "فقط گیرنده می‌تواند پاسخ دهد." } }, 403);
      if (decision === "cancelled" && item.sender_user_id !== userId) return reply({ error: { code: "FORBIDDEN", message: "فقط فرستنده می‌تواند درخواست را لغو کند." } }, 403);
      const updated = await serviceFetch(`ema_social_connection_requests?id=eq.${requestMatch[1]}`, {
        method: "PATCH", headers: { Prefer: "return=representation" },
        body: JSON.stringify({ status: decision, updated_at: new Date().toISOString() }),
      });
      if (decision === "accepted") {
        const [low, high] = [item.sender_user_id, item.recipient_user_id].sort();
        await serviceFetch("ema_social_connections?on_conflict=user_low,user_high", {
          method: "POST", headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
          body: JSON.stringify({ user_low: low, user_high: high }),
        });
      }
      return reply({ request: updated?.[0] || null });
    }

    return reply({ error: { code: "NOT_FOUND", message: "مسیر شبکه اجتماعی پیدا نشد." } }, 404);
  } catch (error) {
    const code = error instanceof Error ? error.message : "INTERNAL_ERROR";
    const status = (error as any)?.status || (code === "ADMIN_REQUIRED" ? 403 : 500);
    const message = code === "ADMIN_REQUIRED" ? "دسترسی مدیر برای این حساب فعال نیست."
      : code === "SOCIAL_DISABLED" ? safeText((error as any)?.maintenanceMessage, 300) || "این بخش موقتاً غیرفعال است."
      : code === "INVALID_JSON" ? "ساختار درخواست معتبر نیست."
      : "در انجام درخواست خطایی رخ داد؛ دوباره تلاش کن.";
    return reply({ error: { code, message } }, status);
  }
});
