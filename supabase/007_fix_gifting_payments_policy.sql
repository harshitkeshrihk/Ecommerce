-- Fix for 006_phase2_gifting_payments.sql.
--
-- BUG: the INSERT policy on gifting_payments checked "no payment exists yet for
-- this order" with a subquery on gifting_payments itself. Postgres applies the
-- table's RLS to that subquery too, which recursed:
--   ERROR: infinite recursion detected in policy for relation "gifting_payments"
-- Every advance/full payment insert failed AFTER Razorpay had charged the
-- customer, leaving the order with no payment recorded (paid = 0).
--
-- FIX: do the lookup in a SECURITY DEFINER function, which bypasses RLS, so
-- the policy no longer queries its own table.
--
-- Run in the Supabase SQL editor after 006. Idempotent.

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

-- =====================================================================
-- REPAIR — orders placed while the bug was live
-- =====================================================================
-- Find gifting orders with no payment recorded:
--
--   select o.id, o.payment_id, o.total_amount, o.status, o.created_at
--   from public.orders o
--   join public.gifting_orders g on g.order_id = o.id
--   where not exists (select 1 from public.gifting_payments p where p.order_id = o.id);
--
-- For each one, look up o.payment_id in the Razorpay dashboard and record the
-- amount that was ACTUALLY captured (replace the values below):
--
--   insert into public.gifting_payments (order_id, razorpay_payment_id, amount, kind)
--   values (<order id>, '<payment id>', <captured amount in rupees>, 'advance');  -- or 'full'
--
-- If the captured amount equals the order total, also mark it paid:
--   update public.orders set status = 'PAID' where id = <order id> and status = 'ADVANCE_PAID';
