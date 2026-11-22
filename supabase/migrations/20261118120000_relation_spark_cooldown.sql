-- 亲密关系：入口改为点击火花，绑定后 30 天内只能更换一次。
-- get_friend_relation / set_friend_relation 额外返回绑定时间（bound_at）供客户端计算冷却。

drop function if exists public.get_friend_relation(uuid);
create function public.get_friend_relation(p_friend_id uuid)
returns table (relation_type text, bound_at timestamptz)
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
    select r.relation_type, r.created_at
    from public.friend_relations r
    where r.user_id = current_uid
      and r.partner_id = p_friend_id
    limit 1;
end;
$$;

revoke all on function public.get_friend_relation(uuid) from public, anon;
grant execute on function public.get_friend_relation(uuid) to authenticated;

drop function if exists public.set_friend_relation(uuid, text);
create function public.set_friend_relation(p_friend_id uuid, p_relation text)
returns table (relation_type text, bound_at timestamptz)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    kind text := lower(trim(coalesce(p_relation, '')));
    v_type text;
    v_created timestamptz;
    v_lover_partner uuid;
    v_lover_created timestamptz;
    v_now timestamptz := now();
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

    -- 当前与该好友的绑定。
    select r.relation_type, r.created_at into v_type, v_created
    from public.friend_relations r
    where r.user_id = current_uid
      and r.partner_id = p_friend_id;

    -- 重复绑定同一种关系：不变更、不重置计时。
    if v_type = kind then
        return query select kind, v_created;
        return;
    end if;

    -- 30 天冷却：更换该好友的亲密关系需距上次绑定满 30 天。
    if v_type is not null and v_created > v_now - interval '30 days' then
        raise exception 'relation_cooldown';
    end if;

    -- 情侣一对一：对方已与他人绑定情侣则拒绝。
    if kind = 'lover' and exists (
        select 1
        from public.friend_relations r
        where r.user_id = p_friend_id
          and r.relation_type = 'lover'
          and r.partner_id <> current_uid
    ) then
        raise exception 'relation_partner_busy';
    end if;

    -- 情侣换对象同样受 30 天冷却约束。
    if kind = 'lover' then
        select r.partner_id, r.created_at into v_lover_partner, v_lover_created
        from public.friend_relations r
        where r.user_id = current_uid
          and r.relation_type = 'lover'
          and r.partner_id <> p_friend_id;
        if v_lover_partner is not null and v_lover_created > v_now - interval '30 days' then
            raise exception 'relation_cooldown';
        end if;
    end if;

    -- 同一好友只保留一种关系：绑定新类型时清掉与对方的其他类型记录。
    delete from public.friend_relations r
    where r.relation_type <> kind
      and ((r.user_id = current_uid and r.partner_id = p_friend_id)
        or (r.user_id = p_friend_id and r.partner_id = current_uid));

    -- 情侣独占：解除我与旧情侣的双向记录（保留与当前好友的记录）。
    if kind = 'lover' then
        delete from public.friend_relations r
        where r.relation_type = 'lover'
          and ((r.user_id = current_uid and r.partner_id <> p_friend_id)
            or (r.partner_id = current_uid and r.user_id <> p_friend_id));
    end if;

    -- 其余关系（挚友/兄弟/闺蜜）允许绑定多位好友，直接写入镜像记录。
    -- 用约束名避免与返回列 relation_type 产生歧义。
    insert into public.friend_relations as target (user_id, partner_id, relation_type)
    values (current_uid, p_friend_id, kind)
    on conflict on constraint friend_relations_pkey do update
    set created_at = now();

    insert into public.friend_relations as target (user_id, partner_id, relation_type)
    values (p_friend_id, current_uid, kind)
    on conflict on constraint friend_relations_pkey do update
    set created_at = now();

    return query select kind, v_now;
end;
$$;

revoke all on function public.set_friend_relation(uuid, text) from public, anon;
grant execute on function public.set_friend_relation(uuid, text) to authenticated;

notify pgrst, 'reload schema';
