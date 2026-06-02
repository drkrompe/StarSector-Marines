# Vehicle Navigation Rework

> Long-form design doc for the ground-vehicle motion overhaul. Open this
> when picking up the navigation rework cold. Substrate beneath
> [`../overview.md`](../overview.md) — every ground vehicle (convoy trucks,
> the APC, future tanks / player vehicles) rides on this stack.

## The problem

Vehicles "follow logical paths" badly. Two symptoms, one root cause:

1. **Instant 90° corner snaps.** On road bends the vehicle pivots ~90° in a
   fraction of a second while sliding through a right angle — not a vehicle
   model at all, just a grid-aligned polyline being replayed.
2. **Recovery fails spectacularly.** When a vehicle deviates or wedges, it
   rarely recovers — it drives through walls, jitters, or sits stuck.

### Root cause — the `headings != null` fork

`GroundSystem.advancePath` dispatches on one question: *does this path carry
per-pose headings?*

```java
if (headings != null) { advancePlayback(...); return; }   // dead-reckon the rails
... else PurePursuit + reactive recovery ...
```

But `ConvoyPlanner.refineWithFallback` **always returns headings** — when
Hybrid A* fails it synthesizes them with `deriveSegmentHeadings` (bearing to
the next waypoint). So in practice vehicles are *almost always on
`advancePlayback`*, and the pure-pursuit branch (with all the recovery logic)
is nearly dead code.

That single fact produces both symptoms:

- **90° snaps:** `deriveSegmentHeadings` runs over raw road-graph cell centers
  (grid-aligned). At a corner the heading jumps 90° between adjacent cells, and
  `advancePlayback` linearly interpolates heading across a 1-cell segment → a
  near-instant pivot through a right angle. It's a polyline a real car can't
  drive, replayed as if it could. The prefix-refine path has the same defect
  on its raw-cell suffix.
- **No recovery:** `advancePlayback` has **no feasibility check and no
  recovery** — it teleports the body along the rails regardless of walls. All
  the wall-stuck / reverse-pulse / re-plan logic lives only on the pure-pursuit
  branch, which the synthetic-heading fallback bypasses. The cases that most
  need recovery never reach it.

So we don't have "a flaky controller with a playback safety net." We have
**two playback sources conflated** — kinematically feasible HA* poses (smooth)
and infeasible coarse polylines (90° corners) — *both dead-reckoned on rails*,
with the actual bicycle controller mostly sidelined.

## How other games structure this

The near-universal pattern separates layers and **never dead-reckons a coarse
plan**:

- **Route / global plan** — topological, coarse, may have right angles
  (navmesh, grid A*, or our road-graph BFS). *Advisory.*
- **Reference smoothing / local planning** — turn the coarse route into
  something physically drivable *before* anyone follows it.
- **Tracking controller** — the actual vehicle dynamics follow the reference
  and *own the pose*. The path is a suggestion it tracks, **not a rail it's
  snapped to.** This is what makes motion read as natural — the body always
  obeys its own kinematics.
- **Local avoidance / recovery** — reactive layer for drift or blockage.

The DARPA-lineage stack we're already half-using (Hybrid A* + pure pursuit) is
exactly this — but in real systems **Hybrid A* runs on a rolling horizon,
continuously, as a *local* planner**, and pure pursuit tracks it live. It is
never computed once and dead-reckoned. The reframe: *playback-on-rails is only
appropriate for a tight, pre-validated maneuver in a small window* — which is
precisely our Reeds-Shepp docking, and nothing else.

## Target architecture

Collapse the fork. **Always run the bicycle controller; never dead-reckon a
coarse path.** Layers, mapped onto the codebase:

