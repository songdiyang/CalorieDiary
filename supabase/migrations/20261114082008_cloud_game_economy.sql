-- 云端金币、每日签到与游戏背包。
-- 客户端只能读取自己的数据；余额、签到和库存变更必须通过受控 RPC。

create table public.game_wallets (
    user_id uuid primary key references public.profiles(id) on delete cascade,
    coins integer not null default 0 check (coins >= 0),
    last_check_in_date date,
    updated_at timestamptz not null default now()
);

create table public.game_shop_items (
    item_id text primary key,
    price integer not null check (price > 0),
    active boolean not null default true,
    sort_order integer not null default 0
);

insert into public.game_shop_items (item_id, price, sort_order) values
    ('star_sticker', 40, 1),
    ('goal_badge', 80, 2),
    ('heart_decoration', 60, 3),
    ('celebration_mascot', 100, 4)
on conflict (item_id) do update set
    price = excluded.price,
    sort_order = excluded.sort_order;

create table public.game_inventory (
    user_id uuid not null references public.profiles(id) on delete cascade,
    item_id text not null references public.game_shop_items(item_id),
    quantity integer not null check (quantity > 0),
    acquired_at timestamptz not null default now(),
    primary key (user_id, item_id)
);

-- 仅记录“当天有食物记录”的必要凭证，不包含食物名称、克重或热量。
create table public.game_food_activity (
    user_id uuid not null references public.profiles(id) on delete cascade,
    client_ref text not null check (char_length(client_ref) between 1 and 100),
    entry_date date not null,
    created_at timestamptz not null default now(),
    primary key (user_id, client_ref)
);

create index game_food_activity_user_date_idx
    on public.game_food_activity(user_id, entry_date);

alter table public.game_wallets enable row level security;
alter table public.game_shop_items enable row level security;
alter table public.game_inventory enable row level security;
alter table public.game_food_activity enable row level security;

create policy game_wallets_select_own on public.game_wallets
    for select to authenticated
    using ((select auth.uid()) = user_id);

create policy game_shop_items_select_active on public.game_shop_items
    for select to authenticated
    using (active);

create policy game_inventory_select_own on public.game_inventory
    for select to authenticated
    using ((select auth.uid()) = user_id);

create policy game_food_activity_select_own on public.game_food_activity
    for select to authenticated
    using ((select auth.uid()) = user_id);

revoke all on table public.game_wallets from anon;
revoke all on table public.game_shop_items from anon;
revoke all on table public.game_inventory from anon;
revoke all on table public.game_food_activity from anon;

revoke all on table public.game_wallets from authenticated;
revoke all on table public.game_shop_items from authenticated;
revoke all on table public.game_inventory from authenticated;
revoke all on table public.game_food_activity from authenticated;

grant select on table public.game_wallets to authenticated;
grant select on table public.game_shop_items to authenticated;
grant select on table public.game_inventory to authenticated;
grant select on table public.game_food_activity to authenticated;

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
    food_count integer := 0;
    items jsonb := '[]'::jsonb;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select w.coins, w.last_check_in_date
    into wallet_coins, wallet_last_date
    from public.game_wallets w
    where w.user_id = current_uid;

    wallet_coins := coalesce(wallet_coins, 0);

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
        'last_check_in_date', wallet_last_date,
        'today_food_count', food_count,
        'server_date', current_date,
        'items', items
    );
end;
$$;

create or replace function public.record_game_food(
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
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;
    if p_client_ref is null or char_length(trim(p_client_ref)) not between 1 and 100 then
        raise exception 'invalid_client_ref' using errcode = '22023';
    end if;
    if p_entry_date is null or p_entry_date <> current_date then
        raise exception 'invalid_entry_date' using errcode = '22023';
    end if;

    insert into public.game_food_activity (user_id, client_ref, entry_date)
    values (current_uid, trim(p_client_ref), p_entry_date)
    on conflict (user_id, client_ref) do nothing;

    return jsonb_build_object('status', 'recorded');
end;
$$;

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

    if not exists (
        select 1 from public.game_food_activity a
        where a.user_id = current_uid
          and a.entry_date = current_date
    ) then
        return jsonb_build_object('status', 'food_required');
    end if;

    update public.game_wallets
    set coins = coins + 20,
        last_check_in_date = current_date,
        updated_at = now()
    where user_id = current_uid;

    return jsonb_build_object('status', 'success');
end;
$$;

create or replace function public.purchase_game_item(p_item_id text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    current_uid uuid := auth.uid();
    item_price integer;
    wallet_coins integer;
begin
    if current_uid is null then
        raise exception 'authentication_required' using errcode = '42501';
    end if;

    select s.price into item_price
    from public.game_shop_items s
    where s.item_id = p_item_id
      and s.active;
    if not found then
        return jsonb_build_object('status', 'unknown_item');
    end if;

    insert into public.game_wallets (user_id)
    values (current_uid)
    on conflict (user_id) do nothing;

    select w.coins into wallet_coins
    from public.game_wallets w
    where w.user_id = current_uid
    for update;

    if wallet_coins < item_price then
        return jsonb_build_object('status', 'insufficient_coins');
    end if;

    update public.game_wallets
    set coins = coins - item_price,
        updated_at = now()
    where user_id = current_uid;

    insert into public.game_inventory (user_id, item_id, quantity)
    values (current_uid, p_item_id, 1)
    on conflict (user_id, item_id) do update set
        quantity = public.game_inventory.quantity + 1;

    return jsonb_build_object('status', 'success');
end;
$$;

revoke all on function public.get_game_state() from public;
revoke all on function public.record_game_food(text, date) from public;
revoke all on function public.claim_game_check_in() from public;
revoke all on function public.purchase_game_item(text) from public;

revoke all on function public.get_game_state() from anon;
revoke all on function public.record_game_food(text, date) from anon;
revoke all on function public.claim_game_check_in() from anon;
revoke all on function public.purchase_game_item(text) from anon;

grant execute on function public.get_game_state() to authenticated;
grant execute on function public.record_game_food(text, date) to authenticated;
grant execute on function public.claim_game_check_in() to authenticated;
grant execute on function public.purchase_game_item(text) to authenticated;
