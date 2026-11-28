-- EMA credit wallet v1. Credit units are separate from payment currency (toman).
-- Apply only after reviewing this migration; this file is for the development branch.
begin;

alter table public.ema_billing_settings
  add column if not exists credits_per_toman numeric(20,8);

alter table public.ema_payment_orders
  add column if not exists credits_to_grant bigint not null default 0;

create table if not exists public.ema_credit_wallets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  balance_credits bigint not null default 0 check (balance_credits >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.ema_credit_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  entry_type text not null check (entry_type in ('signup_gift','topup','subscription_credit','ai_reserve','ai_charge','ai_refund','admin_adjustment')),
  amount_credits bigint not null check (amount_credits <> 0),
  balance_after_credits bigint not null check (balance_after_credits >= 0),
  reference text not null unique,
  description text not null default '',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists ema_credit_ledger_user_created_idx
  on public.ema_credit_ledger(user_id, created_at desc);

create table if not exists public.ema_credit_reservations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  feature text not null,
  model text not null,
  reserved_credits bigint not null check (reserved_credits > 0),
  settled_credits bigint not null default 0 check (settled_credits >= 0),
  idempotency_key text not null unique,
  status text not null default 'reserved' check (status in ('reserved','settled','released')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists ema_credit_reservations_user_created_idx
  on public.ema_credit_reservations(user_id, created_at desc);

alter table public.ema_credit_wallets enable row level security;
alter table public.ema_credit_ledger enable row level security;
alter table public.ema_credit_reservations enable row level security;
revoke all on public.ema_credit_wallets, public.ema_credit_ledger, public.ema_credit_reservations from anon, authenticated;
grant all on public.ema_credit_wallets, public.ema_credit_ledger, public.ema_credit_reservations to service_role;

create or replace function public.ema_credit_grant(
  p_user_id uuid, p_amount_credits bigint, p_entry_type text, p_reference text,
  p_description text default '', p_metadata jsonb default '{}'::jsonb
) returns jsonb language plpgsql security definer set search_path = public, pg_temp
as $$
declare v_balance bigint;
begin
  if p_amount_credits <= 0 or p_reference is null or length(p_reference) < 8 then
    raise exception 'INVALID_CREDIT_GRANT';
  end if;
  insert into public.ema_credit_wallets(user_id) values(p_user_id) on conflict(user_id) do nothing;
  if exists(select 1 from public.ema_credit_ledger where reference=p_reference) then
    select balance_credits into v_balance from public.ema_credit_wallets where user_id=p_user_id;
    return jsonb_build_object('ok',true,'duplicate',true,'balance_credits',v_balance);
  end if;
  update public.ema_credit_wallets
    set balance_credits=balance_credits+p_amount_credits, updated_at=now()
    where user_id=p_user_id returning balance_credits into v_balance;
  insert into public.ema_credit_ledger(user_id,entry_type,amount_credits,balance_after_credits,reference,description,metadata)
    values(p_user_id,p_entry_type,p_amount_credits,v_balance,p_reference,p_description,coalesce(p_metadata,'{}'::jsonb));
  return jsonb_build_object('ok',true,'duplicate',false,'balance_credits',v_balance);
end;
$$;

create or replace function public.ema_reserve_credits(
  p_user_id uuid, p_amount_credits bigint, p_reference text, p_feature text, p_model text
) returns jsonb language plpgsql security definer set search_path = public, pg_temp
as $$
declare v_balance bigint; v_id uuid;
begin
  if p_amount_credits <= 0 or p_reference is null or length(p_reference)<8 then raise exception 'INVALID_CREDIT_RESERVATION'; end if;
  insert into public.ema_credit_wallets(user_id) values(p_user_id) on conflict(user_id) do nothing;
  select balance_credits into v_balance from public.ema_credit_wallets where user_id=p_user_id for update;
  if exists(select 1 from public.ema_credit_reservations where idempotency_key=p_reference) then
    select id into v_id from public.ema_credit_reservations where idempotency_key=p_reference;
    return jsonb_build_object('ok',true,'duplicate',true,'reservation_id',v_id,'balance_credits',v_balance);
  end if;
  if v_balance<p_amount_credits then return jsonb_build_object('ok',false,'code','INSUFFICIENT_CREDITS','balance_credits',v_balance,'required_credits',p_amount_credits); end if;
  update public.ema_credit_wallets set balance_credits=balance_credits-p_amount_credits,updated_at=now()
    where user_id=p_user_id returning balance_credits into v_balance;
  insert into public.ema_credit_reservations(user_id,feature,model,reserved_credits,idempotency_key)
    values(p_user_id,p_feature,p_model,p_amount_credits,p_reference) returning id into v_id;
  insert into public.ema_credit_ledger(user_id,entry_type,amount_credits,balance_after_credits,reference,description)
    values(p_user_id,'ai_reserve',-p_amount_credits,v_balance,'credit-reserve:'||p_reference,'AI credit reservation');
  return jsonb_build_object('ok',true,'reservation_id',v_id,'balance_credits',v_balance);
end;
$$;

create or replace function public.ema_release_credits(p_reservation_id uuid,p_reason text default 'provider_error')
returns jsonb language plpgsql security definer set search_path = public, pg_temp
as $$
declare r public.ema_credit_reservations%rowtype; v_balance bigint;
begin
  select * into r from public.ema_credit_reservations where id=p_reservation_id for update;
  if not found then raise exception 'CREDIT_RESERVATION_NOT_FOUND'; end if;
  if r.status<>'reserved' then return jsonb_build_object('ok',true,'duplicate',true,'status',r.status); end if;
  update public.ema_credit_wallets set balance_credits=balance_credits+r.reserved_credits,updated_at=now()
    where user_id=r.user_id returning balance_credits into v_balance;
  update public.ema_credit_reservations set status='released',updated_at=now() where id=r.id;
  insert into public.ema_credit_ledger(user_id,entry_type,amount_credits,balance_after_credits,reference,description,metadata)
    values(r.user_id,'ai_refund',r.reserved_credits,v_balance,'credit-release:'||r.id::text,'Release failed AI reservation',jsonb_build_object('reason',p_reason));
  return jsonb_build_object('ok',true,'balance_credits',v_balance);
end;
$$;

create or replace function public.ema_settle_credits(
  p_reservation_id uuid,p_actual_credits bigint,p_provider text,p_input_tokens bigint,
  p_output_tokens bigint,p_provider_cost_usd numeric,p_charged_toman bigint default 0
) returns jsonb language plpgsql security definer set search_path = public, pg_temp
as $$
declare r public.ema_credit_reservations%rowtype; v_delta bigint; v_balance bigint; v_event uuid;
begin
  if p_actual_credits<0 then raise exception 'INVALID_CREDIT_SETTLEMENT'; end if;
  select * into r from public.ema_credit_reservations where id=p_reservation_id for update;
  if not found then raise exception 'CREDIT_RESERVATION_NOT_FOUND'; end if;
  if r.status<>'reserved' then return jsonb_build_object('ok',true,'duplicate',true,'status',r.status,'settled_credits',r.settled_credits); end if;
  if p_actual_credits>r.reserved_credits then raise exception 'CREDIT_SETTLEMENT_EXCEEDS_RESERVATION'; end if;
  v_delta:=r.reserved_credits-p_actual_credits;
  update public.ema_credit_wallets set balance_credits=balance_credits+v_delta,updated_at=now()
    where user_id=r.user_id returning balance_credits into v_balance;
  update public.ema_credit_reservations set settled_credits=p_actual_credits,status='settled',updated_at=now() where id=r.id;
  if v_delta>0 then
    insert into public.ema_credit_ledger(user_id,entry_type,amount_credits,balance_after_credits,reference,description)
      values(r.user_id,'ai_refund',v_delta,v_balance,'credit-settle-refund:'||r.id::text,'Release unused AI credits');
  end if;
  insert into public.ema_usage_events(user_id,reservation_id,feature,provider,model,input_tokens,output_tokens,provider_cost_usd,charged_toman,metadata)
    values(r.user_id,null,r.feature,p_provider,r.model,greatest(0,p_input_tokens),greatest(0,p_output_tokens),
      greatest(0,p_provider_cost_usd),greatest(0,p_charged_toman),jsonb_build_object('charged_credits',p_actual_credits,'credit_reservation_id',r.id))
    returning id into v_event;
  insert into public.ema_provider_cost_ledger(user_id,usage_event_id,provider,model,cost_usd)
    values(r.user_id,v_event,p_provider,r.model,greatest(0,p_provider_cost_usd));
  return jsonb_build_object('ok',true,'balance_credits',v_balance,'settled_credits',p_actual_credits);
end;
$$;

-- One-time signup gift; ledger reference makes retries and duplicate triggers harmless.
create or replace function public.ema_on_auth_user_created()
returns trigger language plpgsql security definer set search_path = public, pg_temp
as $$
begin
  insert into public.ema_profiles(user_id,email) values(new.id,new.email)
    on conflict(user_id) do update set email=excluded.email,updated_at=now();
  insert into public.ema_wallets(user_id) values(new.id) on conflict(user_id) do nothing;
  perform public.ema_credit_grant(new.id,10000,'signup_gift','signup-gift:'||new.id::text,
    'هدیه ثبت‌نام ایما',jsonb_build_object('grant_version','1'));
  return new;
end;
$$;

-- Existing users are not silently given the signup gift by this migration.
-- Only newly-created accounts receive it through the auth trigger.

-- Replace payment approval so paid top-ups become credits, never toman-denominated balance.
create or replace function public.ema_admin_review_payment(
  p_order_id uuid,p_admin_id uuid,p_decision text,p_rejection_reason text default ''
) returns jsonb language plpgsql security definer set search_path = public, pg_temp
as $$
declare
  v_order public.ema_payment_orders%rowtype;
  v_plan public.ema_plans%rowtype;
  v_rate numeric;
  v_credit bigint;
  v_balance bigint;
  v_reference text;
begin
  if p_decision not in ('approve','reject') then raise exception 'INVALID_DECISION'; end if;
  if not exists(select 1 from public.ema_admin_users where user_id=p_admin_id) then raise exception 'ADMIN_REQUIRED'; end if;
  select * into v_order from public.ema_payment_orders where id=p_order_id for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if v_order.status<>'receipt_submitted' then raise exception 'ORDER_ALREADY_REVIEWED'; end if;
  if p_decision='reject' then
    update public.ema_payment_orders set status='rejected',receipt_note=left(coalesce(p_rejection_reason,''),300),
      reviewed_by=p_admin_id,reviewed_at=now(),updated_at=now() where id=p_order_id;
    insert into public.ema_audit_log(actor_user_id,action,target_user_id,details)
      values(p_admin_id,'payment_rejected',v_order.user_id,jsonb_build_object('order_id',p_order_id,'reason',left(coalesce(p_rejection_reason,''),300)));
    return jsonb_build_object('ok',true,'status','rejected');
  end if;

  select credits_per_toman into v_rate from public.ema_billing_settings where singleton=true;
  if v_rate is null or v_rate<=0 then raise exception 'CREDIT_CONVERSION_NOT_CONFIGURED'; end if;
  if v_order.order_type='wallet_topup' then
    if v_order.amount_toman<500000 then raise exception 'MIN_TOPUP_500000_TOMAN'; end if;
    v_credit:=floor(v_order.amount_toman*v_rate)::bigint;
  elsif v_order.order_type='subscription' then
    select * into v_plan from public.ema_plans where id=v_order.plan_id and enabled=true;
    if not found then raise exception 'PLAN_NOT_AVAILABLE'; end if;
    v_credit:=floor(v_order.amount_toman*v_rate*v_plan.ai_credit_percent/100.0)::bigint;
    insert into public.ema_subscriptions(user_id,plan_id,starts_at,ends_at,payment_order_id)
      values(v_order.user_id,v_plan.id,now(),now()+make_interval(days=>v_plan.duration_days),p_order_id);
  else
    raise exception 'INVALID_ORDER_TYPE';
  end if;
  if v_credit<=0 then raise exception 'CREDIT_AMOUNT_TOO_SMALL'; end if;
  v_reference:='approved-payment:'||p_order_id::text;
  perform public.ema_credit_grant(v_order.user_id,v_credit,
    case when v_order.order_type='wallet_topup' then 'topup' else 'subscription_credit' end,
    v_reference,'تبدیل پرداخت تأییدشده به کردیت',
    jsonb_build_object('order_id',p_order_id,'admin_id',p_admin_id,'amount_toman',v_order.amount_toman,'credits_per_toman',v_rate));
  select balance_credits into v_balance from public.ema_credit_wallets where user_id=v_order.user_id;
  update public.ema_payment_orders set status='paid',credits_to_grant=v_credit,
    reviewed_by=p_admin_id,reviewed_at=now(),updated_at=now() where id=p_order_id;
  insert into public.ema_audit_log(actor_user_id,action,target_user_id,details)
    values(p_admin_id,'payment_approved',v_order.user_id,jsonb_build_object('order_id',p_order_id,
      'amount_toman',v_order.amount_toman,'credits_granted',v_credit));
  return jsonb_build_object('ok',true,'status','paid','credited_credits',v_credit,'balance_credits',v_balance);
end;
$$;

revoke all on function public.ema_credit_grant(uuid,bigint,text,text,text,jsonb) from public,anon,authenticated;
revoke all on function public.ema_reserve_credits(uuid,bigint,text,text,text) from public,anon,authenticated;
revoke all on function public.ema_release_credits(uuid,text) from public,anon,authenticated;
revoke all on function public.ema_settle_credits(uuid,bigint,text,bigint,bigint,numeric,bigint) from public,anon,authenticated;
grant execute on function public.ema_credit_grant(uuid,bigint,text,text,text,jsonb) to service_role;
grant execute on function public.ema_reserve_credits(uuid,bigint,text,text,text) to service_role;
grant execute on function public.ema_release_credits(uuid,text) to service_role;
grant execute on function public.ema_settle_credits(uuid,bigint,text,bigint,bigint,numeric,bigint) to service_role;

commit;
