-- 安全加固：
-- 1. 匿名 RPC 按 IP 限流（邮箱/邀请码校验），降低枚举风险；
-- 2. 邀请码只让新用户关注邀请人，不再直接建立好友，保持“互关后才能申请好友”；
-- 3. 6 位群号加入按用户限流，降低穷举加群风险；
-- 4. 私聊/群聊发送频率限制，降低刷屏滥用；
-- 5. 用户搜索转义 LIKE 通配符并限制最短查询长度，降低批量抓取。

create table if not exists public.rpc_throttle (
    bucket text primary key,
    window_start timestamptz not null default now(),
    hits integer not null default 0
);

alter table public.rpc_throttle enable row level security;
revoke all on table public.rpc_throttle from public, anon, authenticated;

create or replace function public.consume_rpc_quota(
    p_bucket text,
    p_limit integer,
    p_window_seconds integer
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_start timestamptz;
    current_hits integer;
begin
    if p_bucket is null or p_bucket = '' or p_limit <= 0 or p_window_seconds <= 0 then
        return true;
    end if;

    insert into public.rpc_throttle (bucket, window_start, hits)
    values (p_bucket, now(), 0)
    on conflict (bucket) do nothing;

    select t.window_start, t.hits
    into current_start, current_hits
    from public.rpc_throttle t
    where t.bucket = p_bucket
    for update;

    if now() - current_start > (p_window_seconds * interval '1 second') then
        update public.rpc_throttle
        set window_start = now(), hits = 1
        where bucket = p_bucket;
        return true;
    end if;

    if current_hits >= p_limit then
        return false;
    end if;

    update public.rpc_throttle
    set hits = hits + 1
    where bucket = p_bucket;
    return true;
end;
$$;

revoke all on function public.consume_rpc_quota(text, integer, integer)
    from public, anon, authenticated;

create or replace function public.request_client_ip()
returns text
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    headers json;
begin
    begin
        headers := current_setting('request.headers', true)::json;
    exception when others then
        return '';
    end;
    if headers is null then
        return '';
    end if;
    return btrim(split_part(coalesce(headers ->> 'x-forwarded-for', ''), ',', 1));
end;
$$;

revoke all on function public.request_client_ip() from public, anon, authenticated;

-- 邮箱是否注册：匿名可调用，按请求 IP 限流。
create or replace function public.is_email_registered(p_email text)
returns boolean
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
    ip text := public.request_client_ip();
begin
    if ip <> '' and not public.consume_rpc_quota('email_check:' || ip, 30, 60) then
        raise exception 'rate_limited' using errcode = '42901';
    end if;
    return exists (
        select 1 from auth.users u
        where lower(u.email) = lower(trim(coalesce(p_email, '')))
    );
end;
$$;

-- 邀请码校验：匿名可调用，按请求 IP 限流。
create or replace function public.is_invitation_code_valid(p_code text)
returns boolean
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
    ip text := public.request_client_ip();
begin
    if ip <> '' and not public.consume_rpc_quota('invite_check:' || ip, 30, 60) then
        raise exception 'rate_limited' using errcode = '42901';
    end if;
    return case
        when p_code is null or p_code !~ '^[0-9]{8}$' then false
        else exists (select 1 from public.profiles p where p.invitation_code = p_code)
    end;
end;
$$;

create or replace function public.is_invitation_code_deleted(p_code text)
returns boolean
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
    ip text := public.request_client_ip();
begin
    if ip <> '' and not public.consume_rpc_quota('invite_check:' || ip, 30, 60) then
        raise exception 'rate_limited' using errcode = '42901';
    end if;
    return case
        when p_code is null or p_code !~ '^[0-9]{8}$' then false
        else exists (select 1 from public.deleted_profiles d where d.invitation_code = p_code)
    end;
end;
$$;

-- 邀请码仅自动关注邀请人；成为好友仍需互相关注 + 好友申请。
create or replace function public.apply_invitation_code(p_code text)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    referrer_id uuid;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_code is null or p_code !~ '^[0-9]{8}$' then
        return false;
    end if;
    select p.id into referrer_id
    from public.profiles p
    where p.invitation_code = p_code;
    if referrer_id is null or referrer_id = current_uid then
        return false;
    end if;
    insert into public.follows (follower_id, followee_id)
    values (current_uid, referrer_id)
    on conflict (follower_id, followee_id) do nothing;
    return true;
end;
$$;

-- 群号加入：每用户 10 分钟最多 20 次尝试，降低穷举。
create or replace function public.join_group(p_group_code text)
returns table (
    group_id text,
    name text,
    owner_id uuid,
    calorie_goal integer,
    created_at timestamptz
)
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
    if not public.consume_rpc_quota('group_join:' || current_uid::text, 20, 600) then
        raise exception 'join_rate_limited' using errcode = '42901';
    end if;
    if not exists (select 1 from public.groups g where g.id = p_group_code) then
        raise exception 'group_not_found';
    end if;

    insert into public.group_members (group_id, user_id, nickname, avatar_emoji)
    select p_group_code, p.id, p.nickname, p.avatar_emoji
    from public.profiles p where p.id = current_uid
    on conflict on constraint group_members_pkey do nothing;

    return query
    select g.id, g.name, g.owner_id, g.calorie_goal, g.created_at
    from public.groups g where g.id = p_group_code;
end;
$$;

-- 搜索：转义 LIKE 通配符，昵称搜索至少 2 个字符；纯数字 UID 仍支持前缀匹配。
create or replace function public.search_users(p_query text)
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
    escaped text;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if cleaned = '' then
        return;
    end if;
    if char_length(cleaned) < 2 and cleaned !~ '^[0-9]{1,8}$' then
        return;
    end if;

    escaped := replace(replace(replace(cleaned, '\', '\\'), '%', '\%'), '_', '\_');

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
          lower(p.nickname) like '%' || lower(escaped) || '%' escape '\'
          or (cleaned ~ '^[0-9]{1,8}$' and p.public_id like cleaned || '%')
      )
    order by (p.public_id = cleaned) desc, p.nickname
    limit 20;
end;
$$;

-- 私聊发送限流：每分钟 30 条、每天 500 条。
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
    if not public.consume_rpc_quota('dm_min:' || current_uid::text, 30, 60)
       or not public.consume_rpc_quota('dm_day:' || current_uid::text, 500, 86400) then
        raise exception 'rate_limited' using errcode = '42901';
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

-- 群聊发送限流：每分钟 30 条、每天 500 条。
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
    if not public.consume_rpc_quota('group_msg_min:' || current_uid::text, 30, 60)
       or not public.consume_rpc_quota('group_msg_day:' || current_uid::text, 500, 86400) then
        raise exception 'rate_limited' using errcode = '42901';
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

notify pgrst, 'reload schema';