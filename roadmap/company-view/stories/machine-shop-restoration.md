# Machine-shop restoration

Status: IMPLEMENTED — live campaign acceptance open
Written: 2026-10-12
Updated: 2026-10-12 — staged interiors, transactions and UI verified; live walkthrough remains.

## Outcome

Second Wind's fixed machine shop starts buried and out of service. After Hold
Until Relieved, the company can clear it, restore its machinery, and install
tooling. Each paid step changes the real room and provides an explicit benefit.
The ship view carries Castle's portrait, room inspection, compact progress
indication, a reviewed bill, and the current workshop service.

## First chain

- Clear workspace: 750 credits at a planetary yard; recover 10 metals once.
- Repair machinery: 3,000 credits, including labor/parts; enable reclamation of
  one cargo heavy-machinery unit into four metals, with explicit confirmation.
- Install tooling: 4,500 credits; reduce supplies costs by 20% for boat building,
  boat fittings, Mech Lab chassis and weapon fabrication. Round each supplies
  line up, minimum one. Other commodities, squad issue and pattern gates remain.

Reclamation is an instantaneous, irreversible workshop transaction using cargo,
never prepaid starting-depot stores. Reclamation can be used aboard the repaired
home ship without a planetary yard. Output must fit the fleet hold. Tooling in
this slice is installed equipment attached to this hull; portable tool inventory
and alternative specialization branches remain future work.

## Acceptance

- Room footprint, location, doors, Mech Lab and both shuttle bays remain fixed
  across all workshop states. Debris, damaged machinery, restored stations and
  installed tools are visible in the normal ship renderer.
- Inactive machinery offers no ambient work; repaired and equipped stations
  offer appropriate reachable work. Clearing cannot strand crew or block the
  first-mission preparation rooms.
- State is saved by original starting-hull identity. Legacy starts default to
  buried; other ships retain their existing rooms and ordinary fabrication bills.
- Stage, ownership, home ship, mission, funds, port and hold-space prerequisites
  are rechecked at commit. Repeated/stale orders award nothing and spend nothing.
- Discounted previews and actual debits agree, including affordability and short
  resource messages. Credits and boat-bay restoration are never discounted.
- Ship-view state, benefits and deck refresh immediately; no reopening required.
- Focused state/economy tests, paired room/layout and UI evidence at 100/150%,
  and package boundary validation. Record live acceptance separately.

## Ownership

Company campaign restoration owns saved steps and transactions; fabrication
owns quotes and spends; ship interiors reads condition and presents its effects.
Canonical models: `company-view-nouns.md`, `ship-interiors-nouns.md`.

## Remaining acceptance

Focused tests cover prices, cargo limits, once-only salvage, stale orders,
ownership, persistence, quoted/debited supplies and two-click purchase input.
The opt-in Cerberus evidence checks fixed layouts and first-mission room access
across three seeds and all four workshop states, with ship-view renders at
100% and 150% scale. Mod-jar boundary verification passes.

In a live campaign, complete Hold Until Relieved, visit a planetary spaceport,
and use HQ's ship view to inspect and buy each stage. Confirm the visible room
refreshes immediately, reclamation also works underway, and the Boat Deck and
Mech Lab show and charge the reduced supplies bill. Save/reload and transfer
the company to another hull to confirm the restoration stays with Second Wind.
No live save walkthrough or deployment is claimed by the construction evidence.
