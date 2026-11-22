-- 好友关系改为双向关注：先发送好友请求，对方接受后才建立互关关系，
-- 私聊与火花等依赖 friendships 的功能因此只在双方同意后可用。

create table if not exists public.friend_requests (
    requester_id uuid not null references public.profiles(id) on delete cascade,
    addressee_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (requester_id, addressee_id),
    check (requester_id <> addressee_id)
);

create index if not exists friend_requests_addressee_idx
    on public.friend_requests(addressee_id);

alter table public.friend_requests enable row level security;

-- 请求只通过下面的安全函数读写，表本身不对客户端开放。
revoke all on table public.friend_requests from anon, authenticated;

-- 返回类型从好友条目改为带状态的请求结果，需先删除旧函数。
drop function if exists public.add_friend(uuid);

create function public.add_friend(p_user_id uuid)
returns table (
    status text,
    friend_id uuid,
    nickname text,
    avatar_emoji text,
    friends_since timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target_nickname text;
    target_avatar text;
    low_id uuid;
    high_id uuid;
    since timestamptz;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_user_id = current_uid then
        raise exception 'cannot_add_self';
    end if;

    select p.nickname, p.avatar_emoji
    into target_nickname, target_avatar
    from public.profiles p
    where p.id = p_user_id;

    -- 不透露账号是否存在之外的任何资料。
    if not found then
        return query
        select 'not_found'::text, null::uuid, null::text, null::text, null::timestamptz;
        return;
    end if;

    low_id := least(current_uid, p_user_id);
    high_id := greatest(current_uid, p_user_id);

    select f.created_at into since
    from public.friendships f
    where f.user_low = low_id and f.user_high = high_id;

    if since is not null then
        return query
        select 'already_friends'::text, p_user_id, target_nickname, target_avatar, since;
        return;
    end if;

    -- 双方都已关注对方：直接建立互关，聊天立即解锁。
    if exists (
        select 1 from public.friend_requests r
        where r.requester_id = p_user_id and r.addressee_id = current_uid
    ) then
        insert into public.friendships (user_low, user_high)
        values (low_id, high_id)
        on conflict (user_low, user_high) do nothing;

        delete from public.friend_requests r
        where (r.requester_id = current_uid and r.addressee_id = p_user_id)
           or (r.requester_id = p_user_id and r.addressee_id = current_uid);

        select f.created_at into since
        from public.friendships f
        where f.user_low = low_id and f.user_high = high_id;

        return query
        select 'friends'::text, p_user_id, target_nickname, target_avatar, since;
        return;
    end if;

    insert into public.friend_requests (requester_id, addressee_id)
    values (current_uid, p_user_id)
    on conflict (requester_id, addressee_id) do nothing;

    return query
    select 'request_sent'::text, null::uuid, target_nickname, target_avatar, null::timestamptz;
end;
$$;

create or replace function public.list_friend_requests()
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
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
    select p.id, p.nickname, p.avatar_emoji, r.created_at
    from public.friend_requests r
    join public.profiles p on p.id = r.requester_id
    where r.addressee_id = current_uid
    order by r.created_at desc;
end;
$$;

create or replace function public.respond_friend_request(p_requester_id uuid, p_accept boolean)
returns text
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

    if not exists (
        select 1 from public.friend_requests r
        where r.requester_id = p_requester_id and r.addressee_id = current_uid
    ) then
        return 'not_found';
    end if;

    delete from public.friend_requests r
    where (r.requester_id = p_requester_id and r.addressee_id = current_uid)
       or (r.requester_id = current_uid and r.addressee_id = p_requester_id);

    if not coalesce(p_accept, false) then
        return 'declined';
    end if;

    insert into public.friendships (user_low, user_high)
    values (least(current_uid, p_requester_id), greatest(current_uid, p_requester_id))
    on conflict (user_low, user_high) do nothing;

    return 'friends';
end;
$$;

-- 删除好友时一并清理双方尚未处理的请求。
create or replace function public.remove_friend(p_friend_id uuid)
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

    delete from public.friend_requests r
    where (r.requester_id = current_uid and r.addressee_id = p_friend_id)
       or (r.requester_id = p_friend_id and r.addressee_id = current_uid);

    delete from public.friendships f
    where (f.user_low = current_uid and f.user_high = p_friend_id)
       or (f.user_high = current_uid and f.user_low = p_friend_id);
end;
$$;

revoke all on function public.add_friend(uuid) from public, anon, authenticated;
revoke all on function public.list_friend_requests() from public, anon, authenticated;
revoke all on function public.respond_friend_request(uuid, boolean) from public, anon, authenticated;

grant execute on function public.add_friend(uuid) to authenticated;
grant execute on function public.list_friend_requests() to authenticated;
grant execute on function public.respond_friend_request(uuid, boolean) to authenticated;
