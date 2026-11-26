-- Daily free chat quota. The counter is updated atomically by a SECURITY DEFINER RPC.
create table if not exists public.ema_chat_daily_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  usage_date date not null default (now() at time zone 'utc')::date,
  request_count integer not null default 0 check (request_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (user_id, usage_date)
);
alter table public.ema_chat_daily_usage enable row level security;

create or replace function public.ema_consume_chat_quota(p_user_id uuid, p_daily_limit integer default 50)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare v_count integer;
begin
  if p_user_id is null or p_daily_limit < 1 or p_daily_limit > 500 then
    return false;
  end if;
  insert into public.ema_chat_daily_usage(user_id, usage_date, request_count)
  values(p_user_id, (now() at time zone 'utc')::date, 1)
  on conflict(user_id, usage_date)
  do update set request_count = public.ema_chat_daily_usage.request_count + 1, updated_at = now()
  where public.ema_chat_daily_usage.request_count < p_daily_limit
  returning request_count into v_count;
  return v_count is not null;
end;
$$;
revoke all on function public.ema_consume_chat_quota(uuid, integer) from public, anon, authenticated;
grant execute on function public.ema_consume_chat_quota(uuid, integer) to service_role;
