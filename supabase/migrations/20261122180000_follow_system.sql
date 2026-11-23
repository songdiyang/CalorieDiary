-- 关注与好友两阶段模型：
-- 1. 关注是单向关系（follows 表），对方在消息页看到“xxx 关注了你”，可回关；
-- 2. 只有互相关注后才能发起好友申请（friend_requests）；
-- 3. 对方接受申请后建立 friendships，解锁私聊、火花与亲密关系。

create table if not exists public.follows (
    follower_id uuid not null references public.profiles(id) on delete cascade,
    followee_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (follower_id, followee_id),
    check (follower_id <> followee_id)
);

create index if not exists follows_followee_idx on public.follows(followee_id);

alter table public.follows enable row level security;

-- 关注关系只通过下面的安全函数读写，表本身不对客户端开放。
revoke all on table public.follows from anon, authenticated;

-- 历史好友关系补成互相关注（好友必然互关）。
insert into public.follows (follower_id, followee_id, created_at)
select f.user_low, f.user_high, f.created_at
from public.friendships f
on conflict (follower_id, followee_id) do nothing;

insert into public.follows (follower_id, followee_id, created_at)
select f.user_high, f.user_low, f.created_at
from public.friendships f
on conflict (follower_id, followee_id) do nothing;

-- 历史“好友请求”本质是单向关注：转为关注关系后由对方回关，
-- 双方互关后再走新的好友申请流程。
insert into public.follows (follower_id, followee_id, created_at)
select r.requester_id, r.addressee_id, r.created_at
from public.friend_requests r
on conflict (follower_id, followee_id) do nothing;

delete from public.friend_requests;

-- 关注某人：已关注时幂等；对方也关注我时返回 mutual。
create or replace function public.follow_user(p_user_id uuid)
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
    if p_user_id = current_uid then
        return 'self';
    end if;
    if not exists (select 1 from public.profiles p where p.id = p_user_id) then
        return 'not_found';
    end if;

    insert into public.follows (follower_id, followee_id)
    values (current_uid, p_user_id)
    on conflict (follower_id, followee_id) do nothing;

    if exists (
        select 1 from public.follows f
        where f.follower_id = p_user_id and f.followee_id = current_uid
    ) then
        return 'mutual';
    end if;

    return 'following';
end;
$$;

-- 取消关注只撤销“我关注对方”这一个方向：好友关系一并解除（好友必须互关），
-- 但对方对我的关注保留，仍会留在我的粉丝列表里。
create or replace function public.unfollow_user(p_user_id uuid)
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    was_following boolean := false;
    had_friendship boolean := false;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_user_id = current_uid then
        return 'self';
    end if;

    delete from public.follows f
    where f.follower_id = current_uid
      and f.followee_id = p_user_id;
    was_following := found;

    delete from public.friendships fr
    where fr.user_low = least(current_uid, p_user_id)
      and fr.user_high = greatest(current_uid, p_user_id);
    had_friendship := found;

    -- 互关解除后，双向的好友申请都不再有效。
    delete from public.friend_requests r
    where (r.requester_id = current_uid and r.addressee_id = p_user_id)
       or (r.requester_id = p_user_id and r.addressee_id = current_uid);

    if was_following or had_friendship then
        return 'unfollowed';
    end if;

    return 'not_following';
end;
$$;

