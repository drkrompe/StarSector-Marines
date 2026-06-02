# Navigation rework — handoff state

## State of play

**Design landed, no code yet.** Track decided: full layered rewrite anchored
on a **rolling-horizon local Hybrid A\*** planner with always-on kinematic
tracking. No backward-compat constraint — we delete the old
`headings != null` dead-reckon fork rather than keep it.

Read [`overview.md`](overview.md) first — it has the diagnosis (the
`refineWithFallback` synthetic-heading fork is why corners snap 90° and
recovery never fires) and the target architecture.

## Slice chain

- [ ] **Slice 0** — `VehicleController` + `ReferenceCorridor` seam, no
      behavior change. ([`stories/slice-0-controller-seam.md`](stories/slice-0-controller-seam.md))
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

Start at slice 0. It's a mechanical extraction with no behavior change —
suitable to delegate to Sonnet (`[[feedback_delegate_mechanical_sonnet]]`)
while design attention stays on slice 1's planner.
</content>
