*Utensils App — Implementation Specification (Doc 2 of 2)*

**INTERNAL PLANNING DOCUMENT · DRAFT FOR REVIEW · DOCUMENT 2 OF 2**

**Utensils, Crockery & Household Appliances App**

**Implementation Specification**

Companion to Document 1 (Flow Diagram Specification) and the Phased Build Plan (Rev. 2). This document is intentionally sequenced bottom-up: it starts at the data/API layer, moves up to per-phase module specs, and ends with system architecture and rollout — so nothing is designed top-down before the underlying data model is settled.

*Prepared: 19 Aug 2026   ·   Companion to: Flow Diagram Specification, Phased Build Plan Rev. 2*

*For review by: Technical Analyst & Business Analyst*
# **How to read this document**
- LOW LEVEL (§1) — data model (tables/fields), API & Edge Function contracts, and core pricing/credit/tax algorithms. Build this first — every phase depends on it.
- MID LEVEL (§2) — per-phase module specs: functional requirements, business rules, screens, and acceptance criteria, matching the seven phases in the Build Plan.
- HIGH LEVEL (§3) — system architecture, tech stack, non-functional requirements, environments, and the rollout timeline.


# **01 · Low-Level Detailing**
**LOW LEVEL**  *Data model, API/Edge Function contracts, and core algorithms — the layer every phase and every diagram in Document 1 is built on.*

**1.1 Data Model**

Tables are grouped by domain. Existing Product.kt / Supabase fields are marked (kept); all others are net-new for this plan. Field lists are representative of the key columns needed to support the flows in Document 1, not an exhaustive DDL.

**users**

*Extends Supabase Auth; role drives RLS policy and which of the 5 app channels is presented.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|id, auth\_uid|uuid|Primary key; FK to Supabase Auth user|
|role|enum|retail | wholesale | distributor | horeca | institutional | admin|
|pricing\_tier\_id|uuid, FK|Null for retail; required for business roles|
|gstin, business\_name|text|Required for all business roles; validated at signup (Flow L1)|
|kyc\_status|enum|pending | verified | rejected|
|credit\_limit\_override|numeric, nullable|Admin-set exception, see credit\_ledger|

**products**

*Extends existing Product.kt — gauge/weight/priceRetail/priceWholesale kept as-is.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|id, sku|uuid, text|Kept|
|material, gauge, weight|text/numeric|Kept from existing model|
|price\_retail, price\_wholesale|numeric|Kept; wholesale price is the base, overridden by moq\_slabs|
|hsn\_code|text|NEW — required for GST invoicing (Flow L5); must exist before Phase 1 invoicing is built|
|unit\_of\_measure|enum|NEW — piece | dozen | kg | set|
|category\_tags|text[]|NEW — supports institutional kit bundling (Phase 3)|

**moq\_slabs**

*One-to-many with products; resolved by the pricing engine at cart time.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|id, product\_id|uuid, FK||
|min\_qty|integer|Slab breakpoint, e.g. 10 / 50 / 200|
|price\_per\_unit|numeric|Overrides price\_wholesale when qty ≥ min\_qty|

**orders / order\_items**

*Central order record; channel field is what routes admin queue (Flow M4).*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|id, order\_number|uuid, text|order\_number is human-readable, sequential|
|channel|enum|retail | wholesale | gifting\_wedding | gifting\_corporate | horeca | institutional|
|status|enum|pending | held | confirmed | in\_production | shipped | delivered | cancelled|
|payment\_method|enum|razorpay | khata | contract\_pricing|
|ship\_by\_date|date, nullable|Drives the deadline production calendar (Flow M2, L4)|
|order\_items: hsn\_code, tax\_rate, line\_total|text/numeric|Snapshotted at order time, not looked up later (Flow L5)|



**rfqs / rfq\_items / quotes**

*Backs the Request-a-Quote pipeline (Flow L2).*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|rfqs.status|enum|open | quoted | negotiating | won | lost|
|rfqs.needed\_by\_date|date|Surfaced in admin quote pipeline for prioritization|
|quotes.version|integer|Increments on each counter-offer round|
|quotes.price\_per\_unit, terms|numeric, text|Locked onto the order on acceptance, overriding moq\_slabs|

**credit\_ledger / ledger\_transactions**

