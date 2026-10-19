FALCON (P) - CINEMATIC GEOMETRY

Purpose
The pirate flagship of the Asharu battle (asharu-battle/asharu-battle-shot.md):
mid-distance at 3-5 km in S4-S6, and large in the foreground of S10 "The fall",
over its shoulder from about 500 m behind and above, where its dorsal and aft
surfaces fill a third of the frame. It follows the ship-model-pipeline skill
(.claude/skills/ship-model-pipeline) and the Lasher package's conventions
(../lasher-geometry/), and shares no code with another hull; turrets come
from the shared turret kit (../turret-kit/).

Hull identity
The vanilla skin data/hulls/skins/falcon_p.skin ("Falcon (P)") on the base
hull falcon (data/hulls/falcon.ship; ship_data.csv row "falcon": Light
Cruiser, Midline, maneuveringjets, 100-180 crew, 120 cargo, 100 fuel, FRONT
shield 270 deg). The skin uses the sprite graphics/ships/falcon/falcon_p.png
(162 x 222), removes WS 003 and WS 004, retypes WS 005/006 to COMPOSITE and
WS 007/008 to MISSILE, and builds in augmentedengines and unstable_injector
(the pirates' patchwork drives). Weapons follow the only (P) variant,
data/variants/falcon/falcon_p_Strike.variant. The base sprite falcon_base.png
was read alongside for shapes the pirate paint hides. There is no lights
layer. Install files are read where they are (art-source/starsector_path.py)
and never copied; the driver checks every slot and engine against them.

Coordinates
+X bow, +Y port, +Z dorsal. One sprite pixel is one unit about the .ship
centre (81, 88), so slot and engine XY from falcon.ship are used unchanged.
Sprite column c, row r (from the top) covers X 134-r..135-r, Y 80-c..81-c.
Heights are proposed art.

Build
  sh ../../blender.sh --background --factory-startup --python build_falcon.py -- [--out DIR] [--views ...]
  python compose_review.py [--dir DIR] [--review DIR]
Outputs go to build/falcon-p/ under the repository root (ignored): renders,
geometry.json and falcon-p.blend, the appendable collection `FALCON (P)` with
root `FALCON (P) | root`. --no-detail builds the pass 1 blockout. --manifest
copies geometry.json back here (the tracked copy).

Modules
  fc_kit      materials, primitives, prisms, lofts, booleans, surface()
  fc_hull     prongs, central body, bridge dome, waist decks and tanks, drive
              blocks, outrigger pods, bow fork jaws
  fc_detail   pass 2 detail
  fc_engines  the twelve drives (Engine 1-6 main, 7-12 manoeuvring)
  fc_mounts   the eight slots the skin keeps: turret-kit turrets on WS 005/006
              (typhoon) and WS 009/010 (the kit's pdlaser standing in for the
              IR pulse laser), box launchers on WS 001/002 (swarmer) and
              WS 007/008 (annihilator pod)
  fc_lights   running lights and the bridge glazing

Scale (pass 4): 0.81 m per unit
From the shared sizing basis, ../ship_scale.py (option C), never set here:
180 crew, 120 cargo, 100 fuel, cruiser class, no fighter bays. Enclosed volume
is the union of ship_scale.SOLIDS['falcon-p']: both prongs, the central body,
the bridge dome and the waist decks (the drive blocks and outrigger pods are
drive housings, the bow forks armour round the hardpoints, the waist tanks
laid on the deck: all excluded), 227,910 units3, which solves to 0.807 (range
0.782-0.831). The hull is 174 m long overall (bow probes to drive lips),
131 m across the outrigger pods and 29 m keel to turret top: 1.6 Colossi
(108 m), 0.58 of an Onslaught (301 m), 2.9 Hounds (61 m). The sprite
plan-area estimate the battle blockout used was 159 m; the built hull is a
little slimmer than that estimate assumes. The blockout measured 0.80; pass 2
opened the waist aft of the bridge (the prongs' inner aft edge runs out
diagonally, as the sprite shows), which moved it to 0.81. The driver
re-measures the union on every build and fails if it drifts more than 2% from
ship_scale.HULLS. A 1.8 m person is 2.2 units (--figure).

Detail (pass 2, fc_detail)
Built for S10, which looks over the Falcon's port quarter from behind and
above: plated prong decks, glacis and belts with seam gaps (plates laid on
the loft's own facets; bare aprons round the turrets), a ridge cap on the
turret line, red pirate patch slabs bolted over the aft decks, two decks of
lit viewports on each prong's inner wall, sensor housings, vents and hatches;
bridge dome bands, crown hatch and mast; the dorsal spine's radiator fins and
pipe runs; banded waist tanks on saddles with pipework to the drive blocks
and across the waist; the drive blocks cut back so the six drives stand proud
as banded cylinders (each main drive's shroud runs its can out to the lip),
a trough exposing the inboard engine's bare core in its cradle, fins, patch
plates and cable bundles on top; outrigger plating and struts; probes at the
bow forks; a stern airlock. Silhouette IoU 0.935 (blockout 0.936).

Texture (pass 6, ../falcon-surface)
The shared surfacing harness with this hull's inputs: a pirate-livery finish
reference and six Codex part sheets (prong decks and glacis per side, belts,
inner walls, body centre, body aft) plus a class swatch sheet, graded to the
reference's palette, applied with the `pirate` wear row. The deck and glacis
plates carry the sprite's red and sand repaint per plate (fc_detail.Livery).
build_falcon.py saves falcon-p-geometry.blend (the review scene the harness
opens) beside falcon-p.blend; export_falcon.py makes the packed asset from a
build or from the harness's skinned.blend.

Effects and export (pass 7)
vfx_hull.py is this hull's profile for the shared VFX kit (../second-wind-vfx):
twelve MIDLINE drives (1-6 main, 7-12 manoeuvring), the eight weapon slots in
manifest order as mounts (launchers for the swarmers, typhoons and annihilator
pods, guns for the IR pulse lasers) and six lamps. export_falcon.kit_muzzles
adds the kit's declared muzzles, `Anchor turret <i> tube|barrel <k> muzzle`,
parented to each `Weapon <id> muzzle anchor <k>`, so a trained turret carries
them. export_effects.py adds the `SWFX` collection beside `FALCON (P)`;
verify_asset.py appends the asset, checks roots, anchors, packed images and
muzzles, trains every turret through its arc and instances it three times.
review_effects.py renders the effects (`--views`, `--percent`, `--samples`
keep a check cheap); a shot fires mounts with swfx_guns.fire(ctx, <mount>, ...).
