*Utensils App — Initial Phases Implementation Specification (Doc 4, unified)*

**INTERNAL PLANNING DOCUMENT · DRAFT FOR REVIEW · DOCUMENT 4**

**Utensils, Crockery & Household Appliances App**

**Initial Phases Roadmap — Wholesale → Bulk Gifting → Premium Retail**

Synthesizes Document 2 (Implementation Specification, Phases 0–6) and Document 3 (Marketplace Addendum) against the actual state of the codebase, then reorders scope around the three verticals confirmed as the business priority for the initial build: (1) wholesale ordering, (2) bulk gifting — corporate, wedding, and event, and (3) retail ordering of premium/costly items. HoReCa, institutional accounts, marketplace/retailer-fulfillment matching, and the trust/ops layer are confirmed out of scope for the initial build and pushed later.

*Prepared: 21 Sep 2026 · Companion to: Doc 1 (Flow Diagrams), Doc 2 (Implementation Spec), Doc 3 (Marketplace Addendum)*

*For review by: Technical Analyst & Business Analyst*

# **00 · Scope Decisions Confirmed With Stakeholder**

Two forks in Doc 2/3 that materially change build effort were resolved directly rather than assumed:

- **Premium retail = curated storefront, not marketplace matching.** Doc 3's addendum redefines "Phase 4" as matching a customer's cart to nearby retailers (with warehouse fallback). That's a heavier build (retailer accounts, geolocation, a matching engine) and depends on wholesale retailer accounts existing first. The confirmed priority is Doc 2's *original* Phase 4 instead: a curated catalog of high-value items, fulfilled by the warehouse only. Marketplace matching is deferred indefinitely — see §05.
- **Wholesale billing stays offline for the initial build.** Doc 3's "Lite" descope (digital ordering + MOQ pricing + RFQ now; credit ledger and GST invoicing later, as Phase 1B) is confirmed. Full ledger + invoicing would add 3–4 weeks before wholesale ships at all, for a problem (offline billing) that isn't yet a bottleneck.

Everything below is scoped against those two decisions.

# **01 · Current State Audit**

Unchanged from the prior pass of this document — reconfirmed against the same source tree, no re-derivation needed here. Summary of what's actually built today:

|**Area**|**State**|**Evidence**|
| :- | :- | :- |
|Auth / signup|Email/password only. No role, GSTIN, or business info collected.|`AuthRepository.kt`, `LoginScreen.kt`|
|Role / RBAC|4-value enum (`RETAIL`, `WHOLESALE`, `DISTRIBUTOR`, `ADMIN`) exists; only `ADMIN` is gated anywhere. `WHOLESALE`/`DISTRIBUTOR` have no screens or logic.|`UserRole.kt`, `MainActivity.kt:219,245`|
|Pricing|`Product.priceWholesale` exists but is dead — checkout always uses `priceRetail`. No MOQ slabs, no tiers.|`Product.kt:19-20`, `CartRepository.kt:201`|
|Orders|Flat model: one payment method, one status string, no `channel`, no gifting/occasion fields, no HSN/tax fields.|`Orders.kt`|
|Legacy `store_id`|Still threaded through `Product`, `Order(Item)Request`, `AdminRepository` (`stores` table) despite multi-store *cart* logic being removed.|`Product.kt:41-42`, `AdminRepository.kt:16-58`|
|Tests / observability|Only Android-Studio boilerplate tests; no crash reporting or analytics dependency.|`app/src/test`, `app/src/androidTest`, `app/build.gradle.kts`|
|Catalog|Single flat catalog (`category`/`subcategory` fields exist), no curated subset, no wishlist, no delivery-serviceability check.|`Product.kt`, `screens/CatalogScreen.kt`|
|Media|Media3/ExoPlayer video support already present on `Product.videoUrl`.|`Product.kt:22-23`, `di/VideoModule.kt`|

**Read-through:** none of the three priority verticals have any working code today beyond the generic retail cart/checkout/order path. Wholesale is the correct first phase because Phase 2 (gifting) and Phase 3 (premium retail) both reuse its role/KYC and order-model groundwork — building gifting or premium retail first would mean redoing that foundation work under a different name.

# **02 · PHASE 1 — Wholesale & Distributor Core (Lite)**   *· 4–6 weeks*

