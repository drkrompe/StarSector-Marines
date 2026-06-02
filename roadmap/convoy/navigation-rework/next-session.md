# Navigation rework — handoff state

## State of play

**Slice 0 shipped — seam in place, no behavior change yet.** The
`VehicleController` + `ReferenceCorridor` seam is extracted; `GroundSystem`'s
state machine drives motion through `controller.tick(dt, inbound)` +
`consumeArrived()`. Motion is byte-identical (still the old `headings != null`
fork, still 90° corners) — slice 2 is where that changes. Details in
[`complete/slice-0-controller-seam.md`](complete/slice-0-controller-seam.md).

Track: full layered rewrite anchored on a **rolling-horizon local Hybrid A\***
planner with always-on kinematic tracking. No backward-compat constraint — we
delete the old `headings != null` dead-reckon fork rather than keep it.

Read [`overview.md`](overview.md) first — it has the diagnosis (the
`refineWithFallback` synthetic-heading fork is why corners snap 90° and
recovery never fires) and the target architecture.

**Next up: slice 1** — build `LocalTrajectoryPlanner` standalone with unit
tests; it doesn't touch live motion, so it's safe to develop while the
`Squad.leader` refactor churns the tree.

## Slice chain

- [x] **Slice 0** — `VehicleController` + `ReferenceCorridor` seam, no
      behavior change. ([`complete/slice-0-controller-seam.md`](complete/slice-0-controller-seam.md)) ✅
- [ ] **Slice 1** — `LocalTrajectoryPlanner` (bounded rolling-horizon HA\*),
      unit-tested standalone. ([`stories/slice-1-local-planner.md`](stories/slice-1-local-planner.md))
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

Slice 0 is done — the seam (`VehicleController` + `ReferenceCorridor`) is the
single place to plug into. Start at **slice 1**: build `LocalTrajectoryPlanner`
as a pure function (pose + corridor + grid → feasible `Trajectory`) wrapping
the existing `HybridAStarPlanner` search on a bounded window, exercised only by
unit tests. It doesn't wire into motion yet (that's slice 2), so it's
unaffected by whatever else is churning the main tree.
</content>
