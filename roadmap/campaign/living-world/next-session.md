# Living world — next session

## State of play

The autonomous political sim ([`overview.md`](overview.md)) is being built
along the A–E slice spine. **A (genesis), B (player transfer), and C (drift +
autonomous promotion) are shipped.** The board is seeded with contested stakes;
player ops move them decisively while the background world moves them slowly.
ACTIVE houses adopt a deterministic local consolidation target (`c26ca415`),
drift 3–5 share every seventh day (`daf3ef1b`), and gain one daily promotion
point while holding a strict home-market majority (`11987f9a`). D1 is also
shipped: monthly autonomous plots bind an actor, rival, market, and industry;
advance one point per day; and resolve exactly once into a 40-share transfer
plus 30 promotion progress (`3137f238`, `2f7b4a86`, `1d6ca71c`).

Two reusable primitives now exist on top of `CampaignState`, and they are the
seams the rest of the thread builds on:

- **`StakeLedger`** — `seizeShare` + stake queries (`findStake`, `shareOf`,
  `totalClaimedShare`, `unclaimedShare`). Tombstone-at-zero soft-delete.
- **`HousePromotion`** — `addProgress` / `addProgressAndPromote` (carry +
  cascade; TIER_4 terminal).

Both are stateless ops, fully unit-tested. The intent: Slices C–D are mostly
"call these on a tick," not new mutation logic.

## Next up — Slice D2 (Chronicle discovery foundation)

Turn D1's persisted terminal-chain seam into learned, player-visible history:

1. Define a small append-only `chronicle[]` table for learned events, including
   source chain, outcome, actor/target/location, happened day, and learned day.
2. Give `DiscoveryPropagationSystem` an exactly-once consumption marker for
   terminal chains; reloads and repeated ticks must not duplicate dispatches.
3. Ship the two-band editor rule: houses the player has touched are intimate,
   Tier-3+ chains are epic, and the silent middle remains unrecorded.
4. Expose learned dispatches on the debug campaign intel first; richer prose and
   active-chain rumor/intervention hooks can layer on after storage is stable.

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
- Slice D1a — autonomous chain identity + lifecycle (`3137f238`).
- Slice D1b — monthly deterministic chain creation (`2f7b4a86`).
- Slice D1c — daily advancement + exactly-once resolution (`1d6ca71c`).
