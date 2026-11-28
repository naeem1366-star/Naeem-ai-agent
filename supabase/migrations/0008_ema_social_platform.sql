-- EMA Social & Collaboration foundation v1
-- Additive only. This file is NOT applied to production by CI.
-- Global social kill switch is server-enforced by the ema-social Edge Function.
create extension if not exists pgcrypto;

create table if not exists public.ema_feature_controls (
  feature_key text primary key,
  enabled boolean not null default false,
  maintenance_message text not null default 'این بخش موقتاً توسط مدیریت غیرفعال شده است.',
  updated_by uuid references auth.users(id),
  updated_at timestamptz not null default now()
);
insert into public.ema_feature_controls(feature_key, enabled)
values ('social_network', false)
on conflict (feature_key) do nothing;

create table if not exists public.ema_social_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (length(display_name) <= 80),
  headline text not null default '' check (length(headline) <= 140),
  bio text not null default '' check (length(bio) <= 2000),
  city text not null default '' check (length(city) <= 100),
  avatar_path text,
  languages text[] not null default '{}',
  interests text[] not null default '{}',
  skills text[] not null default '{}',
  goals text[] not null default '{}',
  experience_summary text not null default '' check (length(experience_summary) <= 2000),
  collaboration_modes text[] not null default '{}',
  availability text not null default 'not_set'
    check (availability in ('not_set','open','limited','unavailable')),
  visibility text not null default 'private'
    check (visibility in ('private','connections','public')),
  discoverable boolean not null default false,
  ai_matching_consent boolean not null default false,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);
create index if not exists ema_social_profiles_discover_idx
  on public.ema_social_profiles(discoverable, visibility, updated_at desc);

