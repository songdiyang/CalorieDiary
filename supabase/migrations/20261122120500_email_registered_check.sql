-- 注册前校验邮箱是否已注册：已注册邮箱引导直接登录，避免验证码注册流程误改已有账号密码。
create or replace function public.is_email_registered(p_email text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
        from auth.users u
        where lower(u.email) = lower(trim(p_email))
    );
$$;

revoke all on function public.is_email_registered(text) from public, anon, authenticated;
grant execute on function public.is_email_registered(text) to anon, authenticated;