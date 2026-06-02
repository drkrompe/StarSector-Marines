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
- [ ] **Slice 3** — recovery ladder. ([`stories/slice-3-recovery-ladder.md`](stories/slice-3-recovery-ladder.md))
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

Slices 0–2 are done. The control stack is live: `VehicleController.tick` →
`corridor.advance(pose)` → terminal RS docking (inbound) → arrival → rolling
`LocalTrajectoryPlanner.plan` refresh → `PurePursuit` carrot along the
trajectory (coarse-corridor fallback when `plan()` is `null`) → `body.tick` →
shared `wallStuckRecovery` reverse stub. The synthetic-heading fork and the
full-path `refine` are gone.

**First: playtest slice 2** (see Next up — it's the unverified bit). Then start
**slice 3 — recovery ladder**. The two slice-2 stubs it replaces:
1. `VehicleController.wallStuckRecovery` — the ad-hoc revert-and-reverse-pulse.
2. The `else` branch in `advance` where `trajectory == null` falls back to
   coarse-corridor pursuit (currently also how the off-map drive-on works).
Formalize both into drift → blocked → stuck → giveup, using
`LocalTrajectoryPlanner` (reverse / 3-point) for extraction and a full re-route
from the nearest road node for "stuck." Heed slice-1 critique #1 (near-goal
`null` is arrival, already special-cased before planning) so recovery never
fires at the LZ. Attach a critique-pass agent.
</content>