Unchanged from the previous version of this spec — reproduced here for a single-document view of the full initial roadmap.

**Functional Requirements**

- Signup role selection (Retail vs Wholesale/Distributor) + business KYC form (GSTIN, business name) for non-retail roles.
- Admin KYC verification queue (approve → `kyc_status = verified`; reject → explicit reason).
- Automated tests: cart, checkout, order creation — the regression baseline every later phase (including 2 and 3 below) must not break.
- Crash reporting + usage analytics integration.
- `resolvePricing` (MOQ-slab resolution; contract-pricing/quote branches from later phases omitted, not stubbed).
- `pricing_tier_id` on `profiles`, `moq_slabs` table, tier assignment at KYC approval.
- Quick-Order Pad (SKU + qty grid, live price via `resolvePricing`).
- RFQ submission + admin quote pipeline (submit → quote → accept → order, price locked, no re-entry).
- Plain (non-tax) order summary for print/WhatsApp — not represented as an invoice.
- ~~Remove legacy `store_id`/`stores` dependencies from `AdminRepository`, `Product`, `Order(Item)Request`.~~ **Revised during implementation** — see note below.

**Business Rules**

- A non-retail signup cannot place an order without admin KYC approval.
- Every cart (Quick-Order Pad or RFQ) prices through `resolvePricing` — never a hardcoded field.
- An accepted RFQ quote overrides the MOQ slab price once locked onto an order.
- No credit/ledger check runs — orders confirm on placement.

**Screens / Components**

Signup Role Selection · Business KYC Form · Admin KYC Verification Queue · Quick-Order Pad · RFQ Submission Form · Admin Quote Pipeline board · Order Summary (print/WhatsApp)

**Acceptance Criteria**

- A rejected KYC submission returns an explicit reason.
- A wholesale/distributor user cannot order pre-approval; retail is unaffected.
- Cart/checkout/order-creation test suite exists and passes.
- MOQ-slab pricing is correct at every quantity breakpoint; RFQ acceptance converts to an order with no manual re-entry.
- No `credit_ledger`, `ledger_transactions`, or `invoices` table is required.
- ~~No route or query depends on the `stores` table or a `store_id`.~~ **Superseded** — `store_id` turned out not to be legacy: it's read by a separate delivery-partner app (its own Supabase RLS policies, e.g. "Partners can view assigned orders," live outside this repo). `store_id` keeps being written on every new product/order so that app keeps working. What *did* ship: `AuthViewModel`'s admin-access check no longer grants ADMIN from "owns a `stores` row" — it's `profiles.role == 'admin'` only. `AdminRepository`/`AdminViewModel`/`AddEditProductViewModel` still look up "my store" to attach `store_id` on writes, which is a data-scoping concern for the partner app, not an authorization mechanism, so that code is intentionally untouched. Full retirement of `store_id` would require migrating the delivery-partner app too — out of this app's scope.

**Data model additions:** `profiles.pricing_tier_id/gstin/business_name/kyc_status`, `products.unit_of_measure`, `moq_slabs`, `rfqs`/`rfq_items`/`quotes`/`quote_items`, `orders.channel` (retail \| wholesale), `store_id` removed from `Order(Item)Request`.

*Correction found during testing:* the original design put `price_per_unit` directly on `quotes` — one price for the entire RFQ. That breaks the moment an RFQ spans more than one distinct product (e.g. Thalis + Copper Jugs quoted at a single blended rate makes no sense). Fixed by moving price to a new `quote_items` table, one row per `rfq_item`; `quotes` is now just the round (version + terms). Admin Quote Pipeline and the customer RFQ view both price/display per line, not per request.

**API contracts:** `resolvePricing`, `submitRfq`, `respondToQuote`, `acceptQuote`/`convertQuoteToOrder`, `approveKyc`/`rejectKyc`, `generateOrderSummary`.

# **03 · PHASE 2 — Bulk Gifting Vertical (Wedding · Corporate · Event)**   *· 5–6 weeks · depends on Phase 1*

*Purpose: one gifting engine — pack builder, engraving/personalization, occasion-aware production scheduling — serving three occasions instead of building wedding and corporate as separate features. Corporate additionally gets branding and account-manager reorder; event gifting is the lean case (bulk pack + personalization, no branding, no installment).*

