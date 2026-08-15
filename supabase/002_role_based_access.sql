-- Phase 0: Role-based access control (retail / wholesale / distributor / admin).
--
-- REVIEW BEFORE RUNNING. This is a reference script, not an applied migration —
-- there is no supabase/ CLI project or tracked schema in this repo, so it hasn't
-- been run against your live database. Read it against your actual `profiles`,
-- `products`, and `orders` tables in the Supabase SQL editor before executing,
-- ideally with your technical analyst. It's written to be idempotent (safe to
-- re-run) but it does enable RLS on `products` and `orders` if not already on.
--
-- Context: the app previously granted "admin" purely client-side (a local
-- DataStore flag set once at login by matching the user's email against
-- stores.owner_email). Product writes (ProductRepository.upsertProduct) and
-- order-status updates (CartRepository/AdminRepository) had no server-side
-- ownership check at all — any authenticated user who guessed a storeId or
-- orderId could write to it. This script adds the missing DB-level checks.

-- 1. Add a role column to profiles. New rows default to 'retail', so the
--    existing (untracked) signup trigger needs no changes.
alter table public.profiles
    add column if not exists role text not null default 'retail';

alter table public.profiles
    drop constraint if exists profiles_role_check;
alter table public.profiles
    add constraint profiles_role_check check (role in ('retail', 'wholesale', 'distributor', 'admin'));

-- 2. Stop a user from granting themselves a role via the app's own profile
--    update call. ProfileRepository.updateProfile only ever sends
--    full_name/phone/address today, but this closes the door at the DB layer
--    too — only the service role (Supabase dashboard, or a trusted
--    server-side function) can change `role` going forward.
create or replace function public.prevent_role_self_update()
returns trigger as $$
begin
    if auth.role() <> 'service_role' and new.role is distinct from old.role then
        new.role := old.role;
    end if;
    return new;
end;
$$ language plpgsql security definer;

drop trigger if exists trg_prevent_role_self_update on public.profiles;
create trigger trg_prevent_role_self_update
    before update on public.profiles
    for each row execute function public.prevent_role_self_update();

-- 3. Helpers used by the policies below.
create or replace function public.is_admin()
returns boolean as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid() and role = 'admin'
    );
$$ language sql stable security definer;

create or replace function public.owns_store(target_store_id uuid)
returns boolean as $$
    select exists (
        select 1 from public.stores
        where id = target_store_id and owner_email = auth.email()
    );
$$ language sql stable security definer;

-- 4. Products: everyone can read; only the owning store or an admin can write.
alter table public.products enable row level security;

drop policy if exists "Products are viewable by everyone" on public.products;
create policy "Products are viewable by everyone"
    on public.products for select
    using (true);

drop policy if exists "Only store owner or admin can insert products" on public.products;
create policy "Only store owner or admin can insert products"
    on public.products for insert
    with check (public.owns_store(store_id) or public.is_admin());

drop policy if exists "Only store owner or admin can update products" on public.products;
create policy "Only store owner or admin can update products"
    on public.products for update
    using (public.owns_store(store_id) or public.is_admin());

drop policy if exists "Only store owner or admin can delete products" on public.products;
create policy "Only store owner or admin can delete products"
    on public.products for delete
    using (public.owns_store(store_id) or public.is_admin());

-- 5. Orders: a customer can see their own orders; the owning store or an
--    admin can see and update any order against that store (status changes).
alter table public.orders enable row level security;

drop policy if exists "Users and store owners can view relevant orders" on public.orders;
create policy "Users and store owners can view relevant orders"
    on public.orders for select
    using (user_id = auth.uid() or public.owns_store(store_id) or public.is_admin());

drop policy if exists "Users can create their own orders" on public.orders;
create policy "Users can create their own orders"
    on public.orders for insert
    with check (user_id = auth.uid());

drop policy if exists "Only store owner or admin can update orders" on public.orders;
create policy "Only store owner or admin can update orders"
    on public.orders for update
    using (public.owns_store(store_id) or public.is_admin());

-- 6. To promote an existing user to a role, run (as the project owner, in the
--    SQL editor, not from the app):
--    update public.profiles set role = 'admin' where id = '<user-uuid>';
