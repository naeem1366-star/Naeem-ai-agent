-- Keep the Auth trigger function callable only by the trigger owner.
-- The trigger continues to fire for new auth.users rows; the function is not a public RPC.
revoke all on function public.ema_on_auth_user_created() from public, anon, authenticated;