*Backs the khata flow (Flow L3); balance is always derived, never stored as a single mutable field.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|credit\_ledger.credit\_limit|numeric|Set at role/tier approval, editable by admin|
|ledger\_transactions.type|enum|debit (order) | credit (payment)|
|ledger\_transactions.due\_date|date|Set on debit entries from the tier's credit period|
|ledger\_transactions.is\_overdue|computed|due\_date < now() AND running balance > 0|

**invoices**

*One per order; PDF generated server-side (Flow L5).*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|invoice\_number|text|Sequential, GST-compliant numbering|
|pdf\_url|text|Supabase Storage path|
|gst\_breakdown\_json|jsonb|Per-line CGST/SGST or IGST split, for filing export|

**gifting\_orders / brand\_assets**

*Extends orders for Phase 2; brand\_assets only populated for gifting\_corporate.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|gifting\_orders.occasion\_type|enum|wedding | corporate|
|gifting\_orders.installment\_schedule\_json|jsonb|Wedding advance-booking schedule|
|brand\_assets.status|enum|uploaded | in\_review | approved | rejected (Flow L4)|
|brand\_assets.version|integer|Increments on each revision loop|

**standing\_orders / contract\_pricing**

*Backs the HoReCa vertical (Phase 3A).*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|standing\_orders.template\_json|jsonb|Saved SKU + quantity list|
|standing\_orders.last\_triggered\_at|timestamp|For admin visibility into reorder cadence|
|contract\_pricing.locked\_price, valid\_from/to|numeric, date|Annual contract rate, distinct from moq\_slabs|

**tenders / compliance\_documents**

*Backs Institutional & Tender Sales (Phase 3B).*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|tenders.source|enum|GeM | state\_portal | direct\_institutional|
|tenders.status|enum|open | bid\_submitted | won | lost|
|tenders.linked\_order\_id|uuid, nullable|Set once a won tender converts to an order|
|compliance\_documents.doc\_type, expiry\_date|text, date|Quality certs, past-performance record, kept ready ahead of bids|



**1.2 API & Edge Function Contracts**

Supabase Postgres tables are accessed via row-level security (RLS) policies scoped to the authenticated user's role; the contracts below cover the logic that cannot be pure client-side table access — pricing resolution, credit checks, PDF generation, and third-party integrations — implemented as Supabase Edge Functions.

|**Function**|**Trigger / Input**|**Output**|**Notes**|
| :- | :- | :- | :- |
|resolvePricing|product\_id, qty, user\_id|unit\_price, source (moq\_slab | contract\_pricing | quote)|Single source of truth for price — called from cart, quick-order pad, and RFQ acceptance|
|checkCreditAvailability|user\_id, order\_total|approved (bool), available\_credit|Called at checkout when payment\_method = khata (Flow L3)|
|submitRfq|user\_id, items[], needed\_by\_date, notes|rfq\_id, status=open|Creates rfq + rfq\_items|
|respondToQuote|rfq\_id, admin\_id, price\_per\_unit, terms|quote\_id, rfq.status=quoted|Admin-only; increments quote version on re-quote|
|acceptQuote / convertQuoteToOrder|quote\_id|order\_id|Locks negotiated price onto new order (Flow L2)|
|generateInvoice|order\_id|invoice\_id, pdf\_url|Server-side PDF; resolves HSN + intra/inter-state tax split (Flow L5)|
|uploadBrandAsset / requestProofing|order\_item\_id, file|brand\_asset\_id, status=in\_review|Validates file type/size before creating preview mockup (Flow L4)|
|approveBrandAsset / requestRevision|brand\_asset\_id, decision|status updated|Approval unlocks production-calendar entry|
|triggerStandingOrder|standing\_order\_id, qty\_overrides?|order\_id|One-tap HoReCa reorder from saved template|
|flagTender / recordBid|tender\_id or new tender data|tender\_id, status|Populates admin tender-tracking queue (Phase 3B)|
|sendNotification|user\_id, channel, template, payload|notification\_id, status|Fans out to WhatsApp Business API and/or FCM|



**1.3 Core Algorithms**

Pseudocode for the three logic pieces most likely to be mis-implemented if left to a plain description — pulled directly from Flow Diagrams M1, L3, and L5.

