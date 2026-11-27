-- EMA owner/admin dashboard snapshot.
-- Read-only aggregate view. Called only by the trusted Edge Function using service_role.
create or replace function public.ema_admin_dashboard_snapshot(p_admin_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_result jsonb;
begin
  if not exists (
    select 1 from public.ema_admin_users
    where user_id = p_admin_id
  ) then
    raise exception 'ADMIN_REQUIRED';
  end if;

  select jsonb_build_object(
    'generated_at', now(),
    'summary', jsonb_build_object(
      'users_total', (select count(*) from public.ema_profiles),
      'wallet_balance_total_toman', (select coalesce(sum(balance_toman), 0) from public.ema_wallets),
      'wallets_with_balance', (select count(*) from public.ema_wallets where balance_toman > 0),
      'paid_revenue_total_toman', (select coalesce(sum(amount_toman), 0) from public.ema_payment_orders where status = 'paid'),
      'paid_revenue_month_toman', (select coalesce(sum(amount_toman), 0) from public.ema_payment_orders where status = 'paid' and reviewed_at >= date_trunc('month', now())),
      'paid_orders_total', (select count(*) from public.ema_payment_orders where status = 'paid'),
      'orders_pending_count', (select count(*) from public.ema_payment_orders where status = 'pending'),
      'orders_pending_amount_toman', (select coalesce(sum(amount_toman), 0) from public.ema_payment_orders where status = 'pending'),
      'receipts_waiting_count', (select count(*) from public.ema_payment_orders where status = 'receipt_submitted'),
      'receipts_waiting_amount_toman', (select coalesce(sum(amount_toman), 0) from public.ema_payment_orders where status = 'receipt_submitted'),
      'subscriptions_total', (select count(*) from public.ema_subscriptions),
      'subscriptions_active', (select count(*) from public.ema_subscriptions where starts_at <= now() and ends_at > now()),
      'provider_cost_total_usd', (select coalesce(sum(cost_usd), 0) from public.ema_provider_cost_ledger),
      'provider_cost_month_usd', (select coalesce(sum(cost_usd), 0) from public.ema_provider_cost_ledger where created_at >= date_trunc('month', now()))
    ),
    'payment_status', (
      select coalesce(jsonb_agg(to_jsonb(s)), '[]'::jsonb)
      from (
        select status, count(*) as order_count, coalesce(sum(amount_toman), 0) as amount_toman
        from public.ema_payment_orders
        group by status
        order by status
      ) s
    ),
    'provider_costs', (
      select coalesce(jsonb_agg(to_jsonb(p)), '[]'::jsonb)
      from (
        select provider, count(*) as event_count, coalesce(sum(cost_usd), 0) as cost_usd
        from public.ema_provider_cost_ledger
        group by provider
        order by sum(cost_usd) desc
      ) p
    ),
    'users', (
      select coalesce(jsonb_agg(to_jsonb(u)), '[]'::jsonb)
      from (
        select p.user_id, p.email, p.status, p.created_at,
               coalesce(w.balance_toman, 0) as balance_toman,
               (select count(*) from public.ema_subscriptions s where s.user_id = p.user_id and s.starts_at <= now() and s.ends_at > now()) as active_subscriptions
        from public.ema_profiles p
        left join public.ema_wallets w on w.user_id = p.user_id
        order by p.created_at desc
        limit 100
      ) u
    ),
    'orders', (
      select coalesce(jsonb_agg(to_jsonb(o)), '[]'::jsonb)
      from (
        select po.id, po.user_id, p.email, po.order_type, po.plan_id,
               pl.name as plan_name, po.amount_toman, po.status,
               po.payment_reference, po.receipt_note, po.created_at,
               po.receipt_submitted_at, po.reviewed_at, po.reviewed_by
        from public.ema_payment_orders po
        left join public.ema_profiles p on p.user_id = po.user_id
        left join public.ema_plans pl on pl.id = po.plan_id
        order by po.created_at desc
        limit 200
      ) o
    ),
    'subscriptions', (
      select coalesce(jsonb_agg(to_jsonb(s)), '[]'::jsonb)
      from (
        select es.id, es.user_id, p.email, es.plan_id, pl.name as plan_name,
               es.starts_at, es.ends_at, es.payment_order_id,
               (es.starts_at <= now() and es.ends_at > now()) as is_active
        from public.ema_subscriptions es
        left join public.ema_profiles p on p.user_id = es.user_id
        left join public.ema_plans pl on pl.id = es.plan_id
        order by es.created_at desc
        limit 100
      ) s
    ),
    'recent_audit', (
      select coalesce(jsonb_agg(to_jsonb(a)), '[]'::jsonb)
      from (
        select id, actor_user_id, action, target_user_id, details, created_at
        from public.ema_audit_log
        order by created_at desc
        limit 50
      ) a
    )
  ) into v_result;

  return v_result;
end;
$$;

revoke all on function public.ema_admin_dashboard_snapshot(uuid) from public, anon, authenticated;
grant execute on function public.ema_admin_dashboard_snapshot(uuid) to service_role;
