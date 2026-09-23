-- Phase 2, slice 2: production capacity + deadline-aware production calendar.
-- Reference: app/src/main/java/com/example/vishnu/docs/Utensils_App_04_Phase1_Spec.md §03
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after
-- 004_phase2_gifting.sql. Idempotent (safe to re-run) and purely additive.
--
-- Capacity is measured in gift packs per day. The seeded default of 300 is a
-- placeholder: set the real figure from the admin Production Calendar screen
-- (or update the row below) before taking gifting orders.

-- =====================================================================
-- SECTION A — capacity settings (single row) + per-date overrides
-- =====================================================================

create table if not exists public.production_capacity_settings (
    id integer primary key default 1 check (id = 1),
    default_packs_per_day integer not null default 300 check (default_packs_per_day >= 0),
    closed_weekdays integer[] not null default '{}', -- ISO: 1 = Monday ... 7 = Sunday
    updated_at timestamptz not null default now()
);

insert into public.production_capacity_settings (id) values (1)
on conflict (id) do nothing;

create table if not exists public.production_capacity_overrides (
    day date primary key,
    packs integer not null check (packs >= 0), -- 0 = workshop closed that day
    note text
);

alter table public.production_capacity_settings enable row level security;
alter table public.production_capacity_overrides enable row level security;

-- Signed-in customers need capacity to run the ship-by check before paying.
drop policy if exists "Capacity settings are viewable by signed-in users" on public.production_capacity_settings;
create policy "Capacity settings are viewable by signed-in users"
    on public.production_capacity_settings for select
    using (auth.uid() is not null);

drop policy if exists "Only admin manages capacity settings" on public.production_capacity_settings;
create policy "Only admin manages capacity settings"
    on public.production_capacity_settings for all
    using (public.is_admin())
    with check (public.is_admin());

drop policy if exists "Capacity overrides are viewable by signed-in users" on public.production_capacity_overrides;
create policy "Capacity overrides are viewable by signed-in users"
    on public.production_capacity_overrides for select
    using (auth.uid() is not null);

drop policy if exists "Only admin manages capacity overrides" on public.production_capacity_overrides;
create policy "Only admin manages capacity overrides"
    on public.production_capacity_overrides for all
    using (public.is_admin())
    with check (public.is_admin());

-- =====================================================================
-- SECTION B — open production load, aggregated
-- =====================================================================
-- Customers can't read other users' gifting_orders (RLS), but the ship-by
-- check needs the workshop's existing commitments. This returns ONLY packs per
-- ship-by date for unshipped orders — no order ids, users, addresses or
-- contents. SECURITY DEFINER so it can aggregate across all users.

create or replace function public.gifting_open_load()
returns table (ship_by_date date, packs bigint)
language sql
stable
security definer
set search_path = public
as $$
    select g.ship_by_date, sum(g.pack_count)::bigint
    from public.gifting_orders g
    join public.orders o on o.id = g.order_id
    where o.status in ('PAID', 'PROCESSING')
    group by g.ship_by_date
$$;

revoke all on function public.gifting_open_load() from public;
grant execute on function public.gifting_open_load() to authenticated;