```
ConvoyPlanner (road-graph BFS)
        │  coarse cell-center polyline  ── the REFERENCE CORRIDOR (advisory)
        ▼
ReferenceCorridor          cursor + "give me a target pose H cells ahead" query
        │
        ▼
LocalTrajectoryPlanner     rolling-horizon Hybrid A*: current pose → a point
        │                  H cells down the corridor → short FEASIBLE trajectory
        │  dense feasible poses (refreshed every K ticks / on deviation)
        ▼
VehicleController          owns the pose. Tracks the local trajectory with
        │                  BicycleBody + PurePursuit EVERY tick. Monitors
        │                  deviation / blockage / progress. Triggers replans.
        ▼
Recovery ladder            feasible-drift → keep tracking
                           blocked        → replan (reverse / 3-point allowed)
                           stuck          → full re-route from nearest road node
                           dead           → graceful giveup
```

Docking (Reeds-Shepp validated playback) survives as the one legitimate
rails case — a special terminal phase of the local planner in the tight LZ
window.

### Why rolling-horizon local HA* (not corner-rounding / splines)

A cheap corner-rounding pass would kill the 90° snaps with far less work, and
that was the lower-risk option. We're deliberately taking the bigger rewrite
because we want **"good" results that generalize**, not a patch:

- The local planner is **feasible by construction** against the *current* grid,
  so it naturally handles dynamic obstacles — wrecks blocking roads
  ([`../stories/vehicle-damage.md`](../stories/vehicle-damage.md)), other
  trucks ([`../stories/multi-truck-convoys.md`](../stories/multi-truck-convoys.md)),
  marines in the road
  ([`../stories/truck-infantry-interaction.md`](../stories/truck-infantry-interaction.md)).
  A static smoothing pass can't.
- Bounding HA* to a short window makes it **cheap and high-success** — the
  one-shot full-path refine that fails today (→ the 90° fallback) was failing
  precisely because it searched the whole map. A small rolling window almost
  always finds a path.
- Recovery becomes a first-class part of the loop instead of an ad-hoc
  reverse-pulse bolt-on: "replan the next H cells" *is* the recovery.

This is also the substrate for the parked **player-side tanks** (hull turret
firing while driving) — same controller, same local planner.

## Component inventory

New:

- **`ReferenceCorridor`** — wraps the coarse route polyline + a progress
  cursor. Query: "target pose `H` cells ahead of my current pose," "remaining
  length," "am I off-corridor by more than `D`." Replaces the raw
  `xs/ys/waypointIndex` tri+ `PurePursuit.pick` carrot bookkeeping.
- **`LocalTrajectoryPlanner`** — rolling-horizon wrapper over the existing
  Hybrid A* search, bounded to a small window. Returns a short dense feasible
  pose sequence, or signals "no forward trajectory" (→ recovery escalation).
- **`VehicleController`** — owns one vehicle's motion: current local
  trajectory, the tracker (BicycleBody + PurePursuit), replan cadence,
  deviation/blockage monitors, and the recovery ladder. Replaces the
  `advancePath` / `advancePlayback` / `advanceDocking` tangle in
  `GroundSystem`. `GroundSystem`'s state machine calls
  `controller.tick(dt)` and reads pose off the body.

Reworked / removed:

- **`HybridAStarPlanner`** — keep the search core; it gets driven by the local
  planner on small windows instead of one-shot full-path. May grow a
  "plan toward a *pose* with a soft goal-region" mode for the rolling target.
- **`ConvoyPlanner.refineWithFallback` + `deriveSegmentHeadings`** — **deleted.**
  The route stops carrying synthetic headings; ConvoyPlanner returns the coarse
  corridor only. No more dead-reckon fallback.
