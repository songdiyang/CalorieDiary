-- 注册改用邮箱验证码（Supabase OTP 验证邮箱归属），移除邀请码体系；
-- 新增账号注销：删除 auth 用户，业务表经外键级联清理。

-- 1. 移除注册邀请码强制校验
drop trigger if exists enforce_registration_invite on auth.users;
drop function if exists public.enforce_registration_invite();
drop function if exists public.is_invitation_code_valid(text);

-- 2. 新用户不再记录邀请人
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    insert into public.profiles (id, nickname)
    values (
        new.id,
        coalesce(
            nullif(new.raw_user_meta_data ->> 'nickname', ''),
            '用户' || upper(substr(new.id::text, 1, 8))
        )
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

revoke all on function public.handle_new_user() from public, anon, authenticated;

-- 3. 清理邀请码与邀请关系列（唯一约束、索引与默认值随列删除）
alter table public.profiles drop column if exists invited_by;
alter table public.profiles drop column if exists invitation_code;
drop function if exists public.generate_invitation_code();

-- 4. 账号注销：仅允许删除当前登录用户；profiles 与全部业务表对 auth.users
--    的外键均为级联删除（profiles 自引用列已在上方移除）。
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    delete from auth.users where id = current_uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;