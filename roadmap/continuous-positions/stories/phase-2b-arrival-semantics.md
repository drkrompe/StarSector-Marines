# Phase 2b — arrival semantics sweep

Replace the cell-hop idioms across behavior families. Canonical replacements
(helpers on `MovementService` / `TacticalScoring`):

- `moveProgress(id) == 0f` ("on a center, safe to act/repath")
  → `!movement.isMoving(id)` (no un-exhausted path) for "idle/done" gates,
  or `movement.arrivedAt(id, cx, cy)` = `dist(pos, center(cx,cy)) <= ARRIVE_R`
  (ARRIVE_R ~0.35f) for "reached this post" gates. Repath-while-moving is
  ALLOWED now (the carrot follower handles a path swap mid-segment) — gates
  that only existed to avoid mid-cell repath can simply drop.
- `cellX(m) == postX && cellY(m) == postY` → `arrivedAt(m, postX, postY)`.
- `setRenderPos(id, cellX(id), cellY(id))` stop-snaps → delete (a continuous
  unit just stops where it is). ~25 sites.
- `FireStance.stanceFor(moveProgress)` → gate on `isMoving`.

Families (disjoint file ownership for fan-out): infantry postures;
goap actions (`AbstractZoneAction`, `ClearZone`, `HoldZone`, `BreakContact`);
decision (`FleeBehavior`, `FallbackBehavior`); mech (`OverwatchKillZone`,
`BackstopAssignedSquad`); evacuation + command objectives; equipment drop +
turret demolition pickup gates.

Watchpoints: exact-equality post identity (`overwatchCellX`) becomes
arrival-radius; `WorldStateBuilder`/`HitResponseSystem` gates; drain-order
assumptions that a unit "occupies" its path cell.