**Pricing resolution (resolvePricing) — Flow M1 / L2**

if activeQuote exists for (user, product):\
`    `return activeQuote.price\_per\_unit          # RFQ overrides everything\
elif contractPricing exists for (user, product) and is valid\_now:\
`    `return contractPricing.locked\_price         # HoReCa annual contract\
else:\
`    `slab = highest moq\_slab where min\_qty <= requested\_qty\
`    `return slab.price\_per\_unit if slab else product.price\_wholesale

**Credit availability check (checkCreditAvailability) — Flow L3**

balance = SUM(ledger\_transactions.amount\
`              `WHERE type='debit') - SUM(... WHERE type='credit')\
available\_credit = credit\_ledger.credit\_limit - balance\
if order\_total <= available\_credit:\
`    `approve()                                   # proceed to checkout\
else:\
`    `hold\_order()                                # route to admin override queue

**GST tax split (generateInvoice) — Flow L5**

for each order\_item:\
`    `rate = gstRateFor(item.hsn\_code)\
`    `if buyer.state == seller.state:\
`        `cgst = rate/2 \* item.taxable\_value\
`        `sgst = rate/2 \* item.taxable\_value\
`    `else:\
`        `igst = rate \* item.taxable\_value\
invoice.total = SUM(taxable\_value) + SUM(cgst+sgst or igst)


# **02 · Mid-Level Detailing**
**MID LEVEL**  *Per-phase module specifications — functional requirements, business rules, screens, and acceptance criteria — built on the data model and APIs defined in §1.*

**PHASE 0   Foundation Cleanup & Fixes**   *·   1–2 weeks*

**Functional Requirements**

- Remove multi-store cart-conflict logic from CartRepository.
- Move Razorpay key to secure config; add production-key switch.
- Implement role enum (6 roles) replacing “store owner = admin”.
- Add GSTIN/business-license capture and verification queue at signup.
- Add automated tests for cart, checkout, order creation.
- Integrate crash reporting and usage analytics.

**Business Rules**

- A non-retail signup cannot reach an active state without either auto-verification or admin approval (Flow L1).
- Role assignment is the sole gate for RLS policy application — no feature elsewhere reads a role that isn’t RLS-enforced.

**Screens / Components**

Signup/Role selection screen  ·  Business KYC form  ·  Admin verification queue  ·  Secure config module

**Acceptance Criteria**

- A rejected KYC submission returns an explicit, actionable error, not a silent failure.
- Existing cart/checkout/order tests pass after multi-store logic removal.
- Razorpay key no longer appears in source control history going forward.

**PHASE 1   Wholesale & Distributor Core**   *·   3–4 weeks*

**Functional Requirements**

- Build MOQ-tiered pricing engine (resolvePricing).
- Build Distributor/Wholesale account type with tier assignment.
- Build Quick-Order Pad (SKU + qty grid).
- Build credit ledger (khata) with balance, due dates, partial payments.
- Build GST-compliant PDF invoicing with HSN/SAC per line.
- Build Request-a-Quote (RFQ) flow and admin quote pipeline.

**Business Rules**

- Every cart, regardless of entry path (quick-order pad or RFQ), passes through checkCreditAvailability before checkout (Flow M1).
- A negotiated quote price always overrides the MOQ slab price once locked onto an order (Flow L2).
- Ledger balance is always computed from ledger\_transactions, never stored as a mutable running total (Flow L3).

**Screens / Components**

Quick-Order Pad  ·  RFQ submission form  ·  Admin Quote Pipeline board  ·  Ledger / Khata view  ·  Invoice PDF viewer

**Acceptance Criteria**

- A distributor placing an order within their credit limit is never blocked.
- An order exceeding credit limit is held, not silently rejected or silently approved.
- Every invoice generated includes HSN code and correct CGST/SGST or IGST split.
- An accepted RFQ quote converts to an order without manual re-entry of line items.

**PHASE 2   Wedding & Corporate Gifting Vertical**   *·   5–6 weeks*

**Functional Requirements**

- 2A Build Wedding/Shagun collection, bulk return-gift pack flow, engraving capture, installment booking.
- 2B Build Corporate budget-tier gift builder with SKU proposal.
- 2B Build brand-asset upload, placement preview, and approval workflow.
- 2B Build deadline-aware production calendar in admin.
- 2B Build one-click annual reorder tied to an account manager.

