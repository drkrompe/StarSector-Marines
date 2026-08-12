# Living world — next session

## State of play

The autonomous political sim ([`overview.md`](overview.md)) is being built
along the A–E slice spine. **A (genesis), B (player transfer), and C (drift +
autonomous promotion) are shipped.** The board is seeded with contested stakes;
player ops move them decisively while the background world moves them slowly.
ACTIVE houses adopt a deterministic local consolidation target (`c26ca415`),
drift 3–5 share every seventh day (`daf3ef1b`), and gain one daily promotion
point while holding a strict home-market majority (`11987f9a`).

Two reusable primitives now exist on top of `CampaignState`, and they are the
seams the rest of the thread builds on:

- **`StakeLedger`** — `seizeShare` + stake queries (`findStake`, `shareOf`,
  `totalClaimedShare`, `unclaimedShare`). Tombstone-at-zero soft-delete.
- **`HousePromotion`** — `addProgress` / `addProgressAndPromote` (carry +
  cascade; TIER_4 terminal).

Both are stateless ops, fully unit-tested. The intent: Slices C–D are mostly
"call these on a tick," not new mutation logic.

## Next up — Slice D1 (Autonomous chain foundation)

Build the discrete-event half without jumping directly to Chronicle UI:

1. Define autonomous chain lifecycle/status identity; current `chains[]` has no
   status or resolved marker and `ChainAdvancementSystem` is still a stub.
2. Deterministically create at most one autonomous chain for an eligible
   ambitious house and bind it to a local rival/industry.
3. Advance slowly, resolve exactly once through `StakeLedger` and
   `HousePromotion`, then leave a persisted event seam for Chronicle discovery.
4. Keep player-backed chains out of the autonomous daily advancement path.

## Open forks still unresolved (design)

- Horizontal (stake competition) vs vertical (loyalty/rebellion) axis — which
  to wire first. Leaning horizontal. See [`ambition.md`](ambition.md).
- "Information has to pay rent" — don't surface a trait on the dossier before
  the system it gates ships. See [`ambition.md`](ambition.md).
- The 7 open questions in [`overview.md`](overview.md) §"Open questions"
  (drift cadence/magnitude, conservation, Chronicle storage, discovery surface,
  …) — most are C/D balance-pass concerns.

## Follow-ups surfaced by the Slice B review

- **`StakeLedger` composite index.** Weekly drift is still small enough for the
  current linear scans. Add `(house,market,industry) → row` only if profiling
  or Slice D chain volume makes it worthwhile.
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
- Slice C1 — home-market-majority autonomous promotion (`11987f9a`).
- Slice C2a — minimal persisted consolidation ambitions (`c26ca415`).
- Slice C2b — exactly-once weekly 3–5 share drift (`daf3ef1b`).
