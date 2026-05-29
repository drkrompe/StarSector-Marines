# Living world — next session

## State of play

The autonomous political sim ([`overview.md`](overview.md)) is being built
along the A–E slice spine. **A (genesis seeding) and B (player transfer) are
shipped** — see [`complete/`](complete/). The board is now seeded with
contested stakes, and player ops permanently move them.

Two reusable primitives now exist on top of `CampaignState`, and they are the
seams the rest of the thread builds on:

- **`StakeLedger`** — `seizeShare` + stake queries (`findStake`, `shareOf`,
  `totalClaimedShare`, `unclaimedShare`). Tombstone-at-zero soft-delete.
- **`HousePromotion`** — `addProgress` / `addProgressAndPromote` (carry +
  cascade; TIER_4 terminal).

Both are stateless ops, fully unit-tested. The intent: Slices C–D are mostly
"call these on a tick," not new mutation logic.

## Next up — Slice C (Drift)

The breathing loop. Make `AutonomousPromotionSystem` (currently a stub) live,
and add the weekly share-drift loop. Per [`overview.md`](overview.md) §"The
hybrid engine":

1. **Drift loop (weekly cadence).** Each `ACTIVE` house with an ambition
   siphons ~3–5 byte-share from a weaker *visible* rival (or the unclaimed
   remainder) on a contested industry. Glacial by design — the
   decisive-accelerant principle: an unattended plurality flip takes many
   months, so the player's intervention stays the decisive force. Use
   `StakeLedger.seizeShare` with a small amount; a zero-sum `transferShare`
   variant can be added to `StakeLedger` if drift should never expand into
   open share.
2. **Autonomous promotion.** Fill in `AutonomousPromotionSystem.tick`: walk
   `houses[]`, compute a `stakeBasedDelta` from each house's holdings vs its
   market total, and call `HousePromotion.addProgressAndPromote`. The primitive
   already exists and is tested — this is the `stakeBasedDelta` formula + the
   walk.

**Prerequisite to confirm:** drift needs houses to *have ambitions* (currently
always `NONE`) and a notion of "visible rival." Ambition assignment is the
[`ambition.md`](ambition.md) layer — decide whether C ships a minimal
deterministic ambition seed (e.g. `CONSOLIDATE_STAKE` toward the industry a
house is contender in) or whether a thin ambition pass lands first. Visibility
is the rank-ladder rule in [`../mechanics.md`](../mechanics.md) §"Visibility
computation" — but no relationship edges are seeded yet, so v1 drift can use
same-market locality directly rather than the `relationships[]` table.

## Open forks still unresolved (design)

- Horizontal (stake competition) vs vertical (loyalty/rebellion) axis — which
  to wire first. Leaning horizontal. See [`ambition.md`](ambition.md).
- "Information has to pay rent" — don't surface a trait on the dossier before
  the system it gates ships. See [`ambition.md`](ambition.md).
- The 7 open questions in [`overview.md`](overview.md) §"Open questions"
  (drift cadence/magnitude, conservation, Chronicle storage, discovery surface,
  …) — most are C/D balance-pass concerns.

## Follow-ups surfaced by the Slice B review

- **`StakeLedger` composite index.** `seizeShare` does up to 3 linear
  `findStake` scans — fine at once-per-mission, but Slice C's weekly drift over
  all ACTIVE houses turns that into O(houses × stakes). Add a
  `(house,market,industry) → row` index *when C lands*, not before.
- **Debug `forceComplete` political shift.** `CampaignDebugIntel.forceComplete`
  flips contract state + rep but intentionally skips the stake seizure /
  promotion (it has no struck industry). For playtesting the political layer
  without running battles, give it a target-derived industry and call the same
  primitives — small, deferred.
- The contract-targeting / affinity / tier-scaling items in
  [`complete/slice-b-player-transfer.md`](complete/slice-b-player-transfer.md)
  §"Follow-ups".

## Commit chain

- Slice A — genesis seeding (`HouseSeeder` rewrite + overview + README row).
- Ambition layer doc (`ambition.md`).
- Slice B — `StakeLedger` + `HousePromotion` + `MissionResolver` wiring +
  tests + this doc set.
