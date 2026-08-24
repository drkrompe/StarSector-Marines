# S2 — Primary weapon catalog expansion

> The primary catalog has a sound rifle spine, but no weapon owns close
> interiors or sustained suppression.

Status: PLANNED — depends on the shipped lethality scale in
`progression-nouns.md`.

Written: 2026-08-23

Updated: 2026-08-24 — mapped the shipped catalog to slug, gauss, and pulse mechanisms and replaced the generic shotgun candidate with a Sector-flavored shredder carbine.

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

Mechanism is not family. Chemical slugs, gauss acceleration, flechette
sub-munitions, and directed energy describe how a shot is delivered; a new
catalog family must still own a distinct engagement decision.

## Shipped-family interpretation

The existing catalog already covers three of the setting's common mechanisms
without adding another persisted handle:

| Shipped family | Setting read | Decision |
| --- | --- | --- |
| `FIELD_RIFLE` / Rook | Heavy-caliber chemical slug rifle and universal service long-arm | Keep as the rugged baseline; faction provenance may change manufacture and presentation without replacing its role |
| `DMR` / Longbow Railgun | Hand-held gauss/rail marksman weapon for composite armor seams | Keep as the precision kinetic primary; the AMR remains the scarce heavier anti-armor special |
| `PULSE_RIFLE` / Lancer | Battery-fed pulse-energy burst carbine | Keep as the flexible upgraded primary; batteries are logistics flavor unless primary-ammunition authority is separately contracted |

Elite Hegemony issue may include energy patterns, while Tri-Tachyon strongly
weights pulse and later laser equipment. That is provenance and roster
availability, not permission for faction-only damage rules.

## Candidate families

| Family | Identity | Notes |
| --- | --- | --- |
| **Flechette / shredder carbine** | Devastating against soft targets in close interiors and poor against rated armor. | Dense dart/sub-munition clouds replace the generic combat-shotgun proposal: wide spread, steep falloff, multi-projectile release, low penetration, and negligible structural-wall value. Boarding-safe flavor grants no arbitrary indoor damage bonus. |
| **Squad automatic slugthrower** | Sustained covering fire through a long belt and wide cone, not precision damage. | A true chemical-slug support primary with a long burst/cadence identity, distinct from the close-range shredder migration. |
| **Laser carbine, deferred candidate** | Pinpoint thermal pressure on seams and exposed targets. | Add only if a held-beam or other readable execution produces a decision distinct from the pulse burst and rail marksman roles; do not ship a green DMR clone. |

`SMG` currently displays as “Light Machine Gun,” but its short range, wide
spread, light penetration, and three-round saturation already read closer to a
shredder carbine. Preserve the compatibility handle/save repair while moving
its player-facing identity to that close-quarters family. The newly authored
squad automatic then owns the LMG/support-gun name and sustained-fire behavior;
do not stack a third overlapping automatic label on top.

## Gameplay-AI integration

- The shredder carbine changes firing-position scoring: its carrier should close to its
  useful band without pulling the rest of the fire team through the squad
  cohesion leash or into a dense hostile formation.
- The squad automatic should feed the existing overwatch and bounding model.
  Within a covering team, its carrier is preferred for a stanced firing lane;
  when that intact team becomes the moving element, the carrier moves and
  withholds fire with their teammates.
- Enemy use is an explicit content decision per family. Shredder defenders in
  apartments and commercial interiors materially change assaults; automatic
  defenders materially strengthen doorway and portal holds.
- New policy remains faction-neutral. Player and defender infantry with the
  same family use the same engagement-band and fire-team rules.

## Boundaries

- No marine-role hierarchy. Fire teams and their current maneuver roles remain
  the AI unit; a weapon family may influence suitability without minting a
  permanent “shotgunner” or “gunner” class.
- No attachment axis. Grade remains the quality modifier.
- No primary-ammunition economy. Slug magazines and energy cells explain the
  weapons, but this story does not make ordinary marines run dry mid-battle.
- Neural/HUD uplinks are compatible provenance and presentation, not a hidden
  accuracy bonus. Any mechanic that changes target registration or squad fire
  arcs must be separately authored and must not duplicate grade, aptitude,
  experience, or AI knowledge.
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
- Focused AI scenarios demonstrate shredder carriers seeking their useful band
  without breaking cohesion and automatic carriers contributing while their
  team covers but withholding fire when that team bounds.
- Existing mission force ratios are re-verified against the shipped S1
  lethality scale.
