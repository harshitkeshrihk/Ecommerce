-- Phase 2, slice 3: advance + balance payments for gifting orders (all occasions).
-- Customer pays at least 40% at checkout (up to 100%); the remaining balance is
-- paid in one payment, due 3 days before ship-by; an order cannot be marked
-- SHIPPED/DELIVERED until it is fully paid.
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after
-- 005_phase2_production_capacity.sql. Idempotent (safe to re-run).
--
-- NOTE ON TRUST: payment rows are written by the app after Razorpay reports
-- success on the phone; nothing here verifies them against Razorpay. Until a
-- server-side check exists (Edge Function + Razorpay secret), confirm large
-- balances in the Razorpay dashboard before dispatch.

-- =====================================================================
-- SECTION A — gifting_orders: balance due date
-- =====================================================================

alter table public.gifting_orders
    add column if not exists balance_due_date date;

-- =====================================================================
-- SECTION B — gifting_payments (every Razorpay payment against a gifting order)
-- =====================================================================

create table if not exists public.gifting_payments (
    id uuid primary key default gen_random_uuid(),
    order_id bigint not null references public.gifting_orders(order_id) on delete cascade,
    razorpay_payment_id text not null unique,
    amount numeric not null check (amount > 0),
    kind text not null check (kind in ('advance', 'full', 'balance')),
    created_at timestamptz not null default now()
);

create index if not exists gifting_payments_order_id_idx on public.gifting_payments (order_id);

alter table public.gifting_payments enable row level security;

drop policy if exists "Users see payments on their own gifting orders, admin sees all" on public.gifting_payments;
create policy "Users see payments on their own gifting orders, admin sees all"
    on public.gifting_payments for select
    using (public.is_admin() or exists (
        select 1 from public.orders
        where orders.id = gifting_payments.order_id and orders.user_id = auth.uid()
    ));

-- Only the FIRST payment (advance or full) is inserted directly, right after
-- the order is created. Balance payments go through pay_gifting_balance()
-- below, which checks the amount.
--
-- The "no payment yet" lookup must be a SECURITY DEFINER function: querying
-- gifting_payments directly inside its own policy makes Postgres re-apply the
-- policy to that subquery -> "infinite recursion detected in policy".
create or replace function public.gifting_order_has_payment(p_order_id bigint)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (select 1 from public.gifting_payments where order_id = p_order_id)
$$;

revoke all on function public.gifting_order_has_payment(bigint) from public;
grant execute on function public.gifting_order_has_payment(bigint) to authenticated;

drop policy if exists "Users record the first payment on their own gifting order" on public.gifting_payments;
create policy "Users record the first payment on their own gifting order"
    on public.gifting_payments for insert
    with check (
        kind in ('advance', 'full')
        and exists (
            select 1 from public.orders
            where orders.id = gifting_payments.order_id and orders.user_id = auth.uid()
        )
        and not public.gifting_order_has_payment(gifting_payments.order_id)
    );

-- Backfill: gifting orders placed before this migration were paid in full
-- upfront (slice 1). Record that payment so the dispatch gate below doesn't
-- treat them as unpaid.
insert into public.gifting_payments (order_id, razorpay_payment_id, amount, kind)
select g.order_id, o.payment_id, o.total_amount, 'full'
from public.gifting_orders g
join public.orders o on o.id = g.order_id
where not exists (select 1 from public.gifting_payments p where p.order_id = g.order_id)
  and o.total_amount > 0
on conflict (razorpay_payment_id) do nothing;

update public.gifting_orders
set balance_due_date = ship_by_date - 3
where balance_due_date is null;

-- =====================================================================
-- SECTION C — paying the balance
-- =====================================================================

create or replace function public.pay_gifting_balance(
    p_order_id bigint,
    p_razorpay_payment_id text,
    p_amount numeric
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_total numeric;
    v_paid numeric;
begin
    select o.total_amount into v_total
    from public.orders o
    join public.gifting_orders g on g.order_id = o.id
    where o.id = p_order_id and o.user_id = auth.uid();

    if v_total is null then
        raise exception 'Gifting order % not found', p_order_id;
    end if;

    select coalesce(sum(amount), 0) into v_paid
    from public.gifting_payments where order_id = p_order_id;

    if v_paid >= v_total - 0.005 then
        raise exception 'Gifting order % is already fully paid', p_order_id;
    end if;

    -- The balance is paid in one payment: it must match what's left.
    if abs(p_amount - (v_total - v_paid)) > 0.005 then
        raise exception 'Balance payment must be exactly % (got %)', v_total - v_paid, p_amount;
    end if;

    insert into public.gifting_payments (order_id, razorpay_payment_id, amount, kind)
    values (p_order_id, p_razorpay_payment_id, p_amount, 'balance');

    -- Only flip the initial status; if admin already moved it to PROCESSING, keep that.
    update public.orders set status = 'PAID'
    where id = p_order_id and status = 'ADVANCE_PAID';
end;
$$;

revoke all on function public.pay_gifting_balance(bigint, text, numeric) from public;
grant execute on function public.pay_gifting_balance(bigint, text, numeric) to authenticated;

-- =====================================================================
-- SECTION D — no dispatch until fully paid
-- =====================================================================

create or replace function public.block_unpaid_gifting_dispatch()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_paid numeric;
begin
    if new.status in ('SHIPPED', 'DELIVERED')
       and new.status is distinct from old.status
       and exists (select 1 from public.gifting_orders where order_id = new.id)
    then
        select coalesce(sum(amount), 0) into v_paid
        from public.gifting_payments where order_id = new.id;

        if v_paid < new.total_amount - 0.005 then
            raise exception 'Gifting order % has an unpaid balance of % and cannot be dispatched',
                new.id, round(new.total_amount - v_paid, 2);
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists block_unpaid_gifting_dispatch on public.orders;
create trigger block_unpaid_gifting_dispatch
    before update of status on public.orders
    for each row execute function public.block_unpaid_gifting_dispatch();

-- =====================================================================
-- SECTION E — advance-paid orders are booked work for the capacity planner
-- =====================================================================

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
    where o.status in ('ADVANCE_PAID', 'PAID', 'PROCESSING')
    group by g.ship_by_date
$$;
