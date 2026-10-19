TARSUS (FREIGHTER) - CINEMATIC GEOMETRY (PASSES 1, 2, 4 AND 5; PASS 3 ROUND 1)

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
Heights are proposed art, and each module stands at its own (pass 3 round 1):
arms are faceted lofts, pod deck 8.5 and inboard strip 5, keel -8.5, raked
down into the bow drums; central body -13..10 over a ventral keel to -20;
aft body -13..10.5 with a raised castle to 16.5 between the aft drum towers
(-14..18.5); drive block -10..6.5 with the cans standing proud.
  ts_arms, ts_body: pass 2 detail on the arms and bow drums, and on the
    body, ventral keel, aft castle and drum towers.

Pass 1 (blockout): silhouette IoU 0.957 against tarsus_base.png.

Pass 2 (plausible detail, IoU 0.958): four module agents, one per file.
  ts_arms: facet plating, crease strakes, a cargo door folded across the pod
  bulge, shoulder hatch, signal panel, masts and drum castings with ribs.
  ts_body: forward lit bay, spine machinery trunk, flank cargo doors and
  hatches, strut frame, the railed aft castle with a stair, ribbed drum
  towers, ventral keel hatches and strakes.
  ts_engines: gimballed main bells with hoops and ribs, thrust collars and
  yokes, turbopumps, manifolds, radiator banks, auxiliary housings, a stern
  service housing with ladder and gantry, RCS quads, stern lamps (red port,
  green starboard). Every part is named for the boarded state to scar.
  ts_bridge, ts_fittings, ts_mounts: mullioned windscreen under a brow,
  ports, mast and dish; strapped and saddled tanks with manways and valve
  manifolds; grapple receivers with socket and lock pins; Kite collars with
  dogged hatches; naval twin turrets (clash-checked at rest).
  check_clashes.py is the cross-module overlap sweep.

Pass 3 round 1 (owner: "too low poly", "the arms with angles instead of
  rectangle volumes", "whole ship is the same thickness on the same plane"):
  faceted arm lofts, faceted drum castings, ventral keel, aft castle, taller
  drum towers and a lower drive block, as above. The arm grapple receivers
  moved onto the flat inboard strip (rel 19.5).

Pass 4 (scale): 0.50 m per unit from ship_scale.py option C (solves 0.500,
  range 0.485-0.515): 94 m long, the Colossus's scale. Crew room sizes it
  (80 x 300 m3 of 24,820 m3 of contents). The enclosed volume
  (ship_scale.SOLIDS['tarsus'], 291,600 units3) is the arms, body, ventral
  keel, aft body and castle, four drums, drive block and command module; the
  deck tanks and drive cans are excluded, as the Colossus excludes its pod
  tanks. The round-1 reshape added depth, so the solve fell from 0.51 (96 m).
  The battle plan's 106 m was a plan-area estimate; 106 m would take an
  override to 0.56.
  Fittings: Kite collar hatch 2.6 m across, grapple receiver plate 2.6 m.
  --figure stands a 1.8 m mannequin on the deck by the starboard Kite collar.

Docking (published in geometry.json 'docking', in units and metres)
  Clamp receiver 1-4 anchor: dorsal, +X out of the hull; two on the arms at
  (70, +-19.5 rel, the flat inboard strip), two on the aft body at (-30, +-14 rel), clear of the port
  tanks and the drums. Kite collar port|starboard anchor: on the central
  body's walls, starboard at X 0, port at X -12, +X outward.

Pass 5 (condition state 'boarded', ts_damage.py)
  The prize after the fight: drives shot out, Kites on the collars, not destroyed.
  One scar table with stable ids, resolved by ray casts against the clean ship
  before any cut, so every id lands in the same place in the geometry build and
  on the skinned build:
    D1-D11 craters: both main bells holed (D1 port, D2 starboard lip), the stern
      service housing burned through (D3), a strafing run along the drive deck
      (D4-D6), both inboard auxiliary bells (D7, D8), the skirt keel (D9), the
      starboard aft drum (D10) and the port drive flank (D11). Each cuts every
      part under it (opened faces 'Scar burnt metal'), lays a scorch decal, curls
      torn plating out of the rim and, where pass 7 wants smoke or sparks, sets
      'Scar <id> smoke anchor' and/or 'Scar <id> spark anchor' (the shared VFX
      kit's conduit-spark name, sparking = True), +X out of the hull, sized in
      radius_m.
    S1, S2: the stern face and the drive deck scorched.
    K1, K2: cutting-torch rings round both Kite collar hatches.
    L1: the drive section's stern and deck marker lamps dark (object-level
      material override; the meshes keep their base material).
  Laws: no base object is renamed, replaced or deleted; holds, command module,
  tanks, turrets, clamp receivers and collars are never cut. A closed mesh cuts
  with MANIFOLD and an open one with hole-tolerant EXACT, and a cut that grows a
  mesh past its own extent (the cutter's skin kept) is put back.
  --state boarded --out DIR builds it here (tarsus-geometry-boarded.blend, with
  the scar record in geometry.json 'scars'); damage_blend.py IN OUT applies it to
  the skinned build (../tarsus-surface/rebuild.sh).

Build (from this folder)
  sh ../../blender.sh --background --factory-startup --python build_tarsus.py -- [--views ...] [--samples N] [--cpu]
  python compose_review.py
The driver writes the renders (ignored), tarsus-geometry.blend and
geometry.json; compose_review.py writes top-overlay.png, the IoU into
geometry.json and tarsus-geometry.reference.png. Close-ups: --views details
(bow, bridge, tanks, turret, engine, stern, underside), plus drum-detail,
clamp-detail and collar-detail. --cpu renders on the CPU: the shared GPU is
often saturated by other sessions.
