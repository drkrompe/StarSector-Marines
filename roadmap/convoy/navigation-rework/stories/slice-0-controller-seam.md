# Slice 0 — Controller seam

> Extract the motion seam with **zero behavior change**. De-risks slices 1–3
> by giving them one place to plug into instead of editing `GroundSystem`'s
> state machine repeatedly.

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
