# Slice 4 — Tuning & feel

> The finishing pass. Absorbs the standalone
> [`../../stories/driving-feel-tuning.md`](../../stories/driving-feel-tuning.md)
> story — that work belongs here now that motion is controller-driven.

## Goal

Tune the controller so vehicles *read* like heavy ground vehicles: deliberate
cornering, smooth speed, no nervousness. Deliberate post-playtest pass — knobs,
not architecture.

## Knobs in play

- **Lookahead-vs-speed curve.** ✅ Landed (da491bb): `VehicleController`
  shrinks the carrot toward `MIN_LOOKAHEAD_CELLS` as the truck slows, back to
  `VehicleType.lookAheadCells` at cruise. Knob values still open to tune.
- **Corner speed taper.** ✅ Landed (da491bb): `curvatureSpeedCap` slows for the
  sharpest bend within `CURVE_PREVIEW_CELLS`. `CURVE_DEADBAND_DEG` /
  `CURVE_FULL_DEG` / `CURVE_MIN_SPEED_FRAC` are the knobs.
- **Replan cadence `K`** and **horizon `H`** (from slices 1–2). Trade
  smoothness/cost against responsiveness to blockage.
- **Accel / braking / steering-slew** per `VehicleType` — the heavy-truck
  "commits to corners" feel.
- Docking trigger range + docking speed (already constants in old code).

### Recovery reaction latency (post-S3 playtest)

S3's recovery works — no trucks hard-stuck — but the re-route fires only after
`STALL_SECONDS` (4) of no progress, so a truck that hits an unturnable corner
visibly **pauses ~4s before lapping around**. The pause reads a little awkward.
`STALL_SECONDS` is deliberately conservative so legitimate slow corners and
in-flight reverses don't false-trip. Options to shorten it, increasing effort:

1. **Lower `STALL_SECONDS`** (4 → ~2–2.5s). One-line; ~80% of the win. The fast
   wall-bump reverse (`WALL_REVERSE_DELAY` 0.3s) is unaffected — this only gates
   the open-space-orbit re-route. Risk: a genuinely slow-but-progressing maneuver
   re-routes a touch eagerly. Try this first and eyeball.
2. **Sharper orbit signal.** Detect the tell-tale of an orbit directly — speed-
   sign oscillation, or near-zero net *displacement* over a ~1.5s window — and
   trigger the re-route immediately, keeping the 4s generic no-progress as a
   backstop. Reacts fast on the buzzing-in-place case without getting twitchy on
   honest slow turns. More code (a small oscillation detector on the pose
   history `Vehicle` already records).
3. **Prevent more orbits up front.** Governor tuning (lookahead floor, curvature
   deadband above) so the truck eases into more turns cleanly and reaches the
   stall path less often. Complements 1/2 rather than replacing them.

Related S3 follow-up (correctness, not feel): the re-route avoid disc is
per-stall, not cumulative — two bad corridors could ping-pong every
`STALL_SECONDS`. Fix = a cumulative avoid-set / reroute cap. Only worth building
if playtest shows a truck re-routing repeatedly in a loop.

## Out of scope

- New mechanics; this is purely parameter + small-curve work.
- Per-vehicle-class profiles beyond what `VehicleType` already carries (future
  tank profile is a later story).

## Acceptance

- Playtest sign-off: convoy run through a cornering city route looks like a
  truck driving, start to finish — no nervous wobble, no corner overshoot into
  walls, clean stop at the LZ.
- Recovery pauses don't read as awkward — when a truck must lap around, the
  beat before it commits feels deliberate, not like a hang.
- Constants documented on `VehicleType` / controller with their rationale
  (heavy vs. future light-scout feel anchors).

## Notes

- This is where subjective "good results" gets locked in — budget real
  playtest time, not just a code read.
- Mark the absorbed `driving-feel-tuning.md` as superseded by this slice when
  it ships (move/annotate it).
</content>
