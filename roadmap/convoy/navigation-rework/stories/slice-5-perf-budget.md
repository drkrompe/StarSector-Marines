# Slice 5 — Perf budget

> Pay the cost of running a planner continuously. Deferred until multi-truck
> convoys make N simultaneous planners actually bite.

## Goal

Keep the now-continuous `LocalTrajectoryPlanner` within tick budget when
several vehicles plan at once, without sacrificing the "good results" the
rework bought.

## Levers

- **Amortize replans across vehicles.** Stagger replan ticks so not every
  vehicle re-plans the same frame; round-robin a per-frame planning budget.
- **Cache the grid-distance field.** `computeGridDistance` (backward Dijkstra
  to goal) is recomputed per refine today — for a static grid + fixed corridor
  goal it can be cached per corridor and shared across replans / vehicles on
  the same route.
- **Cap & reuse.** Bounded window already caps iterations (slice 1); make sure
  the cap is tight, and pool/reuse the planner's open-set / node maps to cut
  allocation churn.
- **Skip when unnecessary.** Don't replan if the current trajectory is still
  valid and the vehicle is tracking it within tolerance — event-driven replan
  beats fixed-cadence for the common straight-road case.

## Out of scope

- Multi-threading the planner (Starsector tick is single-threaded; keep it so).
- The route-finding layer (`ConvoyPlanner` BFS) — it's already cheap and runs
  rarely.

## Acceptance

- A multi-truck convoy ([`../../stories/multi-truck-convoys.md`](../../stories/multi-truck-convoys.md))
  of 4 vehicles holds frame budget with no visible hitch on replan frames.
- Profile (JFR per `[[jfr_analysis_workflow]]`) shows planning cost flat as
  vehicle count rises, not linear-per-tick.

## Notes

- Respect `[[feedback_ship_then_optimize]]`: don't pre-optimize in slices 1–2.
  This slice exists precisely so the earlier ones can stay simple. Only build
  the caching/amortization that the profiler says is needed.
- Static-grid assumption holds today; if wrecks become common
  ([`../../stories/vehicle-damage.md`](../../stories/vehicle-damage.md)) the
  grid-distance cache needs invalidation on grid change — note it, don't
  pre-build it.
</content>
