-- Phase 2, slice 4b: corporate budget proposals + custom proposal requests.
-- Reference: app/src/main/java/com/example/vishnu/docs/Utensils_App_04_Phase1_Spec.md §03
--
-- The automatic proposal (packs within the budget per person) is computed in
-- the app from gift_packs. When nothing fits, the customer sends a custom
-- proposal request; the admin answers it by attaching a pack (typically a
-- hidden one made for that customer), which the customer can then open and
-- order even though it isn't publicly listed.
--
-- REVIEW BEFORE RUNNING. Run in the Supabase SQL editor after 008. Idempotent.

-- =====================================================================
-- SECTION A — custom proposal requests
-- =====================================================================

create table if not exists public.corporate_proposal_requests (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    budget_per_person numeric not null check (budget_per_person > 0),
    headcount integer not null check (headcount > 0),
    notes text not null,
    status text not null default 'open' check (status in ('open', 'proposed', 'closed')),
    proposed_pack_id uuid references public.gift_packs(id) on delete set null,
    admin_note text,
    created_at timestamptz not null default now(),
    responded_at timestamptz
);

alter table public.corporate_proposal_requests enable row level security;

drop policy if exists "Users see their own proposal requests, admin sees all" on public.corporate_proposal_requests;
create policy "Users see their own proposal requests, admin sees all"
    on public.corporate_proposal_requests for select
    using (user_id = auth.uid() or public.is_admin());

drop policy if exists "Users create open proposal requests for themselves" on public.corporate_proposal_requests;
create policy "Users create open proposal requests for themselves"
    on public.corporate_proposal_requests for insert
    with check (user_id = auth.uid() and status = 'open' and proposed_pack_id is null);

drop policy if exists "Only admin responds to proposal requests" on public.corporate_proposal_requests;
create policy "Only admin responds to proposal requests"
    on public.corporate_proposal_requests for update
    using (public.is_admin())
    with check (public.is_admin());

-- =====================================================================
-- SECTION B — who can see a gift pack
-- =====================================================================
-- Previously: active packs (signed-in users) or admin. Now also: a pack
-- attached as the answer to one of the caller's own proposal requests, even
-- if it's hidden. SECURITY DEFINER so the policies on gift_packs /
-- gift_pack_items don't query RLS-protected tables from inside themselves.

create or replace function public.can_view_gift_pack(p_pack_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.is_admin()
        or (auth.uid() is not null and exists (
            select 1 from public.gift_packs where id = p_pack_id and is_active
        ))
        or exists (
            select 1 from public.corporate_proposal_requests
            where proposed_pack_id = p_pack_id and user_id = auth.uid()
        )
$$;

revoke all on function public.can_view_gift_pack(uuid) from public;
grant execute on function public.can_view_gift_pack(uuid) to authenticated;

drop policy if exists "Active gift packs are viewable by signed-in users" on public.gift_packs;
drop policy if exists "Gift packs are viewable when active, proposed to you, or admin" on public.gift_packs;
create policy "Gift packs are viewable when active, proposed to you, or admin"
    on public.gift_packs for select
    using (public.can_view_gift_pack(id));

drop policy if exists "Gift pack items follow parent pack visibility" on public.gift_pack_items;
create policy "Gift pack items follow parent pack visibility"
    on public.gift_pack_items for select
    using (public.can_view_gift_pack(pack_id));

-- =====================================================================
-- SECTION C — remember the budget on corporate orders (used by reorder)
-- =====================================================================

alter table public.gifting_orders
    add column if not exists budget_per_person numeric check (budget_per_person is null or budget_per_person > 0);
