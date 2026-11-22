-- 注册前只返回邀请码是否有效，避免已注册邮箱的防枚举响应绕过无效邀请码提示。
create or replace function public.is_invitation_code_valid(p_code text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select case
        when p_code is null or p_code !~ '^[0-9]{8}$' then false
        else exists (
            select 1
            from public.profiles p
            where p.invitation_code = p_code
        )
    end;
$$;

-- 只暴露布尔结果，不开放资料表或其他用户信息。
revoke all on function public.is_invitation_code_valid(text) from public, anon, authenticated;
grant execute on function public.is_invitation_code_valid(text) to anon, authenticated;
