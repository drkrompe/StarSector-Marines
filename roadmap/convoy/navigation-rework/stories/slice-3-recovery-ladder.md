# Slice 3 — Recovery ladder

> Make failure graceful. Replace the ad-hoc wall-stuck / reverse-pulse code
> with a formal escalation where "replan the next H cells" *is* the recovery.

## Goal

When the vehicle can't make forward progress, escalate through a defined
ladder instead of the current single reverse-pulse + one-shot full-path HA*.

## The ladder

1. **Feasible drift** — pose is off the local trajectory but still on a
   feasible cell, and a fresh local plan exists. → just keep tracking; pure
   pursuit self-corrects. (No escalation.)
2. **Blocked** — current local plan invalidated (e.g. wreck appeared) or
   `LocalTrajectoryPlanner` returns `NoTrajectory` forward-only. → replan with
   **reverse / 3-point allowed** (the local planner already supports reverse
   successors + RS reversal). This is the proper extraction the reverse-pulse
   was faking.
3. **Stuck** — N consecutive blocked replans fail within a window. → **full
   re-route**: snap to the nearest road-graph node, `ConvoyPlanner.planPath`
   to the remaining goal, rebuild the corridor, resume from step 1.
4. **Dead** — re-route also fails (genuinely boxed in). → graceful giveup:
   stop, log, and either despawn off-screen or hold without thrashing. Never
   drive through walls, never jitter.

## What lands

- A `RecoveryState` / ladder inside `VehicleController` with explicit
  transitions and the per-level counters/timers (replacing
  `WALL_REVERSE_DELAY`, `REPLAN_STUCK_THRESHOLD`, `REPLAN_COOLDOWN`,
  `STUCK_ESCAPE_DIST` and the inline block in old `advancePath`).
- Reverse / 3-point handled by *re-planning with reverse enabled*, not by a
  hardcoded backward nudge — so the extraction is feasibility-checked like any
  other trajectory.
- Full re-route path: nearest-node snap + `ConvoyPlanner` + corridor rebuild.

## Out of scope

- Tuning the counters/thresholds (slice 4).
- Dynamic-obstacle *prediction* (other trucks' future positions) — slice 3
  reacts to the current grid only; prediction is a later convoy story.

## Acceptance

- A vehicle dropped into a pocket that requires backing up performs a visible,
  smooth 3-point-style extraction (not a teleport, not a stutter).
- A wreck / wall placed across the planned route triggers a re-route and the
  vehicle finds the alternate road (or gives up gracefully if none).
- No "drive through wall" and no infinite jitter under any tested block.
- Removed: the old wall-stuck inline recovery and its four constants.

## Notes

- Lean on `[[breakcontact_no_sticky_dest]]`'s lesson: re-roll the recovery
  target rather than fixating on a cached cell that's no longer valid.
- Graceful giveup should be observable in logs for debugging, quiet on screen.
</content>
