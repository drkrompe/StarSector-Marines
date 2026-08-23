# Authored walk animation

Status: PARKED

Written: 2026-08-23

Read `ecs-nouns.md` before reviving this story.

## Activation gate

Usable walk-cycle sheets and a concrete frame-layout acceptance plan exist.

## Goal

Add a per-type optional walk-cycle capability to the existing authored
appearance flow. `FacingSystem` should advance it and author the resulting
`SPRITE` frame without changing actors that lack walk-cycle art.

## Acceptance

- Animation state is tier-neutral component data and advances at simulation cadence.
- `FacingSystem` composes locomotion and facing into the authored `SPRITE` frame.
- Actors without the capability keep their current static authored appearance.
- Rendering remains a read-only collector and simulation logic never reads the frame.
