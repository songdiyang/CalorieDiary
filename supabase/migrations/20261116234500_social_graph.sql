-- 社交图谱：邀请注册的好友默认互关，并提供关注/粉丝/互关的统计与列表。
-- 关注 = 已互关的好友 + 我发出但未被接受的请求；
-- 粉丝 = 已互关的好友 + 我收到但尚未回关的请求；
-- 互关 = friendships 中的好友。

-- 历史邀请关系补成互关，邀请双方互相出现在好友列表里。
insert into public.friendships (user_low, user_high)
select least(p.invited_by, p.id), greatest(p.invited_by, p.id)
from public.profiles p
where p.invited_by is not null
  and p.invited_by <> p.id
on conflict (user_low, user_high) do nothing;

delete from public.friend_requests r
using public.friendships f
where (r.requester_id = f.user_low and r.addressee_id = f.user_high)
   or (r.requester_id = f.user_high and r.addressee_id = f.user_low);

-- 通过邀请码注册的新用户与邀请人直接互关。
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    referrer_id uuid;
begin
    select p.id into referrer_id
    from public.profiles p
    where p.invitation_code = nullif(new.raw_user_meta_data ->> 'invite_code', '');

    insert into public.profiles (id, nickname, invited_by)
    values (
        new.id,
        coalesce(
            nullif(new.raw_user_meta_data ->> 'nickname', ''),
            '用户' || upper(substr(new.id::text, 1, 8))
        ),
        referrer_id
    )
    on conflict (id) do nothing;

    if referrer_id is not null and referrer_id <> new.id then
        insert into public.friendships (user_low, user_high)
        values (least(referrer_id, new.id), greatest(referrer_id, new.id))
        on conflict (user_low, user_high) do nothing;

        delete from public.friend_requests r
        where (r.requester_id = referrer_id and r.addressee_id = new.id)
           or (r.requester_id = new.id and r.addressee_id = referrer_id);
    end if;

    return new;
end;
$$;

revoke all on function public.handle_new_user() from public, anon, authenticated;

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
        (
            select count(*) from public.friendships f
            where f.user_low = target_uid or f.user_high = target_uid
        ) + (
            select count(*) from public.friend_requests r
            where r.requester_id = target_uid
              and not exists (
                  select 1 from public.friendships f2
                  where f2.user_low = least(target_uid, r.addressee_id)
                    and f2.user_high = greatest(target_uid, r.addressee_id)
              )
        ),
        (
            select count(*) from public.friendships f
            where f.user_low = target_uid or f.user_high = target_uid
        ) + (
            select count(*) from public.friend_requests r
            where r.addressee_id = target_uid
              and not exists (
                  select 1 from public.friendships f2
                  where f2.user_low = least(target_uid, r.requester_id)
                    and f2.user_high = greatest(target_uid, r.requester_id)
              )
        ),
        (
            select count(*) from public.friendships f
            where f.user_low = target_uid or f.user_high = target_uid
        );
end;
$$;

create or replace function public.list_following(p_user_id uuid default null)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    mutual boolean
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
    select u.user_id, u.nickname, u.avatar_emoji, u.mutual
    from (
        select p.id as user_id, p.nickname, p.avatar_emoji, true as mutual
        from public.friendships f
        join public.profiles p
          on p.id = case
              when f.user_low = target_uid then f.user_high
              else f.user_low
          end
        where f.user_low = target_uid or f.user_high = target_uid
        union all
        select p.id, p.nickname, p.avatar_emoji, false
        from public.friend_requests r
        join public.profiles p on p.id = r.addressee_id
        where r.requester_id = target_uid
          and not exists (
              select 1 from public.friendships f2
              where f2.user_low = least(target_uid, r.addressee_id)
                and f2.user_high = greatest(target_uid, r.addressee_id)
          )
    ) u
    order by u.mutual desc, u.nickname;
end;
$$;

create or replace function public.list_followers(p_user_id uuid default null)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    mutual boolean
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
    select u.user_id, u.nickname, u.avatar_emoji, u.mutual
    from (
        select p.id as user_id, p.nickname, p.avatar_emoji, true as mutual
        from public.friendships f
        join public.profiles p
          on p.id = case
              when f.user_low = target_uid then f.user_high
              else f.user_low
          end
        where f.user_low = target_uid or f.user_high = target_uid
        union all
        select p.id, p.nickname, p.avatar_emoji, false
        from public.friend_requests r
        join public.profiles p on p.id = r.requester_id
        where r.addressee_id = target_uid
          and not exists (
              select 1 from public.friendships f2
              where f2.user_low = least(target_uid, r.requester_id)
                and f2.user_high = greatest(target_uid, r.requester_id)
          )
    ) u
    order by u.mutual desc, u.nickname;
end;
$$;

-- 他人主页只返回公开资料，不包含任何饮食数据。
create or replace function public.get_public_profile(p_user_id uuid)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    public_id text,
    is_self boolean,
    is_friend boolean
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
            select 1 from public.friendships f
            where f.user_low = least(current_uid, p.id)
              and f.user_high = greatest(current_uid, p.id)
        )
    from public.profiles p
    where p.id = p_user_id;
end;
$$;

revoke all on function public.get_social_summary(uuid) from public, anon, authenticated;
revoke all on function public.list_following(uuid) from public, anon, authenticated;
revoke all on function public.list_followers(uuid) from public, anon, authenticated;
revoke all on function public.get_public_profile(uuid) from public, anon, authenticated;

grant execute on function public.get_social_summary(uuid) to authenticated;
grant execute on function public.list_following(uuid) to authenticated;
grant execute on function public.list_followers(uuid) to authenticated;
grant execute on function public.get_public_profile(uuid) to authenticated;
