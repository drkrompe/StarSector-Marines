# Slice 0 — Controller seam  ✅ SHIPPED

> Extract the motion seam with **zero behavior change**. De-risks slices 1–3
> by giving them one place to plug into instead of editing `GroundSystem`'s
> state machine repeatedly.

## Shipped

Commit: _(this commit)_ — `battle/vehicle/` seam extraction.

What actually landed vs. planned:

- **`ReferenceCorridor`** (new) — wraps the coarse `(xs, ys)` polyline + the
  progress cursor that used to be `Vehicle.waypointIndex`. Methods: `carrot`
  (advances cursor, wraps `PurePursuit.pick`), `remainingLength`, `atEnd`,
  `endX/endY/lastIndex`, plus the planner-facing `targetAhead` and
  `offCorridorDistance` queries (real impls, but unused until slices 1–2 —
  they exist so the seam is stable). Deviation from the doc sketch:
  `targetAhead` dropped the unused `facing` parameter — goal facing is the
  corridor direction at the look-ahead point, independent of pose facing.
- **`VehicleController`** (new) — owns one vehicle's motion. Holds the active
  `ReferenceCorridor`, `playbackProgress`, the docking state (`dockingPath`
  etc.), and the wall-stuck recovery state — all migrated off `Vehicle`. Body
  of `tick(dt, isInbound)` is a faithful relocation of the old `advancePath` /
  `advancePlayback` / `advanceDocking` / `tryEngageDocking` / `tryReplan` /
  `isPathFeasible` / `polylineLength`, plus their constants
  (`LZ_ARRIVAL_DIST`, `DOCKING_*`, `WALL_REVERSE_*`, `REPLAN_*`,
  `STUCK_ESCAPE_DIST`). Same `headings != null` fork, same math.
- **Arrival via flag, not direct state writes.** The controller sets an
  `arrived` flag on reaching the terminal waypoint; `GroundSystem` reads
  `consumeArrived()` and drives INCOMING→LANDED (+ `deboardCountdown`) /
  DEPARTING→GONE. (Doc called it `isArrived()`; renamed `consumeArrived()`
  since it clears on read.)
- **Corridor rebuild on direction flip.** The old manual
  `waypointIndex = 1; playbackProgress = 0` resets in `GroundSystem`'s
  LANDED/OVERWATCH cases are gone — the controller rebuilds its corridor (and
  resets playback/docking) the first tick `isInbound` flips. `tryReplan`
  rebuilds the corridor from the freshly refined arrays the same way.
- **`Vehicle`** — gains a `controller` field (set in `GroundSystem.add`);
  loses `waypointIndex`, `playbackProgress`, the five `docking*` fields, and
  the four wall-stuck recovery fields. Keeps `inboundHeading` /
  `outboundHeading` / `pathRefined` (set externally at spawn) and `body` (read
  by renderer + turret loop). `recordTick` reads `controller.wallStuckTime()`.
- **Debug consumers repointed:** `VehicleStateDumper` and `BattleRenderer`'s
  selected-vehicle / docking-path overlays now read the migrated state through
  controller getters (`waypointIndex()`, `playbackProgress()`,
  `wallStuckTime()`, `dockingPath()`, `dockingStartPose()`,
  `dockingTurnRadius()`).

Verification: all touched files are inspection-clean. The whole-project
`gradle build` could not be run green during this slice because a concurrent
session was mid-refactor removing `Squad.leader` (unrelated compile errors in
Squad/infantry/drone/UI files); none of the vehicle files appear in that error
set. Convoy-run playtest of byte-identical motion is the outstanding check —
deferred to whenever the tree compiles again.

---

## Original plan (for reference)

## Goal

`GroundSystem`'s per-state motion calls (`advancePath` for INCOMING /
DEPARTING) route through a new `VehicleController` object owned by the
`Vehicle`. The controller initially just *wraps* today's logic — same
playback / pure-pursuit / docking dispatch, moved behind a method. Motion on
screen is byte-identical.

## What lands

- **`ReferenceCorridor`** — wraps the coarse route polyline (`xs`, `ys`) + a
  progress cursor. Initial API:
  - `targetAhead(float poseX, poseY, facing, float horizonCells) → Pose` (soft
    goal a horizon down the corridor)
  - `remainingLength(poseX, poseY)`
  - `offCorridorDistance(poseX, poseY)`
  - `atEnd(poseX, poseY, threshold)`
  Built from the existing `PurePursuit.pick` / `remainingPathLength` math —
  this slice just relocates that math behind the corridor type; no new
  algorithm.
- **`VehicleController`** — owns one vehicle's motion. Slice-0 body is a
  straight move of `advancePath` / `advancePlayback` / `advanceDocking` out of
  `GroundSystem` into `controller.tick(dt, isInbound)`. The controller holds
  the `GroundBody`, the corridor, and the docking/playback state that today
  lives as loose fields on `Vehicle`.
- `GroundSystem` INCOMING/DEPARTING cases become
  `v.controller.tick(dt, inbound)` + a read of `controller.isArrived()` to
  drive the state transition.

## Out of scope

- The local planner (slice 1).
- Deleting `advancePlayback` / `deriveSegmentHeadings` (slice 2 — they still
  run, just behind the controller).
- Any feel / recovery change.

## Acceptance

- A convoy run looks identical to pre-slice (same path, same — yes, still
  ugly — corners). This slice is a refactor; the 90° snaps are *expected to
  remain* until slice 2.
- `GroundSystem` no longer contains `advancePath` / `advancePlayback` /
  `advanceDocking`; those live on `VehicleController`.
- Existing vehicle tests pass unchanged (or move with the code).

## Notes

- Keep `Vehicle`'s loose motion fields (`waypointIndex`, `playbackProgress`,
  `dockingPath`, …) migrating onto the controller where they belong — but a
  mechanical move that preserves behavior is fine; don't redesign state here.
- This is a good **Sonnet-delegated mechanical extraction** per
  `[[feedback_delegate_mechanical_sonnet]]` — the design is fixed, it's a move.
  Verify in the main tree's git status (`[[subagent_worktree_absolute_paths]]`).
</content>
