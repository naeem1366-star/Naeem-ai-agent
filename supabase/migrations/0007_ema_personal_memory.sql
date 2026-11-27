-- EMA personal memory v1. User-scoped cloud memory; local Android memory remains available offline.
-- Apply only after reviewing the feature branch. This migration is not auto-deployed.

alter table public.ema_profiles
  add column if not exists display_name text not null default '',
  add column if not exists preferences jsonb not null default '{}'::jsonb,
  add column if not exists memory_sync_enabled boolean not null default true;

create table if not exists public.ema_memory_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  memory_type text not null default 'note'
    check (memory_type in ('preference','fact','project','decision','note','correction')),
  content text not null check (length(trim(content)) between 1 and 4000),
  normalized_key text not null default '',
  confidence numeric(4,3) not null default 0.800 check (confidence between 0 and 1),
  source text not null default 'user_explicit'
    check (source in ('user_explicit','user_correction','assistant_suggested','imported')),
  sensitivity text not null default 'normal'
    check (sensitivity in ('normal','sensitive')),
  pinned boolean not null default false,
  last_used_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, normalized_key)
);
create index if not exists ema_memory_items_user_updated_idx
  on public.ema_memory_items(user_id, updated_at desc);
create index if not exists ema_memory_items_user_type_idx
  on public.ema_memory_items(user_id, memory_type, updated_at desc);

create table if not exists public.ema_learning_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null
    check (event_type in ('correction','preference_signal','task_outcome','memory_review','user_feedback')),
  summary text not null check (length(trim(summary)) between 1 and 2000),
  proposed_memory_id uuid references public.ema_memory_items(id) on delete set null,
  status text not null default 'pending'
    check (status in ('pending','accepted','rejected','applied')),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);
create index if not exists ema_learning_events_user_created_idx
  on public.ema_learning_events(user_id, created_at desc);

alter table public.ema_memory_items enable row level security;
alter table public.ema_learning_events enable row level security;

drop policy if exists "memory own read" on public.ema_memory_items;
create policy "memory own read" on public.ema_memory_items
  for select to authenticated using (user_id = auth.uid());
drop policy if exists "learning own read" on public.ema_learning_events;
create policy "learning own read" on public.ema_learning_events
  for select to authenticated using (user_id = auth.uid());

revoke all on public.ema_memory_items, public.ema_learning_events from anon, authenticated;
grant select on public.ema_memory_items, public.ema_learning_events to authenticated;
grant all on public.ema_memory_items, public.ema_learning_events to service_role;
