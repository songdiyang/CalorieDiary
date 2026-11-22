-- 对外短数字 ID、邀请自动成为好友，以及好友续火花。

alter table public.profiles add column if not exists public_id text;

create or replace function public.generate_public_id()
returns text
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
    digits integer;
    candidate text;
begin
    loop
        digits := 6 + floor(random() * 3)::integer;
        candidate := lpad(
            floor(random() * power(10::numeric, digits))::bigint::text,
            digits,
            '0'
        );
        exit when not exists (
            select 1 from public.profiles where public_id = candidate
        );
    end loop;
    return candidate;
end;
$$;

do $$
declare
    row_record record;
begin
    for row_record in select id from public.profiles where public_id is null loop
        update public.profiles
        set public_id = public.generate_public_id()
        where id = row_record.id;
    end loop;
end;
$$;

alter table public.profiles
    alter column public_id set default public.generate_public_id();
alter table public.profiles alter column public_id set not null;
create unique index if not exists profiles_public_id_unique_idx
    on public.profiles(public_id);

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'profiles_public_id_format'
          and conrelid = 'public.profiles'::regclass
    ) then
        alter table public.profiles
        add constraint profiles_public_id_format
        check (public_id ~ '^[0-9]{6,8}$');
    end if;
end;
$$;

revoke all on function public.generate_public_id() from public, anon;
grant execute on function public.generate_public_id() to authenticated;

-- 注册成功后自动与邀请人建立好友关系。
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

    if referrer_id is not null then
        insert into public.friendships (user_low, user_high)
        values (least(new.id, referrer_id), greatest(new.id, referrer_id))
        on conflict (user_low, user_high) do nothing;
    end if;
    return new;
end;
$$;

revoke all on function public.handle_new_user() from public, anon, authenticated;

alter table public.friendships add column if not exists spark_count integer not null default 0;
alter table public.friendships add column if not exists spark_last_date date;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'friendships_spark_count_nonnegative'
          and conrelid = 'public.friendships'::regclass
    ) then
        alter table public.friendships
        add constraint friendships_spark_count_nonnegative check (spark_count >= 0);
    end if;
end;
$$;

-- 会话列表同时返回短数字 ID 和当前有效火花。
drop function if exists public.list_friends();
create function public.list_friends()
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
    order by coalesce(latest.created_at, f.created_at) desc;
end;
$$;

revoke all on function public.list_friends() from public, anon;
grant execute on function public.list_friends() to authenticated;

-- 搜索精确匹配唯一用户名或短数字 ID。
drop function if exists public.search_users(text);
create function public.search_users(p_query text)
returns table (
    user_id uuid,
    public_id text,
    nickname text,
    avatar_emoji text
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
    select p.id, p.public_id, p.nickname, p.avatar_emoji
    from public.profiles p
    where p.id <> current_uid
      and (
          lower(p.nickname) = lower(cleaned)
          or p.public_id = cleaned
      )
    limit 10;
end;
$$;

revoke all on function public.search_users(text) from public, anon;
grant execute on function public.search_users(text) to authenticated;

-- 双方同一天互发消息才点亮或延续火花；好友行锁防止并发重复累计。
create or replace function public.send_friend_message(p_friend_id uuid, p_content text)
returns table (
    message_id bigint,
    sender_id uuid,
    content text,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    cleaned text := trim(coalesce(p_content, ''));
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
    if cleaned = '' or char_length(cleaned) > 1000 then
        raise exception 'invalid_message';
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

    insert into public.friend_messages as target (sender_id, receiver_id, content)
    values (current_uid, p_friend_id, cleaned)
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

    return query select new_id, current_uid, cleaned, new_time;
end;
$$;

revoke all on function public.send_friend_message(uuid, text) from public, anon;
grant execute on function public.send_friend_message(uuid, text) to authenticated;
