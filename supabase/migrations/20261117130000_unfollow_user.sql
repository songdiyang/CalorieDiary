-- 取消关注只解除“我关注对方”这一个方向：
-- 单向关注时撤销我发出的请求；互关时删除好友关系，但把对方对我的关注保留为单向请求，
-- 这样取消关注后对方仍留在我的粉丝列表里。
-- 好友页的 remove_friend 仍然是双向解除，语义不变。

create or replace function public.unfollow_user(p_user_id uuid)
returns text
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    low_id uuid;
    high_id uuid;
    had_friendship boolean := false;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_user_id = current_uid then
        return 'self';
    end if;

    low_id := least(current_uid, p_user_id);
    high_id := greatest(current_uid, p_user_id);

    delete from public.friend_requests r
    where r.requester_id = current_uid
      and r.addressee_id = p_user_id;

    delete from public.friendships f
    where f.user_low = low_id
      and f.user_high = high_id;
    had_friendship := found;

    if had_friendship then
        -- 互关取消后，对方仍然关注我，保留其关注关系为单向请求。
        insert into public.friend_requests (requester_id, addressee_id)
        values (p_user_id, current_uid)
        on conflict (requester_id, addressee_id) do nothing;
        return 'unfollowed';
    end if;

    return 'not_following';
end;
$$;

revoke all on function public.unfollow_user(uuid) from public, anon, authenticated;

grant execute on function public.unfollow_user(uuid) to authenticated;