-- 统计口径：关注 = 我发出的关注；粉丝 = 关注我的用户；互关 = 双向关注。
create or replace function public.get_social_summary(p_user_id uuid default null)
returns table (
    following_count bigint,
    follower_count bigint,
    mutual_count bigint
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target_uid uuid := coalesce(p_user_id, (select auth.uid()));
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if target_uid is null then
        raise exception 'invalid_user' using errcode = '22023';
    end if;

    return query
    select
        (select count(*) from public.follows f where f.follower_id = target_uid),
        (select count(*) from public.follows f where f.followee_id = target_uid),
        (select count(*)
         from public.follows f
         join public.follows back
           on back.follower_id = f.followee_id
          and back.followee_id = f.follower_id
         where f.follower_id = target_uid);
end;
$$;

drop function if exists public.list_following(uuid);

create function public.list_following(p_user_id uuid default null)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    mutual boolean,
    followed_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target_uid uuid := coalesce(p_user_id, (select auth.uid()));
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if target_uid is null then
        raise exception 'invalid_user' using errcode = '22023';
    end if;

    return query
    select
        p.id,
        p.nickname,
        p.avatar_emoji,
        exists (
            select 1 from public.follows back
            where back.follower_id = p.id and back.followee_id = target_uid
        ),
        f.created_at
    from public.follows f
    join public.profiles p on p.id = f.followee_id
    where f.follower_id = target_uid
    order by f.created_at desc;
end;
$$;

drop function if exists public.list_followers(uuid);

create function public.list_followers(p_user_id uuid default null)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    mutual boolean,
    followed_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target_uid uuid := coalesce(p_user_id, (select auth.uid()));
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if target_uid is null then
        raise exception 'invalid_user' using errcode = '22023';
    end if;

    return query
    select
        p.id,
        p.nickname,
        p.avatar_emoji,
        exists (
            select 1 from public.follows back
            where back.follower_id = target_uid and back.followee_id = p.id
        ),
        f.created_at
    from public.follows f
    join public.profiles p on p.id = f.follower_id
    where f.followee_id = target_uid
    order by f.created_at desc;
end;
$$;

-- add_friend 现在表示“申请成为好友”：必须先互相关注。
create or replace function public.add_friend(p_user_id uuid)
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
    i_follow boolean;
    follows_me boolean;
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

    if not found then
        return query
        select 'not_found'::text, null::uuid, null::text, null::text, null::timestamptz;
        return;
    end if;

    low_id := least(current_uid, p_user_id);
    high_id := greatest(current_uid, p_user_id);

    select fr.created_at into since
    from public.friendships fr
    where fr.user_low = low_id and fr.user_high = high_id;

    if since is not null then
        return query
        select 'already_friends'::text, p_user_id, target_nickname, target_avatar, since;
        return;
    end if;

    select exists (
        select 1 from public.follows f
        where f.follower_id = current_uid and f.followee_id = p_user_id
    ), exists (
        select 1 from public.follows f
        where f.follower_id = p_user_id and f.followee_id = current_uid
    ) into i_follow, follows_me;

    if not (i_follow and follows_me) then
        return query
        select 'follow_required'::text, null::uuid, target_nickname, target_avatar, null::timestamptz;
        return;
    end if;

    -- 对方已经申请过：双方意愿一致，直接成为好友。
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

        select fr.created_at into since
        from public.friendships fr
        where fr.user_low = low_id and fr.user_high = high_id;

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

-- 接受好友申请时再次确认双方仍互相关注，避免申请期间一方已取消关注。
create or replace function public.respond_friend_request(p_requester_id uuid, p_accept boolean)
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    mutual boolean;
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

    select exists (
        select 1 from public.follows f
        where f.follower_id = current_uid and f.followee_id = p_requester_id
    ) and exists (
        select 1 from public.follows f
        where f.follower_id = p_requester_id and f.followee_id = current_uid
    ) into mutual;

    if not mutual then
        return 'not_mutual';
    end if;

    insert into public.friendships (user_low, user_high)
    values (least(current_uid, p_requester_id), greatest(current_uid, p_requester_id))
    on conflict (user_low, user_high) do nothing;

    return 'friends';
end;
$$;

-- 搜索结果带出关注/好友申请状态，供列表直接展示对应操作。
drop function if exists public.search_users(text);

create function public.search_users(p_query text)
returns table (
    user_id uuid,
    public_id text,
    nickname text,
    avatar_emoji text,
    is_following boolean,
    is_followed_by boolean,
    is_friend boolean,
    has_sent_request boolean,
    has_incoming_request boolean
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    cleaned text := trim(coalesce(p_query, ''));
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if cleaned = '' then
        return;
    end if;

    return query
    select
        p.id,
        p.public_id,
        p.nickname,
        p.avatar_emoji,
        exists (
            select 1 from public.follows f
            where f.follower_id = current_uid and f.followee_id = p.id
        ),
        exists (
            select 1 from public.follows f
            where f.follower_id = p.id and f.followee_id = current_uid
        ),
        exists (
            select 1 from public.friendships fr
            where fr.user_low = least(current_uid, p.id)
              and fr.user_high = greatest(current_uid, p.id)
        ),
        exists (
            select 1 from public.friend_requests r
            where r.requester_id = current_uid and r.addressee_id = p.id
        ),
        exists (
            select 1 from public.friend_requests r
            where r.requester_id = p.id and r.addressee_id = current_uid
        )
    from public.profiles p
    where p.id <> current_uid
      and (
          lower(p.nickname) like '%' || lower(cleaned) || '%'
          or (cleaned ~ '^[0-9]{1,8}$' and p.public_id like cleaned || '%')
      )
    order by (p.public_id = cleaned) desc, p.nickname
    limit 20;
end;
$$;

-- 他人主页补充关注与好友申请状态，供主页上的关注/申请按钮使用。
drop function if exists public.get_public_profile(uuid);

create function public.get_public_profile(p_user_id uuid)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    public_id text,
    is_self boolean,
    is_friend boolean,
    is_following boolean,
    is_followed_by boolean,
    is_mutual boolean,
    has_sent_request boolean,
    has_incoming_request boolean,
    level integer
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
    select
        p.id,
        p.nickname,
        p.avatar_emoji,
        p.public_id,
        p.id = current_uid,
        exists (
            select 1 from public.friendships fr
            where fr.user_low = least(current_uid, p.id)
              and fr.user_high = greatest(current_uid, p.id)
        ),
        exists (
            select 1 from public.follows f
            where f.follower_id = current_uid and f.followee_id = p.id
        ),
        exists (
            select 1 from public.follows f
            where f.follower_id = p.id and f.followee_id = current_uid
        ),
        (
            exists (
                select 1 from public.follows f
                where f.follower_id = current_uid and f.followee_id = p.id
            )
            and exists (
                select 1 from public.follows f
                where f.follower_id = p.id and f.followee_id = current_uid
            )
        ),
        exists (
            select 1 from public.friend_requests r
            where r.requester_id = current_uid and r.addressee_id = p.id
        ),
        exists (
            select 1 from public.friend_requests r
            where r.requester_id = p.id and r.addressee_id = current_uid
        ),
        public.game_level(coalesce(w.exp, 0))
    from public.profiles p
    left join public.game_wallets w on w.user_id = p.id
    where p.id = p_user_id;
end;
$$;

revoke all on function public.follow_user(uuid) from public, anon, authenticated;
revoke all on function public.list_following(uuid) from public, anon, authenticated;
revoke all on function public.list_followers(uuid) from public, anon, authenticated;
revoke all on function public.search_users(text) from public, anon, authenticated;
revoke all on function public.get_public_profile(uuid) from public, anon, authenticated;

grant execute on function public.follow_user(uuid) to authenticated;
grant execute on function public.list_following(uuid) to authenticated;
grant execute on function public.list_followers(uuid) to authenticated;
grant execute on function public.search_users(text) to authenticated;
grant execute on function public.get_public_profile(uuid) to authenticated;