# T3 — the last testament

**Status:** SLICE 1 CODE COMPLETE (2026-08-19)

**Implemented:** `946262b2`

## Purpose

The kingmaker capstone is the moral compass's single explicit reveal. After the
player decisively helps a claimant overthrow a faction, the displaced ruler's
last testament describes the kind of commander the player's accumulated choices
have made them. It is a judgment grounded in remembered acts, not a scorecard.

This is an epilogue to an attributed political victory. It does not add another
battle, alter the throne-claim result, or reward the player for optimizing a
hidden alignment.

## Trigger — locked v1

Seal exactly one testament for a throne claim when all of these persisted facts
are true:

- the throne claim is `APPLIED`;
- its player consequence is `APPLIED`;
- the captured allegiance is `CLAIMANT`;
- captured contribution is at least 60, the existing decisive/kingmaker band;
- the source is a resolved `CIVIL_WAR` whose claimant and displaced houses match
  the claim and remain valid historical identities; and
- the moral ledger contains the source-unique `CIVIL_WAR_CLAIMANT` row for that
  chain.

Autonomous victories, incumbent defenses, minor or substantial participation,
failed/malformed claims, and unattributed outcomes do not produce this capstone.
Diplomacy writeback is deliberately not a prerequisite: a transient or rejected
external relationship mutation must not erase an otherwise established player
choice or ownership result.

The producer runs immediately after `MoralCompassSystem`. This guarantees the
claim's institutionalism choice is present before the testimony is frozen.

## Immutable testimony snapshot — locked v1

Append one source-unique `kingmakerTestaments[]` row keyed by throne-claim id.
It freezes:

- testament, throne-claim, and source-chain identity;
- claimant and displaced-house identity;
- source faction, result faction, and market identity;
- decisive player contribution;
- all four hidden moral-axis values;
- the moral-ledger row-count boundary available when the testament was sealed;
  and
- sealed day plus delivery lifecycle.

The ledger boundary is an immutable prefix. Later choices may append new ledger
rows and move the live aggregate axes, but neither can rewrite this testament.
Later editorial selection may cite only source rows inside the frozen prefix.
Legacy saves backfill an absent table with empty arrays, sentinel identities and
ticks, and a `NONE` default state; they never synthesize a testament from an
old un-attributed victory.

The snapshot stores internal values for deterministic editorial selection. No
player-facing surface may print axis names, numeric values, thresholds,
contribution totals, ledger ids, or deltas.

## Editorial voice — locked v1

The testament is written in the displaced ruler's voice. It may be hostile,
grudging, wounded, or unexpectedly respectful, but it must remain factually
bounded by the frozen source identities and ledger prefix.

The final text has three movements:

1. **Accusation:** the concrete political act—who the player helped depose whom,
   and where.
2. **Witness:** two or three remembered choices selected from distinct source
   families when available. These are described as acts and consequences, never
   as alignment labels or arithmetic.
3. **Verdict:** one concise synthesis of the player's character. Mixed axes
   should produce tension rather than forcing a saint/monster binary.

If the frozen history has too little evidence for an axis, the testament remains
silent about that dimension. It never invents off-screen conduct to fill a prose
template.

## Delivery and persistence

The content snapshot begins `SEALED`. A later dedicated Last Testament intel
entry reconstructs from every sealed row after load and moves the independent
delivery lifecycle to `REVEALED` when the player reads it. The entry remains as
historical correspondence afterward.

Delivery is not a second consequence pass. Opening, dismissing, reconstructing,
or reloading the intel cannot change reputation, moral axes, campaign events,
contracts, houses, chains, markets, or factions.

## Slices

1. ~~**Sealed testimony authority** — append the persistent snapshot table,
   legacy backfill, source-unique producer after the moral pass, and replay/order
   tests.~~ Shipped in `946262b2`; the producer runs immediately after the moral
   pass, requires an exact handoff-day claimant ledger row, and freezes identities,
   axes, contribution, and the exclusive ledger boundary exactly once.
2. **Evidence editor** — deterministic pure selection from the frozen ledger
   prefix, source-family diversity, mixed-character synthesis, and exhaustive
   prose tests without numeric leakage.
3. **Last Testament intel** — diegetic delivery, load reconstruction, historical
   terminal state, and malformed-data fail-closed behavior.
4. **Closure and reachability** — production-shaped debug setup, Chronicle
   cross-link, save/replay matrix, documentation closeout, and manual smoke.

## Acceptance

- One qualifying claim produces one immutable content snapshot across repeated
  daily ticks and save/load.
- A snapshot includes the claimant moral row and excludes all choices appended
  after its frozen ledger boundary.
- Later moral choices cannot change its axes, evidence candidates, identities,
  or verdict.
- Only decisive, attributed claimant victories qualify.
- The player sees remembered deeds and a verdict, never a hidden-system readout.
- Missing or inconsistent source data fails closed without fabricating prose or
  replaying campaign consequences.

## Non-goals

- No new combat, contract, material reward, reputation change, colony reward, or
  faction mutation.
- No moral-compass dashboard, encyclopedia entry, hover tooltip, debug readout,
  or reusable alignment UI.
- No generated free-form text and no attempt to cover every possible moral
  source in the first editorial pass.
- No capstone for autonomous claimant victories or incumbent victories in v1.
