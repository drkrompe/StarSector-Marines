MUDSKIPPER - CINEMATIC GEOMETRY (PASS 2: PLAUSIBLE DETAILING)

Purpose
A scripted Blender model of the vanilla Mudskipper, for cinematics the way
second-wind-geometry/ serves the Second Wind. It follows the passes in the
ship-model-pipeline skill (.claude/skills/ship-model-pipeline/SKILL.md):
  pass 1 blockout             accepted; snapshot in reference-history/v1-blockout/
  pass 2 plausible detailing  built here
  pass 3 owner review         round 1 below; awaiting review
  pass 4 real-world scale     0.85 m per unit (see Scale below)
No damage states or texturing yet. This is source art only. It changes no
runtime art or gameplay.
All views render the SAME mesh objects; only the active camera changes.

Hull identity (read from the read-only game install, 0.98a-RC8)
hullId mudskipper, "Mudskipper", FRIGATE, LOW_TECH, Civilian Transport.
  Source: data/hulls/mudskipper.ship and ship_data.csv.
Sprite graphics/ships/mudskipper/mudskipper.png: 64x84, with the .ship
  centre at 32,39 from the bottom-left.
Ship system: maneuveringjets. Built-in hullmod: civgrade. No built-in weapons.
Cargo 70, fuel 40, crew 5-100. The wiki's codex describes a rugged backwater
  ferry with no shield, generous bay doors and a rear-facing turret against
  missiles. It was first sold as the Pegasus class.
Variant hulls: there is no (A), (D) or other hull variant. The only relative
  is the skin mudskipper2 ("Mudskipper Mk.II", pirate, mudskipper2.png). It
  removes WS 003, turns WS 002 into a LARGE BALLISTIC HARDPOINT and adds
  ill_advised and comp_storage. The Mk.II is not modelled here.
Variants: mudskipper_Standard (a light dual machine gun in WS 003),
  mudskipper2_CS, mudskipper2_Hellbore.

Coordinates
+X = bow; +Y = port; +Z = dorsal. 1 unit = 1 sprite pixel about the .ship
centre, so sprite pixel (c, r), counted from the top-left, covers hull
X 44-r..45-r and Y 31-c..32-c. XY comes from the game; every Z (deck
heights, drive axes, thicknesses) is art interpretation. Model extent
79.1 x 60.1 units in plan; keel z -8.5 to hull top about 13, mast tip 21.

Scale (pass 4)
One real-world scale: 0.85 m per unit, published as metres_per_unit in
geometry.json and METRES_PER_UNIT in build_reference.py. Every metric value
(VFX sizes and speeds, lamp falloff, cameras, depth of field, flight speeds,
texel density) derives from it; nothing hard-codes metres. She is about 67 m
long, 51 m across the sponsons and 15 m from keel to deck.
Derived from what she carries, by the Second Wind's method (scale_check.py):
  - The mod's deck program (DeckSizing.programFor; printed by
    .claude/skills/ship-model-pipeline/DeckProgramProbe.java) reads her
    designation "Civilian Transport" as a LINER frigate. With crew 5-100 and
    cargo 70 she owes 3,329 m2 of rooms. That includes three 16 x 10 m boat
    bays, for the 95 people she carries beyond her working crew. x1.40
    circulation (DeckSizing.CIRCULATION) x 3.2 m deck height = 14,914 m3.
  - The main pressure hull measures 44,608 cubic units. At 55% usable,
    (14,914 / (0.55 x 44,608))^(1/3) = 0.847 m per unit.
  - Plausible range 0.76 to 0.91. The low end counts the sponsons, cabin hood
    and cockpit as pressure space too, at 65% usable (cramped). The high end
    is the main hull alone at 45% usable (roomy). That is 60 to 72 m long.
Cross-checks at 0.85: the cargo bay door opening (16.8 x 8.4 units) is
14.3 x 7.1 m, which fits the program's 16 x 10 m boat bay. The crew hatch is
1.15 x 2.3 m, and a 1.8 m person is 2.1 units tall. Keel to deck is about
14.9 m: four 3.2 m decks plus structure.
The battle map's landed-ship convention (landed-ships-true-scale.md) sizes
hulls for tactical readability and is a different number.

Weapon slots (exactly mudskipper.ship; the model invents no others)
  id      mount   size   type        location  angle  arc  model
  WS 003  TURRET  SMALL  BALLISTIC   (-9, 0)   180    120  Turret 1, rests trained aft
  WS 002  HIDDEN  LARGE  DECORATIVE  (17, 0)   0      5    empty marker only, no gun
Turret 1 is a small gun house with twin light machine-gun barrels, seated
by ray casts on the aft hull deck (seat z 9.5). WS 002 is the hidden slot
the Mk.II skin turns into its big gun. On the base hull it is only an
Empty named 'Mount WS 002 hidden large decorative marker', with the slot
data as custom properties.