create table if not exists public.ema_social_consents (
  user_id uuid primary key references auth.users(id) on delete cascade,
  profile_processing boolean not null default false,
  semantic_matching boolean not null default false,
  consent_version text not null default '1.0',
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_social_matches (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  candidate_user_id uuid not null references auth.users(id) on delete cascade,
  match_type text not null check (match_type in ('similar_minds','complementary_minds','new_perspectives')),
  score numeric(5,2) not null check (score between 0 and 100),
  reasons jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  unique(user_id, candidate_user_id, match_type),
  check (user_id <> candidate_user_id)
);
create index if not exists ema_social_matches_user_score_idx
  on public.ema_social_matches(user_id, score desc, created_at desc);

create table if not exists public.ema_social_connection_requests (
  id uuid primary key default gen_random_uuid(),
  sender_user_id uuid not null references auth.users(id) on delete cascade,
  recipient_user_id uuid not null references auth.users(id) on delete cascade,
  message text not null default '' check (length(message) <= 1000),
  status text not null default 'pending' check (status in ('pending','accepted','declined','cancelled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (sender_user_id <> recipient_user_id)
);
create unique index if not exists ema_social_connection_pending_unique
  on public.ema_social_connection_requests(sender_user_id, recipient_user_id)
  where status = 'pending';

create table if not exists public.ema_social_connections (
  id uuid primary key default gen_random_uuid(),
  user_low uuid not null references auth.users(id) on delete cascade,
  user_high uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  check (user_low < user_high),
  unique(user_low, user_high)
);

create table if not exists public.ema_social_blocks (
  blocker_user_id uuid not null references auth.users(id) on delete cascade,
  blocked_user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(blocker_user_id, blocked_user_id),
  check (blocker_user_id <> blocked_user_id)
);

create table if not exists public.ema_social_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_user_id uuid not null references auth.users(id) on delete cascade,
  reported_user_id uuid not null references auth.users(id) on delete cascade,
  category text not null check (category in ('spam','harassment','impersonation','unsafe','other')),
  details text not null default '' check (length(details) <= 2000),
  status text not null default 'open' check (status in ('open','reviewing','resolved','dismissed')),
  created_at timestamptz not null default now(),
  check (reporter_user_id <> reported_user_id)
);

create table if not exists public.ema_social_teams (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (length(trim(name)) between 2 and 100),
  description text not null default '' check (length(description) <= 2000),
  visibility text not null default 'private' check (visibility in ('private','invite_only','discoverable')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.ema_social_team_members (
  team_id uuid not null references public.ema_social_teams(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner','admin','member','observer')),
  joined_at timestamptz not null default now(),
  primary key(team_id,user_id)
);
create table if not exists public.ema_social_projects (
  id uuid primary key default gen_random_uuid(),
  team_id uuid not null references public.ema_social_teams(id) on delete cascade,
  created_by uuid not null references auth.users(id),
  title text not null check (length(trim(title)) between 2 and 160),
  description text not null default '' check (length(description) <= 5000),
  status text not null default 'active' check (status in ('draft','active','paused','completed','archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.ema_social_tasks (
  id uuid primary key default gen_random_uuid(),
  project_id uuid not null references public.ema_social_projects(id) on delete cascade,
  assigned_to uuid references auth.users(id) on delete set null,
  created_by uuid not null references auth.users(id),
  title text not null check (length(trim(title)) between 2 and 200),
  description text not null default '' check (length(description) <= 3000),
  status text not null default 'todo' check (status in ('todo','in_progress','blocked','done')),
  due_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_social_opportunities (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (length(trim(title)) between 2 and 160),
  description text not null default '' check (length(description) <= 5000),
  opportunity_type text not null check (opportunity_type in ('job','cofounder','project','mentor','investment','consulting')),
  skills text[] not null default '{}',
  visibility text not null default 'public' check (visibility in ('private','connections','public')),
  status text not null default 'open' check (status in ('draft','open','closed','archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_social_audit (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid references auth.users(id) on delete set null,
  action text not null,
  target_user_id uuid references auth.users(id) on delete set null,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- All social tables are server-mediated; clients cannot bypass the gateway via PostgREST.
do $$
declare t text;
begin
  foreach t in array array[
    'ema_feature_controls','ema_social_profiles','ema_social_consents','ema_social_matches',
    'ema_social_connection_requests','ema_social_connections','ema_social_blocks','ema_social_reports',
    'ema_social_teams','ema_social_team_members','ema_social_projects','ema_social_tasks',
    'ema_social_opportunities','ema_social_audit'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format('grant all on public.%I to service_role', t);
  end loop;
end $$;

create or replace function public.ema_social_is_admin(p_user_id uuid)
returns boolean language sql stable security definer set search_path = public, pg_temp
as $$
  select exists(select 1 from public.ema_admin_users where user_id = p_user_id and role = 'superadmin');
$$;
revoke all on function public.ema_social_is_admin(uuid) from public, anon, authenticated;
grant execute on function public.ema_social_is_admin(uuid) to service_role;

comment on table public.ema_feature_controls is 'Server-enforced feature flags. social_network is the owner-controlled global kill switch.';
comment on table public.ema_social_profiles is 'Explicitly user-managed social profile; never populated from private chats by default.';


-- Atomic request decision + connection creation. The Edge Function calls this with service_role.
create or replace function public.ema_social_respond_request(
  p_request_id uuid,
  p_actor_id uuid,
  p_decision text
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_request public.ema_social_connection_requests%rowtype;
  v_low uuid;
  v_high uuid;
begin
  if p_decision not in ('accepted','declined','cancelled') then
    return jsonb_build_object('ok', false, 'code', 'INVALID_DECISION');
  end if;

  select * into v_request
  from public.ema_social_connection_requests
  where id = p_request_id
  for update;

  if not found then
    return jsonb_build_object('ok', false, 'code', 'REQUEST_NOT_FOUND');
  end if;
  if v_request.status <> 'pending' then
    return jsonb_build_object('ok', false, 'code', 'REQUEST_ALREADY_PROCESSED');
  end if;
  if p_decision in ('accepted','declined') and v_request.recipient_user_id <> p_actor_id then
    return jsonb_build_object('ok', false, 'code', 'FORBIDDEN');
  end if;
  if p_decision = 'cancelled' and v_request.sender_user_id <> p_actor_id then
    return jsonb_build_object('ok', false, 'code', 'FORBIDDEN');
  end if;

  update public.ema_social_connection_requests
  set status = p_decision, updated_at = now()
  where id = p_request_id
  returning * into v_request;

  if p_decision = 'accepted' then
    if v_request.sender_user_id < v_request.recipient_user_id then
      v_low := v_request.sender_user_id; v_high := v_request.recipient_user_id;
    else
      v_low := v_request.recipient_user_id; v_high := v_request.sender_user_id;
    end if;
    insert into public.ema_social_connections(user_low,user_high)
    values(v_low,v_high)
    on conflict(user_low,user_high) do nothing;
  end if;

  return jsonb_build_object('ok', true, 'request', to_jsonb(v_request));
end;
$$;
revoke all on function public.ema_social_respond_request(uuid,uuid,text) from public, anon, authenticated;
grant execute on function public.ema_social_respond_request(uuid,uuid,text) to service_role;
