# Story — Ground turret tiers: point emplacements and raised hardpoints

Status: IN PROGRESS

Written: 2026-10-23
Updated: 2026-10-23 — midline and high-tech emplacement styles built (directions pending the owner's picks).

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
2. **Raised guns** (built): `plinthTiers` and `maxDepressionDeg` in the turret catalog; muzzle
   height in turret fire and in the disciplined crew's lane check; eye height in sight;
   a scene showing a raised gun firing over its own line against a ground-level control
   (`sceneEvidence -Pscene=raised-gun`). `structure.hardpoint-cannon` (3x3, one tier, the
   Hephaestus weapon, 15 degrees of depression, so a dead zone of about 15 cells) is the
   placeholder hardpoint until slice 5 gives it art. Not built: incoming fire at a raised gun
   still meets the low cover round its plinth as if the gun stood on the ground.
3. **Art direction** (built): three looks, kept by the owner as **emplacement styles**
   (`EmplacementStyle`): brutalist (low tech, prepared: poured collars and plinths),
   earthwork (low tech, militia: sandbag and gabion works) and derelict (rusted colony
   steel, for abandoned sites). A structure's `looks` in the turret catalog names each
   style's base and head art; brutalist is its default art. Presentation only, as a tech
   family is: no rule reads a style. Midline and high-tech each add a prepared and a
   militia style, as their own design language (`tech-families.md`), not a recolour:
   modular (League-standard pressed panels in slate blue-grey, strip lamps) and barrier
   (the same heads on stepped, earth-filled mesh barrier cells); composite (seamless white
   shells, graphite gaps, cyan emitter seams, a rounded ramped pedestal) and deployed (the
   same pods on drop pads and a strutted platform). Every look's heads fill the brutalist
   heads' boxes, so one set of muzzles serves all. A militia style's plinth must read as a
   storey from above (stepped courses, a deep deck skirt), not as a paved square.
4. **Point emplacement family** (built): the existing one-cell structures carry the new
   art (Vulcan and heavy MG the autogun, Arbalest and Hephaestus the cannon, grenade
   launcher and both flak positions the grenade head) on a collar, each with its
   earthwork and derelict look. The carried point-defence pod takes the new head with no
   collar, since it is set down in the field. The heavy mortar keeps its vanilla sprite.
   The light post is four concrete parapets and a paved apron round its gun.
5. **Hardpoints** (built): `structure.hardpoint-cannon` (heavy head) and
   `structure.hardpoint-locust` (the vanilla Locust head) on the 3x3 plinth. A large post
   may draw the 5x5 hardpoint layout (plinth plus paved apron); the artillery post is
   now the Locust hardpoint. Apron is a post cell kind: walkable ground the stamper paves.
   The wedge layout (one point gun behind a T of parapets) is retired: a single point
   gun is the medium post, so large posts are multi-gun lines or the hardpoint. Drafts are
   compared in `createSnapshots -Psnapshot=large-post-look`.

A generated post takes its style from the defenders' market at generation
(`EmplacementStylePolicy`): the faction's tech family picks the language (Persean League,
Diktat and Lion's Guard midline, Tri-Tachyon high-tech, everyone else low-tech, pirates
derelict), and prepared defences (ground defences, heavy batteries or a defence level
above one) pick its prepared style, the rest its militia one; no market builds brutalist.
A militia post's parapets are sandbags and it is not paved.

## Turret effects

Presentation only, so a battle plays the same with or without them:

- **Recoil.** Each mount authors its barrel's kick (`render.recoil`: distance as a share of the
  mount's size, and the seconds it takes to settle): autoguns chatter, cannons kick and settle
  slowly, the hardpoint's barrel slides back a third of a cell.
- **Muzzle blast.** The cannons' muzzle effects throw dust forward off the ground.
- **Barrel smoke.** A turret's barrel smokes once a burst stops, longer after a long burst
  (`TurretFx`, fed by the live screen and the review frames alike).
- **Brass.** A mount with `render.casings` throws a glint of brass to its right on every round,
  which lands as a casing decal beside the gun.
- **Scorch.** A mount with `render.scorch` (a length in cells: the hardpoint cannon and the
  Hephaestus point gun) lays a fan of soot on the ground ahead of its muzzle on every round
  (`DecalKind.MUZZLE_SCORCH`, drawn by `art-source/fx/build_muzzle_scorch.py`). Each fan is
  faint and turned a little; overlapping fans darken the mark, and no more than five stack on
  one spot, so a gun's ground darkens without blackening.
- **Damage.** A turret below half its structure smokes, faster as it gets worse; below a quarter
  it throws sparks too.
- **Night light.** The muzzle flash already lights the ground around a turret in the live game.
- **Plinth shadow.** A hardpoint's plinth is a block of its tiers in the ground relief's height
  field (`CellTopology.getPlinthTiers` over the footprint, read by `MacroReliefField`), so the sun
  composite casts it exactly as it casts a wall: same direction, same reach per metre, about five
  cells from a 4 m plinth at the default sun. The block goes with the structure; its rubble casts
  nothing.

`createSnapshots -Psnapshot=ground-turret-look` plays a `firing-<style>` shot with all of it
(`-Dsnapshot.groundTurrets.firingSeconds=30` for long enough to see the scorch build up).

## Open

- The midline and high-tech directions (League standard, Tri-Tachyon white) are the
  recommended options on the picks page; the alternatives' drafts stay in
  `family_drafts.py` / `createSnapshots -Psnapshot=ground-turret-family-draft`.

Settled: a raised gun takes no low cover at its foot (`UnitRosterService.standsAboveLowCover`);
pirate worlds build derelict, and a mission picks derelict for an old work through
`EmplacementStylePolicy.restyle`; a hardpoint's plinth casts a sun shadow from its height.

## Acceptance

- A raised gun behind its own infantry line hits targets past them without striking
  them; the same gun at ground level does not (scene verdict, a mechanic).
- A raised gun does not engage inside its dead zone.
- Renders of each slice at 1920x1080 sent to the owner.
