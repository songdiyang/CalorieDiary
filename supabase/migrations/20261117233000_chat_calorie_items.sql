-- 热量分享卡片增加食物明细快照（items jsonb），可展开查看当天吃了什么。
-- items 结构：[{"name":"米饭","emoji":"🍚","grams":200,"calories":232,"meal":1}]

alter table public.friend_messages
    add column if not exists items jsonb
        check (items is null or jsonb_typeof(items) = 'array');

drop function if exists public.list_friend_messages(uuid);
create function public.list_friend_messages(p_friend_id uuid)
returns table (
    message_id bigint,
    sender_id uuid,
    content text,
    message_type text,
    calories integer,
    entry_date date,
    items jsonb,
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
    if not exists (
        select 1 from public.friendships f
        where f.user_low = least(current_uid, p_friend_id)
          and f.user_high = greatest(current_uid, p_friend_id)
    ) then
        raise exception 'not_friends' using errcode = '42501';
    end if;

    return query
    select recent.message_id, recent.sender_id, recent.content,
           recent.message_type, recent.calories, recent.entry_date,
           recent.items, recent.created_at
    from (
        select m.id as message_id, m.sender_id, m.content,
               m.message_type, m.calories, m.entry_date, m.items, m.created_at
        from public.friend_messages m
        where (m.sender_id = current_uid and m.receiver_id = p_friend_id)
           or (m.receiver_id = current_uid and m.sender_id = p_friend_id)
        order by m.created_at desc
        limit 200
    ) recent
    order by recent.created_at asc;
end;
$$;

revoke all on function public.list_friend_messages(uuid) from public, anon;
grant execute on function public.list_friend_messages(uuid) to authenticated;

drop function if exists public.send_friend_message(uuid, text, text, integer, date);
create function public.send_friend_message(
    p_friend_id uuid,
    p_content text,
    p_message_type text default 'text',
    p_calories integer default null,
    p_entry_date date default null,
    p_items jsonb default null
)
returns table (
    message_id bigint,
    sender_id uuid,
    content text,
    message_type text,
    calories integer,
    entry_date date,
    items jsonb,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    cleaned text := trim(coalesce(p_content, ''));
    kind text := coalesce(nullif(trim(p_message_type), ''), 'text');
    share_calories integer := p_calories;
    share_items jsonb := p_items;
    new_id bigint;
    new_time timestamptz;
    today date := (now() at time zone 'Asia/Shanghai')::date;
    current_count integer;
    last_day date;
    other_sent boolean;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if kind not in ('text', 'calorie_share') then
        raise exception 'invalid_message';
    end if;
    if kind = 'text' and (cleaned = '' or char_length(cleaned) > 1000) then
        raise exception 'invalid_message';
    end if;
    if kind = 'calorie_share' then
        if share_calories is null or share_calories < 0 or share_calories > 100000 then
            raise exception 'invalid_calories';
        end if;
        if share_items is not null then
            if jsonb_typeof(share_items) <> 'array' then
                raise exception 'invalid_items';
            end if;
            if jsonb_array_length(share_items) > 50 then
                raise exception 'invalid_items';
            end if;
        end if;
        cleaned := coalesce(nullif(cleaned, ''), '今日热量分享');
    else
        -- 纯文本消息不接受明细。
        share_calories := null;
        share_items := null;
    end if;

    select f.spark_count, f.spark_last_date
    into current_count, last_day
    from public.friendships f
    where f.user_low = least(current_uid, p_friend_id)
      and f.user_high = greatest(current_uid, p_friend_id)
    for update;
    if not found then
        raise exception 'not_friends' using errcode = '42501';
    end if;

    insert into public.friend_messages as target
        (sender_id, receiver_id, content, message_type, calories, entry_date, items)
    values (current_uid, p_friend_id, cleaned, kind, share_calories, p_entry_date, share_items)
    returning target.id, target.created_at into new_id, new_time;

    select exists (
        select 1 from public.friend_messages m
        where m.sender_id = p_friend_id
          and m.receiver_id = current_uid
          and (m.created_at at time zone 'Asia/Shanghai')::date = today
    ) into other_sent;

    if other_sent and (last_day is null or last_day < today) then
        update public.friendships f
        set spark_count = case
                when last_day = today - 1 then current_count + 1
                else 1
            end,
            spark_last_date = today
        where f.user_low = least(current_uid, p_friend_id)
          and f.user_high = greatest(current_uid, p_friend_id);
    end if;

    return query select new_id, current_uid, cleaned, kind,
        share_calories, p_entry_date, share_items, new_time;
end;
$$;

revoke all on function public.send_friend_message(uuid, text, text, integer, date, jsonb) from public, anon;
grant execute on function public.send_friend_message(uuid, text, text, integer, date, jsonb) to authenticated;

notify pgrst, 'reload schema';
