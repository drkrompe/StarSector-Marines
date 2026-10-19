TARSUS (FREIGHTER) - CINEMATIC GEOMETRY (PASSES 1 AND 4)

Purpose
An editable Blender model of the vanilla Tarsus (hullId tarsus), the convoy
freighter the pirates take as their prize in the Asharu battle cinematic
(asharu-battle/asharu-battle-shot.md): her drives are shot out, Kites (P) board
her at two flank collars, and she is carried off clamped under the Colossus
Mk.III. Close in S7 (about 650 m, from behind her) and S10 (about 700 m,
behind and above); mid-distance in S1, S4 and S6. It follows the
ship-model-pipeline skill and shares no code with any other hull: ts_kit.py
holds copied helpers.

Hull identity (read-only install, starsector-core)
data/hulls/tarsus.ship (hullId tarsus; file name and hullId agree), sprite
graphics/ships/tarsus/tarsus_base.png (106 x 204, centre 53,80; bow up; no
lights layer). ship_data.csv row "tarsus": Freighter, Low Tech, DESTROYER,
crew 30-80, cargo 350, fuel 80, mass 400, OMNI shield, system burndrive,
built-in civgrade. The description: "perhaps the most flexible and durable
freighters in the Sector", built by the Domain "as hazard duty ships".

Slots (checked against the install on every build)
WS 001 (-41,37) 135, WS 002 (-41,-37) 225, WS 003 (101,21) 45, WS 004
(101,-20) -45: TURRET SMALL BALLISTIC, arc 250 -> 'Turret 1..4 WS 00n ...',
on the aft and bow drums, resting at their slot angles (pivot and muzzle
empties).
Engines (angle 180, LOW_TECH): mains 1 (-78,19) and 6 (-78,-18), width 20;
auxiliaries 2 (-76,26) w6, 3 (-76,11) w8, 4 (-76,-11) w8, 5 (-76,-25) w6.
The auxiliaries' widths overlap the mains' in plan, so they hang in a row in
a drive skirt beneath the mains. 'Engine <i> drive can / aft-facing bell /
mouth lip / dark throat / exhaust anchor'.

Model
+X bow, +Y port, +Z dorsal, 1 sprite px = 1 unit about the .ship centre. The
sprite's own centreline is Y +1 (ts_hull.YC).
  ts_hull: Hull port|starboard arm (the twin forward cargo arms), Hull
    port|starboard bow drum, Hull central body, Hull aft body, Hull
    port|starboard aft drum.
  ts_bridge: Bridge command module, standing forward between the arms.
  ts_engines: Hull drive block, Hull drive skirt, Engine 1-6 (the whole
    drive section, so the boarded state works in one module).
  ts_fittings: Tank port fore|aft (the sprite's white capsules), Tank
    starboard cylinder, Pipe blue transfer run, Clamp receiver dorsal ...
    (four grapple receivers for the Colossus Mk.III) and Kite collar flank ...
    (two boarding collars), with their anchors.
  ts_mounts: Turret 1-4.
Heights are proposed art: arms -9..9, central body -12..10, aft body
-11..10.5, drums to 12, drive block -10..9 with the skirt to -15.5.

Pass 1 (blockout): silhouette IoU 0.957 against tarsus_base.png.

Pass 4 (scale): 0.51 m per unit from ship_scale.py option C (solves 0.507,
  range 0.492-0.522): 96 x 52 x 19 m. Crew room sizes it (80 x 300 m3 of
  24,820 m3 of contents). The enclosed volume (ship_scale.SOLIDS['tarsus'],
  279,286 units3) is the arms, body, aft body, four drums, drive block and
  command module; the deck tanks and drive cans are excluded, as the
  Colossus excludes its pod tanks. The battle plan's 106 m was a plan-area
  estimate; she measures 96 m beside the Colossus's 108 m (published by
  override; 94 m solved). Holding 106 m would take an override to 0.56.
  Fittings: Kite collar hatch 2.6 m across, grapple receiver plate 2.6 m.
  --figure stands a 1.8 m mannequin on the deck by the starboard Kite collar.

Docking (published in geometry.json 'docking', in units and metres)
  Clamp receiver 1-4 anchor: dorsal, +X out of the hull; two on the arms at
  (70, +-26 rel), two on the aft body at (-30, +-14 rel), clear of the port
  tanks and the drums. Kite collar port|starboard anchor: on the central
  body's walls, starboard at X 0, port at X -12, +X outward.

Build (from this folder)
  sh ../../blender.sh --background --factory-startup --python build_tarsus.py -- [--views ...] [--samples N] [--cpu]
  python compose_review.py
The driver writes the renders (ignored), tarsus-geometry.blend and
geometry.json; compose_review.py writes top-overlay.png, the IoU into
geometry.json and tarsus-geometry.reference.png. Close-ups: --views details
(bow, bridge, tanks, turret, engine, stern, underside), plus drum-detail,
clamp-detail and collar-detail. --cpu renders on the CPU: the shared GPU is
often saturated by other sessions.
