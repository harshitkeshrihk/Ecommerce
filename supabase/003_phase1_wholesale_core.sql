-- Phase 1: Wholesale & Distributor Core (Lite) + Foundation completion.
-- Reference: app/src/main/java/com/example/vishnu/docs/Utensils_App_04_Phase1_Spec.md
--
-- REVIEW BEFORE RUNNING, same as 002_role_based_access.sql. Run in the Supabase
-- SQL editor against your actual schema. Idempotent (safe to re-run).
--
-- Sections A-G are purely additive (new columns/tables) and carry no
-- regression risk to existing flows.
--
-- Section H retires the `stores` table as the admin-access mechanism, which
-- IS load-bearing today (AuthViewModel.onSignIn grants ADMIN to anyone who
-- owns a row in `stores`, independent of profiles.role). Do NOT run Section H
-- until you've confirmed your own admin account has profiles.role = 'admin'
-- (query at the top of that section) — otherwise the next login will not
-- reach the admin dashboard.

-- =====================================================================
-- SECTION A — profiles: KYC / business fields
-- =====================================================================

alter table public.profiles
    add column if not exists requested_role text;

alter table public.profiles
    add column if not exists gstin text;

alter table public.profiles
    add column if not exists business_name text;

alter table public.profiles
    add column if not exists kyc_status text; -- null (n/a) | pending | verified | rejected

alter table public.profiles
    drop constraint if exists profiles_kyc_status_check;
alter table public.profiles
    add constraint profiles_kyc_status_check
    check (kyc_status is null or kyc_status in ('pending', 'verified', 'rejected'));

alter table public.profiles
    add column if not exists kyc_rejection_reason text;

alter table public.profiles
    drop constraint if exists profiles_requested_role_check;
alter table public.profiles
    add constraint profiles_requested_role_check
    check (requested_role is null or requested_role in ('wholesale', 'distributor'));

-- =====================================================================
-- SECTION B — pricing tiers + tier assignment on profiles
-- =====================================================================

-- profiles' existing RLS ("Users can view/update own profile", predating
-- this migration) is scoped to auth.uid() = id only — an admin can't see or
-- update anyone else's row under it. The KYC queue and approve/reject flow
-- both need to. These are additive (OR with the existing self-only policies,
-- which stay untouched).
drop policy if exists "Admin can view all profiles" on public.profiles;
create policy "Admin can view all profiles"
    on public.profiles for select
    using (public.is_admin());

drop policy if exists "Admin can update all profiles" on public.profiles;
create policy "Admin can update all profiles"
    on public.profiles for update
    using (public.is_admin())
    with check (public.is_admin());

create table if not exists public.pricing_tiers (
    id uuid primary key default gen_random_uuid(),
    name text not null unique,
    description text
);

insert into public.pricing_tiers (name, description)
values
    ('Standard Wholesale', 'Default tier for approved wholesale/distributor accounts')
on conflict (name) do nothing;

alter table public.profiles
    add column if not exists pricing_tier_id uuid references public.pricing_tiers(id);

alter table public.pricing_tiers enable row level security;

drop policy if exists "Pricing tiers are viewable by everyone" on public.pricing_tiers;
create policy "Pricing tiers are viewable by everyone"
    on public.pricing_tiers for select
    using (true);

drop policy if exists "Only admin manages pricing tiers" on public.pricing_tiers;
create policy "Only admin manages pricing tiers"
    on public.pricing_tiers for all
    using (public.is_admin())
    with check (public.is_admin());

