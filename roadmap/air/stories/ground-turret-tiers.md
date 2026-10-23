# Story — Ground turret tiers: point emplacements and raised hardpoints

Status: IN PROGRESS

Written: 2026-10-23

Read `ground-defences.md` (defence nests, multi-cell structures), `elevation-nouns.md`
(tiers, sight) and `ballistics-nouns.md` (muzzle height, catch bands) first.

## What the owner asked for

Ground turrets lean on vanilla ship-weapon sprites (Vulcan, Arbalest, Hephaestus,
heavy mortar, dual flak, light mortar) drawn on a flat pad, so a gun reads as pasted
on whatever ground it was given. The owner wants two tiers (2026-10-23):

1. **Point emplacements** — one cell, weak, automated, many: machine-gun-like static
   defence in one visual family, built into the ground it stands on.
2. **Hardpoints** — several cells, a concrete plinth under a heavy gun so it reads as
   height, shooting over the cells round it (its own infantry and works) and leaving a
   dead zone at its foot.

Decided: height is **real** (it is the elevation model's height, not a cosmetic
rule); the Locust stays (its sprite works); the Vulcan is retired once a replacement
is approved; every look step is shown to the owner as renders.

## Model

- A structure may stand its gun on a **plinth** of whole tiers (`plinthTiers`, 0 by
  default). One tier is the elevation tier rise (4 m, so 4 cells at one metre a cell).
- **Fire.** The muzzle is that high: every round the mount fires starts at the plinth's
  height and descends along its line to where it was aimed (the ballistic resolver's
  muzzle `z`). Friends, sandbags and low cover near the gun lie under the round and are
  overflown; walls stay full height, as they are at every tier. Nothing is special-cased:
  how far a gun shoots over is the geometry of its height and its target's distance.
- **Dead zone.** A gun on a plinth has a depression limit (`maxDepressionDeg` on the
  mount). Its minimum range is the larger of its weapon's own and the plinth height over
  the tangent of that limit: bodies at the foot of the plinth are under the guns.
- **Sight.** The gun's eye stands at its ground's tier plus its plinth: from a plinth it
  sees onto ground up to that tier, and, by the one sight rule, anything it can see down
  to sees it back.
- **Destroyed.** Height belongs to the gun, so a destroyed hardpoint's footprint becomes
  rubble at ground level as any structure's does.

## Slices

1. **Before evidence** (built): `createSnapshots -Psnapshot=ground-turret-sites` frames
   every kind of generated turret site on Conquest maps at play zoom.
2. **Raised guns**: `plinthTiers` and `maxDepressionDeg` in the turret catalog; muzzle
   height in turret fire and in the disciplined crew's lane check; eye height in sight;
   a scene showing a raised gun firing over its own line against a ground-level control.
3. **Art direction**: two or three looks for both tiers rendered on real scenes, put to
   the owner on the picks page.
4. **Point emplacement family**: the chosen look for the one-cell roles (autogun, light
   cannon, grenade/flak, point defence) with a collar base, replacing the vanilla sprites
   (the Locust excepted), and towers and nests sited to read as built.
5. **Hardpoints**: 3x3 plinth structures (heavy cannon, Locust artillery) with concrete
   under them, placed by the post stamper where a large post stood.

## Acceptance

- A raised gun behind its own infantry line hits targets past them without striking
  them; the same gun at ground level does not (scene verdict, a mechanic).
- A raised gun does not engage inside its dead zone.
- Renders of each slice at 1920x1080 sent to the owner.
