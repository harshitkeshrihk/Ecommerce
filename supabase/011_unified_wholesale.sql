-- Unified storefront: Wholesale becomes a section of the normal home screen
-- instead of a separate portal for wholesale/distributor accounts.
--
-- Everyone can now browse the Wholesale section (MOQ slabs, Quick-Order Pad,
-- RFQ). Ordering AT slab prices stays limited to KYC-approved business
-- accounts, and wholesale orders are still confirmed without an in-app
-- payment (billing is offline, Doc 4 §00). Until now the only thing stopping
-- a retail account from creating such an order was that the app never showed
-- it the screen — the orders insert policy (002 §5) only checked user_id.
-- This migration moves that rule into the database.
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after 010. Idempotent.

-- =====================================================================
-- SECTION A — optional GSTIN on each order
-- =====================================================================
-- A snapshot, not a reference to profiles.gstin: the profile value can
-- change later, the order must keep the GSTIN it was billed against.
-- Format only (2-digit state code, PAN, entity code, 'Z', check character);
-- the app additionally verifies the check character.

alter table public.orders
    add column if not exists gstin text;

alter table public.orders
    drop constraint if exists orders_gstin_format_check;
alter table public.orders
    add constraint orders_gstin_format_check
    check (gstin is null or gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$');

-- =====================================================================
-- SECTION B — who may create a wholesale order
-- =====================================================================

create or replace function public.is_business_buyer()
returns boolean as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid() and role in ('wholesale', 'distributor', 'admin')
    );
$$ language sql stable security definer;

-- True when payment_id is 'RFQ-<quote id>' for a quote on one of the
-- caller's own RFQs (RfqRepository.acceptQuote). Any signed-in user may send
-- an RFQ, and an admin-issued quote is an agreed price, so accepting it is
-- allowed without a business account.
create or replace function public.is_own_quote_acceptance(p_payment_id text)
returns boolean as $$
    select exists (
        select 1 from public.quotes q
        join public.rfqs r on r.id = q.rfq_id
        where 'RFQ-' || q.id::text = p_payment_id
          and r.user_id = auth.uid()
    );
$$ language sql stable security definer;

drop policy if exists "Users can create their own orders" on public.orders;
create policy "Users can create their own orders"
    on public.orders for insert
    with check (
        user_id = auth.uid()
        and (
            channel is distinct from 'wholesale'
            or public.is_business_buyer()
            or public.is_own_quote_acceptance(payment_id)
        )
    );
