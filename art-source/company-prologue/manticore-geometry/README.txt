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

Scale (pass 4): 0.59 m per unit
Published through the shared sizing basis, ../ship_scale.py (owner's option C),
never set here: METRES_PER_UNIT in build_manticore.py reads
ship_scale.PUBLISHED and geometry.json's 'scale' is ship_scale.record('Manticore (P)').
Inputs: 80 crew, 80 cargo, 50 fuel, destroyer class. Enclosed volume is the
union of ship_scale.SOLIDS['manticore']: bow head, neck, core, wing body, aft
body, ventral keel, bridge plinth and dome (drive cans, gun pods, launcher
housings, armour plates and horns, and weapons excluded): 310,119 units3 after
review round 5 (294,878 at rounds 3-4, published 0.54; 158,541 at round 1).
That solves to 0.594, published as 0.59: 91 m long, 95 m across the gun pods,
33 m keel to mast. Against known ships: 1.5 Hounds (61 m) long, 0.84 of a
Colossus (108 m). The battle roster's plan-area estimate was 96 m. A 1.8 m person is
3.1 units. The driver re-measures the union on every build and fails if it
drifts more than 2% from ship_scale.HULLS. Viewports (1.0 x 0.8 m) and the
dome's glazed band (1.1 m) are sized in true metres.

Owner review round 11 (2026-10-20)
"What I see out of the sprite is that the wings besides the front sections are
exposed structural framework of the wings, with the armor being spent angled
ahead for combat 'facing' a target." The solid wing now ends just behind the
leading edge, where each arm's armour band is a thick raked glacis (top 3
units over the deck, 12.5 deep); behind it each arm is open lattice
(mt_frames): a main spar girder out to the gun nacelle, a trailing-edge spar
from the nacelle's tail into the engine block, five truss ribs, X-bracing top
and bottom and a stringer, three conduits threaded through to the nacelle,
two rams at its root, and the sensor dome on a platform across two ribs. The
nacelles carry red glacis noses and the beak strakes stand higher. The band
no longer reaches into the main gun's well (it did in rounds 9-10). Mounts
bear ahead as before. Ray-cast IoU 0.811 raw; with the lattice's see-through
gaps filled the outline is 0.923 (round 10: 0.926); the gaps are 11.6% of the
outline. Scale open: the enclosed volume is now 164,819 units3, 28% under the
scale table, so the manifest build's 2% check fails; 0.59 m/u is kept until
the coordinator decides, and geometry.json is still round 10's.

Owner review round 10 (2026-10-20)
"The ship is coming together - next need to read the implied exposed hull
framing and machinery from the sprite and add this detail into the model."
mt_frames.py lets real structure into recesses cut in the round-9 panels:
an open channel inboard of each swept trailing edge holding a Warren truss
(chords, zigzag diagonals, frames); a bay in each engine-block shoulder with a
banded tank on cradles and feed pipes running aft to the outer drive; framed
heat-vent bays either side of the engine deck's centreline, glowing at the
floor; a machinery bay in each gun nacelle's tail with frames and two rams;
coaming rails and a back beam round each launcher recess. The aft deck's old
surface ribs and vent plates are gone (now these bays). Round 9 forms,
silhouette (ray-cast IoU 0.926), mounts and 0.59 m/u kept; the open bays take
volume out of the measured solids, 224,599 units3 (1.8% under the scale
table, inside its 2% check).

