**INTERNAL PLANNING DOCUMENT · DRAFT FOR REVIEW · DOCUMENT 3 OF 3**

**Utensils, Crockery & Household Appliances App**

**Marketplace Discovery & Retailer Fulfillment — Addendum Specification**

*Companion addendum to Document 1 (Flow Diagram Specification) and Document 2 (Implementation Specification). Revises Phase 1's billing scope and inserts a new phase covering consumer-to-retailer order matching.*

*Prepared: 16 Sep 2026 · Companion to: Flow Diagram Specification, Implementation Specification*

*For review by: Technical Analyst & Business Analyst*

**00 · How This Addendum Fits**

This addendum makes two changes to the Phased Build Plan and Implementation Specification (Rev. 2):

- Phase 1 (Wholesale & Distributor Core) is descoped for v1: digital credit ledger and GST-compliant PDF invoicing are deferred. Wholesale billing stays offline, as it is today, until vendor demand justifies digitizing it.
- A new phase — Marketplace Discovery & Retailer Fulfillment — replaces the previously planned Premium Retail Layer (original Phase 4). It reuses that phase's curated catalog and delivery-serviceability logic, but changes who fulfills the order: a matched retailer first, the warehouse only as fallback.

Phase numbering below follows the original plan (0–6); Phase 4 is redefined rather than renumbered, since it replaces — not adds to — the original Phase 4 scope.

**01 · Phase 1 Revision — Wholesale & Distributor Core (Lite)**

*Purpose: Keep the digital ordering flow — the part vendors actually need day-to-day — while deferring the ledger and tax-invoicing build, which is the highest-effort, lowest-urgency part of the original Phase 1 scope.*

**Carried over from Document 2 §Phase 1 (unchanged)**

- MOQ-tiered pricing engine (resolvePricing).
- Distributor/Wholesale account type with tier assignment.
- Quick-Order Pad (SKU + qty grid).
- Request-a-Quote (RFQ) flow and admin quote pipeline.

**Removed from v1 scope (deferred to Phase 1B)**

- Credit ledger (khata) — balance, due dates, partial payments.
- GST-compliant PDF invoicing with HSN/SAC per line.
- Any checkCreditAvailability gate at checkout — v1 has no credit limit to check against.

**Business Rules (revised)**

- An order is confirmed on placement; no ledger check is performed in v1, since no digital credit limit exists yet.
- A confirmed order generates a plain, non-tax order summary (SKU, qty, agreed price) for print/WhatsApp — not a GST-compliant invoice. Statutory invoicing continues offline exactly as it does today.
- An accepted RFQ quote still converts directly into an Order + OrderItems (Flow L2 unchanged); only the downstream invoicing step is descoped.

**Acceptance Criteria (revised)**

- A distributor can browse, quick-order, or RFQ, and reach a confirmed order with no ledger or invoicing step blocking checkout.
- The order summary is available to print or send via WhatsApp but is clearly not represented as a tax invoice anywhere in the UI.
- No credit\_ledger or ledger\_transactions table is required to ship this phase.

**Phase 1B — Digital Ledger & GST Invoicing (deferred, not removed)**

Original Document 2 §1.1/§2 content for credit\_ledger, ledger\_transactions, invoices, and Flow L3/L5 remains valid and unchanged — it is simply rescheduled to whenever offline billing becomes the bottleneck, rather than built in the first pass.

**02 · Phase 0 Addendum — Signup Fields**

One addition to the existing Phase 0 KYC form, since retrofitting location data later is expensive: every Wholesale/Distributor signup now also captures shop address, latitude/longitude, and a service radius (km). This is required before Phase 4 (below) can function and costs nothing extra to collect at signup time, when GSTIN is already being captured.

**03 · Phase 4 (Redefined) — Marketplace Discovery & Retailer Fulfillment**

*Purpose: Let a retail customer shop the curated catalog without ever seeing shops or prices mid-browse; only once their cart is final does the system reveal which nearby retailers can fulfill it completely, so the customer — not the algorithm — makes the final call. A retailer only ever appears as a candidate if they have themselves bought every relevant SKU from us, keeping the network limited to our own vendor base.*

*Actors: Retail Customer, Wholesale/Distributor Retailer (as fulfillment partner), Admin/Ops, System (matching engine).*

**Functional Requirements**

- Carry over curated retail catalog browse from the original Phase 4 scope (curated subset, not the full wholesale SKU list).
- Build cart finalize → fulfillment-matching trigger.
- Build findFullCartRetailers(): filters retailers to only those whose purchase history covers every SKU in the cart, sorts by distance, returns the nearest three.
- Build the “Choose Your Store” comparison screen: three cards, each showing shop name, distance, total cart price, and a pickup/delivery choice.
- Build retailer proposal & accept/decline workflow: the chosen retailer is notified (WhatsApp/push) and has a fixed window to accept before the assignment lapses.
- Build automatic warehouse-fallback: triggers when zero retailers have full-cart coverage, or when the customer's chosen retailer declines or the window lapses.
- Build retailer\_inventory display-price field: seeded from the platform's suggested retail price, editable by the retailer at any time.
- Build order confirmation view showing the assigned fulfiller (retailer or warehouse) and the customer's pickup/delivery choice.

