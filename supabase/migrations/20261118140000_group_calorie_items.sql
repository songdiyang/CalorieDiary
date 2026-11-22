-- 群聊热量分享卡片增加食物明细快照（items jsonb），与好友对话卡片保持一致。
-- items 结构：[{"name":"米饭","emoji":"🍚","grams":200,"calories":232,"meal":1}]

alter table public.group_messages
    add column if not exists items jsonb
        check (items is null or jsonb_typeof(items) = 'array');

-- 返回类型新增 items，必须先删除旧函数。
drop function if exists public.list_group_messages(text);

create function public.list_group_messages(p_group_id text)
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
        select 1 from public.group_members gm
        where gm.group_id = p_group_id and gm.user_id = current_uid
    ) then
        raise exception 'not_group_member' using errcode = '42501';
    end if;

    return query
    select recent.message_id, recent.sender_id, recent.content,
           recent.message_type, recent.calories, recent.entry_date,
           recent.items, recent.created_at
    from (
        select m.id as message_id, m.sender_id, m.content, m.message_type,
               m.calories, m.entry_date, m.items, m.created_at
        from public.group_messages m
        where m.group_id = p_group_id
        order by m.created_at desc
        limit 200
    ) recent
    order by recent.created_at asc;
end;
$$;

revoke all on function public.list_group_messages(text) from public, anon;
grant execute on function public.list_group_messages(text) to authenticated;

-- 发送函数新增 p_items，返回列也多了 items。
drop function if exists public.send_group_message(text, text, text, integer, date);

create function public.send_group_message(
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
    if kind not in ('text', 'calorie_share') then
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

revoke all on function public.send_group_message(text, text, text, integer, date, jsonb) from public, anon;
grant execute on function public.send_group_message(text, text, text, integer, date, jsonb) to authenticated;

notify pgrst, 'reload schema';
