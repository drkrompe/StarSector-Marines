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
D2 is shipped too: learned events persist as append-only snapshots, terminal
chains pass through the intimate/epic/silent editor exactly once, and debug
intel renders the resulting dispatches (`dc3da47d`, `068943ed`, `55eefe4f`).
D3 is shipped: relevant active plots roll deterministic weekly discovery,
persist uncertain rumor snapshots, and expose a stable threatened-house query
for intervention generation (`435b704e`, `b006dc3`, `3a16315e`).
D4 is shipped: intervention contracts distinguish opposed chains from parent
chains, threatened houses issue one bounded Strike offer, stale offers withdraw,
and a correctly attributed player victory fails the hostile plot exactly once
(`dd63a0ba`, `0a2f3c09`, `7b54ec81`).
E1 is shipped: houses with exhausted stake history become stable-id DORMANT
tombstones, their political work closes safely, stale consolidation targets
retarget or clear, and only meaningful disappearances enter the Chronicle
(`975db3ba`, `f736a7d6`, `eefc505c`, `500d2c17`).

Two reusable primitives now exist on top of `CampaignState`, and they are the
seams the rest of the thread builds on:

- **`StakeLedger`** — `seizeShare` + stake queries (`findStake`, `shareOf`,
  `totalClaimedShare`, `unclaimedShare`). Tombstone-at-zero soft-delete.
- **`HousePromotion`** — `addProgress` / `addProgressAndPromote` (carry +
  cascade; TIER_4 terminal).

Both are stateless ops, fully unit-tested. The intent: Slices C–D are mostly
"call these on a tick," not new mutation logic.

## Next up — Slice E2 (Promotion and throne-claim ambitions)

Build the ambition transition that feeds the existing rank ladder and hands off
at the T3 endgame boundary:

1. Define when a consolidating Tier-1/2 house switches to `PROMOTE` rather than
   continuing horizontal expansion, using persisted rank progress and power.
2. Define the T3 `CLAIM_THRONE` eligibility threshold and target identity without
   performing the vanilla faction flip in the living-world layer.
3. Add deterministic re-evaluation cadence/state so ambition transitions do not
   flap daily or overwrite player/story-authored ambitions.
4. Map `PROMOTE`/`CLAIM_THRONE` into chain archetypes only after their resolution
   payload and T3-endgame handoff contracts are explicit.

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
- Slice D2a — append-only learned Chronicle storage (`dc3da47d`).
- Slice D2b — intimate/epic/silent terminal editor (`068943ed`).
- Slice D2c — Chronicle debug-intel dispatches (`55eefe4f`).
- Slice D3a — active-rumor persistence (`435b704e`).
- Slice D3b — deterministic active discovery (`b006dc3`).
- Slice D3c — discovered-threat intervention query (`3a16315e`).
- Slice D4a — separate opposed-chain contract lineage (`dd63a0ba`).
- Slice D4b — bounded threatened-house intervention offers (`0a2f3c09`).
- Slice D4c — validated player-success chain intervention (`7b54ec81`).
- Slice E1a — empty-house dormancy + political cleanup (`975db3ba`, `f736a7d6`).
- Slice E1b — stale consolidation re-evaluation (`eefc505c`).
- Slice E1c — selective dormancy Chronicle events (`500d2c17`).
