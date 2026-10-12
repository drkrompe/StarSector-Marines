# Story — Warship missile flight (slice 1)

Status: PLANNED

Written: 2026-10-11

Read `warship-missiles.md` (the whole design), `air-nouns.md` (**Overhead ships**)
and `warship-on-station.md` first.

## Problem

Every warship missile is a `WarshipShell` flying one straight line at a single
speed and landing as one blast. An unguided Annihilator rocket, a homing Locust
and a Reaper torpedo look and fly identically, and the game's own flight data
(acceleration, turn rate, fuel, engine flame, contrail) is ignored.

## Goal

Every missile mount's round flies a flight read from its own spec, in
atmosphere, and looks like the game's missile: boosting, cruising, wobbling or
weaving within the spec's turn limit, its flame and contrail from the projectile's
engine slots. Landings, damage and the single-clock rule are unchanged in kind.
Unguided rockets visibly scatter and wobble; nothing is split into more bodies yet.

## Scope

1. **Spec reader.** A missile flight spec parsed from the projectile (`missileType`,
   `engineSpec` acc/maxSpeed/turnRate/turnAcc/dec, `armingTime`, `flameoutTime`,
   `engineSlots` flame and `styleSpec`, `behaviorSpec` kind) plus the table's
   `launch speed`, `flight time`, `proj hitpoints` and hints. Guidance class
   derived (unguided, homing, poorly guided, two-stage, custom); two-stage and
   custom fly as their carrier only in this slice.
2. **Closed-form path.** Launch, boost, cruise, and coast with drag when range
   exceeds fuel, evaluated as `position(t)`/`speed(t)`; arrival time committed at
   fire from the kinematics, not `distance / proj speed`. A rocket that coasts
   falls short by the computed loss. Lateral wobble/weave bounded by the turn
   limit, zero at launch and impact.
3. **Seed discipline.** One draw from the system `Random` per fired round seeds a
   stateless hash; the path never draws again.
4. **Point defence reads the path.** `WarshipPointDefence` takes position and
   speed from the round at the current time (the shell's accessors), unchanged
   otherwise.
5. **Rendering.** Tangent-aligned missile sprite, spec-driven flame and glow,
   contrail ribbon sampled from the path; the current puff chain remains the
   fallback. Far-zoom degradation through `ZoomDetail`.
6. **Arming guard.** No detonation before the spec's arming time.
7. Non-missile rounds (guns, flak, pulse) are untouched.

## Out of scope

Bodies beyond one per fired round, fan/stagger, MIRV, homing at moving units,
toughness in point defence, anti-fighter (slice 2 and later; see the design's
Slicing).

## Acceptance

- Unit: path endpoints equal the committed launch and aim points; speed is
  continuous across segments; peak lateral acceleration is under the spec's
  bound; the same seed gives the same path; a coasting rocket lands short.
- Unit: the spec reader against the install's files (Annihilator unguided,
  Locust homing, Reaper arming 0.5 s).
- Scene `warship-rocket-flight` (new) with a straight-line control: flight time
  and landing distribution within tolerance of the control; the rocket volley is
  visibly not a straight line; verdict `landing-clock-preserved`.
- Existing warship scenes still pass: `onslaught-barrage`, `onslaught-district`,
  `warship-point-defence`, `warship-budget`, `onslaught-flux`.
- `createSnapshots` suite `warship-missiles` renders the flight at 1920x1080.
- No inline fully-qualified names; no Java newer than 17.

## Plan

1. Spec record and reader beside `VanillaWeapon` (data-driven, no authored
   numbers); tests against the real install.
2. The path function and its unit tests; commit time from kinematics.
3. Make `WarshipShell` evaluate the path (still one shell per round).
4. Render: tangent sprite, flame, contrail; zoom degradation.
5. Scene and snapshot suite; fold the model into `air-nouns.md` and update the
   design doc's status.
