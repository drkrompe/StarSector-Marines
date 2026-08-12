# Phase 2a — continuous mover

Rewrite `MovementService.advanceAlongPath` as continuous integration:
carrot-following over the A* cell polyline (waypoints at cell centers),
`pos += normalize(carrot - pos) * speed * dt`, clamped so the final waypoint
is never overshot. Template: `vehicle/PurePursuit.pick` — add an overload
taking the flat `int[]` path (+0.5 centers) to avoid per-tick float[]
allocation. Lookahead ~0.6 cells for infantry (tight corners are fine —
paths are wall-legal cell-to-cell hops; lookahead must not cut through a
wall corner, so keep it < 1 and rely on the 8-dir path's wall clearance).

- `MOVEMENT` component: `MOVE_PROGRESS` field retired (replaced by a
  `pathCursor`-style int already present as `PATH_IDX`); add what the carrot
  follower needs. `MovementService.isMoving(id)` = has un-exhausted path.
- Arrival at path end: position pinned to final waypoint (cell center),
  path cleared — so downstream cell reads see exactly the destination cell.
- `RENDER_POSITION` deleted: `renderX/renderY` become *tolerant* reads of
  `POSITION` (render must not fail-loud on a maybe-released ref — keep the
  default-0 read). All `renderX(u) + 0.5f` draw sites drop the `+0.5`.
  Corpse draw keeps working (POSITION survives the transmute).
- `FacingSystem` / mech locomotion phase derive from the mover's velocity
  (direction to carrot), not cell deltas; mech pivot-in-place keeps its
  feel by gating translation on facing alignment as today.
- `mayRepath` does NOT become plain `true` at the swap: the old cell-boundary
  gate doubled as a pathfinding rate limit (~once per cell traversed, i.e.
  every ~0.3-0.5s at typical move speeds). It becomes a per-unit repath
  throttle: allow when no active path, else when a repath cooldown has
  elapsed — the retired `MOVE_PROGRESS` field slot holds the cooldown timer.
  Without this, every `mayRepath` site re-runs findPath every tick.
- `settled` becomes "no un-exhausted path"; `atCell` becomes the
  ARRIVE_RADIUS test; `moveProgress` accessors deleted (2b already swept the
  gates, so the remaining references are the mover itself + FacingSystem's
  locomotion phase).

## 2a-3 — critique findings on 2d4bad01 (fix slice)

The 2a-1 critique pass confirmed the mover core sound (repath throttle,
arrival pinning, gait cadence, conventions, released-ref safety all clean)
but found:

1. **BLOCKER — one-cell paths born exhausted.** `findPath(start==goal)`
   returns `{x,y}`; `setPath` inits `pathIdx=1` == count, so the unit is
   never pulled to the pin. Behaviors that re-issue findPath every repath
   window (HoldPortalCordon, GarrisonCordon, BreakLOS, planter…) clobber
   the in-flight path inside the final entry band (0.35–0.71 from center)
   and strand the unit: `settled` true, `atCell` false forever —
   ChargeSiteObjective gates on both → mission softlock.
   Fix: init `pathIdx = 0` for one-cell paths (PurePursuit's n==1 branch
   returns the center as an atEnd carrot; the mech pivot gate already
   bypasses a (0,0) delta). Add the missing single-cell-assignment test.
2. **Walk-pose/facing strobe.** FacingSystem's `havePathDelta`
   (nextPathCell − flooredCell) is (0,0) for the second half of every
   segment → `moving` flickers ~50% duty and targetless walkers snap to
   SOUTH each cell.
3. **Mech per-cell stutter + lost turn-step.** MechLocomotionSystem's
   zero-delta fall-through turns the chassis toward its combat target
   mid-segment; the pivot gate then freezes translation at the next
   waypoint. And mech `moving` = path-un-exhausted is true during the
   pivot gate, so `turnStepPhase` can't play.
4. **Hold-with-retained-path renders frozen mid-stride** (rocket aim,
   flee dwell) — presentation half of the deleted stop-snaps.

Unified fix (this was in fact the original 2a design above — "derive from
the mover's velocity"): MOVEMENT gains per-tick `VEL_X/VEL_Y` (cells/sec),
zeroed for all movers at tick start, written by `advanceAlongPath` when it
actually translates (and on the pin). FacingSystem: `moving` := |v| > 0;
travel bearing := sign-quantized velocity octant (replaces the path-cell
delta fallback). Mech row same; pivot-gated mech has v=0 → turnStep plays.
MechLocomotionSystem: zero path-delta holds heading (stopTurning +
continue), never falls through to target-turning while a path is
un-exhausted. Plus: clamp translation step to carrot distance (future
fast-mover overshoot); fix stale HoldPortalCordon doc.