**Business Rules**

- A brand asset only flags an order line “ready for production” once status = approved (Flow L4).
- A revision request always loops back to a new upload version — prior versions are retained, not overwritten.
- Festive orders entering the production calendar are checked against ship\_by\_date at entry time, not at fulfillment time.

**Screens / Components**

Wedding Collection browse  ·  Bulk Pack Builder  ·  Corporate Budget-Tier Builder  ·  Brand Asset Upload & Proofing screen  ·  Admin Production Calendar  ·  Installment Schedule view

**Acceptance Criteria**

- A corporate order cannot enter production without an approved brand asset on file.
- The production calendar surfaces a capacity conflict before, not after, a ship-by date is missed.
- A repeat corporate order pre-fills the prior year’s SKU list and branding for one-click confirmation.

**PHASE 3   HoReCa & Institutional Accounts**   *·   3–4 weeks*

**Functional Requirements**

- 3A Build HoReCa account type with catering-specific SKU catalog.
- 3A Build standing/recurring order templates with one-tap re-trigger.
- 3A Build annual contract-pricing object distinct from MOQ slabs.
- 3B Build tender-tracking module (open/bid/won/lost).
- 3B Build compliance-document vault.
- 3B Build pre-bundled institutional kit SKUs.

**Business Rules**

- contract\_pricing, when valid\_now, overrides moq\_slabs for that user/product (Flow M1 pricing precedence).
- A tender cannot move to bid\_submitted without at least one non-expired compliance document on file.
- A lost tender is archived with a reason, remaining visible in the pipeline for forecasting, not deleted.

**Screens / Components**

HoReCa Account dashboard  ·  Standing Order template editor  ·  Contract Pricing admin view  ·  Tender Tracking board  ·  Compliance Document vault  ·  Institutional Kit catalog entries

**Acceptance Criteria**

- A saved standing order can be re-triggered with adjusted quantities in one tap.
- A won tender converts directly into a bulk order record without manual data re-entry.
- Institutional kit SKUs appear as single catalog entries, not assembled manually per order.



**PHASE 4   Premium Retail Layer**   *·   2–3 weeks*

**Functional Requirements**

- Build curated small-SKU storefront, separate from wholesale catalog.
- Build pincode/delivery-radius serviceability check at checkout.
- Build wishlist.
- Extend existing Media3/ExoPlayer video support for material/finish detail.

**Business Rules**

- Retail catalog is a curated subset, not the full wholesale SKU list.
- Checkout blocks or flags orders outside the serviceable delivery radius rather than accepting and failing at fulfillment.

**Screens / Components**

Retail Storefront  ·  Delivery Serviceability check  ·  Wishlist  ·  Product Video detail view

**Acceptance Criteria**

- An out-of-radius pincode is caught at checkout, not after payment.
- Wishlist persists across sessions for a logged-in retail user.

**PHASE 5   Trust & Operations Layer**   *·   2–3 weeks*

**Functional Requirements**

- Build WhatsApp order confirmation and support integration.
- Build push notifications for order status and festive offers.
- Build distributor volume loyalty/rebate program.
- Build low-stock alerts tuned for breakage-prone inventory.
- Evaluate and, if selected, integrate a trade-credit/BNPL partner alongside the internal khata ledger.

**Business Rules**

- sendNotification fans out to WhatsApp and/or push depending on user preference and event type.
- Low-stock thresholds are configurable per SKU, not a single global threshold, to account for breakage-prone categories.

**Screens / Components**

WhatsApp integration settings  ·  Notification preferences  ·  Loyalty/Rebate dashboard  ·  Low-stock alert admin view

**Acceptance Criteria**

- An order status change reliably triggers a WhatsApp and/or push notification within an agreed SLA.
- A SKU falling below its configured threshold raises an admin alert automatically.

**PHASE 6   Scale & Hardening**   *·   Ongoing, post-launch*

**Functional Requirements**

- Load/performance testing as order volume grows.
- Regional courier/trade-transport integration.
- Demand forecasting from order data, informed by festive and tender-cycle seasonality.
- Full security review before wide public launch.

**Business Rules**

