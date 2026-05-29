# Slice B — player stake transfer + promotion

Implementation of Slice B from [`../overview.md`](../overview.md) §"Slice
spine". Takes the houses graph from "seeded but inert" (Slice A) to "player
ops leave a permanent mark on it." A victorious contract mission now moves
industry share from the contract's target house to the patron and accrues the
patron's promotion progress — the first rung of the impact ladder
([`../../themes.md`](../../themes.md)) made real.

Builds directly on Slice A's genesis seeding (the contested stakes this slice
moves) and the contract bridge from
[`../../contracts/complete/contracts-loop.md`](../../contracts/complete/contracts-loop.md)
(the `MissionResolver` writeback path it hooks into).

## What landed

Two reusable, stateless operation classes on top of `CampaignState` — the
"higher-level transfer operations" the data class's header says belong on top
of it, and the seams Slices C–D plug their autonomous loops into:

```
src/main/java/com/dillon/starsectormarines/campaign/
  StakeLedger.java       Stake-table operations. seizeShare(from,to,market,
                         industry,amount) sources from the loser first, then the
                         unclaimed remainder, so a strike always plants a
                         foothold. Plus findStake / shareOf / totalClaimedShare /
                         unclaimedShare queries. Tombstone-at-zero soft-delete
                         (no row removal — preserves stakeIndexById per
                         architecture.md §1). Maintains the one-row-per
                         (house,market,industry) invariant.
  HousePromotion.java    Rank-ladder operations. addProgress (clamped, floored —
                         negative deltas suppress) and addProgressAndPromote
                         (threshold cross + remainder carry + multi-tier cascade,
                         mirroring the captain XP ladder). TIER_4 is terminal —
                         the hand-off to the T3 endgame thread. Side-effect hook
                         (onPromoted) logs; visibility/edge expansion deferred to
                         the relationship sim, contract-unlock is pull-based.
```

Wired into the existing resolver bridge:

```
src/main/java/com/dillon/starsectormarines/ops/MissionResolver.java
  + applyPoliticalShift(state,row,outcome,day)   called on every victorious
      contract mission inside applyContractBridge. Reads the contract's target
      house + the struck industry (outcome.targetIndustryId), seizes
      CONTRACT_STAKE_SEIZE (20/255 ≈ 8%) from target→patron, and accrues
      CONTRACT_PROMOTION_PROGRESS (15; T1→T2 threshold is 100). Mechanism is in
      the primitives; magnitudes are policy constants here.
```

## Key decisions

- **Contested ground is the target's market, not the patron's.**
  `ContractGenerator` picks patron and target sector-wide, so they usually sit
  on different markets. The transfer therefore reads as the patron *expanding
  into the rival's turf* (gaining share on the target's market), not
  consolidating a shared one. Market-local targeting — so repeated strikes
  contest a single market — is a `ContractGenerator` refinement left for a
  later slice (see follow-ups).
- **Winner's gain is sourced loser-first, then unclaimed.** A pure zero-sum
  transfer would no-op when the target doesn't hold the struck industry (only
  ~2 of N houses hold a stake in any given industry after seeding), leaving no
  mark. Topping up from the open remainder guarantees a successful strike is
  always visible, while the 255 ceiling keeps the industry's claimed total
  conserved.
- **Promotion fires eagerly on the triggering mission**, using the same
  primitive the tick loop will use, rather than deferring to the next daily
  tick. One promotion implementation, two callers (player now, autonomous
  next) — `mechanics.md`'s "tick promotes" sketch becomes "whoever bumps,
  promotes."
- **Remainder carries on promotion** (progress -= threshold), matching the
  in-repo captain XP ladder rather than `mechanics.md`'s reset-to-zero sketch —
  no progress is lost on a big bump.

## Tests

```
src/test/java/com/dillon/starsectormarines/campaign/
  StakeLedgerTest.java       8 cases: zero-sum when loser covers it, top-up from
                             unclaimed, pure-unclaimed claim, bounded by a nearly
                             full pie, tombstone revival (no duplicate row),
                             self/zero no-ops, id->index on new rows, unclaimed math.
  HousePromotionTest.java    5 cases: sub-threshold bump, single promote + carry,
                             multi-tier cascade, TIER_4 terminal + short clamp,
                             negative-delta suppression floor.
```

## Follow-ups (not in scope here)

- **Market-local contract targeting.** `ContractGenerator.pickStrikeTarget` is
  sector-wide; biasing it toward rivals on (or near) the patron's market would
  make transfers consolidate a contested market — closer to the "House
  Drennar's stake transferred to House Korvath" framing in `mechanics.md`.
- **Relationship affinity tank + player rep on the target.** `mechanics.md`'s
  `onMissionVictory` also drops patron↔target affinity and the player's rep
  with the burned target. Affinity needs seeded relationship edges (none yet);
  target-side player rep is a small add once that edge exists.
- **Tier-scaled transfer amounts.** `CONTRACT_STAKE_SEIZE` is a flat constant;
  `mechanics.md` wants T2/T3 missions to move materially more.
