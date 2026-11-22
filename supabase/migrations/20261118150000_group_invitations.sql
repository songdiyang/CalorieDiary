-- 群聊只能被邀请：邀请不再直接把人塞进群，而是生成一条待处理邀请，
-- 被邀请人在消息页看到「群聊邀请」后自行选择加入或拒绝。

create table if not exists public.group_invitations (
    id bigserial primary key,
    group_id text not null references public.groups (id) on delete cascade,
    inviter_id uuid not null references public.profiles (id) on delete cascade,
    invitee_id uuid not null references public.profiles (id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
    created_at timestamptz not null default now(),
    responded_at timestamptz,
    unique (group_id, invitee_id)
);

-- 消息页每次刷新都按 (invitee, status) 拉待处理邀请。
create index if not exists group_invitations_invitee_idx
    on public.group_invitations (invitee_id, status, created_at desc);

alter table public.group_invitations enable row level security;

-- 只允许通过 security definer 的 RPC 访问，客户端无法直接读写。
revoke all on table public.group_invitations from public, anon, authenticated;

-- 邀请好友进群：只记录邀请，不直接入群；重复邀请复用同一条记录。
create or replace function public.invite_group_members(
    p_group_id text,
    p_user_ids uuid[]
)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target uuid;
    invited integer := 0;
    affected integer;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if not exists (
        select 1 from public.group_members gm
        where gm.group_id = p_group_id and gm.user_id = current_uid
    ) then
        raise exception 'not_group_member' using errcode = '42501';
    end if;
    if p_user_ids is null then
        return 0;
    end if;

    foreach target in array p_user_ids loop
        if target = current_uid then
            continue;
        end if;
        -- 仅允许邀请自己的好友，避免被任意拉群
        if not exists (
            select 1 from public.friendships f
            where f.user_low = least(current_uid, target)
              and f.user_high = greatest(current_uid, target)
        ) then
            continue;
        end if;
        -- 已经在群里的不再打扰
        if exists (
            select 1 from public.group_members gm
            where gm.group_id = p_group_id and gm.user_id = target
        ) then
            continue;
        end if;
        insert into public.group_invitations as gi (group_id, inviter_id, invitee_id)
        values (p_group_id, current_uid, target)
        on conflict (group_id, invitee_id) do update
            set status = 'pending',
                inviter_id = current_uid,
                created_at = now(),
                responded_at = null
            where gi.status <> 'pending';
        get diagnostics affected = row_count;
        invited := invited + affected;
    end loop;
    return invited;
end;
$$;

-- 当前待处理的群聊邀请（含群名、邀请人资料、成员数）。
drop function if exists public.list_group_invitations();

create function public.list_group_invitations()
returns table (
    invitation_id bigint,
    group_id text,
    group_name text,
    inviter_id uuid,
    inviter_nickname text,
    inviter_avatar_emoji text,
    member_count integer,
    created_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    return query
    select i.id, i.group_id, g.name, i.inviter_id,
           coalesce(p.nickname, ''), coalesce(p.avatar_emoji, ''),
           (select count(*)::integer from public.group_members gm where gm.group_id = i.group_id),
           i.created_at
    from public.group_invitations i
    join public.groups g on g.id = i.group_id
    left join public.profiles p on p.id = i.inviter_id
    where i.invitee_id = current_uid
      and i.status = 'pending'
    order by i.created_at desc;
end;
$$;

-- 接受或拒绝邀请；接受时才写入成员表，返回 (joined_group_id, response_status)。
-- 输出列不能用 group_id/status：RETURNS TABLE 的列会变成 plpgsql 变量，
-- 与插入/更新语句里的同名列产生歧义。
drop function if exists public.respond_group_invitation(bigint, boolean);

create function public.respond_group_invitation(
    p_invitation_id bigint,
    p_accept boolean
)
returns table (joined_group_id text, response_status text)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    invitation public.group_invitations%rowtype;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select * into invitation
    from public.group_invitations i
    where i.id = p_invitation_id
      and i.invitee_id = current_uid
      and i.status = 'pending'
    for update;

    if not found then
        raise exception 'invitation_not_found';
    end if;

    if p_accept then
        insert into public.group_members (group_id, user_id, nickname, avatar_emoji)
        select invitation.group_id, pr.id, pr.nickname, pr.avatar_emoji
        from public.profiles pr
        where pr.id = current_uid
        on conflict (group_id, user_id) do nothing;

        update public.group_invitations
           set status = 'accepted', responded_at = now()
         where id = invitation.id;

        return query select invitation.group_id, 'accepted'::text;
    else
        update public.group_invitations
           set status = 'declined', responded_at = now()
         where id = invitation.id;

        return query select invitation.group_id, 'declined'::text;
    end if;
end;
$$;

revoke all on function public.invite_group_members(text, uuid[]) from public, anon;
revoke all on function public.list_group_invitations() from public, anon;
revoke all on function public.respond_group_invitation(bigint, boolean) from public, anon;

grant execute on function public.invite_group_members(text, uuid[]) to authenticated;
grant execute on function public.list_group_invitations() to authenticated;
grant execute on function public.respond_group_invitation(bigint, boolean) to authenticated;

notify pgrst, 'reload schema';