- Demand forecasting consumes invoice history plus tender/festive calendar data established in Phases 2 and 3, not raw order counts alone.

**Screens / Components**

Forecasting dashboard  ·  Courier integration settings  ·  Security review checklist

**Acceptance Criteria**

- Forecasting output visibly accounts for known festive/tender demand spikes rather than a flat trend line.
- Security review sign-off is a precondition for wide public launch, tracked as a gating checklist item.


# **03 · High-Level Detailing**
**HIGH LEVEL**  *System architecture, technology stack, non-functional requirements, and rollout — the view that ties every low- and mid-level spec above back into one system.*

**3.1 System Architecture**

The Android client and Supabase backend are shared by all five channels; Edge Functions carry the logic (pricing, credit, invoicing, notifications) that must be centralized rather than duplicated per channel.

![](../../../../../../../../../../Downloads/Utensils_App_02_Implementation_Spec%20(1)/Aspose.Words.319b5957-a8b3-4720-913e-56e3fe9ee3c8.001.png)

**3.2 Technology Stack**

|**Layer**|**Technology**|**Notes**|
| :- | :- | :- |
|Client|Kotlin, Jetpack Compose, Hilt, MVVM|Kept from existing app; navigation extended for 6 roles / 5 channels|
|Backend|Supabase (Postgres, Auth, Storage, Edge Functions)|Kept; RLS policies extended per role|
|Payments|Razorpay|Kept; production key migrated to secure config (Phase 0)|
|Messaging|WhatsApp Business API (provider TBD — open question)|New, Phase 5|
|Push|Firebase Cloud Messaging (or alternative — open question)|New, Phase 5|
|PDF generation|Server-side, via Edge Function (library TBD — open question)|New, Phase 1|
|Institutional|GeM portal (manual or API bridge, TBD)|New, Phase 3B|
|Trade credit|BNPL/trade-finance partner (evaluated, not yet selected)|New, Phase 5, open question|



**3.3 Non-Functional Requirements**

|**Category**|**Requirement**|
| :- | :- |
|Security|Razorpay and any BNPL partner keys held in secure config, never in source; RLS enforced per role on every table; brand-asset and compliance-document storage access-controlled per owning user/admin.|
|Performance|Quick-Order Pad and pricing resolution respond within interactive latency (target sub-second) even as MOQ slab and contract-pricing tables grow.|
|Scalability|Supabase plan/limits reconfirmed (open question, §05) once ledger, invoice, brand-asset, and compliance-document storage volumes are added.|
|Compliance|GST invoice numbering and HSN/tax split must meet statutory requirements; compliance-document vault must track expiry dates for institutional bids.|
|Availability|Checkout, credit-check, and invoice generation are on the critical path and should degrade gracefully (e.g. queued invoice generation) rather than block order confirmation.|
|Auditability|Ledger transactions and quote versions are append-only records, supporting dispute resolution and GST filing export.|

**3.4 Environments & Release Strategy**

- Dev → Staging → Production Supabase projects, with RLS policies and Edge Functions version-controlled and deployed per environment.
- Feature flags recommended for channel-by-channel rollout (e.g. enable HoReCa accounts for a pilot set of users before general availability).
- Phase 0 test coverage (cart, checkout, order creation) forms the regression baseline that every subsequent phase must not break.

**3.5 Rollout Timeline Summary**

|**#**|**Phase**|**Duration**|**Depends on**|
| :- | :- | :- | :- |
|0|Foundation Cleanup & Fixes|1–2 wks|—|
|1|Wholesale & Distributor Core|3–4 wks|Phase 0 (roles, KYC)|
|2|Wedding & Corporate Gifting Vertical|5–6 wks|Phase 1 (pricing, orders)|
|3|HoReCa & Institutional Accounts|3–4 wks|Phase 1 (pricing, ledger)|
|4|Premium Retail Layer|2–3 wks|Phase 0|
|5|Trust & Operations Layer|2–3 wks|Phases 1–3 (order/ledger events to notify on)|
|6|Scale & Hardening|Ongoing|All prior phases|

*Phase durations assume one focused developer, consistent with the Phased Build Plan; adjust proportionally if team size changes. Phases 2 and 3 can be parallelized across two developers once Phase 1's pricing engine and ledger are complete, since neither depends on the other.*
Page  of 
