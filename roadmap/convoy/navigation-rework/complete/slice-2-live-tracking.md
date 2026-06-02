# Slice 2 — Live tracking replaces playback  ✅ SHIPPED

> The payoff slice. Flip from dead-reckon-on-rails to always-kinematic
> tracking, and **delete the synthetic-heading fork**. The 90° snaps die here.

## Shipped

Commit: _(this commit)_ — full project builds, all tests green. **Acceptance is
partly visual ("verify by eye in a convoy run") — that playtest is still
outstanding; this records what landed in code.**

What actually landed vs. planned:

- **`VehicleController.tick` rewritten to always-kinematic tracking.** Each tick:
  `corridor.advance(pose)` keeps the advisory cursor abreast → terminal RS
  docking (inbound, unchanged) → arrival check → rolling local-trajectory
  refresh → pursue a carrot (BicycleBody + PurePursuit) → shared wall-stuck
  reverse stub. The body's kinematics own the pose every tick; nothing is
  teleported along synthetic-heading rails anymore.
- **Two carrot sources, one tracker.** Primary: the rolling `Trajectory` from
  `LocalTrajectoryPlanner.plan` (refreshed every `REPLAN_INTERVAL_SEC = 0.25s`,
  on consume — carrot pinned to traj end or past 50% — or on >2-cell corridor
  drift). Fallback when `plan()` returns `null` (the off-map approach before the
  truck reaches the grid, or a transient planner gap): pursue the **coarse
  corridor** directly. Both are kinematic, so neither snaps — the deleted thing
  was *playback*, not pursuit.
- **Brake taper** always sizes to `corridor.remainingLength` (distance to the
  LZ), not the local horizon, so the truck still stops cleanly at the end
  regardless of where the rolling trajectory currently ends. (Deviation from the
  doc's "trajectory length + corridor length" — adding the two double-counts and
  would brake early; distance-to-LZ is the proven taper from the old pursuit
  branch.)
- **Deletions (no backward compat):** `GroundSystem.advancePlayback` *(already
  gone — it had moved onto the controller in slice 0; the controller's copy is
  now deleted)*, `VehicleController.tryReplan` / `polylineLength`,
  `ConvoyPlanner.refineWithFallback` + `deriveSegmentHeadings`, the
  `Vehicle.inboundHeading` / `outboundHeading` / `pathRefined` fields, and the
  spawn-time `HybridAStarPlanner.refine` calls in `ConvoyMeans` + `BattleSetup`
  (both sites now hand the vehicle the coarse `expandToWaypoints` corridor only).
- **`HybridAStarPlanner.refine` + `extractPath` + their refine-only helpers
  deleted.** With the one-shot full-path planner retired, only `planLocal`
  remains — which **resolves slice-1 critique #7 outright** (no second A* loop
  left to drift from; nothing to consolidate). `planLocal` / `extractLocal` and
  all shared helpers are untouched.
- **Slice-1 critique #1 handled:** the arrival check runs *before* asking for a
  plan, so the near-corridor-end `null` (start already inside the soft goal
  radius) reads as arrival/coast-in, never a stuck/recovery signal.
- **Docking unchanged** — RS terminal LZ phase survives as the one legitimate
  rails case; engaging it now also clears the current trajectory.
- **Debug readers updated:** the overlay/dumper show `tracking` / `docking` /
  `corridor` and `trajectoryProgress` instead of the old `playback` / `HA*` /
  `coarse` + `playbackProgress`.

Constants (`REPLAN_INTERVAL_SEC`, `REPLAN_DRIFT_CELLS`, `REPLAN_CONSUMED_FRACTION`)
are slice-2 starting values — tuned for feel in slice 4.

---

## Goal

`VehicleController` tracks the rolling local trajectory (slice 1) with
`BicycleBody` + `PurePursuit` **every tick**, refreshing the local plan every
`K` ticks or on deviation/blockage. The body's own kinematics always govern
the pose — no segment is ever teleported along synthetic-heading rails.

## What lands

- Controller loop per tick:
  1. If no current local trajectory (or it's mostly consumed / vehicle drifted
     off it), ask `LocalTrajectoryPlanner` for a fresh one toward the corridor
     goal region.
  2. `PurePursuit.pick` a carrot **along the local trajectory** (not the coarse
     corridor), derive target speed from remaining trajectory + corridor
     length (keep the `sqrt(2·brake·remaining)` taper into the LZ).
  3. `body.tick(carrot, targetSpeed, dt)`.
  4. Advance corridor cursor by projecting the new pose onto the corridor.
- **Deletions** (no backward compat):
  - `GroundSystem.advancePlayback` (main-path dead-reckoning).
  - `ConvoyPlanner.refineWithFallback` and `deriveSegmentHeadings`.
  - The `inboundHeading` / `outboundHeading` arrays on `Vehicle` and the
    `headings != null` dispatch — routes now carry only the coarse corridor.
- **Docking** (`advanceDocking` / Reeds-Shepp) stays as a distinct terminal
  phase in the LZ window — still the one legitimate rails case.

## Out of scope

- Recovery escalation beyond "replan failed → hold position / brake" stub
  (slice 3 makes it a proper ladder).
- Feel tuning of `K`, `H`, lookahead curve (slice 4 — pick sane constants now).
- Perf amortization (slice 5).

## Acceptance

- **No 90° snaps.** On road corners the vehicle traces a continuous
  min-radius-respecting arc; heading rate stays within the bicycle model's
  limit at all times. This is the headline visual fix — verify by eye in a
  convoy run with a cornering route.
- Inbound truck still arrives at the LZ with a sane heading and transitions
  LANDED; departing truck still reaches GONE.
- HA*-failure no longer degrades to ugly playback — a failed *local* plan
  just means "replan next tick / brake," never a synthetic-heading rail.
- Docking into the LZ unchanged.

## Carried in from the slice-1 critique

- **Near-goal `null` must not alias "stuck".** `LocalTrajectoryPlanner.plan`
  returns `null` when the start is already inside the soft goal radius (≈3
  cells for the APC) — and as a vehicle nears the corridor end the rolling goal
  pins to the endpoint, so the start sits inside that radius every tick over the
  last few cells. The tracking loop must **check `corridor.atEnd(pose, …)`
  (or `dist(pose, goal) ≤ goalRadius`) *before* asking for a plan** and treat
  that as arrival/idle, never as a failed plan that escalates to recovery.
  Otherwise every successful LZ approach reads as a false stuck.
- **Consolidate `planLocal` / `refine` once full-path `refine` is retired.**
  They share ~120 lines (kinematics unpack, steer-angle table, the whole
  open/closed A* loop + successor gen + analytic block). When slice 2 stops
  calling the one-shot `refine`, collapse both onto one private core
  parameterized by a goal-test + window so the two loops can't drift (e.g. a
  heuristic fix landing in one but not the other). Deferred, not a blocker.

## Notes

- This is the slice to attach a **critique-pass background agent**
  (`[[feedback_critique_pass]]`) — it's the behavioral pivot most likely to
  hide a regression (LZ arrival precision, corridor-cursor projection drift).
- Keep `[[air_unit_render_sync]]` in mind: whatever the renderer reads
  (cellX/Y, renderX/Y) must sync from `body.x/y` each tick now that the body —
  not a playback sampler — owns the pose.
- Expect the `Vehicle` motion-field surface to shrink a lot here; fold the
  `next-session.md` update into the same commit (`[[feedback_docs_at_commit]]`).
</content>
