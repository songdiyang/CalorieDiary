-- 私聊支持热量分享卡片；新增一对一亲密关系（挚友/情侣/兄弟/闺蜜）。
-- 关系为双方同时生效：写入当前用户与对方两条镜像记录。

alter table public.friend_messages
    add column if not exists message_type text not null default 'text'
        check (message_type in ('text', 'calorie_share')),
    add column if not exists calories integer
        check (calories is null or (calories >= 0 and calories <= 100000)),
    add column if not exists entry_date date;

drop function if exists public.list_friend_messages(uuid);
create function public.list_friend_messages(p_friend_id uuid)
returns table (
    message_id bigint,
    sender_id uuid,
    content text,
    message_type text,
    calories integer,
    entry_date date,
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
           recent.message_type, recent.calories, recent.entry_date, recent.created_at
    from (
        select m.id as message_id, m.sender_id, m.content,
               m.message_type, m.calories, m.entry_date, m.created_at
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

drop function if exists public.send_friend_message(uuid, text);
create function public.send_friend_message(
    p_friend_id uuid,
    p_content text,
    p_message_type text default 'text',
    p_calories integer default null,
    p_entry_date date default null
)
returns table (
    message_id bigint,
    sender_id uuid,
    content text,
    message_type text,
    calories integer,
    entry_date date,
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
        cleaned := coalesce(nullif(cleaned, ''), '今日热量分享');
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
        (sender_id, receiver_id, content, message_type, calories, entry_date)
    values (current_uid, p_friend_id, cleaned, kind, share_calories, p_entry_date)
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

    return query select new_id, current_uid, cleaned, kind, share_calories, p_entry_date, new_time;
end;
$$;

revoke all on function public.send_friend_message(uuid, text, text, integer, date) from public, anon;
grant execute on function public.send_friend_message(uuid, text, text, integer, date) to authenticated;

-- 亲密关系：每种类型每个用户只能绑定一位好友，双方各存一条镜像记录。
create table if not exists public.friend_relations (
    user_id uuid not null references public.profiles(id) on delete cascade,
    partner_id uuid not null references public.profiles(id) on delete cascade,
    relation_type text not null
        check (relation_type in ('best_friend', 'lover', 'brother', 'bestie')),
    created_at timestamptz not null default now(),
    primary key (user_id, relation_type),
    check (user_id <> partner_id)
);

create index if not exists friend_relations_partner_idx
    on public.friend_relations(partner_id);

alter table public.friend_relations enable row level security;
revoke all on table public.friend_relations from public, anon, authenticated;

create or replace function public.get_friend_relation(p_friend_id uuid)
returns table (relation_type text)
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
    select r.relation_type
    from public.friend_relations r
    where r.user_id = current_uid
      and r.partner_id = p_friend_id
    limit 1;
end;
$$;

revoke all on function public.get_friend_relation(uuid) from public, anon;
grant execute on function public.get_friend_relation(uuid) to authenticated;

create or replace function public.set_friend_relation(p_friend_id uuid, p_relation text)
returns table (relation_type text)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    kind text := lower(trim(coalesce(p_relation, '')));
    old_partner uuid;
    partner_partner uuid;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if kind not in ('best_friend', 'lover', 'brother', 'bestie') then
        raise exception 'invalid_relation';
    end if;
    if p_friend_id = current_uid then
        raise exception 'invalid_relation';
    end if;

    -- 锁定好友关系行，串行化双方同时绑定的场景。
    perform 1
    from public.friendships f
    where f.user_low = least(current_uid, p_friend_id)
      and f.user_high = greatest(current_uid, p_friend_id)
    for update;
    if not found then
        raise exception 'not_friends' using errcode = '42501';
    end if;

    -- 当前用户此前绑定了别人：连同对方的镜像记录一起解除。
    select r.partner_id into old_partner
    from public.friend_relations r
    where r.user_id = current_uid
      and r.relation_type = kind;
    if old_partner is not null and old_partner <> p_friend_id then
        delete from public.friend_relations r
        where r.relation_type = kind
          and ((r.user_id = current_uid and r.partner_id = old_partner)
            or (r.user_id = old_partner and r.partner_id = current_uid));
    end if;

    -- 对方已把同类型关系绑给别人则拒绝，保持一对一。
    select r.partner_id into partner_partner
    from public.friend_relations r
    where r.user_id = p_friend_id
      and r.relation_type = kind;
    if partner_partner is not null and partner_partner <> current_uid then
        raise exception 'relation_partner_busy';
    end if;

    -- 用约束名避免与返回列 relation_type 产生歧义。
    insert into public.friend_relations as target (user_id, partner_id, relation_type)
    values (current_uid, p_friend_id, kind)
    on conflict on constraint friend_relations_pkey do update
    set partner_id = excluded.partner_id,
        created_at = now();

    insert into public.friend_relations as target (user_id, partner_id, relation_type)
    values (p_friend_id, current_uid, kind)
    on conflict on constraint friend_relations_pkey do update
    set partner_id = excluded.partner_id,
        created_at = now();

    return query select kind;
end;
$$;

revoke all on function public.set_friend_relation(uuid, text) from public, anon;
grant execute on function public.set_friend_relation(uuid, text) to authenticated;

notify pgrst, 'reload schema';
