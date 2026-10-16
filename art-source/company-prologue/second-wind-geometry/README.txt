SECOND WIND - CURRENT GEOMETRY REFERENCE (V3 DESIGN REVISION)

This is the single accepted clean-hull geometry source. The old V2 model and
build scripts were removed; reference-history/v2/ retains only their reference
images. Historical prompt path mappings are in ../cleanup-record.json.
The surfaced damaged cinematic master is a separate derivative under
../second-wind-cinematic/second-wind-full-surface/.

Purpose
A gritty industrial reading of the vanilla Cerberus sprite: planform, engine
pods, spine and the port-side fitting are measured off it. This is an editable blockout, not
a production game mesh or an assertion of canonical Cerberus interior geometry.
All four views render the SAME mesh objects; only the active camera changes.

Coordinates and decisions
+X = bow; +Y = port; +Z = dorsal. Hull and bay deck datum are level.
The sprite's one asymmetric fitting, on the port bow flank (about x 28..48,
y 24..34), is read as a boarding/cargo grapple: a grey machinery sponson with
winch and work lights hung on the flank, and a two-segment arm folded flat
along the citadel edge with its grapple head parked by the nose block; a small
davit stands over the port bay. Its function is art interpretation; the sprite
supplies only the shape. There is no starboard counterpart.

Four aft-facing engine centers retain vanilla warhound.ship (hullId cerberus)
XY locations: (-41,31), (-39,24), (-39,-24), (-41,-31).
All four are at proposed Z=0. Outer pair is larger and slightly farther aft.
Vanilla engine-slot widths 12:8 supply the 3:2 relative size of the nozzle
mouths (radii 3.9 and 2.6). Effect widths are not
treated as literal solid nozzle diameters. No metre scale is claimed.
Four weapon mount XY locations also follow that vanilla hull specification.

V3 shell construction (detail passes, 2026-10-16)
The hull is assembled from flat-shaded blocks, not one smooth loft. Sections
are one keel chamfer, one vertical wall and one shoulder chamfer; a stack of
chamfers read as a rounded pebble in side view and was removed. The forebody
planform is measured off the sprite (widest +-31.5 over x 30..40, closing in
three large flats to a +-8 nose) and is split into separate armour sections
with dark seam gaps, with hard horizontal strata in side view: steel keel
plate, lower wall, steel belt plates, upper wall, an overhanging deck-edge
lip and the citadel deck plate. Flat armour planes keep the bow angled: a
glacis falling to a raked nose with a cream nose block, raked cheeks and a
raked chin.

Engine pods
Each aft mound is a whole drive unit bolted to the ship, not hull with
nozzles poking out of it. Its sections are faceted engine forms rather than
boxes: rounded-rectangle sections whose corners are cut into flat facets,
lofted so they taper between stations. An intake cap narrows to a raked
intake; an armoured cowl over a steel lower casing carries the turret
platform; proud barrel bands frame a finned heat-sink cylinder; a short
thrust housing ends in two twelve-sided engine cans with the bells sunk into
them. Every section is separated by a seam over a dark core. A low pylon
with coolant and fuel lines ties each pod to the centre body. Smooth and
inflated experiments looked organic and were rejected; facets stay flat and
edges crisp. The four engine centres and the bay thresholds retain their V2
coordinates.

Centre aft deck and stern notch
The deck between the pods is the engine-room roof, read off the sprite's
structural lines (those common to every paint scheme): separate armour
plates and vent grilles on a seamed skin, a two-leaf reactor lift-out hatch,
fore-aft rails carrying a parked hoist bridge over an open plant-access
well, and a bolted girder on the aft edge over the engine-room bulkhead and
door. The stern notch holds a docking/tow cradle: a cross beam with arms
ending in clamp jaws, flanked by armoured prongs carrying winch bays and
bolted to the pods' aft bands.

Command deck
Following the sprite's central capsule, the command deck stands on the
spine amidships rather than in a tower: a low segmented armoured housing
from the aft turret pair to the forward pair, with a dark core showing
between its armour segments, a gunmetal forward trunk, and a low command
section with smoked viewport slits behind heavy mullions under a visor brow,
facing forward over the forward turrets. Raised octagonal access trunks
stand at the sprite's four deck rings; exposed service trunks run along the
housing flanks.

Weapons
Four raised turrets at the vanilla mount XY: three small single-gun houses of
one family and one medium twin. Each is a faceted barbette seated on the deck,
a gun house with a sloped glacis, a mantlet, stepped barrels with muzzle
brakes, and a rear bustle with ammunition feed and vents.

Conforming armor plates, reinforcement bands, exposed protected conduits,
vents, bay buttresses and flank maintenance housings supply structural depth
for the surface interpretation. Detailed plating and hardware in the ImageGen
finish remain conceptual, not a consistent UV map.

Port bay is open with an empty cradle. Starboard bay is sealed. Their location,
depth and one-bay-per-flank arrangement are proposed art, not a procedural deck
layout change. The initial two Hermes remain wrecked. No boat is granted or
repaired, and no runtime art or gameplay is changed by this source reference.

Files
second-wind-geometry.blend: saved editable Blender 5.2 scene, cameras and objects.
build_reference.py: deterministic scene and camera construction; runs in Blender.
  sw_hull.py, sw_aft_deck.py, sw_engines.py, sw_bridge.py, sw_turrets.py,
  sw_port_module.py: one module per area, so
  separate passes do not edit the same file; sw_kit.py holds shared materials
  and primitives; sw_port_module.py builds the port grapple.
geometry.json: coordinates and provenance for the proposed layout.
port-quarter.png, top.png, port-side.png, stern.png: direct Blender guide renders.
compose_guide.py: Pillow composition of those untouched renders into a guide.
second-wind-geometry.reference.png: 1920x1080 guide sent to ImageGen.
../second-wind-design-v3.reference.png: generated surface interpretation for review,
  made from the pre-detail blockout kept in reference-history/v3-blockout/.
../second-wind-design-v3.prompt.txt: exact ImageGen request and review notes.

Authority
The .blend and script define geometry. The generated surface interpretation
does not override geometry if it introduces small detail or projection drift.
Inspect the model views for placement and the generated sheet for finish.
The model is deliberately simplified; detailed pressure spaces, structural
engineering and a production-ready surface mesh are not represented.

Rebuild from this folder
blender --background --factory-startup --python build_reference.py
For review renders that leave the committed guide alone, append
  -- --out <dir> --no-save --views port-quarter,bridge-detail
  --cam NAME:PX,PY,PZ:TX,TY,TZ:ORTHOSCALE
python compose_guide.py
The second command uses Pillow. Blender is not a runtime mod dependency.
Do not use scene generation against a user's unsaved live Blender session.

Shipping
Everything in this directory is source-only. Reference sheets use .reference.png,
not .master.png, and are excluded from the prologue packer's master inputs.
Keep the current campaign scenes until the user finishes reference review.
