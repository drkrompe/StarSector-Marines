# Officer careers and dossiers

Status: IMPLEMENTED - live acceptance open
Updated: 2026-10-11

## Player story

As a company commander I can inspect each officer, understand the squads and
passive effects they bring, and develop a distinct career through operations
and promotion. The left-hand portraits lead to people, not only roster filters.

## Alpha slice

- Barracks dossier: portrait, rank, status, command capacity, XP to promotion,
  service record, existing traits, exact specialty effects and affected squads.
- Frozen deployment attribution credits every participating named officer once;
  idle officers receive nothing. Operation command remains credited separately
  when it owns no troops. Results explain each officer's XP and promotion.
- One specialty point at appointment plus one per rank promotion, including
  rank-equivalent points for existing saves; three specialties, two ranks each.
  Tactical Cover reduces incoming accuracy for assigned deployed marines by
  5/10%; Recovery Planning shortens their WIA recovery by 15/30%; Salvage
  Stewardship adds 10/20 percentage points of recovery on victory, using only
  the strongest participating specialist. These are separate from moral outlook.
- Effects freeze at launch. Home reassignment or later specialty choices do not
  change an operation's attribution or outcome. Wounded keep their billets.
- No repeat credit from reopening the same settled outcome. A fresh attempt at
  a repeatable mission remains a new deployment, even when the mission id repeats.

## Acceptance

- Focused progression, snapshot, persistence, deployment and settlement tests.
- Render dossiers and promotion choices at supported UI scales.
- Live campaign/save/reload acceptance remains open until explicitly exercised.
- Follow-up: Castle's portrait-led interactive officer walkthrough and voiced
  lines; broader trait mechanics remain in S10.

## Implementation evidence

The Alpha slice is implemented. Focused tests cover rank budgets, two-rank
caps, serialized choices/receipts, frozen group attribution, no idle-officer
credit, boat-loss participation, repeatable mission attempts, WIA billets,
strongest-only salvage, debug exclusion, fixture round trips and real dossier
training clicks. The Barracks snapshot suite includes dossiers before/after
training at 100% and 150%, the scrolled service record, and return to preparation.
The canonical company/progression docs record the new visible command modifier.

Live campaign balancing, game save/reload and Castle's officer lesson remain
explicit follow-up acceptance. Existing recruitment traits are preserved;
this slice does not claim to implement S10's six dormant legacy traits.

Verification: 125 focused tests passed; 39 Barracks snapshots rendered; `shadowJar` passed.
