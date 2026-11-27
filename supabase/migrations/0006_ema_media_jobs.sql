-- Track asynchronous NetArz video jobs so EMA can settle or release wallet reservations
-- only after the provider reports the final job status.
create table if not exists public.ema_media_jobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider_job_id text not null unique,
  provider text not null default 'NetArz',
  feature text not null check (feature in ('professional_video')),
  model text not null,
  reservation_id uuid references public.ema_usage_reservations(id),
  estimated_cost_usd numeric(18,8) not null default 0,
  reserve_toman bigint not null default 0,
  size text not null default '',
  seconds integer not null default 0,
  status text not null default 'queued',
  settled boolean not null default false,
  usage_logged boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists ema_media_jobs_user_created_idx
  on public.ema_media_jobs(user_id, created_at desc);
alter table public.ema_media_jobs enable row level security;
revoke all on public.ema_media_jobs from anon, authenticated;
grant all on public.ema_media_jobs to service_role;
