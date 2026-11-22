-- 个人动态：每日热量合计与可见范围，不公开具体食物明细。
alter table public.profiles
    add column if not exists activity_visibility text not null default 'friends';

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'profiles_activity_visibility_check'
    ) then
        alter table public.profiles
            add constraint profiles_activity_visibility_check
            check (activity_visibility in ('public', 'friends', 'private'));
    end if;
end;
$$;

create table public.activity_daily_totals (
    user_id uuid not null references public.profiles(id) on delete cascade,
    entry_date date not null,
    total_calories integer not null check (total_calories >= 0),
    entry_count integer not null check (entry_count >= 0),
    updated_at timestamptz not null default now(),
    primary key (user_id, entry_date)
);

create index activity_daily_totals_user_date_idx
    on public.activity_daily_totals(user_id, entry_date desc);

alter table public.activity_daily_totals enable row level security;

create policy activity_daily_totals_select_own on public.activity_daily_totals
    for select to authenticated
    using ((select auth.uid()) = user_id);

revoke all on table public.activity_daily_totals from anon;
revoke all on table public.activity_daily_totals from authenticated;
grant select on table public.activity_daily_totals to authenticated;

create or replace function public.set_activity_visibility(p_visibility text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_visibility not in ('public', 'friends', 'private') then
        raise exception 'invalid_activity_visibility' using errcode = '22023';
    end if;

    update public.profiles
    set activity_visibility = p_visibility,
        updated_at = now()
    where id = current_uid;

    return jsonb_build_object('status', 'success', 'visibility', p_visibility);
end;
$$;

create or replace function public.sync_activity_daily_totals(p_days jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    day jsonb;
    day_date date;
    day_calories integer;
    day_count integer;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if jsonb_typeof(p_days) <> 'array' or jsonb_array_length(p_days) > 31 then
        raise exception 'invalid_activity_days' using errcode = '22023';
    end if;

    for day in select value from jsonb_array_elements(p_days)
    loop
        day_date := (day ->> 'date')::date;
        day_calories := (day ->> 'total_calories')::integer;
        day_count := (day ->> 'entry_count')::integer;
        if day_date is null or day_date > current_date
            or day_date < current_date - 365
            or day_calories is null or day_calories not between 0 and 100000
            or day_count is null or day_count not between 0 and 1000 then
            raise exception 'invalid_activity_day' using errcode = '22023';
        end if;

        insert into public.activity_daily_totals (
            user_id, entry_date, total_calories, entry_count, updated_at
        ) values (
            current_uid, day_date, day_calories, day_count, now()
        )
        on conflict (user_id, entry_date) do update set
            total_calories = excluded.total_calories,
            entry_count = excluded.entry_count,
            updated_at = now();
    end loop;

    return jsonb_build_object('status', 'success');
end;
$$;

create or replace function public.get_activity_profile(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    target_profile record;
    allowed boolean := false;
    days jsonb := '[]'::jsonb;
    active_days integer := 0;
    total_entries integer := 0;
    total_calories bigint := 0;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select p.id, p.nickname, p.avatar_emoji, p.public_id, p.activity_visibility, p.calorie_goal
    into target_profile
    from public.profiles p
    where p.id = p_user_id;
    if not found then
        return jsonb_build_object('status', 'not_found');
    end if;

    allowed := current_uid = p_user_id
        or target_profile.activity_visibility = 'public'
        or (
            target_profile.activity_visibility = 'friends'
            and exists (
                select 1 from public.friendships f
                where f.user_low = least(current_uid, p_user_id)
                  and f.user_high = greatest(current_uid, p_user_id)
            )
        );
    if not allowed then
        return jsonb_build_object('status', 'private');
    end if;

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'date', d.entry_date,
                'total_calories', d.total_calories,
                'entry_count', d.entry_count
            ) order by d.entry_date desc
        ),
        '[]'::jsonb
    ) into days
    from (
        select a.entry_date, a.total_calories, a.entry_count
        from public.activity_daily_totals a
        where a.user_id = p_user_id
        order by a.entry_date desc
        limit 30
    ) d;

    select
        count(*)::integer,
        coalesce(sum(a.entry_count), 0)::integer,
        coalesce(sum(a.total_calories), 0)::bigint
    into active_days, total_entries, total_calories
    from public.activity_daily_totals a
    where a.user_id = p_user_id;

    return jsonb_build_object(
        'status', 'ok',
        'user_id', target_profile.id,
        'nickname', target_profile.nickname,
        'avatar_emoji', target_profile.avatar_emoji,
        'public_id', target_profile.public_id,
        'visibility', target_profile.activity_visibility,
        'calorie_goal', target_profile.calorie_goal,
        'is_self', current_uid = p_user_id,
        'active_days', active_days,
        'total_entries', total_entries,
        'total_calories', total_calories,
        'days', days
    );
end;
$$;

revoke all on function public.set_activity_visibility(text) from public;
revoke all on function public.sync_activity_daily_totals(jsonb) from public;
revoke all on function public.get_activity_profile(uuid) from public;
revoke all on function public.set_activity_visibility(text) from anon;
revoke all on function public.sync_activity_daily_totals(jsonb) from anon;
revoke all on function public.get_activity_profile(uuid) from anon;

grant execute on function public.set_activity_visibility(text) to authenticated;
grant execute on function public.sync_activity_daily_totals(jsonb) to authenticated;
grant execute on function public.get_activity_profile(uuid) to authenticated;