Engine slots (index order of mudskipper.ship engineSlots)
  #  location   angle  width  object prefix  unit
  1  (-36,  0)  180    24     Engine 1       main drive: banded centre drum
  2  (-36, 25)  180    8      Engine 2       port pod drive
  3  (-36,-23)  180    8      Engine 3       starboard pod drive
  4  ( 19, 29)  150    8      Engine 4       port maneuvering jet
  5  ( 19,-28)  210    8      Engine 5       starboard maneuvering jet
All are LOW_TECH. Each nozzle's mouth sits exactly on its slot (z 0 for the
drives, z 2 for the jets) and points along the slot angle. The bell mouth
radius is 0.31 x width, the Second Wind ratio. The jets use 0.2 x width
because the sprite shows them as about 3 px bumps. Each engine is a whole
unit: the main drive is a 12-sided drum with three barrel bands, and the pods
are separate casings bolted behind the aft hull. A dark drive-bay core shows
through the seams between the units and stops short of the two stern
notches, so the notches stay open.

Pass 2: plausible detailing
Detail is inferred from the sprite at 4-12x and from lore (a rugged low-tech
civilian ferry, LINER-hinted, "generous bay doors", a rear point-defence
turret). One Opus subagent per module did the work, since Sol 6.1 (CLAUDE.md)
is not offered in this harness; each wrote only its own module and the
integration was checked so that no agent changed another's file.
  ms_hull     segmented flank armour on dark seams, deck-edge lips, keel
              plating and strakes; the cargo bay doors as real doors (sliding
              leaves on an overhead track, jambs, hazard sill, work lights)
              with a crew hatch beside them; sponson armour modules, belts,
              passenger ports, vents and trim; flank locker doors; four
              landing pads, drain grilles, chin armour, running lights.
  ms_bridge   a segmented armoured command cab on an armoured base: a raked
              viewport band behind heavy mullions under a visor brow, side
              windows, a mast with yard and whips, comm dish, sensor blister,
              nav and anticollision lamps, the sprite's two yellow lamps, a
              docking collar and tow-eye well on the nose; bolted docking
              bumpers with lamps for the bow fenders.
  ms_deck     the cabin hood in three modules with roof plates, an arched
              studded brow, a dorsal airlock, grab rails, beacon and antenna;
              passenger skylights and outer-band vents; the cargo hatch as a
              coaming with 2x3 slatted grille leaves; the tan turret-well
              coaming; ported two-leaf aft hatches; fore-deck vent grilles and
              mooring winches beside the cockpit.
  ms_engines  the main drive as a flanged, banded drum with gimbal waist,
              actuators, coolant lines and a sunk bell; each pod a faceted
              lofted drive unit with dark seams, tan thrust collar and nozzle
              can, pump/valve housing (the sprite's red box), access hatch,
              radiator bay and flank panels; feed manifold and cross pipes in
              the valley; faceted RCS housings with puff nozzles for the jets.
  ms_turrets  Turret 1 as a small point-defence mount: faceted barbette and
              bolted collar, a gun house with glacis, trunnion mantlet, twin
              jacketed light MGs with flash hiders, ammunition cans and feed
              chutes, tracking director and search dish. A BVH overlap test
              found no clash at rest or at either end of its 120 degree arc.
Close-ups (bow, bridge, turret, waist, stern, engine) before and after are
side by side in closeups-before-after.reference.png. Top-view IoU rose from
0.935 to 0.943 (the engine actuators fill the sprite's dark stern columns).

Pass 3: owner review
Round 1. The owner: the hull joining the centre hull and engine block "needs
a bit more work - the chamfered edge catches the eye too quick". Across the
top, the waist deck ran into the aft hull's forward shoulder chamfer as one
long V-groove. On each flank, the aft hull stepped out past the waist in a
clean full-height corner bevel. ms_hull.build_junction breaks both up with
structure that changes bay by bay and side to side ('Junction ...' and
'{Port|Starboard} junction ...' objects).
Across the top, port to starboard:
  - a louvred vent bank standing in the groove;
  - two feed conduits crossing from the waist deck onto the aft deck, on
    saddles, into a junction box;
  - a low collar segment, then the stepped collar beam with its bolted cap
    on the centreline;
  - two unequal bolted service plates on a recessed seam;
  - three gusset buttresses at uneven spacing.
On each flank:
  - a frame band over the crease between the forward face and the bevel;
  - a stepped profile band that runs up the forward face, over the shoulder
    and onto the aft deck, at a different station on each side;
  - knee gussets in the inside corner.
The bevels differ by side. Port carries a vent bank and a lifting hardpoint
under the existing running light. Starboard carries a service plate, plus a
clamped conduit running down into a junction box.
Before and after (port quarter, port side and both junction close-ups):
junction-before-after.reference.png. The new close-up cameras are
junction-detail and junction-starboard. Top-view IoU is 0.9426 (0.9434
before). The sprite steps in at x -1, so anything standing forward of the
aft hull's face counts against it, and the knees and bands were trimmed to
keep that loss under 0.001.
Pixel diff against pass 2: only the views that can see the junction changed.

Construction (lessons carried over from the Second Wind)
Flat-shaded plate prisms, each section one keel chamfer, one vertical wall
and one shoulder chamfer, with a one-segment crisp edge chamfer. Hull blocks
are joined with MANIFOLD booleans. From aft to bow:
  aft hull       x -16..-1, +-28.5, deck 9.5: turret, two dorsal hatch panels,
                 tan flank lockers
  cargo waist    x -2..19.5, +-22.5, deck 8.5: a two-leaf cargo bay door on
                 each flank (the "generous bay doors"), dorsal cargo hatch
  forebody core  waist width carried forward to x 39.5, deck 9, with a bow
                 glacis and a raked chin
  sponsons       grey-green outer bands (y 21.5..28.5 over x 19..39), stepped
                 below the deck as a separate stratum
  cabin hood     raised centreline housing over x 6..25, top 13, raked brow
  bow cockpit    dome block x 26..42.5, +-8, glazed glacis, two lamps
  bow fenders    the sprite's crenellated front edge, tan blocks at x 39..42
Planform, stripes, hatches, the turret ring and the drum are measured off
the sprite.

Silhouette check
compose_review.py lays the sprite over the transparent top render at
16 px/unit and measures alpha IoU (threshold 128): 0.9426 after pass 3
round 1 (0.9434 after pass 2, 0.935 for the blockout). In units^2, 165 are model-only (mostly the jet
housings, bow fenders and door fittings) and 70 are sprite-only (the
sprite's soft pod corners and part-alpha flank pixels). The figure is also
stored under "silhouette" in geometry.json.

Files
build_reference.py   scene construction, cameras, renders, .blend, geometry.json
ms_kit.py            materials and primitives (copied from the Second Wind
                     kit; this package does not import second-wind-geometry)
ms_hull.py           hull blocks, bow facets, sponsons, flanks, doors, underside
ms_bridge.py         command cab, viewport, mast and sensors, bow fenders
ms_deck.py           cabin hood, skylights, stripes, hatches, turret-well coaming
ms_engines.py        main drive, pod drives, maneuvering jets, nozzles
ms_turrets.py        WS 003 turret and the WS 002 hidden marker
compose_review.py    sprite overlay, IoU and review sheet (Pillow)
scale_check.py       pass 4: measures hull volume and derives metres_per_unit
mudskipper-geometry.blend           saved Blender 5.2 scene with cameras
geometry.json                       hull identity, slots, engines, dimensions,
                                    cameras, decks, silhouette IoU
renders/*.png                       1920x1080 renders: top (transparent),
                                    top-composited, top-sprite-overlay,
                                    port-side, bow, port-quarter, stern-quarter,
                                    and the six *-detail close-ups
closeups-before-after.reference.png pass 2 close-ups against the blockout
junction-before-after.reference.png pass 3 junction, before and after
reference-history/v1-blockout/      the accepted blockout's renders
mudskipper-geometry.reference.png   1920x1080 review sheet of four views

Stable names
Later damage, surfacing and VFX layers key off object names. Use these:
'Main hull - level datum Z0', 'Engine {n} ...' (n = engine slot index above),
'Turret 1 WS 003 ...', 'Mount WS 002 ...', 'Bow cockpit block', 'Cockpit
forward glazing', 'Bridge ...', 'Hood ...', 'Dorsal cargo hatch ...', 'Aft
dorsal hatch ...', 'Fore deck ...', 'Keel ...', 'Ventral ...', and the
'{Port|Starboard} ...' prefixed parts (cargo bay door, crew hatch, sponson,
flank locker). Repeated small fittings (stripes, fenders, hatch panels)
carry Blender's .001 suffixes and are not keys.

Rebuild from this folder
"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python build_reference.py
  options after "--": --views top,bow  --samples N  --no-save  --out DIR
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE; close-ups render only when named
  (bow-detail, bridge-detail, turret-detail, waist-detail, stern-detail,
  engine-detail)
python compose_review.py
About 3 minutes for all eleven views at 32 samples. Do not run the build against a user's unsaved live
Blender session.

Open for the owner
Heights, deck steps and the cockpit and hood forms are interpretation. The
Mk.II skin (its large hardpoint, removed turret and different sprite) is a
separate build if a pirate Mudskipper is wanted. This package carries no
damage states or surfacing yet; they would follow second-wind-pipeline.md.
