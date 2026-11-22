-- 他人主页公开资料增加等级：由 game_wallets.exp 经 game_level() 计算，无钱包记录按 1 级兜底。
-- 返回列变化无法 create or replace，需先删除再重建并恢复授权。

drop function if exists public.get_public_profile(uuid);

create function public.get_public_profile(p_user_id uuid)
returns table (
    user_id uuid,
    nickname text,
    avatar_emoji text,
    public_id text,
    is_self boolean,
    is_friend boolean,
    level integer
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
        p.public_id,
        p.id = current_uid,
        exists (
            select 1 from public.friendships f
            where f.user_low = least(current_uid, p.id)
              and f.user_high = greatest(current_uid, p.id)
        ),
        public.game_level(coalesce(w.exp, 0))
    from public.profiles p
    left join public.game_wallets w on w.user_id = p.id
    where p.id = p_user_id;
end;
$$;

revoke all on function public.get_public_profile(uuid) from public, anon, authenticated;
grant execute on function public.get_public_profile(uuid) to authenticated;