# Story — Body scuffs: worn plate drawn on live hulls and mechs

Status: ACTIVE — slices 1 (hull plates), 2 (turret) and 3 (scuff sheet) built; mechs open
Written: 2026-10-23

Read `combat-durability-nouns.md` (**Decisions, morale, evidence, and presentation**: presentation observes
simulation) and the located-plate model in `convoy-nouns.md` first; for mechs, the section model in
`mechs-nouns.md`. The warship sibling is `warship-hull-damage.md`, whose plating renderer this borrows from.

## Problem

Owner direction (2026-10-23): look into vehicle "scuff" decals that show damage building up on vehicles and
mechs as they take hits.

A live ground hull or mech looks exactly the same at full armour as with a plate stripped bare. The only damage
pictures are the wreck frame and the dead mech sprite, plus the durability bar over the hull. That leaves the
located-plate model invisible on the field. A flanked tank whose left side is gone looks no different from a fresh
one, so the player cannot see the thing the model was built to reward.

## Model

**A scuff is presentation, a pure function of located damage state.** Nothing is recorded per hit and nothing new
is written on the damage path. Each frame, the renderer reads what the simulation already holds:

- for a ground hull, each plate's armour against its maximum (`HullLocations`: front, left, right, rear);
- for a mech (later slice), each section's armour and structure (`MechSections`).

From that state it decides which marks to draw. Because the state is the authority, re-plating a hull removes its
scuffs, a review frame shows exactly what the screen shows, and a replay draws the same marks.

**Wear builds up in place.** Each plate has a fixed set of decal *slots*. Each slot has its own position,
rotation, kind and *threshold*, all hashed from the body's id, the plate and the slot number. A slot shows once
the plate's wear (1 − armour/max) passes its threshold, and fades in over a little more wear, so marks appear one
at a time as the plate is stripped. A mark that has appeared never moves. Early slots are scuffs and scorch
(burns), later slots cracks, and the last slots holes, so a nearly stripped side looks gouged.

**A mark belongs to the plate whose part of the hull it lies on.** A slot's position is picked from the points of
that plate's region of the hull sprite. The sprite's opaque footprint, turned into the hull's own frame by the
sheet's facing offset, is split along its diagonals into a front, rear, left and right region, so wear on the left
plate shows down the left side.

**A mark never spills off the silhouette.** At load, each hull frame's alpha is reduced to a coarse grid of opaque
points, and each point records how far it sits from the nearest transparent pixel. A slot may only take a point
whose inset is at least the mark's reach (how far its paint lies from its centre), so even a turned mark stays
on the hull. This is computed once
per vehicle line, from the image alone.

## Slices

1. **Hull plates (built).** `VehicleScuffs` holds the masks and the placement rule; `ConvoyRenderSystem` draws
   the marks over the hull and its treads, under the turret. The look shot
   `look/tank-scuffs-*` (suite `vehicle-scuffs`) shows a tank at 100, 66, 33 and 10% on every plate and its turret.
2. **Turret (built).** The turret section's armour drawn the same way over the turret frame, as one region, under
   the turret's own transform. On the breakthrough tank the turret covers most of the hull from above, so without
   it the hull's wear barely shows. A turret drawn from its catalog mount (`turretArtFromMount`) has no sheet frame
   and shows none yet.
3. **The scuff sheet (built).** The mod's own marks, made with the geometry-first ImageGen workflow
   (`geometry-first-imagegen.md`, sources in `art-source/vehicles/scuffs/`): a row per stage (scrapes and bullet
   pocks, scorch, gouges and dents) and four variants of each, coloured to sit on any paint. The base game's
   ship decals were tried first and read only as smudges on a tank. A mark is a hull-width share across
   (`DECAL_SHARE`); the owner has a size question open.
4. **Mechs.** Section wear drawn on the part sprite that carries the section (torso, each arm and shoulder, legs
   on the chassis), since the torso, hips and arms turn independently. Uses the same placement rule over each
   part's own mask.

## Constraints

- No new simulation state or write path. Hashes draw no random number from the battle.
- Nothing is drawn on a wreck: the registered wreck art already shows it.
- Mask building decodes each vehicle sheet once per process, when the sheet is loaded. It is not done on a frame.
  The pixels are read through `BattleSprites`, so a headless host reads them off its own roots.
- A mark addresses a cell of the scuff sheet, so the sheet needs a batch in `BattleRenderer`. Without
  one, the GL drain silently drops the quad while the Java2D drain still draws it.

## Acceptance

- The placement rule's laws hold on a synthetic mask (unit test): no marks at full plate; every mark's inset
  covers its reach; marks at lower wear are a subset, in the same places, of marks at higher wear; a mark
  lies in its own plate's region.
- The look shot reads as progressively battered at play zoom and close, with nothing hanging off the hull's edge.
