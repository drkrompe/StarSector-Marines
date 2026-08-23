# S2 — Primary weapon catalog expansion

> The primary catalog has a sound rifle spine, but no weapon owns close
> interiors or sustained suppression.

Status: PLANNED — depends on the shipped lethality scale in
`progression-nouns.md`.

Written: 2026-08-23

Read `progression-nouns.md` before implementing this story.

## Problem

The shipped player catalog is a recruit field rifle plus a DMR / burst-rifle /
automatic-rifle triad. It is a reasonable foundation, but there is no
close-quarters answer in a mod whose mapgen ships two-cell combat aisles and
apartment interiors, and the current automatic option does not produce a
legible suppression role.

The special-equipment slot and shipped smoke and satchel utilities are defined
in `progression-nouns.md`; its remaining expansion story is the retained
fragmentation-grenade plan in `s2d-frag-grenades.md`. This story does not
duplicate its activation or AI work.

## Goal

Add primary families whose tactical identities are statable in one sentence,
give the unlock ladder real lateral choices, and make at least two new
fire-team cards worth fielding.

## Candidate families

| Family | Identity | Notes |
| --- | --- | --- |
| **Combat shotgun** | Devastating inside about eight cells and ineffective past fourteen. | The interior/aisle answer: wide spread, steep falloff, high close damage, and a multi-pellet burst. |
| **Squad automatic** | Sustained suppression through a long belt and wide cone, not precision damage. | Distinct from the current `SMG`, whose stats read as a fast-cycling carbine despite its display name. |

Naming note: `SMG` currently displays as “Light Machine Gun.” Reconcile its
identity before adding another automatic family rather than stacking a third
overlapping name on top.

## Gameplay-AI integration

- The shotgun changes firing-position scoring: its carrier should close to its
  useful band without pulling the rest of the fire team through the squad
  cohesion leash or into a dense hostile formation.
- The squad automatic should feed the existing overwatch and bounding model.
  Within a covering team, its carrier is preferred for a stanced firing lane;
  when that intact team becomes the moving element, the carrier moves and
  withholds fire with their teammates.
- Enemy use is an explicit content decision per family. Shotgun defenders in
  apartments and commercial interiors materially change assaults; automatic
  defenders materially strengthen doorway and portal holds.
- New policy remains faction-neutral. Player and defender infantry with the
  same family use the same engagement-band and fire-team rules.

## Boundaries

- No marine-role hierarchy. Fire teams and their current maneuver roles remain
  the AI unit; a weapon family may influence suitability without minting a
  permanent “shotgunner” or “gunner” class.
- No attachment axis. Grade remains the quality modifier.
- No new suppression status is promised here. The automatic family must first
  make covering fire legible through cadence, cone, and the shipped bounding
  behavior; a mechanical suppression model would need its own story.

## Acceptance

- Each family has a one-sentence identity in the authored catalog and does not
  occupy the same practical range/cadence niche as a shipped primary.
- Every family is starter issue or has an explicit reachable unlock in
  `s6-unlock-ladder-expansion.md`.
- The template-card library gains a close-assault and/or sustained-fire design
  that materially uses the new family.
- Defender loadout rolls make an explicit player-only versus enemy-available
  decision for each family.
- Focused AI scenarios demonstrate shotgun carriers seeking their useful band
  without breaking cohesion and automatic carriers contributing while their
  team covers but withholding fire when that team bounds.
- Existing mission force ratios are re-verified against the shipped S1
  lethality scale.
