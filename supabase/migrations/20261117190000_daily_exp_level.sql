-- 每日签到改为“打开应用自动签到”：不再要求当天有饮食记录。
-- 拍照记录饮食改为每日任务：每天首次记录奖励经验，经验累计提升等级。
-- 等级曲线：从 L 级升到 L+1 级需要 100*L 经验（累计 50*L*(L-1)）。

alter table public.game_wallets
    add column if not exists exp integer not null default 0 check (exp >= 0);

alter table public.game_wallets
    add column if not exists last_task_reward_date date;

-- 累计升到 p_level 所需的经验值。
create or replace function public.game_level_floor(p_level integer)
returns integer
language sql
immutable
as $$
    select 50 * greatest(coalesce(p_level, 1), 1) * (greatest(coalesce(p_level, 1), 1) - 1);
$$;

create or replace function public.game_level(p_exp integer)
returns integer
language plpgsql
immutable
as $$
declare
    lvl integer := 1;
    remaining integer := greatest(coalesce(p_exp, 0), 0);
begin
    while remaining >= 50 * (lvl + 1) * lvl loop
        lvl := lvl + 1;
    end loop;
    return lvl;
end;
$$;

create or replace function public.get_game_state()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    wallet_coins integer := 0;
    wallet_last_date date;
    wallet_exp integer := 0;
    wallet_task_date date;
    wallet_level integer := 1;
    level_base integer := 0;
    level_next integer := 100;
    food_count integer := 0;
    items jsonb := '[]'::jsonb;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select w.coins, w.last_check_in_date, w.exp, w.last_task_reward_date
    into wallet_coins, wallet_last_date, wallet_exp, wallet_task_date
    from public.game_wallets w
    where w.user_id = current_uid;

    wallet_coins := coalesce(wallet_coins, 0);
    wallet_exp := coalesce(wallet_exp, 0);
    wallet_level := public.game_level(wallet_exp);
    level_base := public.game_level_floor(wallet_level);
    level_next := public.game_level_floor(wallet_level + 1) - level_base;

    select count(*)::integer into food_count
    from public.game_food_activity a
    where a.user_id = current_uid
      and a.entry_date = current_date;

    select coalesce(
        jsonb_agg(
            jsonb_build_object('item_id', i.item_id, 'quantity', i.quantity)
            order by i.acquired_at, i.item_id
        ),
        '[]'::jsonb
    ) into items
    from public.game_inventory i
    where i.user_id = current_uid;

    return jsonb_build_object(
        'user_id', current_uid,
        'coins', wallet_coins,
        'exp', wallet_exp,
        'level', wallet_level,
        'level_exp', wallet_exp - level_base,
        'level_next', level_next,
        'last_check_in_date', wallet_last_date,
        'daily_task_done', coalesce(wallet_task_date = current_date, false),
        'today_food_count', food_count,
        'server_date', current_date,
        'items', items
    );
end;
$$;

-- 自动签到：应用打开/刷新时调用；同一天重复调用只返回 already_claimed。
create or replace function public.claim_game_check_in()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    wallet_date date;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    insert into public.game_wallets (user_id)
    values (current_uid)
    on conflict (user_id) do nothing;

    select w.last_check_in_date into wallet_date
    from public.game_wallets w
    where w.user_id = current_uid
    for update;

    if wallet_date = current_date then
        return jsonb_build_object('status', 'already_claimed');
    end if;

    update public.game_wallets
    set coins = coins + 20,
        last_check_in_date = current_date,
        updated_at = now()
    where user_id = current_uid;

    return jsonb_build_object('status', 'success', 'coins_awarded', 20);
end;
$$;

-- 每日任务：记录一次饮食即完成，每天只发一次经验；
-- 奖励日期单独记录，网络失败的重复调用仍可补发，不会重复计数。
create or replace function public.complete_photo_task(
    p_client_ref text,
    p_entry_date date
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    wallet_exp integer := 0;
    wallet_task_date date;
    old_level integer := 1;
    new_exp integer := 0;
    new_level integer := 1;
    exp_reward constant integer := 30;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_client_ref is null or char_length(btrim(p_client_ref)) not between 1 and 100 then
        raise exception 'invalid_client_ref' using errcode = '22023';
    end if;
    if p_entry_date is null or p_entry_date <> current_date then
        raise exception 'invalid_entry_date' using errcode = '22023';
    end if;

    insert into public.game_wallets (user_id)
    values (current_uid)
    on conflict (user_id) do nothing;

    select w.exp, w.last_task_reward_date
    into wallet_exp, wallet_task_date
    from public.game_wallets w
    where w.user_id = current_uid
    for update;

    wallet_exp := coalesce(wallet_exp, 0);
    old_level := public.game_level(wallet_exp);

    -- 只作为“今天有记录”的凭证，不包含任何食物内容。
    insert into public.game_food_activity (user_id, client_ref, entry_date)
    values (current_uid, btrim(p_client_ref), p_entry_date)
    on conflict (user_id, client_ref) do nothing;

    if wallet_task_date = current_date then
        return jsonb_build_object(
            'status', 'already_done',
            'exp', wallet_exp,
            'level', old_level,
            'leveled_up', false
        );
    end if;

    new_exp := wallet_exp + exp_reward;
    update public.game_wallets
    set exp = new_exp,
        last_task_reward_date = current_date,
        updated_at = now()
    where user_id = current_uid;

    new_level := public.game_level(new_exp);

    return jsonb_build_object(
        'status', 'success',
        'exp', new_exp,
        'level', new_level,
        'leveled_up', new_level > old_level,
        'exp_awarded', exp_reward
    );
end;
$$;

revoke all on function public.game_level_floor(integer) from public, anon;
revoke all on function public.game_level(integer) from public, anon;
revoke all on function public.get_game_state() from public, anon, authenticated;
revoke all on function public.claim_game_check_in() from public, anon, authenticated;
revoke all on function public.complete_photo_task(text, date) from public, anon, authenticated;

grant execute on function public.get_game_state() to authenticated;
grant execute on function public.claim_game_check_in() to authenticated;
grant execute on function public.complete_photo_task(text, date) to authenticated;

notify pgrst, 'reload schema';
