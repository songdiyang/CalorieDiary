-- 米饭山分享此前被消息类型白名单拒绝（invalid_message），导致分享失败。
-- 私聊与群聊均允许 rice_mountain，校验规则与 calorie_share 一致。

alter table public.friend_messages
    drop constraint if exists friend_messages_message_type_check;
alter table public.friend_messages
    add constraint friend_messages_message_type_check
    check (message_type in ('text', 'calorie_share', 'rice_mountain'));

alter table public.group_messages
    drop constraint if exists group_messages_message_type_check;
alter table public.group_messages
    add constraint group_messages_message_type_check
    check (message_type in ('text', 'calorie_share', 'rice_mountain'));

alter table public.group_messages
    drop constraint if exists group_messages_check;
alter table public.group_messages
    add constraint group_messages_check
    check (
        (message_type = 'text' and calories is null)
        or (message_type in ('calorie_share', 'rice_mountain')
            and calories is not null and entry_date is not null)
    );

create or replace function public.send_friend_message(
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
    if kind not in ('text', 'calorie_share', 'rice_mountain') then
        raise exception 'invalid_message';
    end if;
    if kind = 'text' and (cleaned = '' or char_length(cleaned) > 1000) then
        raise exception 'invalid_message';
    end if;
    if kind in ('calorie_share', 'rice_mountain') then
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

create or replace function public.send_group_message(
    p_group_id text,
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
    share_date date := p_entry_date;
    share_items jsonb := p_items;
    new_id bigint;
    new_time timestamptz;
    today date := (now() at time zone 'Asia/Shanghai')::date;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if kind not in ('text', 'calorie_share', 'rice_mountain') then
        raise exception 'invalid_message_type';
    end if;
    if not exists (
        select 1 from public.group_members gm
        where gm.group_id = p_group_id and gm.user_id = current_uid
    ) then
        raise exception 'not_group_member' using errcode = '42501';
    end if;

    if kind = 'text' then
        if cleaned = '' or char_length(cleaned) > 1000 then
            raise exception 'invalid_message';
        end if;
        -- 纯文本消息不接受热量与明细，避免卡片样式被误用。
        share_calories := null;
        share_date := null;
        share_items := null;
    else
        if share_calories is null or share_calories < 0 or share_calories > 100000 then
            raise exception 'invalid_calories';
        end if;
        if share_date is null then
            share_date := today;
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
        if char_length(cleaned) > 1000 then
            raise exception 'invalid_message';
        end if;
    end if;

    insert into public.group_messages as target (
        group_id, sender_id, content, message_type, calories, entry_date, items
    ) values (
        p_group_id, current_uid, cleaned, kind, share_calories, share_date, share_items
    )
    returning target.id, target.created_at into new_id, new_time;

    return query
    select new_id, current_uid, cleaned, kind, share_calories, share_date, share_items, new_time;
end;
$$;