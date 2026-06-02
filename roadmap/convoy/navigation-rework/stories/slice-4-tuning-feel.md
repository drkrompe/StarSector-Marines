# Slice 4 — Tuning & feel

> The finishing pass. Absorbs the standalone
> [`../../stories/driving-feel-tuning.md`](../../stories/driving-feel-tuning.md)
> story — that work belongs here now that motion is controller-driven.

## Goal

Tune the controller so vehicles *read* like heavy ground vehicles: deliberate
cornering, smooth speed, no nervousness. Deliberate post-playtest pass — knobs,
not architecture.

## Knobs in play

- **Lookahead-vs-speed curve.** Pure pursuit lookahead should scale with speed
  — longer at cruise (smooth, lazy), shorter when slow/maneuvering (tight
  tracking). Today `lookAheadCells` is a flat per-type constant.
- **Replan cadence `K`** and **horizon `H`** (from slices 1–2). Trade
  smoothness/cost against responsiveness to blockage.
- **Corner speed taper.** Slow into corners proportional to upcoming curvature
  so the truck doesn't arrive at a bend too fast for its turn radius (it
  can't tighten the radius by braking — see `BicycleBody` min-radius note).
- **Accel / braking / steering-slew** per `VehicleType` — the heavy-truck
  "commits to corners" feel.
- Docking trigger range + docking speed (already constants in old code).

## Out of scope

- New mechanics; this is purely parameter + small-curve work.
- Per-vehicle-class profiles beyond what `VehicleType` already carries (future
  tank profile is a later story).

## Acceptance

- Playtest sign-off: convoy run through a cornering city route looks like a
  truck driving, start to finish — no nervous wobble, no corner overshoot into
  walls, clean stop at the LZ.
- Constants documented on `VehicleType` / controller with their rationale
  (heavy vs. future light-scout feel anchors).

## Notes

- This is where subjective "good results" gets locked in — budget real
  playtest time, not just a code read.
- Mark the absorbed `driving-feel-tuning.md` as superseded by this slice when
  it ships (move/annotate it).
</content>
