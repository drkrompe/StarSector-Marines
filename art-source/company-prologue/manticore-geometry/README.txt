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
drifts more than 2% from ship_scale.HULLS. Crew-deck windows (1.5 x 0.6 m,
two panes) and the dome's glazed band (1.1 m, a mullion every metre) are
sized in true metres.

Owner review round 13 (2026-10-20)
"Manti-core looks good. Let's continue with refined detail across the hull
and command deck." Round 12's forms are accepted; mt_refine.py lays detail
on them, seated by ray-casts and sized in true metres. Command deck: mullions
and a sill on the glazing band, an aft mast with a radar bar, yards, whips
and red beacons on both masts, two comms dishes, two 1.2 m roof hatches aft
of the dome, walkways either side of it with 1.1 m rails on a 1.5 m pitch,
and an aft rail. Hull: plate seams along the forms (core shoulders and aft
deck, glacis bands, wing armour, pod collars, beak strakes); eight pirate
patch plates with weld beads, placed unevenly; 1 x 2 m crew doors on the
core's flanks into the wing framework, with landings; three deck hatches;
RCS quads at the bow, the nacelles and the engine block; finned heat
exchangers on the engine block's shoulders; transfer pipes from each wing
tank into its shoulder machinery bay. Fixed: the trailing spar's alternating
closed panels read as windows; its outer face is now one continuous lower
web. Outline IoU 0.924 with the gaps filled; mounts and seats unchanged;
0.59 m/u owner override.

Owner review round 12 (2026-10-20)
"Next less AND thicker framing though - light interspaced plating and
machinery / tubing. Also the armor should wrap a bit further back, the outline
of this 'armor' should be visible like it is to me in the sprite." The armour
edge is read off the sprite's red: a red plate over the solid forward wing
runs back from the glacis to X 15.5 across each arm and wraps aft along the
core's flank to X -1.5, its edges chamfered 45 deg so the aft edge is one
raked line from above and from the side; the solid wing stops 2 units ahead
of it. Behind it the regular grid is gone: a heavy trailing-edge spar girder
(red-capped, every other face panel closed), two deep I-girder ribs with
lightening holes (the outer one, at Y 45, red-capped as the sprite draws it),
three heavy braces, three partial plates, two big conduits collared at the
ribs, a banded tank on cradles, a pump with feed pipes, two heavy rams to the
nacelle and the sensor dome's platform. Fixed: the tank, plinth and dome were
never lowered with the arm's anhedral (shear read their local coordinates;
round 11's dome floated). Outline IoU 0.924 with the gaps filled (0.878 raw,
gaps 4.8% of the outline). Mounts bear ahead. Enclosed volume 176,949 units3,
recorded in ship_scale.py; 0.59 m/u stays an owner override (the rule would
solve 0.65).

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
outline. Scale: 0.59 m/u (91 m) is now an owner override (ship_scale.py): the open
wings are not enclosed volume, so the enclosed volume fell to 164,819 units3
and the rule would solve about 0.66 (101 m), but a reshape never resizes a
hull.

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

Owner review round 14 (2026-10-20)
"Command deck: too low poly. Facetted edges, lower deck windows read as weird
rectangle lights". The command deck only; the hull detail stays as round 13
left it. The dome's plan is a smooth closed curve through its round-3 outline,
with a rounded shoulder (mt_hull.dome_plan, dome_profile); its visor rake
starts further forward so the glazing has a wall to sit in. The dome, plinth,
brow, sill, masts, dishes, hatches, rails and beacons take rounded
multi-segment chamfers with hardened and weighted normals and smooth shading
below 30 deg (mt_kit.smooth), and their round parts 24 to 48 sides. The
glazing band is let 0.3 m into the dome's wall along its curve, with dark
glass at the back, a mullion every metre, a sill and a rounded visor brow
overhanging it (mt_detail.bridge_glazing). The plinth's crew-deck windows are
six a side, 1.5 x 0.6 m, each recessed into the wall with a frame, a centre
mullion and a hood (mt_detail.crew_windows). The glass is dark and glossy with
only a faint warm interior glow (no lit panels).
Also from the owner: "Strange low poly radar dish and light pole as well", and
across the board, "we're getting the rough shape right, but need to add
detail". mt_fittings.py builds deck fittings at finished fidelity: the bridge
search radar is a 3.2 m parabolic reflector (64 segments, rolled rim lip,
eight ribs behind it, a feed horn at the focus on three struts) on trunnions
in a yoke, on a turntable and a pedestal with a bolted flange; the comms
dishes are 1.6 m reflectors on the same mount. The aft mast and a 3 m forward
light pole are tapered tubes on bolted, gusseted flanges with a collar and a
beacon housing (base plate, lens, four guard bars, cap); the whips taper from
insulator feet. Hull-wide, every cylinder, drive can, bell, rod, pipe and ram
now has 24 to 48 sides with smooth shading below 30 deg, every bevelled box a
two-segment rounded edge with hardened normals, and the sensor domes and
running lights are smooth spheres. The lofted hull sections stay faceted on
purpose (round 9).

Owner review round 15 (2026-10-20)
"You had complaints already. Also the wing tips near the turrets have weird
hull still." Round 14's own flags are fixed: the glass is a near-black
absorbing tint with a low specular level and only a trace of interior glow, so
it reads dark under the grey studio world. The red armour is laid in plates
(mt_kit.armour_plates): planes across each part split its faces where a plate
break belongs (the core at X 34, 8, -3, -14, -26; the glacis bands at Y 33, 43
and 53; the wing armour at Y 30 and 46; the strakes at X 55 and 70; the pods
at X 11.3 and 22.5; the plinth at X -8 and -17), then every armour face is
inset and lifted 0.3, leaving a dark V-groove either side of every crease and
break and an edge thickness on every plate. The decks fittings stand on stay
whole. Round 13's seam lines on the armour are now raised weld beads in those
breaks. The gun nacelles are rebuilt to the sprite's plan: a blunt chamfered
nose at X 37 and the outboard edge at Y 77.5, where round 11 ran a point out
to X 42 beyond the sprite with a separate collar and nose glacis standing
below the deck. Each is one lofted body (mt_hull.pod_section): a flat turret
deck, a raised armour coaming along its outboard edge, a vertical outboard
wall, a nose rake and chin, red armour on the nose and outboard side and the
dark structure inboard where it meets the wing framework. Raw IoU rose to
0.897 with it.

Owner direction after round 15 (2026-10-20)
"Manticore hull/plating gaps seem weird. It needs to take more queues from the
onslaught there for the armored sections of hull facing combat." Round 15's
plates were every loft facet inset with a wide dark groove, which drew an
outline round each facet. mt_kit.armour_plates now lays the combat armour as
the Onslaught's belts are laid (onslaught-geometry/os_armour.py, its pass 6
review): plate breaks at a regular pitch across each course (the glacis bands
every 5 units of Y, the wing armour every 6, the beak strakes and gun pods
every 6 to 8 of X), faces grouped into one plate across creases gentler than
35 deg so a plate is a thick slab bent over the form, each plate lifted 0.45
with a chamfered border, narrow 0.08 gunmetal seams between plates, and a row
of bolt heads just inside every plate edge. Dark bolted straps cross the glacis
bands, the wing armour and the strakes over their seams. The core and the
bridge plinth are hull rather than combat armour and carry only fine plate
lines (mt_kit.hull_plating); round 13's welds stay on the core's shoulders.
