-- 邀请码恢复为“注册选填”：填写有效邀请码时，注册后自动与邀请人成为好友。
-- 用户邀请码仍为 8 位数字，供老用户分享给新用户。

create or replace function public.generate_invitation_code()
returns text
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
    candidate text;
begin
    loop
        candidate := lpad(floor(random() * 100000000)::bigint::text, 8, '0');
        exit when not exists (
            select 1 from public.profiles where invitation_code = candidate
        );
    end loop;
    return candidate;
end;
$$;

revoke all on function public.generate_invitation_code() from public, anon, authenticated;

alter table public.profiles add column if not exists invitation_code text;
update public.profiles set invitation_code = public.generate_invitation_code()
where invitation_code is null;
alter table public.profiles alter column invitation_code set default public.generate_invitation_code();
create unique index if not exists profiles_invitation_code_unique_idx
    on public.profiles (invitation_code);
alter table public.profiles drop constraint if exists profiles_invitation_code_format;
alter table public.profiles add constraint profiles_invitation_code_format
    check (invitation_code ~ '^[0-9]{8}$');

-- 注册页校验邀请码（选填）；只返回布尔值，不泄露其他用户资料。
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
            select 1 from public.profiles p where p.invitation_code = p_code
        )
    end;
$$;

revoke all on function public.is_invitation_code_valid(text) from public, anon, authenticated;
grant execute on function public.is_invitation_code_valid(text) to anon, authenticated;

-- 注册成功后应用邀请码：与邀请人建立双向好友关系。
create or replace function public.apply_invitation_code(p_code text)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    referrer_id uuid;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_code is null or p_code !~ '^[0-9]{8}$' then
        return false;
    end if;
    select p.id into referrer_id
    from public.profiles p
    where p.invitation_code = p_code;
    if referrer_id is null or referrer_id = current_uid then
        return false;
    end if;
    insert into public.friendships (user_low, user_high)
    values (least(current_uid, referrer_id), greatest(current_uid, referrer_id))
    on conflict (user_low, user_high) do nothing;
    return true;
end;
$$;

revoke all on function public.apply_invitation_code(text) from public, anon, authenticated;
grant execute on function public.apply_invitation_code(text) to authenticated;