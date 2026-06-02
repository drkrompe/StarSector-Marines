# Navigation rework — handoff state

## State of play

**Slices 0–1 shipped.** The seam (slice 0) and the rolling-horizon planner
(slice 1) are in. Motion is still byte-identical to pre-rework — the planner
exists and is unit-tested but is **not yet wired to motion**. Slice 2 is the
behavioral pivot that flips to it and kills the 90° snaps.

- Slice 0: `VehicleController` + `ReferenceCorridor` seam; `GroundSystem`
  drives motion via `controller.tick(dt, inbound)` + `consumeArrived()`.
  ([`complete/slice-0-controller-seam.md`](complete/slice-0-controller-seam.md))
- Slice 1: `LocalTrajectoryPlanner` + `Trajectory` +
  `HybridAStarPlanner.planLocal` (bounded window, soft goal). Pure,
  unit-tested, untouched live `refine()`.
  ([`complete/slice-1-local-planner.md`](complete/slice-1-local-planner.md))

Track: full layered rewrite anchored on a **rolling-horizon local Hybrid A\***
planner with always-on kinematic tracking. No backward-compat constraint — we
delete the old `headings != null` dead-reckon fork rather than keep it.

Read [`overview.md`](overview.md) first — it has the diagnosis (the
`refineWithFallback` synthetic-heading fork is why corners snap 90° and
recovery never fires) and the target architecture.

**Next up: slice 2** — wire `VehicleController.tick` to track the rolling
`Trajectory` with `BicycleBody` + `PurePursuit` every tick (replan every K
ticks / on deviation); **delete `advancePlayback`, `ConvoyPlanner.refineWithFallback`,
`deriveSegmentHeadings`, and the `inbound/outboundHeading` arrays**. Keep RS
docking as the terminal LZ phase. The 90° snaps die here — attach a critique-pass
agent (it's the regression-prone behavioral pivot). Heed slice 1's note: the
replan cadence must keep the rolling goal marching down the corridor or corners
under-turn.

## Slice chain

- [x] **Slice 0** — `VehicleController` + `ReferenceCorridor` seam, no
      behavior change. ([`complete/slice-0-controller-seam.md`](complete/slice-0-controller-seam.md)) ✅
- [x] **Slice 1** — `LocalTrajectoryPlanner` (bounded rolling-horizon HA\*),
      unit-tested standalone. ([`complete/slice-1-local-planner.md`](complete/slice-1-local-planner.md)) ✅
- [ ] **Slice 2** — live tracking; delete `advancePlayback` /
      `refineWithFallback` / `deriveSegmentHeadings`. **90° snaps die here.**
      ([`stories/slice-2-live-tracking.md`](stories/slice-2-live-tracking.md))
- [ ] **Slice 3** — recovery ladder. ([`stories/slice-3-recovery-ladder.md`](stories/slice-3-recovery-ladder.md))
- [ ] **Slice 4** — tuning & feel (absorbs `../stories/driving-feel-tuning.md`).
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

Slices 0–1 are done: the seam (`VehicleController` + `ReferenceCorridor`) and
the planner (`LocalTrajectoryPlanner.plan` → `Trajectory`, backed by
`HybridAStarPlanner.planLocal`) both exist and are tested. Start at **slice 2**:
inside `VehicleController.tick`, replace the `advance`/`advancePlayback`
dispatch with a live loop — hold a current `Trajectory`, refresh it from
`LocalTrajectoryPlanner.plan(currentPose, corridor, type, grid)` every K ticks
(or when consumed / drifted), `PurePursuit.pick` a carrot along the
*trajectory* (not the coarse corridor), brake-taper on
`trajectory.lengthCells() + corridor.remainingLength`, `body.tick(...)`, then
advance the corridor cursor by projecting the new pose. Delete `advancePlayback`,
`ConvoyPlanner.refineWithFallback` + `deriveSegmentHeadings`, and the
`inbound/outboundHeading` arrays + `headings != null` dispatch. Keep
`advanceDocking` (RS) as the terminal LZ phase. Sync `body.x/y` → renderer each
tick (`[[air_unit_render_sync]]`). Attach a critique-pass agent.
</content>
