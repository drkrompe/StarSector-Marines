# Story — Ground turret tiers: point emplacements and raised hardpoints

Status: IN PROGRESS

Written: 2026-10-23
Updated: 2026-10-24 — the high-tech hardpoints' flux shield is a real directional mechanic; point guns lose the drawn bubble.

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
   style's base and head art; brutalist is its default art. Presentation, as a tech
   family is, except that a look may fit its structure's flux shield. Midline and high-tech each add a prepared and a
   militia style, as their own design language (`tech-families.md`), not a recolour:
   midline has two pairs, the League's and the Diktat's: modular (League-standard pressed
   panels in slate blue-grey, strip lamps) and barrier (the same heads on stepped,
   earth-filled mesh barrier cells); export (the Diktat's sand-and-rust export kit) and
   blockwork (the same heads on interlocking moulded blocks). High-tech is the field
   emitter: composite (pearl composite shells in the Paragon's hex ablative plating, glass
   domes, blue projector rings) and deployed (the same heads on a skirted deck). A
   composite or deployed hardpoint carries a directional flux shield (see Flux shields).
   Heads are cut by registering each piece to its own guide
   (`export_ship.fit_registered`): a family's guide puts its turret ring on the pivot and
   its barrel tips where the brutalist guide does, so heads sit centred on their collars
   and the brutalist muzzles serve every look. Never stretch one family's head onto another
   head's box; that moved the rings off the pivot. A militia style's plinth must read as a
   storey from above (stepped courses, a deep deck skirt), not as a paved square.
4. **Point emplacement family** (built): the existing one-cell structures carry the new
   art (Vulcan and heavy MG the autogun, Arbalest and Hephaestus the cannon, grenade
   launcher and both flak positions the grenade head) on a collar, each with its
   earthwork and derelict look. The carried point-defence pod takes the new head with no
   collar, since it is set down in the field. The heavy mortar keeps its vanilla sprite.
   The light post is a closed ring of eight concrete parapets round its gun, corner pieces
   included; a militia style sandbags the ring and leaves the gun's pit unpaved earth.
5. **Hardpoints** (built): `structure.hardpoint-cannon` (heavy head) and
   `structure.hardpoint-locust` (the vanilla Locust head) on the 3x3 plinth. A large post
   may draw the 5x5 hardpoint layout (plinth plus paved apron); the artillery post is
   now the Locust hardpoint. Apron is a post cell kind: walkable ground the stamper paves.
   The wedge layout (one point gun behind a T of parapets) is retired: a single point
   gun is the medium post, so large posts are multi-gun lines or the hardpoint. Drafts are
   compared in `createSnapshots -Psnapshot=large-post-look`.

## Flux shields

A hardpoint built by a field-emitter builder carries a **directional flux shield**: an arc
of the hex shield standing out from the gun toward its threat, which stops rounds from
that side and leaves the flanks and rear open. Point guns carry none.

- **Fitted by the look, defined by the structure.** The structure owns the generator
  (`fluxShield` in the turret catalog: arc width, radius, capacitors, dissipation, upkeep,
  flux per damage and per round fired, raise, lower and overload times, turn rate,
  linger); a style's look fits it with a tint (`"shield": "blue"`). This is the one thing a
  style decides that a battle reads. Blue is the high-tech field emitter (composite,
  deployed); orange is kept for a low- or mid-tech shield no style fits yet.
- **Raised when threatened.** The shield rises while the gun has a target, facing it, and
  while it is being shot at, facing the last shooter; it lingers a few seconds after the
  threat and then folds away. The arc grows out from its facing as it rises and covers
  only what has grown, and it swings round at its own turn rate, so a flank shot is open
  until the arc comes round.
- **Blocks by bearing.** A round is judged by its shooter's bearing from the gun: inside
  the arc it is stopped whole, before armour, and its damage goes onto the capacitors as
  flux. Damage credited to no one (a crash, a fire) is never stopped.
- **Costly.** The generator sheds flux always; holding the shield up costs upkeep (less
  than it sheds), and each round the gun fires with the shield up costs flux, so blocking
  is what fills the capacitors.
- **Overload.** Full capacitors break the shield: it shatters, the gun is silent, and the
  capacitors vent to empty over the overload; then it may rise again. Concentrated fire is
  how a shielded hardpoint is opened, and its silence is the moment to rush it.
- **Drawn from the sim.** `TurretShieldField` reads the shield (`TurretShieldService`) and
  draws it with the dome shader cut to the arc: strain whitening the field as flux fills,
  and a flare on the rim at each block. A break plays for under a second (the cells flicker
  out as shards, the arc narrows into its facing, a ring blows off it) and then nothing is
  drawn: a broken shield is gone until the overload ends and it is raised again.
- **Flux hardware you can read.** A shielded hardpoint carries one compact flux device on its
  plinth's west wall, about one cell by two: a capacitor at the north end ringed by six charge
  lamps, and a radiator at the south end. Each tech family paints it in its own language
  (`fluxDevice` on the look): a riveted steel box and three cast fins for low tech, a bolted can
  and a five-fin skid for midline, a pearl pod and glass fins in a capsule for high tech, from
  one guide per family (`make_flux_device_guide.py`, `export_flux_device.py`). The device is
  painted cold and at full charge, and the hot layer is laid over the cold one at the shield's
  heat (`TurretFluxHeat`); the lamps light one by one in the shield's colour as the capacitor
  fills, and the radiator gives off the flux haze ships, convoy guns and mechs share
  (`FluxHaze`), thin at half charge and thick near full. Past three quarters the glow and the
  last lamp stutter, sparks jump off the capacitor and the arc stutters with it; at a break the
  radiator blows out vapour (`TurretFx`) and vents the lavender plume through the overload as
  its lamps go out, and the shield may rise again once it is cold. Drawn only while a shield is
  fitted, so the low and midline devices wait for those families' shields;
  `-Dsnapshot.groundTurrets.fluxHardware=brutalist,modular,composite` renders each cold, at 80%
  and just broken.

`createSnapshots -Psnapshot=ground-turret-look` plays a `shield-<style>` shot for each
shielded style: a squad with rockets in front of the cannon hardpoint and a rifleman on its
flank, through a raise, blocks, a break and the shield's return.

A generated post takes its style from the defenders' market at generation
(`EmplacementStylePolicy`): the faction's tech family picks the language (the Persean
League builds League standard, the Diktat and Lion's Guard Diktat export, Tri-Tachyon the
field emitter, everyone else low-tech, pirates patchwork: old works patched with scrap, on derelict guns), and prepared defences (ground defences, heavy batteries or a defence level
above one) pick its prepared style, the rest its militia one; no market builds brutalist.
A militia post's parapets are sandbags and it is not paved.

