# Slice 2 — Live tracking replaces playback

> The payoff slice. Flip from dead-reckon-on-rails to always-kinematic
> tracking, and **delete the synthetic-heading fork**. The 90° snaps die here.

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
