-- 注销墓碑：业务数据全部随 auth 用户级联删除，
-- 仅保留"该 ID 已注销"这一条最小记录，供他人主页展示已注销状态。

create table if not exists public.deleted_profiles (
    user_id uuid primary key,
    deleted_at timestamptz not null default now()
);

alter table public.deleted_profiles enable row level security;

revoke all on table public.deleted_profiles from public, anon, authenticated;

-- 注销时先写入墓碑，再删除 auth 用户（级联清理全部业务数据）
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
    insert into public.deleted_profiles (user_id)
    values (current_uid)
    on conflict (user_id) do nothing;
    delete from auth.users where id = current_uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- 他人主页查不到资料时，用它区分“已注销”和“不存在”
create or replace function public.is_profile_deleted(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1 from public.deleted_profiles d where d.user_id = p_user_id
    );
$$;

revoke all on function public.is_profile_deleted(uuid) from public, anon, authenticated;
grant execute on function public.is_profile_deleted(uuid) to authenticated;