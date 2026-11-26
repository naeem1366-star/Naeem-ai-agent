-- EMA ASI PLATFORM: Supabase-native billing foundation.
-- Keeps the existing Supabase Auth users and UUIDs. No auth migration is performed.
create extension if not exists pgcrypto;

create table if not exists public.ema_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  email text,
  status text not null default 'active' check (status in ('active','suspended')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_wallets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  balance_toman bigint not null default 0 check (balance_toman >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_wallet_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  entry_type text not null check (entry_type in ('topup','subscription_credit','ai_reserve','ai_charge','ai_refund','adjustment')),
  amount_toman bigint not null,
  balance_after_toman bigint not null check (balance_after_toman >= 0),
  reference text not null unique,
  description text not null default '',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists ema_wallet_ledger_user_created_idx on public.ema_wallet_ledger(user_id, created_at desc);

create table if not exists public.ema_plans (
  id text primary key,
  name text not null,
  duration_days integer not null check (duration_days > 0),
  price_toman bigint not null default 0 check (price_toman >= 0),
  ai_credit_percent integer not null default 50 check (ai_credit_percent between 0 and 100),
  enabled boolean not null default false,
  sort_order integer not null default 0,
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  plan_id text not null references public.ema_plans(id),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  payment_order_id uuid,
  created_at timestamptz not null default now(),
  check (ends_at > starts_at)
);
create index if not exists ema_subscriptions_user_end_idx on public.ema_subscriptions(user_id, ends_at desc);

create table if not exists public.ema_payment_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  order_type text not null check (order_type in ('subscription','wallet_topup')),
  plan_id text references public.ema_plans(id),
  amount_toman bigint not null check (amount_toman > 0),
  status text not null default 'pending' check (status in ('pending','receipt_submitted','paid','rejected','cancelled')),
  payment_reference text,
  receipt_note text,
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists ema_payment_orders_user_idx on public.ema_payment_orders(user_id, created_at desc);

create table if not exists public.ema_pricing_rules (
  model text primary key,
  input_usd_per_million numeric(18,8) not null default 0 check (input_usd_per_million >= 0),
  output_usd_per_million numeric(18,8) not null default 0 check (output_usd_per_million >= 0),
  enabled boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_billing_settings (
  singleton boolean primary key default true check (singleton),
  ai_billing_enabled boolean not null default false,
  usd_to_toman numeric(18,2) not null default 0 check (usd_to_toman >= 0),
  markup_bps integer not null default 3000 check (markup_bps between 0 and 100000),
  subscription_ai_credit_percent integer not null default 50 check (subscription_ai_credit_percent between 0 and 100),
  updated_at timestamptz not null default now()
);
insert into public.ema_billing_settings(singleton) values(true) on conflict(singleton) do nothing;

create table if not exists public.ema_usage_reservations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  feature text not null,
  model text not null,
  reserved_toman bigint not null check (reserved_toman > 0),
  settled_toman bigint not null default 0 check (settled_toman >= 0),
  status text not null default 'reserved' check (status in ('reserved','settled','released','reconcile')),
  idempotency_key text not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists ema_reservations_user_status_idx on public.ema_usage_reservations(user_id,status,created_at desc);

create table if not exists public.ema_usage_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  reservation_id uuid references public.ema_usage_reservations(id),
  feature text not null,
  provider text not null,
  model text not null,
  input_tokens bigint not null default 0,
  output_tokens bigint not null default 0,
  provider_cost_usd numeric(18,8) not null default 0,
  charged_toman bigint not null default 0,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.ema_provider_cost_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id),
  usage_event_id uuid references public.ema_usage_events(id),
  provider text not null,
  model text not null,
  cost_usd numeric(18,8) not null check (cost_usd >= 0),
  created_at timestamptz not null default now()
);

create table if not exists public.ema_admin_users (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null default 'admin' check (role in ('admin','superadmin')),
  created_at timestamptz not null default now()
);

create table if not exists public.ema_audit_log (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid references auth.users(id),
  action text not null,
  target_user_id uuid references auth.users(id),
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- A wallet and profile are created automatically for every existing and future Auth user.
create or replace function public.ema_on_auth_user_created()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  insert into public.ema_profiles(user_id,email) values(new.id,new.email)
  on conflict(user_id) do update set email=excluded.email, updated_at=now();
  insert into public.ema_wallets(user_id) values(new.id) on conflict(user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists ema_auth_user_created on auth.users;
create trigger ema_auth_user_created after insert on auth.users
for each row execute procedure public.ema_on_auth_user_created();

insert into public.ema_profiles(user_id,email)
select id,email from auth.users on conflict(user_id) do update set email=excluded.email, updated_at=now();
insert into public.ema_wallets(user_id)
select id from auth.users on conflict(user_id) do nothing;

-- Atomic reservation: row lock prevents concurrent double-spend.
create or replace function public.ema_reserve_wallet(
  p_user_id uuid, p_amount_toman bigint, p_reference text, p_feature text, p_model text
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare v_balance bigint; v_id uuid;
begin
  if p_amount_toman <= 0 or p_reference is null or length(p_reference) < 8 then
    raise exception 'INVALID_RESERVATION';
  end if;
  select balance_toman into v_balance from public.ema_wallets where user_id=p_user_id for update;
  if not found then raise exception 'WALLET_NOT_FOUND'; end if;
  if exists(select 1 from public.ema_usage_reservations where idempotency_key=p_reference) then
    select id into v_id from public.ema_usage_reservations where idempotency_key=p_reference;
    return jsonb_build_object('ok',true,'duplicate',true,'reservation_id',v_id,'balance_toman',v_balance);
  end if;
  if v_balance < p_amount_toman then
    return jsonb_build_object('ok',false,'code','INSUFFICIENT_AI_CREDIT','balance_toman',v_balance,'required_toman',p_amount_toman);
  end if;
  update public.ema_wallets set balance_toman=balance_toman-p_amount_toman,updated_at=now() where user_id=p_user_id returning balance_toman into v_balance;
  insert into public.ema_usage_reservations(user_id,feature,model,reserved_toman,idempotency_key)
  values(p_user_id,p_feature,p_model,p_amount_toman,p_reference) returning id into v_id;
  insert into public.ema_wallet_ledger(user_id,entry_type,amount_toman,balance_after_toman,reference,description)
  values(p_user_id,'ai_reserve',-p_amount_toman,v_balance,'reserve:'||p_reference,'AI request cost reservation');
  return jsonb_build_object('ok',true,'duplicate',false,'reservation_id',v_id,'balance_toman',v_balance);
end;
$$;

-- Settlement charges actual amount; any unused reserve is returned atomically.
create or replace function public.ema_settle_wallet(
  p_reservation_id uuid, p_actual_toman bigint, p_provider text, p_input_tokens bigint,
  p_output_tokens bigint, p_provider_cost_usd numeric
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare r public.ema_usage_reservations%rowtype; v_delta bigint; v_balance bigint; v_event uuid;
begin
  if p_actual_toman < 0 then raise exception 'INVALID_SETTLEMENT'; end if;
  select * into r from public.ema_usage_reservations where id=p_reservation_id for update;
  if not found then raise exception 'RESERVATION_NOT_FOUND'; end if;
  if r.status <> 'reserved' then
    return jsonb_build_object('ok',true,'duplicate',true,'status',r.status,'settled_toman',r.settled_toman);
  end if;
  if p_actual_toman > r.reserved_toman then raise exception 'SETTLEMENT_EXCEEDS_RESERVATION'; end if;
  v_delta := r.reserved_toman-p_actual_toman;
  update public.ema_wallets set balance_toman=balance_toman+v_delta,updated_at=now()
  where user_id=r.user_id returning balance_toman into v_balance;
  update public.ema_usage_reservations set settled_toman=p_actual_toman,status='settled',updated_at=now() where id=r.id;
  if v_delta > 0 then
    insert into public.ema_wallet_ledger(user_id,entry_type,amount_toman,balance_after_toman,reference,description)
    values(r.user_id,'ai_refund',v_delta,v_balance,'settle-release:'||r.id::text,'Release unused AI reservation');
  end if;
  insert into public.ema_usage_events(user_id,reservation_id,feature,provider,model,input_tokens,output_tokens,provider_cost_usd,charged_toman)
  values(r.user_id,r.id,r.feature,p_provider,r.model,greatest(0,p_input_tokens),greatest(0,p_output_tokens),greatest(0,p_provider_cost_usd),p_actual_toman)
  returning id into v_event;
  insert into public.ema_provider_cost_ledger(user_id,usage_event_id,provider,model,cost_usd)
  values(r.user_id,v_event,p_provider,r.model,greatest(0,p_provider_cost_usd));
  return jsonb_build_object('ok',true,'balance_toman',v_balance,'charged_toman',p_actual_toman);
end;
$$;

create or replace function public.ema_release_wallet(p_reservation_id uuid, p_reason text default 'provider_error')
returns jsonb language plpgsql security definer set search_path = public
as $$
declare r public.ema_usage_reservations%rowtype; v_balance bigint;
begin
  select * into r from public.ema_usage_reservations where id=p_reservation_id for update;
  if not found then raise exception 'RESERVATION_NOT_FOUND'; end if;
  if r.status <> 'reserved' then return jsonb_build_object('ok',true,'duplicate',true,'status',r.status); end if;
  update public.ema_wallets set balance_toman=balance_toman+r.reserved_toman,updated_at=now()
  where user_id=r.user_id returning balance_toman into v_balance;
  update public.ema_usage_reservations set status='released',updated_at=now() where id=r.id;
  insert into public.ema_wallet_ledger(user_id,entry_type,amount_toman,balance_after_toman,reference,description,metadata)
  values(r.user_id,'ai_refund',r.reserved_toman,v_balance,'release:'||r.id::text,'Release failed AI reservation',jsonb_build_object('reason',p_reason));
  return jsonb_build_object('ok',true,'balance_toman',v_balance);
end;
$$;

-- Client access is read-only and scoped to the authenticated user. Mutations use Edge Functions.
alter table public.ema_profiles enable row level security;
alter table public.ema_wallets enable row level security;
alter table public.ema_wallet_ledger enable row level security;
alter table public.ema_plans enable row level security;
alter table public.ema_subscriptions enable row level security;
alter table public.ema_payment_orders enable row level security;
alter table public.ema_usage_reservations enable row level security;
alter table public.ema_usage_events enable row level security;

create policy "profile own read" on public.ema_profiles for select to authenticated using (user_id=auth.uid());
create policy "wallet own read" on public.ema_wallets for select to authenticated using (user_id=auth.uid());
create policy "ledger own read" on public.ema_wallet_ledger for select to authenticated using (user_id=auth.uid());
create policy "enabled plans read" on public.ema_plans for select to authenticated using (enabled=true);
create policy "subscriptions own read" on public.ema_subscriptions for select to authenticated using (user_id=auth.uid());
create policy "orders own read" on public.ema_payment_orders for select to authenticated using (user_id=auth.uid());
create policy "reservations own read" on public.ema_usage_reservations for select to authenticated using (user_id=auth.uid());
create policy "usage own read" on public.ema_usage_events for select to authenticated using (user_id=auth.uid());

revoke all on function public.ema_reserve_wallet(uuid,bigint,text,text,text) from public, anon, authenticated;
revoke all on function public.ema_settle_wallet(uuid,bigint,text,bigint,bigint,numeric) from public, anon, authenticated;
revoke all on function public.ema_release_wallet(uuid,text) from public, anon, authenticated;
grant execute on function public.ema_reserve_wallet(uuid,bigint,text,text,text) to service_role;
grant execute on function public.ema_settle_wallet(uuid,bigint,text,bigint,bigint,numeric) to service_role;
grant execute on function public.ema_release_wallet(uuid,text) to service_role;

grant select on public.ema_profiles, public.ema_wallets, public.ema_wallet_ledger,
 public.ema_plans, public.ema_subscriptions, public.ema_payment_orders,
 public.ema_usage_reservations, public.ema_usage_events to authenticated;
