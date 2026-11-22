-- 注销后邀请码随之失效：把被注销账号的邀请码记入墓碑，
-- 供注册时区分“邀请人已注销”与“邀请码不存在”。

alter table public.deleted_profiles add column if not exists invitation_code text;

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    current_code text;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    select p.invitation_code into current_code
    from public.profiles p
    where p.id = current_uid;
    insert into public.deleted_profiles (user_id, invitation_code)
    values (current_uid, current_code)
    on conflict (user_id) do update set invitation_code = excluded.invitation_code;
    delete from auth.users where id = current_uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

create or replace function public.is_invitation_code_deleted(p_code text)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select case
        when p_code is null or p_code !~ '^[0-9]{8}$' then false
        else exists (
            select 1 from public.deleted_profiles d where d.invitation_code = p_code
        )
    end;
$$;

revoke all on function public.is_invitation_code_deleted(text) from public, anon, authenticated;
grant execute on function public.is_invitation_code_deleted(text) to anon, authenticated;