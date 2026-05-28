# Convoy → reinforcement integration (Stage 2 item 1)

The visible payoff: convoys stopped being a debug toy and became a real
reinforcement means in the Conquest loop. The **orchestration** layer
(why/when reinforcements arrive, the means-provider abstraction) is owned
by [`../../reinforcement/architecture.md`](../../reinforcement/architecture.md);
this doc seals the **convoy-specific** side — what the convoy provider
does once the reinforcement system hands it a request.

## Commit chain

```
1315241  battle: resource blackboard — compound-driven reinforcement tickets
11b2c9f  battle/compound: slice 3 — trigger + means gating on compound state
11b012f  battle: ConvoyMeans avoids stacking trucks at the same junction
ef4cfeb  battle: per-faction unit roster — fixes thematic mismatch on deboard
4096a1d  battle: filter convoy entry to defender side + halve reinforcement rate
```

## What landed

`ConvoyMeans implements ReinforcementMeans` (in
`battle/reinforcement/`). `DEBUG_SPAWN_TEST_CONVOY` is now legacy —
retained for emergency rollback, not called from any active path.

- **Trigger / gating.** Dispatch is resource-gated via `BattleResources`
  — compound-driven ticket production (ARMORY → REINFORCEMENT). The
  compound-as-supply gate in `canFulfill` checks
  `hasAliveCompound(ARMORY, DEFENDER)`: once every armory flips
  marine-held, the trucks have nothing to ferry and the dispatcher falls
  through to walk-in / shuttle. ARMORY captures naturally retire convoy
  in priority order without explicit re-ordering.
- **Destination = rally point.** `dispatch` routes to a road-graph
  junction near the request's rally `(rallyX, rallyY)` rather than V1's
  fixed trunk crossing. The entry-pick (sort perimeter by distance →
  BFS-flood reachable component per candidate → best interior junction
  within it, degree 3 → 2 fallback) is the disconnected-graph fix from
  [`v1-polish.md`](v1-polish.md), now living here.
- **Defender-side perimeter filter.** `defenderSidePerimeter` excludes
  the marine entry edge so convoys don't spawn behind the player's
  beachhead; the two lateral edges stay valid (neutral flanks).
- **Militia loadout.** Deboard routes through the per-faction unit roster
  (`ef4cfeb`) instead of the default `COMBATANT`, fixing thematic
  mismatch.
- **Multi-convoy LZ separation.** `MIN_DEST_SEPARATION` (4 cells) keeps a
  fresh dispatch's destination junction clear of already-active trucks'
  LZs — a *soft* preference (exhausted before degrading to no-separation),
  so a clogged rally still resolves rather than failing. This is the LZ
  half of [`../stories/multi-truck-convoys.md`](../stories/multi-truck-convoys.md);
  same-road staggered following is still open.
- **Pathing.** Inbound + outbound run through `HybridAStarPlanner.refine`
  / `ConvoyPlanner.refineWithFallback`; departure picks a fresh exit gate
  via `pickExitNode` (the pose-playback work from `v1-polish.md`).

## Still open in this seam

- **Deboarded squad → commander.** The militia squad enters the same
  "free agent" pool the marine commander picks up; no explicit
  registration. See the Parked list in [`../overview.md`](../overview.md).
