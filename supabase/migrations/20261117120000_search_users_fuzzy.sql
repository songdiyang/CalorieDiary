-- 搜索好友页：昵称支持模糊匹配，公开 UID 支持前缀匹配（原来只支持精确匹配）。

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
          lower(p.nickname) like '%' || lower(cleaned) || '%'
          or (cleaned ~ '^[0-9]{1,8}$' and p.public_id like cleaned || '%')
      )
    order by (p.public_id = cleaned) desc, p.nickname
    limit 20;
end;
$$;

revoke all on function public.search_users(text) from public, anon;
grant execute on function public.search_users(text) to authenticated;