- **`GroundSystem.advancePlayback`** — **deleted** for the main path
  (docking-style playback moves into the controller's docking phase).
- The ad-hoc `WALL_REVERSE_*` / `REPLAN_STUCK_*` constants in `GroundSystem` —
  subsumed by the recovery ladder.

Unchanged: `BicycleBody` / `GroundBody` kinematics, `PurePursuit` carrot math
(now tracking the *local* trajectory, not the coarse polyline), `ReedsShepp`,
`VehicleFootprint`, the road graph and `ConvoyPlanner.planPath` /
`expandToWaypoints` route-finding.

## Story decomposition

Each slice leaves the game working. No backward-compat constraint — we delete
the old fork rather than keep it alongside.

| # | Story | What it lands |
| --- | --- | --- |
| 0 | [`slice-0-controller-seam`](stories/slice-0-controller-seam.md) | Extract `VehicleController` + `ReferenceCorridor`; `GroundSystem` calls `controller.tick(dt)`. **No behavior change** — wraps today's advance* logic behind the seam. De-risks the rest. |
| 1 | [`slice-1-local-planner`](stories/slice-1-local-planner.md) | `LocalTrajectoryPlanner`: rolling-horizon HA* over a bounded window. Unit-tested standalone against `NavigationGrid`. Not yet wired into motion. |
| 2 | [`slice-2-live-tracking`](stories/slice-2-live-tracking.md) | Controller tracks the rolling local plan with BicycleBody+PurePursuit every tick; replan every K ticks / on deviation. **Delete `advancePlayback`, `refineWithFallback`, `deriveSegmentHeadings`.** The 90° snaps die here; motion is always kinematic. |
| 3 | [`slice-3-recovery-ladder`](stories/slice-3-recovery-ladder.md) | Formal escalation: drift → blocked → stuck → giveup. Replaces ad-hoc wall-stuck/reverse-pulse. Reverse / 3-point extraction via the local planner. |
| 4 | [`slice-4-tuning-feel`](stories/slice-4-tuning-feel.md) | Lookahead-vs-speed curve, replan cadence, horizon length, corner speed taper. Folds in [`../stories/driving-feel-tuning.md`](../stories/driving-feel-tuning.md). Playtest pass. |
| 5 | [`slice-5-perf-budget`](stories/slice-5-perf-budget.md) | Budget the now-continuous planner: amortize replans across vehicles, cache grid-distance fields, cap iterations. Matters once multi-truck convoys run N planners. |

### Why this order

0 establishes the seam so 1–3 don't thrash `GroundSystem`. 1 builds the
planner in isolation where it's testable before it can break live motion. 2 is
the payoff — it flips to live tracking and deletes the dead-reckon fork, which
is what fixes the visible bug. 3 makes failure graceful. 4 is feel. 5 is the
cost of running planners continuously, deferred until multi-truck makes it
bite.

## Open questions

- **Replan trigger policy.** Fixed cadence (every K ticks), event-driven
  (deviation > D, or blockage detected), or both? Lean both: cheap monitors
  each tick, replan on event or when the current trajectory is < H/2 consumed.
- **Horizon length `H`.** Long enough to cover a corner + a bit of straight
  (so corners are planned, not reacted-to), short enough to stay cheap and
  high-success. Start ~2× min-turn-radius; tune in slice 4.
- **Soft vs. hard rolling goal.** Plan to an exact pose `H` cells ahead, or to
  a goal *region* on the corridor (more slack = higher success)? Region is
  probably right; the corridor is advisory.
- **Docking integration.** Keep RS docking as a distinct terminal phase, or
  let the local planner's analytic-expansion (already RS-based) subsume it?
  Probably keep distinct in slice 2, revisit.

## Cross-references

- [`../overview.md`](../overview.md) — convoy feature this sits beneath.
- [`../stories/driving-feel-tuning.md`](../stories/driving-feel-tuning.md) —
  absorbed by slice 4.
- [`../stories/multi-truck-convoys.md`](../stories/multi-truck-convoys.md),
  [`vehicle-damage`](../stories/vehicle-damage.md),
  [`truck-infantry-interaction`](../stories/truck-infantry-interaction.md) —
  dynamic-obstacle payoffs the local planner unlocks.
- Memory: `[[ground_vehicle_kinematics]]`, `[[ground_vehicle_playback]]`,
  `[[road_graph_design]]`, `[[zone_graph_ignores_edges]]`.

## How this directory is laid out

- **`overview.md`** (this file) — diagnosis, target architecture, decomposition.
- **`stories/`** — active/queued slice docs (slice-0 … slice-5).
- **`complete/`** — sealed shipped slices (commit hash + what actually landed).
- **`next-session.md`** — handoff state for picking up cold.
</content>
</invoke>
