SECOND WIND - CURRENT GEOMETRY REFERENCE (V3 DESIGN REVISION)

This is the single accepted clean-hull geometry source. The old V2 model and
build scripts were removed; reference-history/v2/ retains only their reference
images. Historical prompt path mappings are in ../cleanup-record.json.
The per-state cinematic exports are separate derivatives under
../second-wind-cinematic/second-wind-states/.

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
treated as literal solid nozzle diameters. Real-world scale is 0.65 m per
unit (metres_per_unit in geometry.json; see second-wind-pipeline.md, Scale).
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
brakes, and a rear bustle with ammunition feed and vents. These are exactly
warhound.ship's four weapon slots (WS 001-004: small ballistic turrets at
(-11,20), (-11,-20) and (41,11), medium at (36,-16)); the model invents no
other gun positions and the hull has no built-in weapons. Each turret rests
trained to its slot's angle: the aft pair broadside to port and starboard,
the forward pair 30 degrees outboard. Turret 3 stands on a taller barbette
so its resting line fires over the stowed port grapple arm.

Main drives and attitude thrusters (sw_engines.build_nozzles, sw_rcs)
Each of the four drives (Engine 1-2 port, 3-4 starboard) is a contoured bell
lined with six faceted film-cooling bands ("Engine {i} aft-facing bell", the
liner) inside a steel outer skin, a throat insert ("throat ring") with an
open bore, and the injector face at the bottom ("Engine {i} dark throat", the
part a running drive's glow goes on) with its pintle and injector ring. A
12-sided gimbal ring sits between the mouth and the can face on two
trunnions, worked by two actuator rams lying on the can flats; a dark
heat-shield band rings the exposed exterior behind the mouth lip. Sizes at
0.65 m/unit (outer / inner engine): mouth radius 2.54 / 1.69 m, throat 1.40
/ 0.93 m radius 1.77 / 1.18 m behind the mouth, injector face 2.03 / 1.35 m
behind it, gimbal ring 3.09 / 2.06 m outer radius, rams 2.9 / 2.1 m long.
Attitude control is eight RCS clusters of small bells (mouth radius 0.30 m,
blocks 1-1.75 m): bow-port, bow-stbd, pod-port, pod-stbd (the widest
station, |y| ~38), stern-port, stern-stbd, dorsal, ventral; 24 bells, axis
named for the exhaust direction (up, down, port, stbd, fore, aft). Bank to
starboard (C1) fires pod-port down with pod-stbd up; bow to starboard fires
bow-port port with stern-stbd stbd, bow to port bow-stbd stbd with
stern-port port; sw_rcs's docstring has the full table.
Effect anchors: one empty per drive mouth, "Engine {i} exhaust anchor"
(props engine, radius, radius_m), and per RCS bell, "RCS <cluster> <axis>
anchor" (props cluster, axis, radius_m, working); local +X is the exhaust
direction. geometry.json in every build lists engine_anchors (name,
position, position_m, exhaust_dir, engine, radius, radius_m, running) and
rcs_anchors (name, position, position_m, exhaust_dir, cluster, axis,
radius_m, working) for that state. The engine rows in sw_scars_pods set
running (derelict none; patched and refit 3-4; restored all) and the rcs-
rows in sw_scars_rcs set working (derelict none; some port clusters
stripped or blanked until restored). Names and cluster/axis words are keys
for the VFX kit: never rename them.

Condition states (sw_damage.py and sw_scars_*.py, --state S)
The committed guide above is always the clean ship. The campaign life is one
ladder of builds of the same model, derelict -> patched -> refit -> restored
(see ../second-wind-pipeline.md); clean is the reference. sw_damage.apply is
the last phase of build_reference.py, so surfacing can be laid on the clean
parts before scars cut them, and the clean renders stay pixel-identical.
Every damage site is one Scar row with a stable id, a kind and a status per
state: open -> patched -> replaced -> faded, or gone (drive pods: dead,
capped, jury, running). Rows are placed by ray casts against the clean ship
and every change to a base object is deferred to the end of the build, so
each id lands in the same place in all four states. states/scar-table.md
lists every id and its ladder. Rows live in one module per area, each owning
its rows and any kinds of its own: sw_scars_forebody (fb-), sw_scars_pods
(pod-), sw_scars_aft (aft-), sw_scars_spine (sp-), sw_scars_rcs (rcs-),
sw_scars_dorsal (ds-); sw_damage holds the
ladder, the schema, the generic kinds (basin, cluster, spall, gouge, plate,
buckle, strip, power) and the ship-power row.
Base objects are never renamed, replaced, deleted or re-materialled. Scars
Boolean-cut them (the opened faces take the cutter's scar material), bend
them with a shape key named 'Scar <id>', hide them (a stripped or replaced
part stays in the file, flagged scar_hidden), or override their look with an
object-level material slot (flagged scar_override; the mesh keeps its base
material). Everything added is named 'Scar <id> <part>' and uses scar and
repair materials only, so a base skin keyed by object name serves every
state. Damage never moves or removes the bays, the four turret slots, the
four engine centres, the grapple module or the silhouette; no breach into
occupied space, no fire, no new engines.
derelict: ~8 years tethered in the belt as an unpowered parts bin, port side
  and deck to the drift. Lipped basins and strike clusters on that windward
  face, shallow dents on the lee, scrapes and gouges from glancing contact,
  spalls, torn and unbolted deck plates, buckles, tether padeyes. The anchor
  rock that crushed Engine 1 (port outer) stove in the whole upper-outboard
  quarter of the port engine bay: the drum's aft band and the fin cage
  pressed flat over five fin rings (fins crushed together, the cage rail
  bent and torn), the housing corner knocked in with an L-shaped breach (one
  casing plate folded back, one gone) showing the bay's broken frame,
  manifold, severed feeds and sheared pump brackets. Engine 1 itself: can
  dented and torn open, mouth flattened about 40% and the bell cocked 5
  degrees toward the strike, top ram snapped, shroud collar torn. Five dorsal rips (ds-) where whole deck
  panels tore away along their seams: torn fastener rows, a panel folded back,
  the frame and stringers broken and pushed in at the strike, crushed and
  severed pipe and conduit. Salvagers took the pumps, coils, access panels,
  the two sound gun houses (turrets 2 and 4) and hatch seals and torch-cut two
  waist runs; they left the wrecked mounts: turret 1 jammed 24 degrees off its
  slot with a bent barrel, turret 3 holed through the roof, barrel snapped; the mast is sheared; the sealed starboard bay's jamb is bowed
  and its door creased. No power.
patched: the escape, only what the flight needed. Collar patch on the
  sponson, welded doublers on the bow cheek and spine housing, pressure
  patches over strikes on occupied space, borrowed seals and one trunk dogged
  shut, turrets 2 and 4 back on adapter rings (turret 2's house in another
  owner's primer) while 1 and 3 fly wrecked and dead, the dorsal rips left
  open except the forward one over the command deck (pressure plate welded
  over it, the torn ledge showing). Re-powering the bus left four severed
  conduits live, so they spark intermittently (the rips and turret 3's torn
  feed), the jamb shored with cargo rail and the starboard bay door welded,
  clamp sleeves on the cut runs, jury whip and nav sensor on the mast stub.
  Starboard pod on a jury-rigged salvaged coil and feed pump, its power run
  clipped across the aft deck to the engine-room hatch; port pod cold with
  its feed lines blind-flanged, nozzles open.
refit: the limited berthing/command conversion. More is done properly (shop
  primer, fresh seams): the collar, the bow-cheek doubler, the spine and
  command-deck patches and cluster, the dogged trunk, the trunk seals, the
  mast, one waist run, the torn, unbolted and buckled forebody deck plates and
  the three deck rips over the new berthing (both waists and the forward one).
  It is still beaten up: heavy craters, dents, spalls and gouges, the
  shoulder peels, the dead port pod with its stove-in engine bay and crushed
  Engine 1, the wrecked turrets
  1 and 3 and the jury coil remain, and nothing is repainted. Turret 3's live
  feed is booted (no sparks).
  Paint: patched and refit keep the derelict's sun-faded paint exactly
  (../second-wind-surface/wear.json); only their own plates, welds and primer
  are new. The restoration is the first repaint.
restored: structurally whole, both pods running (Engine 1 a new can and
  nozzle), stripped parts reinstalled and turrets 1 and 3 given new houses.
  Replacement plates repainted near (never exactly) the hull colour with
  seams visible; never-plated scars are painted-over shallow dents.
Continuity scars from ../second-wind-skins/README.txt, remapped onto this
geometry (the old teal side pod no longer exists): the impact cluster on the
forward port flank sits on the grapple sponson and the port bow cheek above
it; the scarred port aft shoulder is the dead port pod's cowl; the scorched
waist service covers are the waist service lines and port trunks.
Effect anchors for damage: 'Scar <id> spark anchor' (and spark-2, ...)
empties sit at severed live conduit, local +X along the spray, with sparking
(true only in patched) and radius_m; geometry.json lists them as
spark_anchors and the VFX kit builds an intermittent spark emitter for each
live one. 'Engine 1 exhaust anchor' stays put, but while the can is crushed it
carries mouth_deformed with the measured oval (mouth_minor_m, mouth_major_m,
mouth_minor_dir), the cocked axis (mouth_axis_dir, mouth_tilt_deg) and the
centre offset, exported in engine_anchors. Turret collars carry jammed and
jam_deg (turret 1: 24 degrees, already baked into the mesh).
Each state writes review renders, a .blend and geometry.json (with a damage
record) to states/<state>/; compose_guide.py --state <state> writes the
1920x1080 review sheet states/<state>/second-wind-<state>.reference.png.

Conforming armor plates, reinforcement bands, exposed protected conduits,
vents, bay buttresses and flank maintenance housings supply structural depth
for the surface interpretation. Detailed plating and hardware in the ImageGen
finish remain conceptual, not a consistent UV map.

Port bay is open with an empty cradle. Starboard bay is sealed. The port bay
is deepened inboard (back wall at y 12.4, 7.9 m to the mouth) so the Hermes
stands in it behind a door on the starboard bay's line (y 22.6) with 0.45 m
each side; the starboard bay keeps its wall at y 15.55. Their location,
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
sw_bridge_interior.py: the bridge room (owner brief 2026-10-18), cut out of the command
  deck by a hidden cutter as live 'Bridge interior cavity' Booleans: main floor 16.6,
  ceiling 20.3, a raised flight dais forward (17.55) with the helm seat and low
  consoles, the port comms bank (Castle's), the starboard systems bank, a plot table, the
  dais rail and the aft hatch; dress(state) adds the jury-rigged details of 'patched' (and
  'derelict', 'refit'). figures() builds the seated pilot and standing Castle mannequins for
  review renders and shots (Castle is the MakeHuman figure from ../castle-figure/ when its
  .blend is built); they are never saved. Outer faces keep their positions and
  normals under the cut. Close-up detail (2026-10-19): console faces (bezels, switch rows,
  knobs, keypads, label plates, lamps), casing seams and cable glands, a real helm seat
  with harness, hatch hinges/dogs/gasket, caged lamps, frames, seams, vents, trims and
  grab rails; patched cables hang and sag with tape, ties and clips. sw_damage casts
  through the room, so a part near the dais plating can move a skirt scar outside: diff
  the exterior of every state after adding detail.
bridge_interior_review.py: Workbench review of the room at 1920x1080 (the C2 stations,
  the room from helm and hatch, a cutaway, a plan, close-ups of the comms console, helm
  seat, aft hatch and patched cable runs) plus two exterior views for diffs:
  blender --background --factory-startup <blend> --python bridge_interior_review.py --
    --out <dir> [--figures] [--views a,b] [--exterior-only] [--no-shadows]
sw_damage.py, sw_scars_*.py: condition states (see Condition states).
states/<state>/ (derelict, patched, refit, restored): review renders, .blend,
  geometry.json and review sheet per state; never the guide sent to ImageGen.
states/scar-table.md: every scar id and its status in each state.
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
  -- --out <dir> --no-save --views port-quarter,bridge-detail --res 1920x1080
  --cam NAME:PX,PY,PZ:TX,TY,TZ:ORTHOSCALE
python compose_guide.py
Condition states (about 30 s each), then their review sheets:
blender --background --factory-startup --python build_reference.py -- --state derelict
blender --background --factory-startup --python build_reference.py -- --state patched
blender --background --factory-startup --python build_reference.py -- --state refit
blender --background --factory-startup --python build_reference.py -- --state restored
python compose_guide.py --state derelict
python compose_guide.py --state patched
python compose_guide.py --state refit
python compose_guide.py --state restored
The compose commands use Pillow. Blender is not a runtime mod dependency.
Do not use scene generation against a user's unsaved live Blender session.

Shipping
Everything in this directory is source-only. Reference sheets use .reference.png,
not .master.png, and are excluded from the prologue packer's master inputs.
Keep the current campaign scenes until the user finishes reference review.
