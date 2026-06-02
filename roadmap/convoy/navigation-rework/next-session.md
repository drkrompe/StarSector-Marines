# Navigation rework — handoff state

## State of play

**Slices 0–2 shipped.** The seam (0), the rolling-horizon planner (1), and live
always-kinematic tracking (2) are in. The `headings != null` dead-reckon fork is
**gone** — vehicles track a rolling local `Trajectory` (or the coarse corridor
as a kinematic fallback) every tick; RS docking is the one surviving rails case.
The 90° snaps are dead **in code** — but slice-2 acceptance is partly visual, so
a **convoy-run playtest is still outstanding** (see Next up).

- Slice 0: `VehicleController` + `ReferenceCorridor` seam; `GroundSystem`
  drives motion via `controller.tick(dt, inbound)` + `consumeArrived()`.
  ([`complete/slice-0-controller-seam.md`](complete/slice-0-controller-seam.md))
- Slice 1: `LocalTrajectoryPlanner` + `Trajectory` +
  `HybridAStarPlanner.planLocal` (bounded window, soft goal). Pure, unit-tested.
  ([`complete/slice-1-local-planner.md`](complete/slice-1-local-planner.md))
- Slice 2: `VehicleController.tick` tracks the rolling `Trajectory` (BicycleBody
  + PurePursuit), corridor-pursuit fallback when `plan()` is `null`; deleted the
  whole synthetic-heading fork (`refineWithFallback`/`deriveSegmentHeadings`/
  heading arrays/spawn-time `refine`) **and** the now-orphaned full-path
  `HybridAStarPlanner.refine` (which also resolved slice-1 critique #7).
  ([`complete/slice-2-live-tracking.md`](complete/slice-2-live-tracking.md))

**Corner-speed governor landed early (a slice-4 down-payment).** A cost-field
convoy playtest surfaced trucks taking tight turns at cruise, overshooting, and
flailing in tiny reverses. Two fixes in `VehicleController.advance`, both applied
to whichever polyline is tracked (local trajectory *or* coarse corridor):
- **Curvature speed cap** (`curvatureSpeedCap` / `previewTurnDegrees`) — slows for
  the sharpest bend within `CURVE_PREVIEW_CELLS` ahead (full cruise below a 20°
  deadband, lerping to 0.35·maxSpeed at 75°+), so corners are entered slow enough
  for the bounded steering slew to make them.
- **Speed-scaled look-ahead** — the carrot shrinks toward `MIN_LOOKAHEAD_CELLS`
  as the truck slows (tight corners track closely instead of the fixed look-ahead
  chord cutting the inside of the turn), stretching back to cruise look-ahead on
  straights.
Unit-tested (`VehicleControllerCurvatureTest`, 7 green); the rest of slice-4 feel
tuning (constants) is still open and **wants the same playtest**. Playtest-watch
items: (a) a sharp corner sitting just past the 4-cell preview window can be
entered a touch fast before the next replan (low risk at ~0.7 cells/replan, but
watch tight back-to-back turns); (b) `MIN_LOOKAHEAD_CELLS 1.2` is below the
~3.96-cell min turn radius — if pursuit visibly oscillates in slow corners,
raise the floor or lean harder on the curvature cap.

**Committed reverse recovery landed (the slice-3 "blocked → back up" rung).** A
playtest showed a truck stuck in tiny-reverse oscillation at the mouth of a
3-wide corridor it had to enter aligned (it arrived sideways; min turn radius
~3.96 > the gap, so it can't pivot in place). Root cause: `planLocal` returns
null there → coarse-corridor pursuit drives into the wall → the old
`wallStuckRecovery` pulsed ONE reverse tick per frame, which the next frame's
forward move cancelled. Replaced the pulse with a **committed** maneuver
(`Recovery.REVERSING`): on blocked-past-`WALL_REVERSE_DELAY`, `maxReverseDistance`
marches the reverse axis footprint-checking each step to find the achievable
backup (bounded by what's behind), then a dedicated `advanceReverse` phase owns
the pose and backs up that far while aiming the nose at the corridor (3-point
setup), before forcing a fresh forward plan from the roomier pose.
After `MAX_RECOVERY_ATTEMPTS=5` recoveries **without net progress toward the LZ**
→ hold position (no thrash). The give-up counter resets only when corridor
remaining-length drops `RECOVERY_PROGRESS_MARGIN` below its best-so-far — NOT on
any feasible forward step, or an oscillating box-in (back up, nudge one step,
re-stick) would reset every cycle and loop forever (the critique CRITICAL).
Unit-tested (`VehicleControllerRecoveryTest`, 3 green, march values pinned).

Track: full layered rewrite anchored on a **rolling-horizon local Hybrid A\***
planner with always-on kinematic tracking. No backward-compat constraint — the
old `headings != null` dead-reckon fork is deleted, not kept.

Read [`overview.md`](overview.md) first — it has the diagnosis and the target
architecture.

**Next up: PLAYTEST slice 2, then slice 3.** Slice 2's acceptance is partly
visual and can't be self-verified headless. Run a convoy with a cornering route
and confirm by eye: (a) no 90° snaps — corners are continuous min-radius arcs;
(b) inbound trucks reach the LZ with a sane heading → LANDED, departing trucks
reach GONE; (c) the off-map drive-on and RS docking still look right. Off *feel*
is slice 4 (tuning); a *broken* arrival/stuck/drive-on is a slice-2 bug to fix
first. Then **slice 3 — recovery ladder**: replace the ad-hoc reverse stub in
`VehicleController.wallStuckRecovery` + the `plan()==null` corridor-pursuit
fallback with a formal escalation (drift → blocked → stuck → giveup), using the
local planner for reverse / 3-point extraction.

## Slice chain

- [x] **Slice 0** — `VehicleController` + `ReferenceCorridor` seam, no
      behavior change. ([`complete/slice-0-controller-seam.md`](complete/slice-0-controller-seam.md)) ✅
- [x] **Slice 1** — `LocalTrajectoryPlanner` (bounded rolling-horizon HA\*),
      unit-tested standalone. ([`complete/slice-1-local-planner.md`](complete/slice-1-local-planner.md)) ✅
- [x] **Slice 2** — live tracking; deleted `advancePlayback` /
      `refineWithFallback` / `deriveSegmentHeadings` / `refine`. **90° snaps
      dead in code (playtest pending).**
      ([`complete/slice-2-live-tracking.md`](complete/slice-2-live-tracking.md)) ✅
- [~] **Slice 3** — recovery ladder. The "blocked → committed backup" rung
      landed (committed `Recovery.REVERSING` + `maxReverseDistance`, replacing the
      oscillating pulse; see State of play). Remaining rungs: drift handling, a
      real give-up (re-route from nearest road node / deload-in-place) instead of
      hold-position after `MAX_RECOVERY_ATTEMPTS`, and using the local planner's
      reverse for true 3-point extraction.
      ([`stories/slice-3-recovery-ladder.md`](stories/slice-3-recovery-ladder.md))
- [~] **Slice 4** — tuning & feel (absorbs `../stories/driving-feel-tuning.md`).
      Corner-speed governor + speed-scaled look-ahead landed early (see State of
      play); remaining constant-tuning open.
      ([`stories/slice-4-tuning-feel.md`](stories/slice-4-tuning-feel.md))
- [ ] **Slice 5** — perf budget (deferred until multi-truck). ([`stories/slice-5-perf-budget.md`](stories/slice-5-perf-budget.md))

## Key files this rework touches

- `battle/vehicle/GroundSystem.java` — `advancePath` / `advancePlayback` /
  `advanceDocking` move out (slice 0) then shrink/delete (slice 2).
- `battle/vehicle/ConvoyPlanner.java` — `refineWithFallback` +
  `deriveSegmentHeadings` deleted (slice 2); `planPath` / `expandToWaypoints`
  stay.
- `battle/vehicle/HybridAStarPlanner.java` — search core kept, driven by the
  new local planner on small windows.
- `battle/vehicle/PurePursuit.java`, `BicycleBody.java`, `Vehicle.java` —
  PurePursuit now tracks the *local trajectory*; `Vehicle` motion fields
  migrate onto the controller.

## Picking up cold

Slices 0–2 are done; slices 3–4 partially in (see State of play). The control
stack is live: `VehicleController.tick` → `corridor.advance(pose)` → committed
reverse recovery (if `REVERSING`) → terminal RS docking (inbound) → arrival →
rolling `LocalTrajectoryPlanner.plan` refresh → speed-scaled `PurePursuit` carrot
along the trajectory (coarse-corridor fallback when `plan()` is `null`) →
curvature-capped `body.tick` → `wallStuckRecovery` (now a committed-backup
trigger). The synthetic-heading fork and the full-path `refine` are gone.

**First: playtest** the corner governor + reverse recovery on a cornering convoy
run (the prior stuck-truck case). Then finish **slice 3 — recovery ladder**.
What's left now that the committed backup is in:
1. The remaining rungs: drift handling, and a real **give-up** — after
   `MAX_RECOVERY_ATTEMPTS` the controller just holds position; replace with a
   re-route from the nearest road node, or deload-in-place, so a kinematically
   impossible corridor doesn't strand the truck.
2. The `else` branch in `advance` where `trajectory == null` falls back to
   coarse-corridor pursuit (also how the off-map drive-on works) — the dumb
   pursuit there is what drives into the wall in the first place; consider using
   `LocalTrajectoryPlanner`'s reverse-capable search for true 3-point extraction
   instead of the straight-ish backup.
Deeper root cause worth a separate look: the cost router (clearance radius 1)
will route a truck through a 3-wide gap it fits *statically* but can't *enter*
kinematically (min turn radius ~3.96). A turn-aware clearance or approach check
in `cost-field-routing` would stop picking those corridors. Heed slice-1 critique
\#1 (near-goal `null` is arrival, special-cased before planning) so recovery
never fires at the LZ. Attach a critique-pass agent.
</content>
