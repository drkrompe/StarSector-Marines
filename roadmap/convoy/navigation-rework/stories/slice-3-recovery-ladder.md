# Slice 3 — Recovery ladder

> Make failure graceful. When a vehicle stops making progress, **detect it and
> build a different plan** — not track the same plan harder. Two escalations the
> playtest pointed at: lap around (re-route avoiding the failing spot) and
> reverse to a pose that makes the missed turn work.

## The core reframe (from playtest)

The stuck cases are **non-convergence**, not wall-contact. The dump that drove
this (truck orbiting a 90° turn into a 3-wide corridor it can't round at its min
turn radius) had `wallStuckTime == 0` for 120 ticks — it never touched a wall.
The body limit-cycled `+0.95 → −0.49 → +0.95` (the `alpha > 90°` reverse flip)
making zero net progress. So:

- **Detection is progress-based, not contact-based.** "Stalled" = corridor
  remaining-length hasn't dropped a margin in `STALL_SECONDS`. This subsumes
  wall-stuck (a wall bump is just one cause of no progress) AND catches the
  open-space orbit the contact check missed.
- **Recovery builds a genuinely different plan.** Faithful playback of the
  planner's trajectory is the rails we deleted — rejected. Routing restrictions
  that shrink where vehicles may go — rejected (bandaid). The truck should notice
  it's failing and try something *qualitatively* different.

## The ladder

1. **Feasible drift** — off the local trajectory but on a feasible cell, a fresh
   plan exists → keep tracking; pure pursuit self-corrects. (No escalation.)
2. **Wall bump** — a forward move hits a wall → the committed reverse recovery
   already in `VehicleController` (`Recovery.REVERSING` + `maxReverseDistance`):
   back up the achievable distance, replan forward. Fast, local.
3. **Stalled → re-route ("lap around the building").** No net progress toward the
   goal for `STALL_SECONDS` (the orbit, or repeated failed reverses) → re-invoke
   the **cost router** from the current pose to the goal, *avoiding a radius
   around the stuck cell*, and adopt the new corridor. The genuinely different
   path. Uses `VehicleRoutePlanner` (cost-field), not the retired road graph.
4. **Dead** — the avoiding re-route returns no path (genuinely boxed in / the
   only way is through the spot it can't drive) → graceful give-up: hold without
   thrashing, log. (Despawn / deload-in-place is a later option.)

Rung 3 is the headline this slice. Rung 2 shipped already. Reverse-to-a-pose-that-
makes-the-turn (the planner's reverse successors as true 3-point extraction) is a
natural rung 2.5 / follow-up; rung 3's re-route handles the same cases by going
around, which is more robust when the turn is simply impossible.

## What lands (this slice)

- **Stall detection** in `VehicleController.advance` — a progress timer on
  corridor remaining-length, running every tick (so it sees open-space orbits,
  not just wall contact). Exposes `consumeRerouteRequest()` → the stuck cell (or
  none), consumed once like `consumeArrived()`.
- **Avoiding re-route** — `VehicleRoutePlanner.route(...)` overload that treats a
  disc around an avoid-cell as impassable (on top of the clearance mask), so the
  search detours. `snapToMask` promoted from `ConvoyMeans` to `VehicleRoutePlanner`
  (shared by spawn + re-route).
- **Re-route plumbing** — the per-battle `TerrainCostField` + `VehicleClearance`
  stash on the `Vehicle` (set by `ConvoyMeans`); the goal is the route's last
  on-grid waypoint (already on the vehicle — no goal-cell plumbing needed).
  `GroundSystem`, on a reroute request, snaps current+goal into the mask,
  re-routes avoiding the stuck disc, rebuilds the direction's waypoint array
  (re-appending any off-map exit tail), and tells the controller to adopt the new
  corridor. Null re-route → give-up hold.

## Out of scope

- Tuning thresholds (slice 4).
- Dynamic-obstacle *prediction* — reacts to the current grid only.
- The deeper cure (turn-aware clearance so the router never picks a gap the truck
  can't turn into) — tracked in `cost-field-routing`; rung 3 makes it non-fatal
  meanwhile.

## Acceptance

- The orbiting-at-a-narrow-corner truck detects the stall and **takes a different
  route** to the LZ (or gives up gracefully if none exists) — no permanent orbit.
- A wreck/wall across the route still triggers the reverse (rung 2) or re-route
  (rung 3) and the vehicle finds the alternate or gives up — never drives through
  a wall, never jitters forever.
- Normal cornering is unaffected (no false stalls on legitimate slow turns —
  `STALL_SECONDS` is generous enough for rung-2 reverses to resolve first).

## Notes

- `[[breakcontact_no_sticky_dest]]`: re-roll the recovery target; don't fixate on
  a cached cell that's no longer valid. The avoid-disc moves with each stall.
- Give-up is observable in logs, quiet on screen.