-- =====================================================================
-- SECTION C — admin can approve/reject KYC (change someone else's role)
-- =====================================================================
-- 002_role_based_access.sql's prevent_role_self_update trigger blocks EVERY
-- non-service-role change to profiles.role, including an admin approving a
-- KYC request for a different user. This extends the same trigger with one
-- narrow exception: an admin may change another user's role, never their own.

create or replace function public.prevent_role_self_update()
returns trigger as $$
begin
    if auth.role() <> 'service_role'
       and new.role is distinct from old.role
       and not (public.is_admin() and new.id <> auth.uid())
    then
        new.role := old.role;
    end if;
    return new;
end;
$$ language plpgsql security definer;
-- (trigger itself already exists from 002_role_based_access.sql — CREATE OR
-- REPLACE FUNCTION above is sufficient, no need to re-create the trigger.)

-- =====================================================================
-- SECTION D — products: unit of measure (for Quick-Order Pad qty entry)
-- =====================================================================

alter table public.products
    add column if not exists unit_of_measure text not null default 'piece';

alter table public.products
    drop constraint if exists products_unit_of_measure_check;
alter table public.products
    add constraint products_unit_of_measure_check
    check (unit_of_measure in ('piece', 'dozen', 'kg', 'set'));

-- =====================================================================
-- SECTION E — moq_slabs (MOQ-tiered pricing)
-- =====================================================================

create table if not exists public.moq_slabs (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.products(id) on delete cascade,
    min_qty integer not null check (min_qty > 0),
    price_per_unit numeric not null check (price_per_unit >= 0),
    unique (product_id, min_qty)
);

alter table public.moq_slabs enable row level security;

drop policy if exists "MOQ slabs are viewable by everyone" on public.moq_slabs;
create policy "MOQ slabs are viewable by everyone"
    on public.moq_slabs for select
    using (true);

drop policy if exists "Only admin manages MOQ slabs" on public.moq_slabs;
create policy "Only admin manages MOQ slabs"
    on public.moq_slabs for all
    using (public.is_admin())
    with check (public.is_admin());

-- =====================================================================
-- SECTION F — orders: channel (distinguishes wholesale from retail)
-- =====================================================================

alter table public.orders
    add column if not exists channel text not null default 'retail';

alter table public.orders
    drop constraint if exists orders_channel_check;
alter table public.orders
    add constraint orders_channel_check check (channel in ('retail', 'wholesale'));

-- =====================================================================
-- SECTION G — RFQ / Quote pipeline
-- =====================================================================

create table if not exists public.rfqs (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'open' check (status in ('open', 'quoted', 'negotiating', 'won', 'lost')),
    needed_by_date date,
    notes text,
    created_at timestamptz not null default now()
);

create table if not exists public.rfq_items (
    id uuid primary key default gen_random_uuid(),
    rfq_id uuid not null references public.rfqs(id) on delete cascade,
    product_id uuid not null references public.products(id),
    qty integer not null check (qty > 0)
);

create table if not exists public.quotes (
    id uuid primary key default gen_random_uuid(),
    rfq_id uuid not null references public.rfqs(id) on delete cascade,
    version integer not null default 1,
    price_per_unit numeric not null check (price_per_unit >= 0),
    terms text,
    created_by uuid references auth.users(id),
    created_at timestamptz not null default now()
);

alter table public.rfqs enable row level security;
alter table public.rfq_items enable row level security;
alter table public.quotes enable row level security;

drop policy if exists "Users see their own RFQs, admin sees all" on public.rfqs;
create policy "Users see their own RFQs, admin sees all"
    on public.rfqs for select
    using (user_id = auth.uid() or public.is_admin());

drop policy if exists "Users create their own RFQs" on public.rfqs;
create policy "Users create their own RFQs"
    on public.rfqs for insert
    with check (user_id = auth.uid());

drop policy if exists "Admin updates RFQ status" on public.rfqs;
create policy "Admin updates RFQ status"
    on public.rfqs for update
    using (public.is_admin() or user_id = auth.uid())
    with check (public.is_admin() or user_id = auth.uid());

drop policy if exists "RFQ items follow parent RFQ visibility" on public.rfq_items;
create policy "RFQ items follow parent RFQ visibility"
    on public.rfq_items for select
    using (exists (
        select 1 from public.rfqs
        where rfqs.id = rfq_items.rfq_id
          and (rfqs.user_id = auth.uid() or public.is_admin())
    ));

drop policy if exists "RFQ items are inserted with their parent RFQ" on public.rfq_items;
create policy "RFQ items are inserted with their parent RFQ"
    on public.rfq_items for insert
    with check (exists (
        select 1 from public.rfqs
        where rfqs.id = rfq_items.rfq_id and rfqs.user_id = auth.uid()
    ));

drop policy if exists "Quotes follow parent RFQ visibility" on public.quotes;
create policy "Quotes follow parent RFQ visibility"
    on public.quotes for select
    using (exists (
        select 1 from public.rfqs
        where rfqs.id = quotes.rfq_id
          and (rfqs.user_id = auth.uid() or public.is_admin())
    ));

drop policy if exists "Only admin creates quotes" on public.quotes;
create policy "Only admin creates quotes"
    on public.quotes for insert
    with check (public.is_admin());

-- =====================================================================
-- SECTION G-FIX — per-line quote pricing (quote_items)
-- =====================================================================
-- quotes.price_per_unit applied ONE price to the WHOLE rfq, which breaks the
-- moment an RFQ has more than one distinct product (e.g. 50 Thalis + 20
-- Copper Jugs quoted at a single "₹250/unit" makes no sense). quotes becomes
-- just the "round" (version, terms); price now lives per rfq_item.

alter table public.quotes drop column if exists price_per_unit;

create table if not exists public.quote_items (
    id uuid primary key default gen_random_uuid(),
    quote_id uuid not null references public.quotes(id) on delete cascade,
    rfq_item_id uuid not null references public.rfq_items(id),
    price_per_unit numeric not null check (price_per_unit >= 0)
);

alter table public.quote_items enable row level security;

drop policy if exists "Quote items follow parent quote visibility" on public.quote_items;
create policy "Quote items follow parent quote visibility"
    on public.quote_items for select
    using (exists (
        select 1 from public.quotes
        join public.rfqs on rfqs.id = quotes.rfq_id
        where quotes.id = quote_items.quote_id
          and (rfqs.user_id = auth.uid() or public.is_admin())
    ));

drop policy if exists "Only admin creates quote items" on public.quote_items;
create policy "Only admin creates quote items"
    on public.quote_items for insert
    with check (public.is_admin());

-- =====================================================================
-- SECTION H — DANGER ZONE: retire `stores`/store_id as the admin gate
-- =====================================================================
-- Run this section ONLY after confirming (and if needed, fixing) that every
-- real admin account already has profiles.role = 'admin':
--
--   select p.id, u.email, p.role
--   from public.profiles p join auth.users u on u.id = p.id
--   where p.role = 'admin' or p.id in (select owner_email from public.stores); -- adjust join if stores links by email
--
-- If an admin's row shows role <> 'admin', fix it first (as the project
-- owner, in the SQL editor):
--   update public.profiles set role = 'admin' where id = '<admin-user-uuid>';
--
-- Once every admin account is confirmed, uncomment and run the block below.
-- It is commented out by default so this migration cannot accidentally lock
-- anyone out on a blind run.

-- alter table public.products alter column store_id drop not null;
-- alter table public.orders alter column store_id drop not null;
--
-- drop policy if exists "Only store owner or admin can insert products" on public.products;
-- create policy "Only admin can insert products"
--     on public.products for insert
--     with check (public.is_admin());
--
-- drop policy if exists "Only store owner or admin can update products" on public.products;
-- create policy "Only admin can update products"
--     on public.products for update
--     using (public.is_admin());
--
-- drop policy if exists "Only store owner or admin can delete products" on public.products;
-- create policy "Only admin can delete products"
--     on public.products for delete
--     using (public.is_admin());
--
-- drop policy if exists "Users and store owners can view relevant orders" on public.orders;
-- create policy "Users and admin can view relevant orders"
--     on public.orders for select
--     using (user_id = auth.uid() or public.is_admin());
--
-- drop policy if exists "Only store owner or admin can update orders" on public.orders;
-- create policy "Only admin can update orders"
--     on public.orders for update
--     using (public.is_admin());






















