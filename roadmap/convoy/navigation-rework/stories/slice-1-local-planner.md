# Slice 1 — Rolling-horizon local planner

> Build `LocalTrajectoryPlanner` in isolation and prove it out with unit
> tests before it touches live motion. The heart of the rewrite.

## Goal

Given a current pose, a `ReferenceCorridor`, and a horizon `H`, return a short
**kinematically feasible** dense pose sequence from the pose toward a goal
region `H` cells down the corridor — or a clear "no forward trajectory"
signal. Bounded search → cheap, high success rate.

## What lands

- **`LocalTrajectoryPlanner`** wrapping the existing `HybridAStarPlanner`
  search core, with:
  - A **bounded window**: cap the search to a box / radius around the segment
    from the start pose to the rolling goal (don't let it flood the whole
    map). The one-shot full-path refine fails today *because* it searches
    everything; a small window almost always succeeds.
  - A **soft goal region** on the corridor rather than an exact pose — more
    slack = higher success. The corridor is advisory; we just need to make
    forward progress along it with a feasible heading.
  - Returns `Trajectory` (dense `Pose[]`, feasible by construction against the
    grid passed in) or `null` / a `NoTrajectory` result.
- **`Trajectory`** value type — dense feasible poses + total length + a
  sample-at-distance helper (mirrors how docking samples RS paths).
- Reuse `VehicleFootprint` + `PLANNER_CLEARANCE` for feasibility, and the
  bicycle successor generation + analytic (RS) expansion already in
  `HybridAStarPlanner`.

## Out of scope

- Wiring into `VehicleController` motion (slice 2). This slice produces a
  planner that's exercised only by tests.
- Recovery escalation (slice 3) — slice 1 just reports success/failure.
- Perf budgeting (slice 5) — correctness first, make it cheap-ish via the
  window, profile later.

## Acceptance

Unit tests against hand-built `NavigationGrid` fixtures:
- Straight clear corridor → trajectory roughly follows it, headings smooth
  (no >X°/cell jump).
- 90° road corner with adequate width → feasible rounded trajectory through
  the corner (the case that produces the snap today).
- Corner too tight for min-turn-radius → either a feasible 3-point/reverse
  trajectory or a clean `NoTrajectory` (so slice 3 can escalate). Never an
  infeasible-but-returned path.
- Dead-end / fully blocked window → `NoTrajectory`, bounded iteration count
  (no map-wide flood).

## Notes

- Keep the planner **pure** (pose + corridor + grid → trajectory), no `Vehicle`
  / `GroundSystem` coupling — that's what makes it unit-testable and reusable
  for future tanks / player vehicles.
- Decide window sizing here and record it: radius ~`ANALYTIC_RANGE_FACTOR ×
  turnRadius` around the goal is a reasonable start; `H` ≈ 2× min-turn-radius.
- Watch `[[zone_graph_ignores_edges]]` — feasibility is footprint-vs-grid, not
  zone flood; don't reach for ZoneGraph for reachability inside the window.
</content>
