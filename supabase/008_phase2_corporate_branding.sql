-- Phase 2, slice 4a: corporate gifting — logo upload, proofing, production gate.
-- Reference: app/src/main/java/com/example/vishnu/docs/Utensils_App_04_Phase1_Spec.md §03
--
-- Flow: the customer uploads a logo at checkout (corporate orders only) →
-- the admin team uploads a proof (v1, v2, ...) → the customer approves or
-- requests changes → a corporate order cannot move to PROCESSING / SHIPPED /
-- DELIVERED until its latest proof is approved. Wedding/event orders are
-- unaffected. Unapproved corporate orders still hold production capacity.
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after 007. Idempotent.

-- =====================================================================
-- SECTION A — gifting_orders: logo
-- =====================================================================

alter table public.gifting_orders
    add column if not exists logo_path text; -- path in the brand-assets bucket

alter table public.gifting_orders
    add column if not exists logo_notes text; -- e.g. "gold foil on the box lid"

alter table public.gifting_orders
    drop constraint if exists gifting_orders_corporate_logo_check;
alter table public.gifting_orders
    add constraint gifting_orders_corporate_logo_check
    check (occasion_type <> 'corporate' or logo_path is not null);

-- =====================================================================
-- SECTION B — brand_assets: proof versions (spec: status + version)
-- =====================================================================

create table if not exists public.brand_assets (
    id uuid primary key default gen_random_uuid(),
    order_id bigint not null references public.gifting_orders(order_id) on delete cascade,
    version integer not null check (version > 0),
    proof_path text not null, -- path in the brand-assets bucket
    status text not null default 'in_review'
        check (status in ('in_review', 'approved', 'revision_requested')),
    admin_note text,
    customer_comment text,
    created_at timestamptz not null default now(),
    decided_at timestamptz,
    unique (order_id, version)
);

alter table public.brand_assets enable row level security;

drop policy if exists "Users see proofs for their own orders, admin sees all" on public.brand_assets;
create policy "Users see proofs for their own orders, admin sees all"
    on public.brand_assets for select
    using (public.is_admin() or exists (
        select 1 from public.orders
        where orders.id = brand_assets.order_id and orders.user_id = auth.uid()
    ));

drop policy if exists "Only admin uploads proofs" on public.brand_assets;
create policy "Only admin uploads proofs"
    on public.brand_assets for insert
    with check (public.is_admin());
-- No UPDATE policy: customers decide through decide_brand_proof() below.

-- =====================================================================
-- SECTION C — customer decisions
-- =====================================================================

create or replace function public.decide_brand_proof(
    p_asset_id uuid,
    p_approve boolean,
    p_comment text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_order_id bigint;
    v_version integer;
    v_status text;
begin
    select b.order_id, b.version, b.status into v_order_id, v_version, v_status
    from public.brand_assets b
    join public.orders o on o.id = b.order_id
    where b.id = p_asset_id and o.user_id = auth.uid();

    if v_order_id is null then
        raise exception 'Proof not found';
    end if;
    if v_status <> 'in_review' then
        raise exception 'This proof has already been decided';
    end if;
    if exists (select 1 from public.brand_assets where order_id = v_order_id and version > v_version) then
        raise exception 'A newer proof exists for this order';
    end if;
    if not p_approve and coalesce(trim(p_comment), '') = '' then
        raise exception 'Say what should change';
    end if;

    update public.brand_assets
    set status = case when p_approve then 'approved' else 'revision_requested' end,
        customer_comment = nullif(trim(p_comment), ''),
        decided_at = now()
    where id = p_asset_id;
end;
$$;

revoke all on function public.decide_brand_proof(uuid, boolean, text) from public;
grant execute on function public.decide_brand_proof(uuid, boolean, text) to authenticated;

-- Replace the logo (e.g. wrong file) — only until a proof has been approved.
create or replace function public.replace_gifting_logo(
    p_order_id bigint,
    p_logo_path text,
    p_logo_notes text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not exists (
        select 1 from public.gifting_orders g
        join public.orders o on o.id = g.order_id
        where g.order_id = p_order_id and o.user_id = auth.uid() and g.occasion_type = 'corporate'
    ) then
        raise exception 'Corporate order % not found', p_order_id;
    end if;
    if exists (select 1 from public.brand_assets where order_id = p_order_id and status = 'approved') then
        raise exception 'The logo proof is already approved and can no longer be changed';
    end if;
    -- The file must be in the caller's own folder of the brand-assets bucket.
    if split_part(p_logo_path, '/', 1) <> auth.uid()::text then
        raise exception 'Invalid logo path';
    end if;

    update public.gifting_orders
    set logo_path = p_logo_path, logo_notes = nullif(trim(p_logo_notes), '')
    where order_id = p_order_id;
end;
$$;

revoke all on function public.replace_gifting_logo(bigint, text, text) from public;
grant execute on function public.replace_gifting_logo(bigint, text, text) to authenticated;

-- =====================================================================
-- SECTION D — no production before the proof is approved
-- =====================================================================

create or replace function public.block_unapproved_corporate_production()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.status in ('PROCESSING', 'SHIPPED', 'DELIVERED')
       and new.status is distinct from old.status
       and exists (
           select 1 from public.gifting_orders
           where order_id = new.id and occasion_type = 'corporate'
       )
       and not exists (
           select 1 from public.brand_assets
           where order_id = new.id and status = 'approved'
       )
    then
        raise exception 'Corporate order % is awaiting logo approval and cannot enter production', new.id;
    end if;
    return new;
end;
$$;

drop trigger if exists block_unapproved_corporate_production on public.orders;
create trigger block_unapproved_corporate_production
    before update of status on public.orders
    for each row execute function public.block_unapproved_corporate_production();

-- =====================================================================
-- SECTION E — private storage bucket for logos and proofs
-- =====================================================================
-- Layout: <customer user id>/<...>. Customers read/write only their own
-- folder; admins read everything and write proofs into the customer's folder.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('brand-assets', 'brand-assets', false, 5242880, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do nothing;

drop policy if exists "Brand assets: owner or admin can read" on storage.objects;
create policy "Brand assets: owner or admin can read"
    on storage.objects for select
    using (
        bucket_id = 'brand-assets'
        and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin())
    );

drop policy if exists "Brand assets: owner or admin can upload" on storage.objects;
create policy "Brand assets: owner or admin can upload"
    on storage.objects for insert
    with check (
        bucket_id = 'brand-assets'
        and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin())
    );
