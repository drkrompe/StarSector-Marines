MANTICORE (P) - CINEMATIC GEOMETRY (PASS 1 BLOCKOUT)

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

Files
build_manticore.py (driver), mt_kit.py, mt_hull.py, mt_engines.py,
mt_mounts.py, mt_lights.py (one module per area), compose_review.py (IoU,
shot extents, review set; Python + Pillow + numpy), geometry.json.

Rebuild from this folder (outputs go to build/manticore/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_manticore.py -- \
    --views top,three-quarter,port-side,front,stern,details,shot --manifest
python compose_review.py --manifest [--review DIR]

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
