-- 好友码与好友关系。好友关系不包含饮食记录访问权限。

alter table public.profiles
    add column if not exists friend_code text;

create or replace function public.generate_friend_code()
returns text
language plpgsql
volatile
security definer
set search_path = public, pg_temp
as $$
declare
    candidate text;
begin
    loop
        candidate := lpad(floor(random() * 100000000)::bigint::text, 8, '0');
        exit when not exists (
            select 1 from public.profiles where friend_code = candidate
        );
    end loop;
    return candidate;
end;
$$;

-- 为迁移前已经注册的账户补齐好友码。
do $$
declare
    row_record record;
begin
    for row_record in
        select id from public.profiles where friend_code is null
    loop
        update public.profiles
        set friend_code = public.generate_friend_code()
        where id = row_record.id;
    end loop;
end;
$$;

alter table public.profiles
    alter column friend_code set default public.generate_friend_code();
alter table public.profiles
    alter column friend_code set not null;

create unique index if not exists profiles_friend_code_unique_idx
    on public.profiles(friend_code);

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'profiles_friend_code_format'
          and conrelid = 'public.profiles'::regclass
    ) then
        alter table public.profiles
        add constraint profiles_friend_code_format
        check (friend_code ~ '^[0-9]{8}$');
    end if;
end;
$$;

create table if not exists public.friendships (
    user_low uuid not null references public.profiles(id) on delete cascade,
    user_high uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_low, user_high),
    check (user_low < user_high)
);

-- 主键覆盖 user_low 查询；额外索引支持 user_high 查询及外键级联。
create index if not exists friendships_user_high_idx
    on public.friendships(user_high);

alter table public.friendships enable row level security;

drop policy if exists friendships_select_participant on public.friendships;
create policy friendships_select_participant on public.friendships
    for select to authenticated
    using (
        user_low = (select auth.uid())
        or user_high = (select auth.uid())
    );

revoke all on table public.friendships from anon;
grant select on table public.friendships to authenticated;

create or replace function public.list_friends()
returns table (
    friend_id uuid,
    nickname text,
    avatar_emoji text,
    friend_code text,
    friends_since timestamptz
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
        p.friend_code,
        f.created_at
    from public.friendships f
    join public.profiles p
      on p.id = case
          when f.user_low = current_uid then f.user_high
          else f.user_low
      end
    where f.user_low = current_uid or f.user_high = current_uid
    order by p.nickname;
end;
$$;

create or replace function public.add_friend(p_friend_code text)
returns table (
    friend_id uuid,
    nickname text,
    avatar_emoji text,
    friend_code text,
    friends_since timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target_id uuid;
    target_nickname text;
    target_avatar text;
    target_code text;
    low_id uuid;
    high_id uuid;
    since timestamptz;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select p.id, p.nickname, p.avatar_emoji, p.friend_code
    into target_id, target_nickname, target_avatar, target_code
    from public.profiles p
    where p.friend_code = p_friend_code;

    -- 不透露“未找到”和“无权访问”之外的任何账户资料。
    if not found then
        return;
    end if;
    if target_id = current_uid then
        raise exception 'cannot_add_self';
    end if;

    low_id := least(current_uid, target_id);
    high_id := greatest(current_uid, target_id);

    insert into public.friendships (user_low, user_high)
    values (low_id, high_id)
    on conflict (user_low, user_high) do nothing;

    select f.created_at into since
    from public.friendships f
    where f.user_low = low_id and f.user_high = high_id;

    return query
    select target_id, target_nickname, target_avatar, target_code, since;
end;
$$;

create or replace function public.remove_friend(p_friend_id uuid)
returns void
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

    delete from public.friendships f
    where (f.user_low = current_uid and f.user_high = p_friend_id)
       or (f.user_high = current_uid and f.user_low = p_friend_id);
end;
$$;

revoke all on function public.generate_friend_code() from public;
revoke all on function public.list_friends() from public;
revoke all on function public.add_friend(text) from public;
revoke all on function public.remove_friend(uuid) from public;

grant execute on function public.generate_friend_code() to authenticated;
grant execute on function public.list_friends() to authenticated;
grant execute on function public.add_friend(text) to authenticated;
grant execute on function public.remove_friend(uuid) to authenticated;