## High-tech lasers

A high-tech builder (composite, deployed) fits lasers where the others fit guns: the structure
names what a style builds in its place (`builtIn` in the turret catalog), and a post built in that
style puts it up (`DefensePost.withStyle`, from the planned structure, so restyling starts from the
plan). There are two kinds, both the space game's own turrets up-res'd in Tri-Tachyon white,
graphite and cyan (`art-source/ground-turrets/laser/`):

- **Beam over time:** the PD laser (`structure.turret-pd-laser`, the laser tank's turret) for the
  vulcan, heavy MG and dual flak, and an anti-air version for the flak position. Its red beam
  plays ten pulses a burst and engages fliers.
- **Burst:** the Burst PD Laser (`structure.turret-burst-laser`, twin emitters) for the Arbalest,
  Hephaestus and grenade launcher: four quick blue pulses.
- **Hardpoint:** the laser tank's own red PD laser, turret and weapon, at hardpoint size
  (`structure.hardpoint-laser`) for the cannon hardpoint, behind its flux shield.

Every post laser rakes as a tank's lance does: a beam opens up to 20 degrees off its target, lands
short along the mount's facing and drags across onto it as the mount comes round, striking and
scorching whatever it crosses (`TurretBehavior`, `TurretFireSystem.fireBeam`). A line of infantry
is raked from one to the next; `-Dsnapshot.groundTurrets.rake=true` renders it.

Laser heads have no recoil and no barrel layer. A first-pass balance, matched to the damage per
second of the guns they replace, to be moved by play. Missions that spawn guns directly (not
through a post) are unchanged; the Locust hardpoint keeps its missiles.

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
- **Flak plume.** A dual-flak burst in the air leaves a dark smoke puff about the size of
  its burst, with lighter wisps trailing off it for a second or two: authored as the
  weapon's impact and aftermath layers, so the AA emplacement and the dug flak positions
  in the Heart missions share it.
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

- The League militia set borrows its grenade and point-defence heads from the prepared
  set, recoloured: their own sheet was refused by the image generator three times.
- The alternatives' drafts stay in `family_drafts.py` /
  `createSnapshots -Psnapshot=ground-turret-family-draft`.

Settled: a raised gun takes no low cover at its foot (`UnitRosterService.standsAboveLowCover`);
pirate worlds build patchwork, and a mission picks derelict for an old work through
`EmplacementStylePolicy.restyle`; a hardpoint's plinth casts a sun shadow from its height.

## Acceptance

- A raised gun behind its own infantry line hits targets past them without striking
  them; the same gun at ground level does not (scene verdict, a mechanic).
- A raised gun does not engage inside its dead zone.
- Renders of each slice at 1920x1080 sent to the owner.