**Why event gifting is new relative to Doc 2:** Doc 2 only specced wedding and corporate. "Event" (festivals, functions, bulk institutional-style gifting outside a corporate account) is added here as a third `occasion_type`, scoped to reuse the generic bulk-pack builder — it does not get its own screens.

**Functional Requirements**

- Gifting collection browse: curated SKUs suited for return-gifts/hampers (reuses `category_tags`).
- Bulk Pack Builder: pick a base pack + quantity + budget tier — shared across all three occasion types.
- Engraving/personalization capture (name, initials, date) — available for wedding and event; optional for corporate.
- Occasion type on the order: `wedding` \| `corporate` \| `event`.
- Wedding-specific: installment/advance-booking schedule (a fixed payment schedule with due dates, collected via repeated Razorpay charges — **not** the deferred wholesale credit ledger; no credit-limit logic involved).
- Corporate-specific: budget-tier gift builder with SKU proposal; brand-asset upload, placement preview, and approval workflow; one-click annual reorder tied to an account manager.
- Deadline-aware production calendar in admin, checked against `ship_by_date` at order entry for all three occasion types.

**Business Rules**

- A brand asset (corporate only) flags an order line "ready for production" only once `status = approved`; a revision request creates a new version, prior versions retained.
- Festive/event/wedding orders are checked against `ship_by_date` at entry time, not at fulfillment time — the admin calendar surfaces a capacity conflict before the ship-by date is missed, for all three occasion types.
- A corporate order cannot enter production without an approved brand asset on file; wedding and event orders have no such gate (no branding step for them).
- A repeat corporate order pre-fills the prior year's SKU list and branding for one-click confirmation.

**Screens / Components**

Gifting Collection browse · Bulk Pack Builder (shared) · Engraving/Personalization capture · Wedding Installment Schedule view · Corporate Budget-Tier Builder · Brand Asset Upload & Proofing screen · Admin Production Calendar

**Acceptance Criteria**

- A corporate order cannot enter production without an approved brand asset; wedding/event orders can, with no branding step blocking them.
- The production calendar surfaces a capacity conflict before, not after, a ship-by date is missed — verified for all three occasion types.
- A repeat corporate order pre-fills the prior year's SKUs and branding for one-click confirmation.
- An event-gifting order can be placed end-to-end (browse → pack → personalize → checkout) with zero corporate-only screens surfaced.

**Data model additions:** `gifting_orders` (`occasion_type`: wedding \| corporate \| event; `installment_schedule_json` nullable, wedding-only in practice), `brand_assets` (`status`, `version` — populated for corporate only), `products.category_tags`.

**API contracts:** `uploadBrandAsset`/`requestProofing`, `approveBrandAsset`/`requestRevision`, `checkProductionCapacity(ship_by_date, qty)` → ok/conflict (new — makes the "surface conflict before it's missed" acceptance criterion concrete rather than implied).

# **04 · PHASE 3 — Premium Retail Storefront**   *· 2–3 weeks · depends on Phase 0/1 foundation only*

*Curated catalog of high-value items, fulfilled by the existing warehouse — no retailer network, no geolocation matching. This is Doc 2's original Phase 4 scope; Doc 3's retailer-matching redefinition of "Phase 4" is not part of this build (see §05).*

**Functional Requirements**

- Curated storefront: a filtered view of premium/high-value SKUs, separate from the general retail catalog.
- Delivery-serviceability check at checkout: pincode allowlist (not lat/long radius — no geocoding needed for a single warehouse origin).
- Wishlist, persisted per logged-in user.
- Product video detail view (already supported by `Product.videoUrl`/Media3 — just needs to be surfaced prominently on premium listings).

**Business Rules**

- The premium catalog is a curated subset (`is_curated_retail = true`), not a re-skin of the full catalog.
- Checkout blocks or flags an out-of-serviceability-area order at checkout, not after payment.

**Screens / Components**

Premium Storefront browse · Delivery Serviceability check (at checkout) · Wishlist · Product Video detail view

**Acceptance Criteria**

- An out-of-serviceability pincode is caught at checkout, before payment.
- Wishlist persists across sessions for a logged-in user.
- A premium listing plays its product video without additional integration work (confirms existing Media3 support is sufficient).

**Data model additions:** `products.is_curated_retail` (boolean), `wishlist_items` (`user_id`, `product_id`), `serviceable_pincodes` (`pincode`, `deliverable`).

