-- 群号搜索开关：群主可在群设置里关闭“通过群号被搜索加入”。
-- 关闭后 join_group 拒绝非成员凭群号加入，只能由群成员邀请。

alter table public.groups
    add column if not exists searchable boolean not null default true;

-- 返回列变化，需要先删除再重建。
drop function if exists public.get_group_info(text);

create function public.get_group_info(p_group_id text)
returns table (
    info_group_id text,
    info_name text,
    info_owner_id uuid,
    info_announcement text,
    info_member_count integer,
    info_searchable boolean
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
begin
    if current_uid is null then
        raise exception 'not_authenticated';
    end if;
    if not exists (
        select 1 from public.group_members gm
        where gm.group_id = p_group_id and gm.user_id = current_uid
    ) then
        raise exception 'not_group_member';
    end if;

    return query
    select
        g.id,
        g.name,
        g.owner_id,
        coalesce(g.announcement, ''),
        (select count(*)::integer from public.group_members m where m.group_id = g.id),
        g.searchable
    from public.groups g
    where g.id = p_group_id;
end;
$$;

revoke all on function public.get_group_info(text) from public, anon, authenticated;
grant execute on function public.get_group_info(text) to authenticated;

create or replace function public.set_group_searchable(p_group_id text, p_searchable boolean)
returns jsonb
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
    update public.groups g
    set searchable = coalesce(p_searchable, true)
    where g.id = p_group_id and g.owner_id = current_uid;
    if not found then
        raise exception 'not_group_owner' using errcode = '42501';
    end if;
    return jsonb_build_object(
        'status', 'success',
        'searchable', coalesce(p_searchable, true)
    );
end;
$$;

revoke all on function public.set_group_searchable(text, boolean) from public, anon, authenticated;
grant execute on function public.set_group_searchable(text, boolean) to authenticated;

-- 关闭搜索后，未入群的人无法凭群号加入；已是成员时重复加入仍可用。
create or replace function public.join_group(p_group_code text)
returns table (
    group_id text,
    name text,
    owner_id uuid,
    calorie_goal integer,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    group_searchable boolean;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if not public.consume_rpc_quota('group_join:' || current_uid::text, 20, 600) then
        raise exception 'join_rate_limited' using errcode = '42901';
    end if;

    select g.searchable into group_searchable
    from public.groups g where g.id = p_group_code;
    if not found then
        raise exception 'group_not_found';
    end if;
    if not coalesce(group_searchable, true)
       and not exists (
           select 1 from public.group_members gm
           where gm.group_id = p_group_code and gm.user_id = current_uid
       ) then
        raise exception 'group_not_searchable' using errcode = '42501';
    end if;

    insert into public.group_members (group_id, user_id, nickname, avatar_emoji)
    select p_group_code, p.id, p.nickname, p.avatar_emoji
    from public.profiles p where p.id = current_uid
    on conflict on constraint group_members_pkey do nothing;

    return query
    select g.id, g.name, g.owner_id, g.calorie_goal, g.created_at
    from public.groups g where g.id = p_group_code;
end;
$$;

revoke all on function public.join_group(text) from public, anon, authenticated;
grant execute on function public.join_group(text) to authenticated;

notify pgrst, 'reload schema';