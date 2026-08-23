# Overwatch tower adoption

Status: PARKED

Written: 2026-08-23

Read `mapgen-nouns.md` before reviving this story.

## Activation gate

Defender-budget and legacy-map intent define whether additional manned corner
towers improve those battles rather than silently increasing difficulty.

## Goal

Decide whether the shipped positional overwatch pass should enter the legacy
city recipe and whether its currently unmanned towers receive guard squads.
Keep placement, defender roster cost, and release behavior as one balanced
vertical rather than treating manning as a cosmetic follow-up.

## Acceptance

- Each opted-in recipe has an explicit tower budget and directional fallback.
- Manned towers reserve defender strength and create valid guard deployment.
- Turret death/release behavior matches other guarded emplacements.
- Deterministic validation and playtest compare coverage and difficulty against
  the current unmanned conquest behavior.