**API contracts:** `checkServiceability(pincode)` → serviceable (bool); wishlist add/remove is plain RLS-scoped table access, no edge function needed.

# **05 · Explicitly Deferred (confirmed out of initial scope)**

|**Deferred item**|**Original phase**|**Why not in the initial build**|
| :- | :- | :- |
|Credit ledger (khata), `checkCreditAvailability`, GST-compliant invoicing|1B|Confirmed: offline billing stays as-is until it's an actual bottleneck|
|Marketplace discovery & retailer fulfillment (`retailer_storefronts`, `retailer_inventory`, `fulfillment_assignment`, `findFullCartRetailers`)|Doc 3's redefined Phase 4|Confirmed: premium retail ships as a curated warehouse-fulfilled storefront instead; matching engine + retailer network is a separate, later decision|
|Lat/long/service-radius capture at signup|Doc 3 Phase 0 addendum|Only needed by marketplace matching, which is deferred — no reason to add the fields or the UI friction now|
|HoReCa & Institutional Accounts (standing orders, contract pricing, tenders, compliance vault)|3|Not one of the three confirmed priority verticals|
|Trust & Operations Layer (WhatsApp/push notifications, loyalty/rebate, low-stock alerts, BNPL)|5|Needs Phase 1–3 order events to notify on; revisit once those ship|
|Scale & Hardening (load testing, courier integration, forecasting, security review)|6|Post-launch, ongoing|

# **06 · Rollout**

|**#**|**Scope**|**Duration**|**Depends on**|
| :- | :- | :- | :- |
|1|Foundation completion + Wholesale & Distributor Core (Lite)|4–6 wks|—|
|2|Bulk Gifting Vertical (Wedding · Corporate · Event)|5–6 wks|Phase 1 (roles/KYC, order model)|
|3|Premium Retail Storefront|2–3 wks|Phase 1 foundation only — can run in parallel with Phase 2 on a second developer, since neither depends on the other|
|1B|Digital Ledger & GST Invoicing (deferred, not removed)|3–4 wks|Phase 1; scheduled when offline billing becomes the bottleneck|
|—|HoReCa/Institutional, Marketplace Matching, Trust & Ops, Scale|—|Out of initial scope — revisit after Phases 1–3 ship, per §05|

Test coverage added in Phase 1 (§02) remains the regression baseline for Phases 2 and 3.

# **07 · Implementation & Testing Status**

Phase 1 is implemented and has been manually tested end-to-end on a live rebuilt database: KYC application → admin approval → Quick-Order Pad with MOQ-slab pricing → RFQ submission → admin per-line quoting → accept-to-order → retail catalog/cart/Razorpay checkout regression → admin product form (wholesale price, unit of measure, MOQ slab editor). All confirmed working.

Bugs found and fixed during that testing pass, for the record:
- `KycSubmissionRequest`/`KycApprovalRequest`/`KycRejectionRequest` silently dropped their `kyc_status` field from outgoing writes — kotlinx.serialization omits any field equal to its declared Kotlin default when `encodeDefaults` isn't set, and `"pending"`/`"verified"`/`"rejected"` were exactly those defaults. Fixed by making the field required at every call site instead of defaulted.
- `profiles`' pre-existing RLS (predating this session) only allowed `auth.uid() = id` — an admin couldn't see or update anyone else's row, which silently emptied the KYC queue. Added additive admin-scoped SELECT/UPDATE policies.
- `QuickOrderViewModel`'s per-line price resolution had a race condition (overlapping, uncancelled network calls on rapid qty changes) and `totalAmount` was a plain property read outside Compose's snapshot system, so it could lag one recomposition behind the displayed price. Both fixed.
- `quotes.price_per_unit` was one value for the whole RFQ, which breaks for any RFQ spanning more than one product. Restructured to a `quote_items` child table, one price per RFQ line.
- The admin product form had `unit_of_measure` state with no UI control to actually change it.

Not yet done: crash reporting/analytics (needs a real Firebase project — none exists in this repo, see Doc 4 original scope note) and an automated regression suite for cart/checkout/order-creation beyond the pure-logic unit tests already in `app/src/test` (`PricingResolverTest`, `OrderSummaryTest`) — `CartRepository` has no seam for mocking Postgrest yet.
