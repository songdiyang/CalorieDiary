-- 用户 UUID 用于加好友；独立邀请码用于注册与传火计划。

alter table public.profiles
    add column if not exists invited_by uuid references public.profiles(id) on delete set null;
create index if not exists profiles_invited_by_idx on public.profiles(invited_by);

alter table public.profiles rename column friend_code to invitation_code;
alter index if exists profiles_friend_code_unique_idx
    rename to profiles_invitation_code_unique_idx;
alter table public.profiles
    rename constraint profiles_friend_code_format to profiles_invitation_code_format;

create or replace function public.generate_invitation_code()
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
            select 1 from public.profiles where invitation_code = candidate
        );
    end loop;
    return candidate;
end;
$$;

alter table public.profiles
    alter column invitation_code set default public.generate_invitation_code();

drop function if exists public.generate_friend_code();

-- 邀请码和邀请关系只能由服务端注册流程写入，用户资料可修改字段保持最小化。
revoke update on table public.profiles from authenticated;
grant update (nickname, avatar_emoji, calorie_goal, water_goal)
    on public.profiles to authenticated;

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
        coalesce(nullif(new.raw_user_meta_data ->> 'nickname', ''), '我'),
        referrer_id
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

revoke all on function public.handle_new_user() from public, anon, authenticated;

-- Auth 写入新用户前强制验证邀请码；仅在 App 隐藏输入框不能绕过此检查。
create or replace function public.enforce_registration_invite()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    submitted_code text := nullif(new.raw_user_meta_data ->> 'invite_code', '');
begin
    if submitted_code is null then
        raise exception 'invitation_code_required';
    end if;
    if not exists (
        select 1 from public.profiles p where p.invitation_code = submitted_code
    ) then
        raise exception 'invalid_invitation_code';
    end if;
    return new;
end;
$$;

drop trigger if exists enforce_registration_invite on auth.users;
create trigger enforce_registration_invite
    before insert on auth.users
    for each row execute function public.enforce_registration_invite();

revoke all on function public.enforce_registration_invite() from public, anon, authenticated;

-- 好友列表只返回好友 UUID、昵称、头像和建立时间，不再返回注册邀请码。
drop function if exists public.list_friends();
create function public.list_friends()
returns table (
    friend_id uuid,
    nickname text,
    avatar_emoji text,
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
    select p.id, p.nickname, p.avatar_emoji, f.created_at
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

revoke all on function public.list_friends() from public, anon;
grant execute on function public.list_friends() to authenticated;

drop function if exists public.add_friend(text);
create function public.add_friend(p_user_id uuid)
returns table (
    friend_id uuid,
    nickname text,
    avatar_emoji text,
    friends_since timestamptz
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target_nickname text;
    target_avatar text;
    low_id uuid;
    high_id uuid;
    since timestamptz;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select p.nickname, p.avatar_emoji
    into target_nickname, target_avatar
    from public.profiles p
    where p.id = p_user_id;

    if not found then
        return;
    end if;
    if p_user_id = current_uid then
        raise exception 'cannot_add_self';
    end if;

    low_id := least(current_uid, p_user_id);
    high_id := greatest(current_uid, p_user_id);

    insert into public.friendships (user_low, user_high)
    values (low_id, high_id)
    on conflict (user_low, user_high) do nothing;

    select f.created_at into since
    from public.friendships f
    where f.user_low = low_id and f.user_high = high_id;

    return query
    select p_user_id, target_nickname, target_avatar, since;
end;
$$;

revoke all on function public.add_friend(uuid) from public, anon;
grant execute on function public.add_friend(uuid) to authenticated;

revoke all on function public.generate_invitation_code() from public, anon;
grant execute on function public.generate_invitation_code() to authenticated;
