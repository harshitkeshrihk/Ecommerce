-- Phase 2, slice 4c: one-tap corporate reorder.
-- Reference: app/src/main/java/com/example/vishnu/docs/Utensils_App_04_Phase1_Spec.md §03
-- ("A repeat corporate order pre-fills the prior year's SKUs and branding
--   for one-click confirmation.")
--
-- A reorder is a normal new gifting order (today's prices, new payment, new
-- ship-by date) that remembers which order it repeats. If it uses the SAME
-- logo file and the SAME set of products as the original, the original's
-- approved proof is carried over, so it can go straight into production.
-- Otherwise it goes through proofing again.
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after 009. Idempotent.

alter table public.gifting_orders
    add column if not exists reorder_of bigint references public.gifting_orders(order_id) on delete set null;

create or replace function public.carry_over_approved_proof(
    p_new_order_id bigint,
    p_source_order_id bigint
)
returns boolean -- true if the approved proof was carried over
language plpgsql
security definer
set search_path = public
as $$
declare
    v_new public.gifting_orders%rowtype;
    v_source public.gifting_orders%rowtype;
    v_proof public.brand_assets%rowtype;
begin
    -- Both orders must be the caller's own corporate orders.
    select g.* into v_new
    from public.gifting_orders g join public.orders o on o.id = g.order_id
    where g.order_id = p_new_order_id and o.user_id = auth.uid() and g.occasion_type = 'corporate';

    select g.* into v_source
    from public.gifting_orders g join public.orders o on o.id = g.order_id
    where g.order_id = p_source_order_id and o.user_id = auth.uid() and g.occasion_type = 'corporate';

    if v_new.order_id is null or v_source.order_id is null then
        return false;
    end if;

    -- Only once, only for a fresh order, only if it's recorded as a repeat of the source.
    if v_new.reorder_of is distinct from p_source_order_id
       or exists (select 1 from public.brand_assets where order_id = p_new_order_id) then
        return false;
    end if;

    -- Same logo file ...
    if v_new.logo_path is distinct from v_source.logo_path then
        return false;
    end if;

    -- ... and the same set of products (quantities may differ).
    if (select array_agg(distinct x->>'product_id' order by x->>'product_id')
        from jsonb_array_elements(v_new.pack_contents) x)
       is distinct from
       (select array_agg(distinct x->>'product_id' order by x->>'product_id')
        from jsonb_array_elements(v_source.pack_contents) x)
    then
        return false;
    end if;

    select * into v_proof
    from public.brand_assets
    where order_id = p_source_order_id and status = 'approved'
    order by version desc
    limit 1;

    if v_proof.id is null then
        return false;
    end if;

    insert into public.brand_assets (order_id, version, proof_path, status, admin_note, customer_comment, decided_at)
    values (
        p_new_order_id, 1, v_proof.proof_path, 'approved',
        'Approved proof carried over from order #' || p_source_order_id,
        v_proof.customer_comment, now()
    );
    return true;
end;
$$;

revoke all on function public.carry_over_approved_proof(bigint, bigint) from public;
grant execute on function public.carry_over_approved_proof(bigint, bigint) to authenticated;
