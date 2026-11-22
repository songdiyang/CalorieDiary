-- Supabase 可能通过默认权限单独授予 anon/authenticated 函数执行权。
-- 显式撤销匿名调用；业务函数仍需登录后使用。

revoke all on function public.handle_new_user() from anon, authenticated;
revoke all on function public.generate_friend_code() from anon;
revoke all on function public.list_friends() from anon;
revoke all on function public.add_friend(text) from anon;
revoke all on function public.remove_friend(uuid) from anon;
revoke all on function public.get_group_daily_totals(text, date) from anon;
revoke all on function public.is_group_member(text) from anon;
revoke all on function public.is_group_owner(text) from anon;
