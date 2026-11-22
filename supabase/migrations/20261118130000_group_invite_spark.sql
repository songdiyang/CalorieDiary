-- 群聊邀请好友与群火花：成员可把好友拉进群，list_groups 返回连续聊天天数。

-- 邀请好友进群：只允许群成员邀请自己的好友，重复邀请自动忽略。
create or replace function public.invite_group_members(
    p_group_id text,
    p_user_ids uuid[]
)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    target uuid;
    added integer := 0;
    inserted integer;
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
    if p_user_ids is null then
        return 0;
    end if;

    foreach target in array p_user_ids loop
        -- 仅允许拉自己的好友，避免被任意拉群
        if not exists (
            select 1 from public.friendships f
            where f.user_low = least(current_uid, target)
              and f.user_high = greatest(current_uid, target)
        ) then
            continue;
        end if;
        insert into public.group_members (group_id, user_id, nickname, avatar_emoji)
        select p_group_id, p.id, p.nickname, p.avatar_emoji
        from public.profiles p
        where p.id = target
        on conflict (group_id, user_id) do nothing;
        get diagnostics inserted = row_count;
        added := added + inserted;
    end loop;
    return added;
end;
$$;

revoke all on function public.invite_group_members(text, uuid[]) from public, anon;
grant execute on function public.invite_group_members(text, uuid[]) to authenticated;

-- 返回类型新增 spark_days，必须先删除旧函数。
drop function if exists public.list_groups();

-- 群火花：今天（或昨天）往前连续有群消息的天数；今天、昨天都没有消息则为 0。
create function public.list_groups()
returns table (
    group_id text,
    name text,
    owner_id uuid,
    calorie_goal integer,
    created_at timestamptz,
    last_message text,
    last_message_at timestamptz,
    spark_days integer
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
    select g.id, g.name, g.owner_id, g.calorie_goal, g.created_at,
           latest.content, latest.created_at, spark.spark_days
    from public.groups g
    left join lateral (
        select m.content, m.created_at
        from public.group_messages m
        where m.group_id = g.id
        order by m.created_at desc
        limit 1
    ) latest on true
    left join lateral (
        with day_set as (
            select distinct (m.created_at at time zone 'Asia/Shanghai')::date as d
            from public.group_messages m
            where m.group_id = g.id
              and m.created_at >= now() - interval '366 days'
        ), ordered as (
            select d, row_number() over (order by d desc) as rn
            from day_set
            where d <= today
        ), latest_day as (
            select max(d) as d from ordered
        )
        select case
            when (select d from latest_day) is null
                 or (select d from latest_day) < today - 1 then 0
            else (
                select count(*)::integer
                from ordered o
                where (select d from latest_day) - o.d = o.rn - 1
            )
        end as spark_days
    ) spark on true
    where exists (
        select 1 from public.group_members gm
        where gm.group_id = g.id and gm.user_id = current_uid
    )
    order by coalesce(latest.created_at, g.created_at) desc;
end;
$$;

revoke all on function public.list_groups() from public, anon;
grant execute on function public.list_groups() to authenticated;
