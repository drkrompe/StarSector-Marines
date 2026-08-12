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
E2 is shipped: houses persist a 30-day ambition-review clock; strong Tier-1/2
houses switch from consolidation to rank-targeted promotion intent at 75% of
their progress threshold; and Tier-3 houses at 750 progress / 1000 power persist
a throne claim against their faction identity (`f022d4ad`, `31bb6875`,
`3c58964b`, `57be6214`). Vertical ambitions deliberately do not create generic
consolidation chains.
The first vertical payload is also shipped: non-majority Tier-1/2 promotion
schemes bind a same-faction local rival for 60 days, resolve into +90 actor / -30
rival promotion progress plus a 20-share material shift, and safely short-circuit
if another route already promoted the actor (`0a00ceb0`, `156ac5b1`, `a4cd42c8`).
The throne-claim producer is shipped too: generic progress caps at Tier 3,
capped claimants run 180-day discoverable/intervenable civil wars, and
resolution prepares one persisted source-chain-unique handoff without changing
vanilla state or Tier 4 (`76c7579a`, `9bf2356b`, `bdb45f7b`, `35ec0ccf`,
`77657bb8`, `51fad0ca`).
The isolated throne-claim consumer is now shipped: one predeclared Claimant
League provides supported faction identity; a narrow adapter probes, repairs,
transfers, and verifies vanilla market/entity ownership; successful handoffs
become `APPLIED` and promote their claimant to Tier 4; and Chronicle dispatch
waits until that writeback finishes (`42f00725`, `ae35056d`, `870d5b96`,
`5174f44c`).
The first diplomacy consequence is shipped as a separate persisted lifecycle:
after ownership applies, the Claimant League and former ruler become mutually
hostile through a postcondition-first, partial-repair-safe adapter. Autonomous
success remains player-reputation-neutral until an explicit kingmaker choice can
be attributed (`ad6ff5fd`, `527535fb`).
Applied claims now also emit a dedicated immutable faction-flip dispatch that
names the claimant, displaced rival, source/result factions, market, and actual
writeback day (`d7be2649`, `31be86ed`).
The civil-war participation foundation is shipped: existing lineage encodes
claimant vs incumbent work, contracts snapshot their progress band, the first
successful operation locks chain allegiance, early work applies ±15/±30
progress, open-conflict assaults are decisive, completed stationing work is
recoverable, and successful claimant attribution snapshots into the throne
handoff (`926047e8`, `4332927f`, `50591863`, `3610923d`, `487134ae`).
The player-facing offer lifecycle is shipped too: a dedicated producer creates
one paired offer per side/band with a frozen contested objective, retires stale
offers, and shared battle/stationing acceptance immediately withdraws the
opposing choice without blocking later assault phases (`551081ab`, `4f6afb8b`).

Two reusable primitives now exist on top of `CampaignState`, and they are the
seams the rest of the thread builds on:

- **`StakeLedger`** — `seizeShare` + stake queries (`findStake`, `shareOf`,
  `totalClaimedShare`, `unclaimedShare`). Tombstone-at-zero soft-delete.
- **`HousePromotion`** — `addProgress` / `addProgressAndPromote` (carry +
  cascade; TIER_4 terminal).

Both are stateless ops, fully unit-tested. The intent: Slices C–D are mostly
"call these on a tick," not new mutation logic.

## Next up — Attributed terminal consequences

Finish the last implementation slice in
[`civil-war-participation.md`](civil-war-participation.md):

1. Lock the claimant/incumbent player-reputation deltas and whether accumulated
   contribution changes their magnitude.
2. Persist a postcondition-first, exactly-once consequence lifecycle for the
   successful handoff path and the incumbent-attributed failed-chain path.
3. Consume only snapshotted/persisted attribution; autonomous `NONE` outcomes
   and mere offer acceptance remain neutral.
4. Cover retries, partial application, save/load, and terminal replay before the
   player-facing kingmaker capstone is layered on top.

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
- Slice E2a — persisted monthly ambition review (`f022d4ad`).
- Slice E2b — power/progress-gated promotion intent (`31bb6875`).
- Slice E2c — faction-targeted Tier-3 throne claims (`3c58964b`).
- Slice E2d — vertical-chain integration guards (`57be6214`).
- Slice F1a — ordinary promotion-chain contract (`0a00ceb0`).
- Slice F1b — deterministic promotion-chain creation (`156ac5b1`).
- Slice F1c — exactly-once promotion-chain resolution (`a4cd42c8`).
- Slice F2a — Tier-3 progress handoff cap (`76c7579a`).
- Slice F2b — throne-handoff contract + persistence (`49f057ad`, `9bf2356b`).
- Slice F2c — civil-war creation + handoff preparation (`bdb45f7b`, `35ec0ccf`).
- Slice F2d — discovery/intervention + replay guards (`77657bb8`, `51fad0ca`).
- Slice F3a — isolated handoff consumer (`42f00725`).
- Slice F3b — predeclared Claimant League (`ae35056d`).
- Slice F3c — vanilla ownership adapter (`870d5b96`).
- Slice F3d — post-writeback Chronicle ordering (`5174f44c`).
- Slice F4a — persisted consequence lifecycle (`ad6ff5fd`).
- Slice F4b — replay-safe diplomatic rupture (`527535fb`).
- Slice F5a — immutable faction-flip Chronicle snapshot (`d7be2649`).
- Slice F5b — faction-flip dispatch rendering (`31be86ed`).
- Slice F6a — civil-war participation contract (`926047e8`).
- Slice F6b — persisted participation schema (`4332927f`).
- Slice F6c — validated weighted/decisive contributions (`50591863`).
- Slice F6d — throne attribution snapshot (`3610923d`).
- Slice F6e — recoverable contribution integration (`487134ae`).
- Slice F7a — paired band offer generation (`551081ab`).
- Slice F7b — shared acceptance and opposing withdrawal (`4f6afb8b`).