Owner review round 9 (2026-10-20)
"Manticore hull needs long angular hull sections. This reads as bloby with
the small angular sections." The primary forms are now a few long panels with
straight creases: head and neck lofted through four and two stations, the
core one constant section from the gun's step to the engine block; the swept
trailing edge one straight line; the leading-edge armour and launcher bay one
red band swept along the whole leading edge from the neck to the gun nacelle
(the Harpoon recess cut into it); two long red armour panels a side swept
parallel to the trailing edge, each creased once; three-panel gun nacelles
with one long flank plate; the bow strake's inboard edge one line. Gone: the
third wing plate, the leading-edge skirt, the pod spars and nose glacis.
Silhouette, flush bays, mounts bearing ahead and 0.59 m/u kept (volume 226,129
units3; ray-cast IoU 0.926 against round 8's 0.927 by the same method).

Owner review round 8 (2026-10-19)
"I don't like how the hull juts out for the missiles." The launcher bay no
longer grows forward of the arm: it ends at the leading edge with its face
raked 45 deg like the leading-edge armour beside it, and the Harpoon rack sits
in its recess, open through the edge. All six mounts still bear ahead.

Owner review round 7 (2026-10-19)
"The missile hookup ... like we just cut a wedge into the hull", and "cohesive
angling ... very wobbly in places". Each Harpoon slot sits on the arm's
leading edge, so the arm now grows a faceted red launcher bay forward round it,
deck flush with the wing and following its anhedral; the rack sits down a
chamfered recess in the bay, open forward. The hull now speaks one angle
vocabulary (mt_hull SHOULDER/LOWER/FALL/RAKE): lofted shoulders fall 30 deg
and lower faces 45 deg, armour plates fall 30 deg and rake 45 deg, the forward
hull bends down in one line from X 10 (0.18 per unit) and the arms in one
anhedral (0.30 per unit outboard of |Y| 24). The stern keeps its accepted
sections. Bridge clear to 4.5 deg down dead ahead, 10-18 deg elsewhere; all
six mounts bear dead ahead and 10 deg below ahead. Volume 225,259 units3;
0.59 m/u stands.

Owner review round 6 (2026-10-19)
"Remove the hockey pucks from the two harpoon pods", "the arm ends are oddly
cylinderish", "the front needs cohesion work", "angular armor ... reminiscent
of the Onslaught"; the stern was accepted. The Harpoon hardpoints never train,
so they get no barbette and no kit turntable: each rack stands on its pocket
floor (mt_mounts). The octagonal gun pods are now faceted nacelles lofted to a
point forward, with a raked red flank plate and a chevron glacis on the nose.
One red armour strake a side now runs from the core down the neck and wraps
the head to the beak's tip, where the two converge (replacing the separate
horns, neck rails and prow block), and a thick raked red plate armours each
arm's root leading edge. All six mounts still bear dead ahead and 10 deg below
ahead; 0.59 m/u stands (228,901 units3).

Owner review round 5 (2026-10-19)
"The missile pods ... inset into the hull", "the back near the engines less
blocky", "the central underside too fat ... slender ... lean into this
downward angling ... to get more guns onto targets ahead". The raised
launcher housings and cheeks are gone: each Harpoon rack sits 4 units down a
pocket in the wing, open forward through the leading edge. The aft engine
block is lofted through faceted sections tapering to the drive face, so the
cans stand out of it. The core's underside is a slender V (keel a quarter of
the beam, no deeper than -18; the ventral blade a thin fin to -22), and the
wings fall 0.50 per unit outboard of |Y| 46. All six mounts bear dead ahead
and 10 deg below ahead (geometry.json forward.bearing_dead_ahead). The slimmer
belly moved the scale: 228,776 units3 solves 0.594, published 0.59 (was 0.54):
91 m long.

Owner review round 4 (2026-10-19)
"The neck is still angled up? ... The central hull can go down." Round 3
bent only the beak; now the whole forward hull bends down from just ahead of
the bridge (mt_hull.NOSE_DROOP: 0.10 per unit forward of X 10, 0.32 forward of
X 62), core front, gun pit, neck, wing leading parts, pods, launcher housings
and head alike, so the line runs downhill from the bridge to the beak. Turret
rings stay level. Bridge eye clear to 4 deg down dead ahead, 9-14 deg on every
other bearing; all six mounts still bear dead ahead. Volume 294,597 units3;
0.54 m/u stands.

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
mt_detail.py, mt_frames.py, mt_mounts.py, mt_lights.py (one module per area), compose_review.py (IoU,
shot extents, review set; Python + Pillow + numpy), geometry.json.

Rebuild from this folder (outputs go to build/manticore/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_manticore.py -- \
    --views top,three-quarter,port-side,front,stern,details,shot,bridge-eye --manifest [--ahead]
python compose_review.py --manifest [--review DIR]

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
