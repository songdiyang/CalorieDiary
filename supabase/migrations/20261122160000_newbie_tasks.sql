-- 新手任务：修改用户名、添加第一个好友、记录第一次食物。
-- 完成条件全部由服务端根据真实数据判定，客户端只能触发同步，不能伪造完成；
-- 奖励参数沿用 game_task_rewards（客户端只读），每个任务只发放一次。

create table if not exists public.game_newbie_tasks (
    user_id uuid not null references public.profiles(id) on delete cascade,
    task_id text not null,
    completed_at timestamptz not null default now(),
    primary key (user_id, task_id)
);

comment on table public.game_newbie_tasks is
    '新手任务完成记录（服务端判定后写入）；客户端无读写权限。';

alter table public.game_newbie_tasks enable row level security;
revoke all on table public.game_newbie_tasks from public, anon, authenticated;

insert into public.game_task_rewards(task_id, coin_reward, exp_reward)
values
    ('newbie_nickname', 10, 20),
    ('newbie_first_friend', 20, 30),
    ('newbie_first_food', 15, 25)
on conflict (task_id) do nothing;

-- 新手任务同步：检查真实数据，完成且未发奖的任务立即发放，可重复调用。
create or replace function public.sync_newbie_tasks()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    cfg record;
    task_completed boolean;
    inserted_rows integer;
    wallet_exp integer := 0;
    old_level integer := 1;
    new_exp integer := 0;
    new_level integer := 1;
    total_coins integer := 0;
    total_exp integer := 0;
    awarded jsonb := '[]'::jsonb;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    insert into public.game_wallets (user_id)
    values (current_uid)
    on conflict (user_id) do nothing;

    select coalesce(w.exp, 0) into wallet_exp
    from public.game_wallets w
    where w.user_id = current_uid
    for update;

    wallet_exp := coalesce(wallet_exp, 0);
    old_level := public.game_level(wallet_exp);

    for cfg in
        select r.task_id, r.coin_reward, r.exp_reward
        from public.game_task_rewards r
        where r.task_id in ('newbie_nickname', 'newbie_first_friend', 'newbie_first_food')
        order by array_position(
            array['newbie_nickname', 'newbie_first_friend', 'newbie_first_food']::text[],
            r.task_id
        )
    loop
        task_completed := case cfg.task_id
            when 'newbie_nickname' then exists (
                select 1 from public.nickname_changes c where c.user_id = current_uid
            )
            when 'newbie_first_friend' then exists (
                select 1 from public.friendships f
                where f.user_low = current_uid or f.user_high = current_uid
            )
            when 'newbie_first_food' then exists (
                select 1 from public.meal_entries m where m.user_id = current_uid
            ) or exists (
                select 1 from public.game_food_activity a where a.user_id = current_uid
            )
            else false
        end;

        if not task_completed then
            continue;
        end if;

        insert into public.game_newbie_tasks (user_id, task_id)
        values (current_uid, cfg.task_id)
        on conflict (user_id, task_id) do nothing;

        get diagnostics inserted_rows = row_count;
        if inserted_rows > 0 then
            total_coins := total_coins + cfg.coin_reward;
            total_exp := total_exp + cfg.exp_reward;
            awarded := awarded || jsonb_build_object(
                'task_id', cfg.task_id,
                'coin_reward', cfg.coin_reward,
                'exp_reward', cfg.exp_reward
            );
        end if;
    end loop;

    if jsonb_array_length(awarded) = 0 then
        return jsonb_build_object(
            'status', 'nothing',
            'awarded', '[]'::jsonb,
            'coins_awarded', 0,
            'exp_awarded', 0,
            'level', old_level,
            'leveled_up', false
        );
    end if;

    new_exp := wallet_exp + total_exp;
    update public.game_wallets
    set coins = coins + total_coins,
        exp = new_exp,
        updated_at = now()
    where user_id = current_uid;

    new_level := public.game_level(new_exp);

    return jsonb_build_object(
        'status', 'success',
        'awarded', awarded,
        'coins_awarded', total_coins,
        'exp_awarded', total_exp,
        'level', new_level,
        'leveled_up', new_level > old_level
    );
end;
$$;

-- 状态读取：附带新手任务配置、完成状态与奖励，供任务页展示。
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
    newbie_tasks jsonb := '[]'::jsonb;
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

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'task_id', r.task_id,
                'done', exists (
                    select 1 from public.game_newbie_tasks t
                    where t.user_id = current_uid and t.task_id = r.task_id
                ),
                'coin_reward', r.coin_reward,
                'exp_reward', r.exp_reward
            )
            order by array_position(
                array['newbie_nickname', 'newbie_first_friend', 'newbie_first_food']::text[],
                r.task_id
            )
        ),
        '[]'::jsonb
    ) into newbie_tasks
    from public.game_task_rewards r
    where r.task_id in ('newbie_nickname', 'newbie_first_friend', 'newbie_first_food');

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
        'items', items,
        'newbie_tasks', newbie_tasks
    );
end;
$$;

revoke all on function public.sync_newbie_tasks() from public, anon, authenticated;
grant execute on function public.sync_newbie_tasks() to authenticated;

notify pgrst, 'reload schema';