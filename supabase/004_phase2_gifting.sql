-- Phase 2, slice 1: Bulk Gifting engine (shared) + event gifting end-to-end.
-- Reference: app/src/main/java/com/example/vishnu/docs/Utensils_App_04_Phase1_Spec.md §03
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after
-- 003_phase1_wholesale_core.sql. Idempotent (safe to re-run) and purely
-- additive: no existing column, policy or flow is changed except that
-- orders.channel additionally accepts 'gifting'.
--
-- Run this BEFORE installing the app build that ships Phase 2: the app now
-- reads/writes products.category_tags, and a product save will fail if the
-- column doesn't exist yet.

-- =====================================================================
-- SECTION A — products: category tags ('gifting' marks gift-suitable SKUs)
-- =====================================================================

alter table public.products
    add column if not exists category_tags text[] not null default '{}';

create index if not exists products_category_tags_idx
    on public.products using gin (category_tags);

-- =====================================================================
-- SECTION B — admin-defined gift packs
-- =====================================================================
-- A pack is what ONE recipient gets (e.g. 1 bowl + 1 spoon + 1 gift box).
-- There is no stored pack price: price per pack is always computed from the
-- items' retail prices, so a customer-edited pack prices the same way as an
-- admin-defined one. Packaging (gift box, wrap) is modelled as a product.

create table if not exists public.gift_packs (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text,
    image_url text,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.gift_pack_items (
    id uuid primary key default gen_random_uuid(),
    pack_id uuid not null references public.gift_packs(id) on delete cascade,
    product_id uuid not null references public.products(id) on delete cascade,
    qty integer not null check (qty > 0),
    unique (pack_id, product_id)
);

alter table public.gift_packs enable row level security;
alter table public.gift_pack_items enable row level security;

drop policy if exists "Active gift packs are viewable by signed-in users" on public.gift_packs;
create policy "Active gift packs are viewable by signed-in users"
    on public.gift_packs for select
    using ((is_active and auth.uid() is not null) or public.is_admin());

drop policy if exists "Only admin manages gift packs" on public.gift_packs;
create policy "Only admin manages gift packs"
    on public.gift_packs for all
    using (public.is_admin())
    with check (public.is_admin());

drop policy if exists "Gift pack items follow parent pack visibility" on public.gift_pack_items;
create policy "Gift pack items follow parent pack visibility"
    on public.gift_pack_items for select
    using (exists (
        select 1 from public.gift_packs
        where gift_packs.id = gift_pack_items.pack_id
          and ((gift_packs.is_active and auth.uid() is not null) or public.is_admin())
    ));

drop policy if exists "Only admin manages gift pack items" on public.gift_pack_items;
create policy "Only admin manages gift pack items"
    on public.gift_pack_items for all
    using (public.is_admin())
    with check (public.is_admin());

-- =====================================================================
-- SECTION C — orders: 'gifting' channel
-- =====================================================================

alter table public.orders
    drop constraint if exists orders_channel_check;
alter table public.orders
    add constraint orders_channel_check check (channel in ('retail', 'wholesale', 'gifting'));

-- =====================================================================
-- SECTION D — gifting_orders (one row per gifting order, extends orders)
-- =====================================================================
-- order_items still holds the expanded product quantities (per-pack qty x
-- pack_count) so stock/fulfilment/order history keep working unchanged.
-- pack_contents keeps the per-pack composition the customer confirmed, which
-- is what the workshop actually assembles.

create table if not exists public.gifting_orders (
    order_id bigint primary key references public.orders(id) on delete cascade,
    occasion_type text not null check (occasion_type in ('wedding', 'corporate', 'event')),
    source_pack_id uuid references public.gift_packs(id) on delete set null, -- null = built from scratch
    pack_name text not null,
    pack_count integer not null check (pack_count > 0),
    pack_contents jsonb not null, -- [{product_id, product_name, qty_per_pack, unit_price}]
    personalization_text text,
    ship_by_date date not null,
    installment_schedule_json jsonb, -- wedding only (slice 3)
    created_at timestamptz not null default now()
);

alter table public.gifting_orders enable row level security;

drop policy if exists "Users see their own gifting orders, admin sees all" on public.gifting_orders;
create policy "Users see their own gifting orders, admin sees all"
    on public.gifting_orders for select
    using (public.is_admin() or exists (
        select 1 from public.orders
        where orders.id = gifting_orders.order_id and orders.user_id = auth.uid()
    ));

drop policy if exists "Users create gifting details for their own orders" on public.gifting_orders;
create policy "Users create gifting details for their own orders"
    on public.gifting_orders for insert
    with check (exists (
        select 1 from public.orders
        where orders.id = gifting_orders.order_id and orders.user_id = auth.uid()
    ));
