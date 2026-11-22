-- 注册触发器只能由 auth.users 触发，不能作为 RPC 接口调用。
revoke all on function public.handle_new_user() from public, anon, authenticated;
