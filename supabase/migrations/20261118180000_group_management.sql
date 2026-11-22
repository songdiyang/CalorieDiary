-- 群聊管理：群主解散群聊 / 转让群主，普通成员退出群聊。
-- 解散与转让只允许群主；退出群聊时群主需先转让或解散。
-- 三张关联表（group_members / group_messages / group_invitations）的
-- group_id 外键都是 on delete cascade，解散群聊会一并清理。

-- 群主解散群聊：删除群记录，成员/消息/邀请级联删除。
create or replace function public.dissolve_group(p_group_id text)
returns table (
    result_group_id text,
    result_status text
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
        select 1 from public.groups g
        where g.id = p_group_id and g.owner_id = current_uid
    ) then
        raise exception 'not_group_owner';
    end if;

    delete from public.groups g where g.id = p_group_id;
    return query select p_group_id, 'dissolved'::text;
end;
$$;

-- 群主把群转让给群内其他成员。
create or replace function public.transfer_group_ownership(
    p_group_id text,
    p_new_owner uuid
)
returns table (
    result_group_id text,
    result_status text
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
        select 1 from public.groups g
        where g.id = p_group_id and g.owner_id = current_uid
    ) then
        raise exception 'not_group_owner';
    end if;
    if p_new_owner = current_uid then
        raise exception 'already_owner';
    end if;
    if not exists (
        select 1 from public.group_members gm
        where gm.group_id = p_group_id and gm.user_id = p_new_owner
    ) then
        raise exception 'transfer_target_not_member';
    end if;

    update public.groups g
    set owner_id = p_new_owner
    where g.id = p_group_id;

    return query select p_group_id, 'transferred'::text;
end;
$$;

-- 普通成员退出群聊：移除成员关系并清理自己在该群的邀请记录。
create or replace function public.leave_group(p_group_id text)
returns table (
    result_group_id text,
    result_status text
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
    if exists (
        select 1 from public.groups g
        where g.id = p_group_id and g.owner_id = current_uid
    ) then
        raise exception 'owner_cannot_leave';
    end if;

    delete from public.group_members gm
    where gm.group_id = p_group_id and gm.user_id = current_uid;
    delete from public.group_invitations gi
    where gi.group_id = p_group_id and gi.invitee_id = current_uid;

    return query select p_group_id, 'left'::text;
end;
$$;

revoke all on function public.dissolve_group(text) from public, anon;
revoke all on function public.transfer_group_ownership(text, uuid) from public, anon;
revoke all on function public.leave_group(text) from public, anon;
grant execute on function public.dissolve_group(text) to authenticated;
grant execute on function public.transfer_group_ownership(text, uuid) to authenticated;
grant execute on function public.leave_group(text) to authenticated;

notify pgrst, 'reload schema';
