# Slice 1 — Rolling-horizon local planner  ✅ SHIPPED

> Build `LocalTrajectoryPlanner` in isolation and prove it out with unit
> tests before it touches live motion. The heart of the rewrite.

## Shipped

Commit: _(this commit)_ — `battle/vehicle/` local planner + `Trajectory`,
unit-tested. Not yet wired to motion (that's slice 2).

What actually landed vs. planned:

- **`Trajectory`** (new) — immutable dense pose sequence (`xs/ys/headings`),
  cumulative arc length, `lengthCells()`, `sampleAtDistance(d)` (shortest-arc
  heading interp, mirrors the old playback sampler), and `start/end/pose(i)`.
  Always ≥2 poses; the planner returns `null` (not an empty `Trajectory`) for
  "no trajectory".
- **`HybridAStarPlanner.planLocal(...)`** (new, additive) — the bounded
  rolling-horizon search. Reuses the existing private helpers (`Node`,
  `heuristic`, `tryAnalyticExpansion`, `headingBinFor`, `stateIndex`,
  successor gen, RS analytic) so the proven `refine()` path is **untouched**
  (zero regression risk; consolidating the two onto a shared core is deferred
  until slice 2 retires the full-path refine usage). Two differences from
  `refine()`: (a) successor expansion **and** the grid-distance flood are
  clamped to a cell window; (b) acceptance is a goal **radius** (any heading)
  rather than exact cell+heading — plus the usual RS analytic shortcut to the
  exact goal pose. `computeGridDistance` gained a windowed overload (old 5-arg
  delegates to it with the whole grid).
- **`LocalTrajectoryPlanner.plan(start, corridor, type, grid)`** (new) — pure
  policy layer: derives the horizon (`max(6, 2.5×turnRadius)`), the soft goal
  via `corridor.targetAhead`, the goal radius (`0.75×turnRadius`, floor 1.5),
  and the search window (start↔goal span + `turnRadius + ½footprint + 2`
  slack), then calls `planLocal` and wraps the result in a `Trajectory`.
- **Constants recorded** on `LocalTrajectoryPlanner` (`HORIZON_*`,
  `GOAL_RADIUS_*`, `WINDOW_SLACK_CELLS`, `LOCAL_MAX_ITERATIONS=4000`) —
  slice-1 starting values, to be tuned in slice 4.

Tests (`LocalTrajectoryPlannerTest`, all green): straight clear lane → smooth
feasible trajectory staying near centerline; wide 90° corner → feasible rounded
trajectory with a real (>30°) net turn and **no heading jump >40°/pose** (the
explicit anti-snap assertion); walled-off goal → `null`; 3-cell tight elbow →
`null`-or-feasible, never an infeasible returned path. `Global.getLogger` in
`HybridAStarPlanner`'s static init is headless-safe (tests construct the
planner directly).

Note (informs slice 2): a single local plan from well *before* a bend turns
only slightly — the full corner is rounded over several replans as the vehicle
advances. The slice-2 replan cadence has to be frequent enough that the rolling
goal keeps marching down the corridor.

## Critique follow-up (post-ship, same slice)

A background critique pass found no correctness bug in the output contract
(every returned `Trajectory` is feasible-by-construction and ≥2 poses). Fixed
in-slice:

- **Degenerate end-goal facing** (`ReferenceCorridor.targetAhead`): when the
  carrot coincides with the pose at the corridor end, facing now falls back to
  the final-segment direction instead of collapsing to an arbitrary 0°
  (`facingToward(0,0)`), which had been biasing the RS tail / turn-cost
  heuristic toward due-north.
- **Goal-cell-outside-window guard** (`HybridAStarPlanner.planLocal`): a caller
  passing a window that excludes the goal cell now returns `null` immediately
  rather than running a heuristic-less uniform-cost search to the iteration cap.
- **Coincident-pose dedup** (`extractLocal`): consecutive duplicate positions
  (RS sampling + appended exact goal) are collapsed so `Trajectory.cum` is
  strictly increasing; the later heading wins so the terminal goal facing isn't
  lost.
- **Heuristic admissibility doc**: windowed grid-distance is now documented as a
  non-admissible *estimate* (a cell whose true path detours outside the window
  over-estimates) — callers must not assume cost-optimality.
- **Test rigor**: the tight-corridor test no longer passes trivially — its start
  was moved to the bend so the rolling goal lands deep in the horizontal leg,
  out of soft-radius reach from the vertical lane, so a straight stub can't
  satisfy it (the planner must round the elbow → `null` in a 3-wide corridor);
  the corner test now asserts the turn goes the *correct* way (toward east, not
  just >30° magnitude); a new fixture starts 30° off the corridor to exercise
  the previously-untested start→first-lattice transition.

Routed to **slice 2** (fixes belong in the controller / a later consolidation):
the near-goal `null` must be read as arrival (check `corridor.atEnd` before
planning) not as a stuck signal; and `planLocal`/`refine` collapse onto one core
once full-path `refine` is retired. Both recorded in
[`../stories/slice-2-live-tracking.md`](../stories/slice-2-live-tracking.md).

---

## Original plan (for reference)

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
