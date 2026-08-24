# Story — Fold fighters into the air entity model

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-23 — reduced to the remaining overlay-to-world migration

Read `air-nouns.md` before changing this story. A fighter is an air entity
with shared hull-derived kinematics and fighter-specific mission, loadout, and
presentation behavior; the air world owns identity and lifecycle while systems
own behavior.

## Current substrate

`FlybyOverlay` already drives each fighter's `AirBody` through
`AirSteeringSystem`, and `HullKinematicsResolver` supplies mod-aware handling
from the loaded hull specification. Fighter profiles, wing scheduling,
weapon/tracer effects, cycling re-entry, and the debug aircraft picker remain
in the `battle.flyby` shell. The shell still keeps a private fighter record and
its own draw/fire coupling rather than exposing fighter entities through the
air-world query.

## Goal

Move fighters from the cosmetic overlay's private roster into the shared air
entity lifecycle while preserving their current strafing behavior and making
the fighter mission, render, and weapon systems consume the composed world
state.

## Decisions

- Keep `FighterProfile` as loadout and presentation data; hull-derived
  `AirHandling` remains the kinematics authority.
- Use world entity ids and the existing air components; do not mint a fighter
  id space or retain a parallel component store.
- Preserve the current wing schedule, strafing-run planning, cycling re-entry,
  tracers, audio, and debug picker while moving their state reads to the world
  entity and fighter mission.
- Fighter damage/anti-air and modeled fighter fire remain separate Air-owned
  follow-ups; this story establishes the composed entity and movement/render
  seam only.

## Acceptance

- Fighter spawn creates an air-world entity with its kinematics, identity,
  appearance, and fighter mission/loadout state; despawn removes it through the
  shared lifecycle.
- A dedicated fighter mission system supplies goals to
  `AirSteeringSystem`; the old private heading/speed integration and duplicate
  motion state are gone.
- Rendering, engine FX, weapon fire, tracers, and audio read the entity's live
  `AirBody`/air components rather than a shadow `FlybyOverlay.Fighter` state.
- Existing wing timing, strafing waypoints, cycling re-entry, and debug-only
  aircraft selection remain behaviorally intact.
- The migration leaves the shared vocabulary in `air-nouns.md` and does not
  create a second fighter-specific air model.

## Out of scope

- Anti-air health/death, fighter collision, and air-to-air or modeled fighter
  fire; those remain future Air-world policy after the shared entity seam is
  established.
- Wing composition from `wing_data.csv` beyond the current spawn mapping.
- Dense storage optimization; it follows measured fighter-swarm pressure.
