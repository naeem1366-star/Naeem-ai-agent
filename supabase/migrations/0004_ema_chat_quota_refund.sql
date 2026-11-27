-- Return one daily free-chat request when the provider fails before a usable answer is returned.
-- The caller supplies the UTC date used by ema_consume_chat_quota so midnight crossings are safe.
create or replace function public.ema_refund_chat_quota(
  p_user_id uuid,
  p_usage_date date default (now() at time zone 'utc')::date
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_refunded boolean := false;
begin
  if p_user_id is null or p_usage_date is null then
    return false;
  end if;

  update public.ema_chat_daily_usage
  set request_count = request_count - 1,
      updated_at = now()
  where user_id = p_user_id
    and usage_date = p_usage_date
    and request_count > 0
  returning true into v_refunded;

  return coalesce(v_refunded, false);
end;
$$;

revoke all on function public.ema_refund_chat_quota(uuid, date) from public, anon, authenticated;
grant execute on function public.ema_refund_chat_quota(uuid, date) to service_role;
