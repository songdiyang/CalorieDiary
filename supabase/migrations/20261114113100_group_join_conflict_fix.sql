-- 使用主键约束名处理重复成员，避免输出字段与表字段歧义。
drop function if exists public.join_group(text);
create function public.join_group(p_group_code text)
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

revoke all on function public.join_group(text) from public, anon;
grant execute on function public.join_group(text) to authenticated;
