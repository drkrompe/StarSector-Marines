MUDSKIPPER - GEOMETRY BLOCKOUT (V1)

Purpose
An initial Blender blockout of the vanilla Mudskipper, for cinematics the way
second-wind-geometry/ serves the Second Wind. It fixes the silhouette, the
sections, the mass and the slots. There is no fine detail, damage or
texturing yet. This is source art only. It changes no runtime art or gameplay.
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
X 44-r..45-r and Y 31-c..32-c. No metre scale is claimed. XY comes from the
game; every Z (deck heights, drive axes, thicknesses) is art interpretation.
Model extent: X -36.5..42.5, Y -29.6..30.6, Z -8.5..13.

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
16 px/unit and measures alpha IoU (threshold 128): 0.935. In units^2, 145
are model-only (mostly the jet housings and bow fenders) and 122 are
sprite-only (the sprite's soft pod corners and part-alpha flank pixels). The
figure is also stored under "silhouette" in geometry.json.

Files
build_reference.py   scene construction, cameras, renders, .blend, geometry.json
ms_kit.py            materials and primitives (copied from the Second Wind
                     kit; this package does not import second-wind-geometry)
ms_hull.py           hull blocks, bow facets, sponsons, cockpit, hood, doors
ms_engines.py        main drive, pod drives, maneuvering jets, nozzles
ms_turrets.py        WS 003 turret and the WS 002 hidden marker
compose_review.py    sprite overlay, IoU and review sheet (Pillow)
mudskipper-geometry.blend           saved Blender 5.2 scene with cameras
geometry.json                       hull identity, slots, engines, dimensions,
                                    cameras, decks, silhouette IoU
renders/*.png                       1920x1080 renders: top (transparent),
                                    top-composited, top-sprite-overlay,
                                    port-side, bow, port-quarter, stern-quarter
mudskipper-geometry.reference.png   1920x1080 review sheet of four views

Stable names
Later damage, surfacing and VFX layers key off object names. Use these:
'Main hull - level datum Z0', 'Engine {n} ...' (n = engine slot index above),
'Turret 1 WS 003 ...', 'Mount WS 002 ...', and the '{Port|Starboard} ...'
prefixed parts. Repeated small fittings (stripes, fenders, hatch panels)
carry Blender's .001 suffixes and are not keys.

Rebuild from this folder
"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python build_reference.py
  options after "--": --views top,bow  --samples N  --no-save
python compose_review.py
About 40 s at 32 samples. Do not run the build against a user's unsaved live
Blender session.

Open for the owner
Heights, deck steps and the cockpit and hood forms are interpretation. The
Mk.II skin (its large hardpoint, removed turret and different sprite) is a
separate build if a pirate Mudskipper is wanted. This package carries no
damage states or surfacing yet; they would follow second-wind-pipeline.md.
