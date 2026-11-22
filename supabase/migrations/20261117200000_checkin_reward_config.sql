-- 签到奖励改为服务端配置：每日签到默认 5 金币 + 10 经验；
-- 连续签到每满 7 天，当天金币与经验翻倍（×2）。
-- 奖励参数只存数据库（game_task_rewards），客户端只能读取，不能修改。

-- 1) 奖励配置表：只读（对 anon/authenticated 无任何权限），由服务端函数读取。
create table if not exists public.game_task_rewards (
    task_id text primary key,
    coin_reward integer not null default 0 check (coin_reward >= 0),
    exp_reward integer not null default 0 check (exp_reward >= 0),
    streak_days integer not null default 0 check (streak_days >= 0),
    streak_multiplier integer not null default 1 check (streak_multiplier >= 1),
    updated_at timestamptz not null default now()
);

comment on table public.game_task_rewards is
    '任务奖励配置（服务端唯一可信来源）；客户端无读写权限，只能通过 RPC 返回值展示。';

insert into public.game_task_rewards(task_id, coin_reward, exp_reward, streak_days, streak_multiplier)
values ('daily_check_in', 5, 10, 7, 2)
on conflict (task_id) do update
set coin_reward = excluded.coin_reward,
    exp_reward = excluded.exp_reward,
    streak_days = excluded.streak_days,
    streak_multiplier = excluded.streak_multiplier,
    updated_at = now();

alter table public.game_task_rewards enable row level security;
revoke all on table public.game_task_rewards from public, anon, authenticated;

-- 2) 钱包记录连续签到天数。
alter table public.game_wallets
    add column if not exists check_in_streak integer not null default 0 check (check_in_streak >= 0);

-- 3) 状态读取：返回连续天数、今日奖励与翻倍规则，供任务页展示。
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
    wallet_streak integer := 0;
    wallet_level integer := 1;
    level_base integer := 0;
    level_next integer := 100;
    food_count integer := 0;
    cfg_coins integer := 5;
    cfg_exp integer := 10;
    cfg_days integer := 7;
    cfg_mult integer := 2;
    items jsonb := '[]'::jsonb;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select w.coins, w.last_check_in_date, w.exp, w.last_task_reward_date,
           coalesce(w.check_in_streak, 0)
    into wallet_coins, wallet_last_date, wallet_exp, wallet_task_date, wallet_streak
    from public.game_wallets w
    where w.user_id = current_uid;

    wallet_coins := coalesce(wallet_coins, 0);
    wallet_exp := coalesce(wallet_exp, 0);
    wallet_level := public.game_level(wallet_exp);
    level_base := public.game_level_floor(wallet_level);
    level_next := public.game_level_floor(wallet_level + 1) - level_base;

    select r.coin_reward, r.exp_reward, r.streak_days, r.streak_multiplier
    into cfg_coins, cfg_exp, cfg_days, cfg_mult
    from public.game_task_rewards r
    where r.task_id = 'daily_check_in';

    cfg_coins := coalesce(cfg_coins, 5);
    cfg_exp := coalesce(cfg_exp, 10);
    cfg_days := coalesce(cfg_days, 7);
    cfg_mult := greatest(coalesce(cfg_mult, 2), 1);

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
        'check_in_streak', wallet_streak,
        'checkin_coin_reward', cfg_coins,
        'checkin_exp_reward', cfg_exp,
        'checkin_streak_days', cfg_days,
        'checkin_streak_multiplier', cfg_mult,
        'daily_task_done', coalesce(wallet_task_date = current_date, false),
        'today_food_count', food_count,
        'server_date', current_date,
        'items', items
    );
end;
$$;

-- 4) 自动签到：按连续天数发放金币与经验，每满 streak_days 天翻倍。
create or replace function public.claim_game_check_in()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    wallet_date date;
    wallet_streak integer := 0;
    wallet_exp integer := 0;
    cfg_coins integer := 5;
    cfg_exp integer := 10;
    cfg_days integer := 7;
    cfg_mult integer := 2;
    new_streak integer;
    is_double boolean;
    coins_awarded integer;
    exp_awarded integer;
    new_exp integer;
    old_level integer;
    new_level integer;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    insert into public.game_wallets (user_id)
    values (current_uid)
    on conflict (user_id) do nothing;

    select w.last_check_in_date, coalesce(w.check_in_streak, 0), coalesce(w.exp, 0)
    into wallet_date, wallet_streak, wallet_exp
    from public.game_wallets w
    where w.user_id = current_uid
    for update;

    wallet_streak := coalesce(wallet_streak, 0);
    wallet_exp := coalesce(wallet_exp, 0);

    if wallet_date = current_date then
        return jsonb_build_object(
            'status', 'already_claimed',
            'streak', wallet_streak,
            'coins_awarded', 0,
            'exp_awarded', 0,
            'doubled', false
        );
    end if;

    select r.coin_reward, r.exp_reward, r.streak_days, r.streak_multiplier
    into cfg_coins, cfg_exp, cfg_days, cfg_mult
    from public.game_task_rewards r
    where r.task_id = 'daily_check_in';

    cfg_coins := coalesce(cfg_coins, 5);
    cfg_exp := coalesce(cfg_exp, 10);
    cfg_days := coalesce(cfg_days, 7);
    cfg_mult := greatest(coalesce(cfg_mult, 2), 1);

    -- 昨天签过则连续天数 +1，中断则从 1 重新开始。
    new_streak := case
        when wallet_date = current_date - 1 then wallet_streak + 1
        else 1
    end;

    is_double := cfg_days > 0
        and cfg_mult > 1
        and (new_streak % cfg_days) = 0;

    coins_awarded := cfg_coins * (case when is_double then cfg_mult else 1 end);
    exp_awarded := cfg_exp * (case when is_double then cfg_mult else 1 end);

    old_level := public.game_level(wallet_exp);
    new_exp := wallet_exp + exp_awarded;
    new_level := public.game_level(new_exp);

    update public.game_wallets
    set coins = coins + coins_awarded,
        exp = new_exp,
        check_in_streak = new_streak,
        last_check_in_date = current_date,
        updated_at = now()
    where user_id = current_uid;

    return jsonb_build_object(
        'status', 'success',
        'coins_awarded', coins_awarded,
        'exp_awarded', exp_awarded,
        'streak', new_streak,
        'doubled', is_double,
        'level', new_level,
        'leveled_up', new_level > old_level
    );
end;
$$;

revoke all on function public.game_level_floor(integer) from public, anon;
revoke all on function public.game_level(integer) from public, anon;
revoke all on function public.get_game_state() from public, anon, authenticated;
revoke all on function public.claim_game_check_in() from public, anon, authenticated;

grant execute on function public.get_game_state() to authenticated;
grant execute on function public.claim_game_check_in() to authenticated;

notify pgrst, 'reload schema';
