MANTICORE (P) - CINEMATIC GEOMETRY (PASSES 1, 2, 4 SCALE, 6 LIGHT LIVERY; REVIEW ROUND 2)

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

Scale (pass 4): 0.54 m per unit
Published through the shared sizing basis, ../ship_scale.py (owner's option C),
never set here: METRES_PER_UNIT in build_manticore.py reads
ship_scale.PUBLISHED and geometry.json's 'scale' is ship_scale.record('Manticore (P)').
Inputs: 80 crew, 80 cargo, 50 fuel, destroyer class. Enclosed volume is the
union of ship_scale.SOLIDS['manticore']: bow head, neck, core, wing body, aft
body, ventral keel, bridge plinth and dome (drive cans, gun pods, launcher
housings, armour plates and horns, and weapons excluded): 310,119 units3 after
review round 3 (310,119 at round 2, 158,541 at round 1, published 0.67). That
solves to 0.546, published as 0.54: 83 m long, 87 m across the gun pods, 36 m
keel to mast. Against known ships: 1.4 Hounds (61 m) long, 0.77 of a Colossus
(108 m). The battle roster's plan-area estimate was 96 m. A 1.8 m person is
3.3 units. The driver re-measures the union on every build and fails if it
drifts more than 2% from ship_scale.HULLS. Viewports (1.0 x 0.8 m) and the
dome's glazed band (1.1 m) are sized in true metres.

Owner review round 3 (2026-10-19)
"Lower the nose ... the wings tapering downward ... a bird of prey", and
"you raised the bridge higher" (round 2's command tier was a taller bridge,
the one answer the owner had ruled out). The head, horns, prow, crossbar,
neck and rails bend down 0.30 per unit forward of X 62 (mt_hull.droop; the
bow ring stands level on it); the wing deck falls 0.16 per unit outboard of
|Y| 24 and 0.42 outboard of |Y| 46, so the arms bend down to low gun pods.
The bridge is back on a low raked plinth at the core deck (eye Z 19.9, its
round-1 height); instead the Hellbore sits 7.4 units down a 17.5-radius pit
with the deck ahead of it falling to 9.5, so its roof (17.8) is under the
eye. Measured (geometry.json 'forward'): dead ahead clear to 3 deg down,
6-11 deg down on every other bearing to 90 deg either side; the bow gun is
behind the Hellbore's barrel; all six mounts still bear dead ahead.
Enclosed volume 294,878 units3 solves 0.546, published 0.54 unchanged.
Round 2 below is kept for its lofted sections; its command tier is gone.

Owner review round 2 (2026-10-19)
"Comically big central turret", "a thin ship ... flying wings", "what does
the captain see?", "does NOT need to be one flat surface", "can push hull
down". Head, neck and core are lofted through faceted sections (flat deck only
under a turret ring, sloped shoulders and lower faces) reaching to -22, with a
faceted ventral blade to -30. The neck is a glacis climbing from the bow gun's
deck to the main gun's; the deck drops in a step ahead of the main gun, which
is sunk in a 4.4-unit well. A raked command tier lifts the dome and glazing,
and the arms droop 9 deg outboard. The driver measures both owner questions on
the built geometry and records them in geometry.json 'forward': the bridge
eye sees clear to 10.75 deg down, then the bow gun over the Hellbore's roof
(the bridge-eye camera renders that view); all six mounts trained to 0 deg
have a clear muzzle ray dead ahead (--ahead poses the review renders that
way). IoU 0.946.

Owner review round 1 (2026-10-19)
"Very flat surfaces", "the central turret is comical", "a better
interpretation for the front head". WS 000 now carries a Hellbore (owner's
choice) instead of the kit's Mark IX; the core deck is crowned (shoulders
falling 22 deg outboard of the turret ring, aft deck falling 14 deg), the wing
plates carry forward bevels and an outboard fall, wing and pod undersides
are deeply chamfered, the pods carry red armour collars on their outboard
halves; the bow head is a gunmetal frame round the WS 002 ring with two red
armour horns wrapping its forward shoulders, a red crossbar behind the ring,
a raked grey prow block, and long red rails down the neck. IoU 0.946 (the
horns wrap a cell past the head's side walls).

Detail (pass 2)
The wing is a gunmetal structure carrying three raised red armour plates a
side, a dark strake down each swept trailing edge and a spar to each gun pod;
tan sensor domes at the wing roots; neck ribs between red cheek strips; a
segmented collar round the Mark IX; the command dome's glazed band, visor
brow, mast and dish; crew-deck viewports down the command tier; aft heat vents; canister-flak
hatches at the four hidden system slots (mt_detail.py).

Livery (pass 6, light)
The pirate skin as procedural weathered paint (mt_kit.livery): the shared
turret kit's paint tree (turret_kit.paint_material: low mottle, edges chipped
to primer and steel, grime in crevices), plus per-plate shade variation
(Object Info random) for patched, re-sprayed armour and streaked grime on
object coordinates, the "poorly maintained" of manticore_pirates.skin. Blood
red armour, painted brown gunmetal, grey plate, pale command dome. Nothing to
pack; the guns keep their own sprite paints. Like the Lasher, this is the
light pass for a hull seen at 1.5-4 km; Codex part skins only if a shot
brings her closer.

Files
build_manticore.py (driver), mt_kit.py, mt_hull.py, mt_engines.py,
mt_detail.py, mt_mounts.py, mt_lights.py (one module per area), compose_review.py (IoU,
shot extents, review set; Python + Pillow + numpy), geometry.json.

Rebuild from this folder (outputs go to build/manticore/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_manticore.py -- \
    --views top,three-quarter,port-side,front,stern,details,shot,bridge-eye --manifest [--ahead]
python compose_review.py --manifest [--review DIR]

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
