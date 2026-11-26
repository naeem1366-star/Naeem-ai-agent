-- EMA manual card-to-card payment workflow.
-- Apply after 0001_ema_core.sql and 0002_ema_chat_quota.sql.

alter table public.ema_payment_orders
  add column if not exists receipt_submitted_at timestamptz,
  add column if not exists idempotency_key text;

create unique index if not exists ema_payment_orders_idempotency_uidx
  on public.ema_payment_orders(user_id, idempotency_key)
  where idempotency_key is not null;

create table if not exists public.ema_payment_settings (
  singleton boolean primary key default true check (singleton),
  instructions text not null default '',
  min_topup_toman bigint not null default 100000 check (min_topup_toman > 0),
  max_topup_toman bigint not null default 50000000 check (max_topup_toman >= min_topup_toman),
  updated_at timestamptz not null default now()
);
insert into public.ema_payment_settings(singleton) values(true)
on conflict(singleton) do nothing;
alter table public.ema_payment_settings enable row level security;

-- Only the trusted Edge Function (service_role) can approve a payment.
create or replace function public.ema_admin_review_payment(
  p_order_id uuid,
  p_admin_id uuid,
  p_decision text,
  p_rejection_reason text default ''
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_order public.ema_payment_orders%rowtype;
  v_plan public.ema_plans%rowtype;
  v_balance bigint;
  v_credit bigint;
begin
  if p_decision not in ('approve','reject') then
    raise exception 'INVALID_DECISION';
  end if;
  if not exists(select 1 from public.ema_admin_users where user_id=p_admin_id) then
    raise exception 'ADMIN_REQUIRED';
  end if;

  select * into v_order
  from public.ema_payment_orders
  where id=p_order_id
  for update;

  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  if v_order.status <> 'receipt_submitted' then
    raise exception 'ORDER_NOT_AWAITING_REVIEW';
  end if;

  if p_decision='reject' then
    update public.ema_payment_orders
    set status='rejected',
        receipt_note=coalesce(nullif(trim(p_rejection_reason),''),receipt_note),
        reviewed_by=p_admin_id, reviewed_at=now(), updated_at=now()
    where id=v_order.id;
    insert into public.ema_audit_log(actor_user_id,action,target_user_id,details)
    values(p_admin_id,'payment_rejected',v_order.user_id,
      jsonb_build_object('order_id',v_order.id,'reason',coalesce(p_rejection_reason,'')));
    return jsonb_build_object('ok',true,'status','rejected');
  end if;

  update public.ema_payment_orders
  set status='paid', reviewed_by=p_admin_id, reviewed_at=now(), updated_at=now()
  where id=v_order.id;

  if v_order.order_type='wallet_topup' then
    update public.ema_wallets
      set balance_toman=balance_toman+v_order.amount_toman, updated_at=now()
      where user_id=v_order.user_id
      returning balance_toman into v_balance;
    if not found then raise exception 'WALLET_NOT_FOUND'; end if;
    insert into public.ema_wallet_ledger(
      user_id,entry_type,amount_toman,balance_after_toman,reference,description,metadata
    ) values(
      v_order.user_id,'topup',v_order.amount_toman,v_balance,
      'payment-order:'||v_order.id::text,'Manual card-to-card top-up',
      jsonb_build_object('order_id',v_order.id,'reviewed_by',p_admin_id)
    );
  elsif v_order.order_type='subscription' then
    select * into v_plan from public.ema_plans where id=v_order.plan_id and enabled=true;
    if not found then raise exception 'PLAN_NOT_AVAILABLE'; end if;
    insert into public.ema_subscriptions(user_id,plan_id,starts_at,ends_at,payment_order_id)
    values(
      v_order.user_id,v_plan.id,now(),
      now() + make_interval(days => v_plan.duration_days),v_order.id
    );
    v_credit := floor(v_order.amount_toman::numeric * v_plan.ai_credit_percent / 100)::bigint;
    if v_credit > 0 then
      update public.ema_wallets
        set balance_toman=balance_toman+v_credit, updated_at=now()
        where user_id=v_order.user_id
        returning balance_toman into v_balance;
      if not found then raise exception 'WALLET_NOT_FOUND'; end if;
      insert into public.ema_wallet_ledger(
        user_id,entry_type,amount_toman,balance_after_toman,reference,description,metadata
      ) values(
        v_order.user_id,'subscription_credit',v_credit,v_balance,
        'subscription-credit:'||v_order.id::text,'AI credit included with subscription',
        jsonb_build_object('order_id',v_order.id,'reviewed_by',p_admin_id)
      );
    end if;
  else
    raise exception 'INVALID_ORDER_TYPE';
  end if;

  insert into public.ema_audit_log(actor_user_id,action,target_user_id,details)
  values(p_admin_id,'payment_approved',v_order.user_id,
    jsonb_build_object('order_id',v_order.id,'amount_toman',v_order.amount_toman));
  return jsonb_build_object('ok',true,'status','paid','balance_toman',v_balance);
end;
$$;

revoke all on function public.ema_admin_review_payment(uuid,uuid,text,text) from public, anon, authenticated;
grant execute on function public.ema_admin_review_payment(uuid,uuid,text,text) to service_role;