**Business Rules**

- A retailer is only a match candidate if their own purchase history from us covers every SKU currently in the customer's cart — no retailer who hasn't bought the relevant stock from us is ever shown.
- Full-cart coverage is a hard filter, not a ranking factor: partial-coverage retailers are never shown, and are never split across multiple fulfillers for one order.
- Only the nearest three qualifying retailers are surfaced, ranked by distance; no comparison list is shown for zero or one qualifying retailer — one match shows one card, zero matches skips straight to warehouse fulfillment.
- If the customer's chosen retailer declines or the acceptance window lapses, the order falls back to warehouse fulfillment automatically — the customer is never shown a second comparison screen for the same order.
- Exactly one fulfiller — a single retailer or the warehouse — is ever attached to a given order.
- Pickup vs. home delivery is the customer's choice, independent of which retailer (or the warehouse) is fulfilling.

**Screens / Components**

Retail Storefront browse (carried over) · Cart Finalize / Find-a-Store trigger · Choose Your Store comparison screen · Order Confirmation (fulfiller + delivery choice) · Retailer: Incoming Order Proposal screen (accept/decline, countdown) · Admin: Marketplace Match Log

**Acceptance Criteria**

- A cart fully covered by at least one retailer always surfaces a comparison screen — it never silently falls through to warehouse fulfillment.
- A cart with zero fully-covering retailers routes straight to warehouse fulfillment without ever rendering an empty comparison screen.
- A declined or expired retailer proposal always results in warehouse fallback, never a second customer-facing store choice for the same order.
- No order is ever recorded with more than one active fulfiller.
- A retailer's accept/decline response updates order status within the agreed SLA window.

**04 · Data Model Additions**

Extends Document 2 §1.1. Existing tables (users, products, orders/order\_items) are unchanged except where noted.

**retailer\_storefronts**

*One per Wholesale/Distributor user who opts into the marketplace; captured at Phase 0 signup, editable later.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|id, user\_id|uuid, FK|FK to users; only wholesale/distributor roles|
|shop\_name, address|text|Public-facing on the comparison screen|
|latitude, longitude|numeric|Captured at signup (Phase 0 addendum)|
|service\_radius\_km|numeric|Used by findFullCartRetailers for distance eligibility|
|active|boolean|Retailer can pause marketplace visibility without deleting the profile|

**retailer\_inventory**

*Proxy for what a retailer stocks; auto-seeded, not customer-maintained by default.*

|**Field**|**Type**|**Notes**|
| :- | :- | :- |
|retailer\_id, product\_id|uuid, FK|One row per SKU the retailer has ever ordered from us|
|available\_qty|numeric, est.|Auto-derived from purchase history; a proxy, not a live count|
|display\_price|numeric|Customer-facing on the comparison screen; defaults to suggested retail price, retailer-editable|

**fulfillment\_assignment**

*One row per order; the single source of truth for who is fulfilling it.*

|**Field**|**Type**|**Notes**||
| :- | :- | :- | :- |
|order\_id|uuid, FK|One active assignment per order||
|retailer\_id|uuid, FK, nullable|Null when fulfiller is the warehouse||
|status|enum|proposed → accepted → completed, or declined/expired → fallback\_warehouse||
|proposed\_at, responded\_at|timestamp|Drives the acceptance-window countdown||

**05 · API & Edge Function Contracts**

Extends Document 2 §1.2.

- findFullCartRetailers(cart\_items[], customer\_lat, customer\_long) → up to 3 candidates, each with distance and total cart price. Returns an empty list when no retailer has full coverage — the client treats this as “go to warehouse fulfillment,” not as an error state.
- proposeFulfillment(order\_id, retailer\_id) → creates a fulfillment\_assignment row and sends the retailer's accept/decline notification.
- respondFulfillment(assignment\_id, response: accept | decline) → updates status; a decline (or an unanswered expiry, handled by a scheduled check) calls fallbackToWarehouse.
- fallbackToWarehouse(order\_id) → sets the assignment's fulfiller to the warehouse and notifies the customer that fulfillment changed.

**06 · Rollout Timeline (Revised)**

|**#**|**Phase**|**Duration**|**Depends on**|
| :- | :- | :- | :- |
|0|Foundation Cleanup & Fixes (+ location capture)|1–2 wks|—|
|1|Wholesale & Distributor Core (Lite)|2–3 wks|Phase 0|
|1B|Digital Ledger & GST Invoicing (deferred)|3–4 wks|Phase 1; scheduled when offline billing becomes the bottleneck|
|2|Wedding & Corporate Gifting Vertical|5–6 wks|Phase 1|
|3|HoReCa & Institutional Accounts|3–4 wks|Phase 1|
|4|Marketplace Discovery & Retailer Fulfillment|4–5 wks|Phase 1 (retailer accounts), Phase 0 (location capture)|
|5|Trust & Operations Layer|2–3 wks|Phases 1–4 (order/ledger/fulfillment events to notify on)|
|6|Scale & Hardening|Ongoing|All prior phases|

Phase 1's duration drops from the original 3–4 weeks since the ledger and invoicing build are removed; that saved time is a reasonable offset for Phase 4's new 4–5 week estimate, which is net-new scope not present in the original plan.
