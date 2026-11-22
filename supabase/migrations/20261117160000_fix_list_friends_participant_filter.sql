-- 修复 list_friends() 泄露他人好友关系的缺陷：
-- 旧实现 `join profiles on p.id = case when f.user_low = current_uid then f.user_high else f.user_low end`
-- 只按 user_low 判断方向，没有限制「当前用户是该关系的一方」，
-- 导致任意登录用户都会拿到 friendships 里所有「对方的」好友（幽灵好友），
-- 进而在聊天页被 send_friend_message / list_friend_messages 的 not_friends 校验拒绝。
-- 这里补上参与者过滤，并保持返回列与排序不变。

create or replace function public.list_friends()
returns table (
    friend_id uuid,
    public_id text,
    nickname text,
    avatar_emoji text,
    friends_since timestamptz,
    last_message text,
    last_message_at timestamptz,
    spark_count integer
)
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    today date := (now() at time zone 'Asia/Shanghai')::date;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    return query
    select p.id, p.public_id, p.nickname, p.avatar_emoji, f.created_at,
           latest.content, latest.created_at,
           case
               when f.spark_last_date >= today - 1 then f.spark_count
               else 0
           end
    from public.friendships f
    join public.profiles p
      on p.id = case
          when f.user_low = current_uid then f.user_high
          else f.user_low
      end
    left join lateral (
        select m.content, m.created_at
        from public.friend_messages m
        where (m.sender_id = current_uid and m.receiver_id = p.id)
           or (m.receiver_id = current_uid and m.sender_id = p.id)
        order by m.created_at desc
        limit 1
    ) latest on true
    -- 只返回当前用户亲自参与的好友关系。
    where f.user_low = current_uid or f.user_high = current_uid
    order by coalesce(latest.created_at, f.created_at) desc;
end;
$$;

revoke all on function public.list_friends() from public, anon, authenticated;
grant execute on function public.list_friends() to authenticated;
