-- 亲密关系绑定规则调整：只有情侣为一对一，其他关系可绑定多位好友。
-- 主键由 (user_id, relation_type) 调整为 (user_id, partner_id, relation_type)。

alter table public.friend_relations
    drop constraint if exists friend_relations_pkey;
alter table public.friend_relations
    add primary key (user_id, partner_id, relation_type);

create index if not exists friend_relations_partner_idx
    on public.friend_relations(partner_id);

-- 同一好友只保留一种关系：历史数据若存在同好友多种关系，仅保留最新一条。
delete from public.friend_relations
where ctid in (
    select ranked.ctid
    from (
        select ctid,
               row_number() over (
                   partition by user_id, partner_id
                   order by created_at desc, relation_type
               ) as rn
        from public.friend_relations
    ) ranked
    where ranked.rn > 1
);

create or replace function public.set_friend_relation(p_friend_id uuid, p_relation text)
returns table (relation_type text)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := (select auth.uid());
    kind text := lower(trim(coalesce(p_relation, '')));
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

    return query select kind;
end;
$$;

revoke all on function public.set_friend_relation(uuid, text) from public, anon;
grant execute on function public.set_friend_relation(uuid, text) to authenticated;

notify pgrst, 'reload schema';
