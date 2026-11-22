-- 群聊主页：群公告字段 + 群信息/成员读取 RPC + 群主修改群名称与群公告。
-- 读接口要求调用者是群成员；写接口只允许群主（groups.owner_id）。

alter table public.groups add column if not exists announcement text;

-- 群聊主页的基础信息：名称、群主、公告、成员数。
create or replace function public.get_group_info(p_group_id text)
returns table (
    info_group_id text,
    info_name text,
    info_owner_id uuid,
    info_announcement text,
    info_member_count integer
)
language plpgsql
security definer
set search_path = public
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
        (select count(*)::integer from public.group_members m where m.group_id = g.id)
    from public.groups g
    where g.id = p_group_id;
end;
$$;

-- 群成员列表：群主排在最前，其余按入群时间。
-- 输出列带 member_ 前缀，避免与内部查询的同名列在 plpgsql 里产生歧义。
create or replace function public.list_group_members(p_group_id text)
returns table (
    member_id uuid,
    member_nickname text,
    member_avatar_emoji text,
    is_owner boolean,
    member_joined_at timestamptz
)
language plpgsql
security definer
set search_path = public
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
        gm.user_id,
        coalesce(nullif(p.nickname, ''), gm.nickname),
        coalesce(nullif(p.avatar_emoji, ''), gm.avatar_emoji),
        gm.user_id = g.owner_id,
        gm.joined_at
    from public.group_members gm
    join public.groups g on g.id = gm.group_id
    left join public.profiles p on p.id = gm.user_id
    where gm.group_id = p_group_id
    order by (gm.user_id = g.owner_id) desc, gm.joined_at;
end;
$$;

-- 群主修改群名称 / 群公告；传 null 表示该项不变，空公告可清空。
create or replace function public.update_group_info(
    p_group_id text,
    p_name text default null,
    p_announcement text default null
)
returns table (
    updated_name text,
    updated_announcement text
)
language plpgsql
security definer
set search_path = public
as $$
declare
    current_uid uuid := auth.uid();
    trimmed_name text;
    next_announcement text;
begin
    if current_uid is null then
        raise exception 'not_authenticated';
    end if;
    if not exists (
        select 1 from public.groups g
        where g.id = p_group_id and g.owner_id = current_uid
    ) then
        raise exception 'not_group_owner';
    end if;

    if p_name is not null then
        trimmed_name := btrim(p_name);
        if char_length(trimmed_name) < 1 or char_length(trimmed_name) > 30 then
            raise exception 'invalid_group_name';
        end if;
        update public.groups g set name = trimmed_name where g.id = p_group_id;
    end if;

    if p_announcement is not null then
        next_announcement := btrim(p_announcement);
        if char_length(next_announcement) > 500 then
            raise exception 'announcement_too_long';
        end if;
        update public.groups g
        set announcement = nullif(next_announcement, '')
        where g.id = p_group_id;
    end if;

    return query
    select g.name, coalesce(g.announcement, '')
    from public.groups g
    where g.id = p_group_id;
end;
$$;

revoke all on function public.get_group_info(text) from public, anon;
revoke all on function public.list_group_members(text) from public, anon;
revoke all on function public.update_group_info(text, text, text) from public, anon;
grant execute on function public.get_group_info(text) to authenticated;
grant execute on function public.list_group_members(text) to authenticated;
grant execute on function public.update_group_info(text, text, text) to authenticated;

notify pgrst, 'reload schema';
