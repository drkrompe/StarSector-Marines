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
- `moveProgress` accessors deleted → compile errors are the worklist for
  phase 2b (intentional: forces every gate through review).
