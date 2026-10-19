MANTICORE (P) - CINEMATIC GEOMETRY (PASSES 1, 2, 4 SCALE)

Purpose
A scripted Blender model of the vanilla Manticore in its pirate skin for the
Asharu battle cinematic (asharu-battle/asharu-battle-shot.md): the
long-range destroyer that opens the fight and breaks the escort screen. S5
("The opening") frames it on an 85 mm lens along its line of fire at mid to
long range; S4 and S6 see it at about 4 km. It follows the ship-model-pipeline
skill and the Lasher package's conventions (../lasher-geometry/) but shares no
code with another hull except the shared turret kit (../turret-kit/).

Hull identity
manticore (data/hulls/manticore.ship; ship_data.csv row "manticore":
Destroyer, Low Tech, canister_flak system, 50-80 crew, 80 cargo, 50 fuel,
OMNI shield 150 deg, built-in ballistic_rangefinder) in the pirate skin
data/hulls/skins/manticore_pirates.skin (no slot or engine changes; sprite
graphics/ships/manticore/manticore_pirate.png, 164x154, .ship centre (82, 67)
from the bottom edge; no lights layer). Weapons from
data/variants/manticore/manticore_pirates_Support.variant. Install files are
read where they are and never copied into the repository.

Coordinates
+X bow, +Y port, +Z dorsal. One sprite pixel is one unit about the .ship
centre, so slot and engine XY are used unchanged. Sprite column c, row r
(from the top) covers X 86-r..87-r and Y 81-c..82-c. Heights are proposed
art; the game gives none.

Scale (pass 4): 0.66 m per unit
Published through the shared sizing basis, ../ship_scale.py (owner's option C),
never set here: METRES_PER_UNIT in build_manticore.py reads
ship_scale.PUBLISHED and geometry.json's 'scale' is ship_scale.record('Manticore (P)').
Inputs: 80 crew, 80 cargo, 50 fuel, destroyer class. Enclosed volume is the
union of ship_scale.SOLIDS['manticore']: bow head, neck, core, wing body, aft
body, ventral keel and command dome (drive cans, gun pods, launcher housings,
armour plates and weapons excluded): 163,199 units3. That solves to 0.665
(range 0.645-0.685), published as 0.66: 100 m long, 106 m across the gun
pods, 25 m keel to gun top. Against known ships: 1.6 Hounds (61 m) long, 0.93
of a Colossus (108 m). The battle roster's plan-area estimate was 96 m. A
1.8 m person is 2.7 units. The driver re-measures the union on every build and
fails if it drifts more than 2% from ship_scale.HULLS. Viewports (1.0 x 0.8 m)
and the dome's glazed band (1.1 m) are sized in true metres.

Detail (pass 2)
The wing is a gunmetal structure carrying three raised red armour plates a
side, a dark strake down each swept trailing edge and a spar to each gun pod;
tan sensor domes at the wing roots; neck ribs between red cheek strips; a
segmented collar round the Mark IX; the command dome's glazed band, visor
brow, mast and dish; core flank viewports; aft heat vents; canister-flak
hatches at the four hidden system slots (mt_detail.py).

Files
build_manticore.py (driver), mt_kit.py, mt_hull.py, mt_engines.py,
mt_detail.py, mt_mounts.py, mt_lights.py (one module per area), compose_review.py (IoU,
shot extents, review set; Python + Pillow + numpy), geometry.json.

Rebuild from this folder (outputs go to build/manticore/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_manticore.py -- \
    --views top,three-quarter,port-side,front,stern,details,shot --manifest
python compose_review.py --manifest [--review DIR]

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
