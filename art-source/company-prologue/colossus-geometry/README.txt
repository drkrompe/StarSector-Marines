COLOSSUS (HEAVY FREIGHTER) - CINEMATIC GEOMETRY (PASSES 1, 2 AND 4; PASS 3 OWNER REVIEW NEXT)

Purpose
An editable Blender model of the vanilla Colossus (hullId colossus; not the
Mk.II or Mk.III), the ship the Shepherd Morrow came down with in the low-orbit
fight over Asharu (shepherd-recovery.md, early-operation-nouns.md). Its broken
hull lies against the northern canyon wall of Basin's yard (mapgen-nouns.md).
This is the intact ship; the crashed state is pass 5 and waits for a cinematic.
It follows the ship-model-pipeline skill (.claude/skills/ship-model-pipeline)
and shares no code with any other hull: cl_kit.py holds copied helpers.

Hull identity (read-only install, starsector-core)
data/hulls/colossus.ship (hullId colossus; file name and hullId agree),
sprite graphics/ships/colossus/colossus.png (184 x 224, centre 93,128; bow up;
no lights layer). ship_data.csv row "colossus": Heavy Freighter, Low Tech,
CRUISER, crew 40-200, cargo 900, fuel 120, mass 1500, FRONT shield, system
flarelauncher, built-in civgrade. The description: "the core of Domain
frontier supply fleets", "primitive accommodations", "thicker than strictly
necessary hull plating".

Slots (checked against the install on every build)
WS 001 (69,75) 80/140, WS 002 (3,75) 90/130, WS 003 (70,-74) -80/140,
WS 004 (4,-73) -90/130: TURRET SMALL BALLISTIC -> 'Turret 1..4 WS 00n ...',
standing on the side cargo modules at the sprite's turret rings, resting at
their slot angles.
WS 005 (87,83) 45, WS 006 (86,-83) -45, WS 007 (-83,83) 100, WS 008 (-84,-81)
260: HIDDEN SMALL SYSTEM -> 'Launcher 1..4 WS 00n flare launcher ...' (the
flarelauncher system).
Engines 1-6 at X -124, Y 64/56/49 (port pod) and -61/-54/-46 (starboard pod),
width 15, angle 180, LOW_TECH -> 'Engine <i> aft-facing bell / mouth lip /
dark throat / exhaust anchor'.

Model
+X bow, +Y port, +Z dorsal, 1 sprite px = 1 unit about the .ship centre. The
sprite's own centreline is Y +1.5 (cl_hull.YC); parts are symmetric about it.
Modules, named so the crashed state can tear the hull apart along them:
  Hull central cargo hold, Hull hold hatch cover, Hull port|starboard longeron
  (+ aft knee), Cargo port|starboard fore|aft module, Hull keel spine,
  Hull aft machinery body, Frame ... (open lattice trusses), Bridge ...,
  Drive pod port|starboard casing, Engine <i> ..., Turret <i> ..., Launcher <i> ...
Heights are proposed art: hold keel -22 to deck 18, side modules -17..14, aft
body -16..13, deckhouse roof 24, pods +-16.

Pass 2 (plausible detail, IoU 0.967): four module agents, one per file.
  cl_hull: hatch cover as a crane platform plus two hinged leaves with seams and
  dogs; two deck cranes at the sprite's L-shaped marks; hatch-edge lamp rows;
  forward coaming vents; longerons as box girders with lightening holes; side
  modules with the sprite's blue and orange bands, seams, service covers, side
  cargo doors and personnel hatches; a two-leaf bow cargo door.
  cl_aft: ladder keel spine through the waist; open space-frame trusses with
  walkway gratings and pipe runs; segmented aft body with hatches, vents,
  striped band ribs; stern radiator grilles, housings and tail block.
  cl_bridge: deckhouse stepped to a forward annex and a cab with a raked,
  framed windscreen, wing and aft ports, roof ring and dish, mast with lamps,
  aft personnel door with landing, step and rails.
  cl_engines: segmented pods with seam rings, shoulder band, inspection ring and
  port, fins, covers, thrust frame; three gimballed bells per pod at the slots.
  cl_mounts: twin light autocannon turrets (barbette, race, faceted house,
  mantlet, braked barrels, feed trunk); six-tube flare launchers seated and
  tilted to the surface. check_clashes.py is the cross-module overlap sweep.
Pass 4 (scale): 0.50 m per unit from ship_scale.py option C (solves 0.502,
  range 0.487-0.517): 108 x 88 x 28.5 m. Crew room sizes it (200 x 300 m3);
  900 cargo is 1,800 m3 of 61,980. Fittings check: personnel hatch 1.1 x 2.4 m,
  bridge door 1.1 x 2.3 m, side cargo doors 7.5 x 7.0 m, bow door 20.6 x 11.6 m.
  --figure stands a 1.8 m mannequin on the bridge's aft door landing.

Pass 3 round 1 (owner: "a thick cargo front with those engines almost like tug
  boats to move the massive hulk"): the cargo block is deepened below the pass-2
  deck (hold keel -22 -> -50, side modules -17 -> -46), so deck fittings keep
  their seats; full-height bow cargo door, a lower row of side cargo doors, more
  wall strata, deeper bow armour and aft end caps, keel seams and armour strakes.
  The drive pods stay as built and read as tugs: steel thrust yokes (cl_aft,
  'Frame <side> thrust yoke / pad') run from each pod's nose and keel into a
  bolted thrust pad low on the side module's aft face. IoU 0.967.
  Scale: the measured volume rose to 1.18 M units3, so option C now solves 0.426
  (92 m); the owner kept 0.50 (108 m) by override (2026-10-18).
Pass 3 round 2 (owner: the hull as a thin wrapper round stacked containers; the
  grey slots in the front coloured as stacked containers): each fore module's
  bow face is recessed 7 units into open container bays behind a thin hull lip;
  steel cell guides on the sprite's slot lines ('<module> bow armour') hold, per
  cell, a stack of 40 ft containers end-on, two across and four high (4.9 x 5.2
  units at 0.50 m/u), 48 per face, in seven liveries ('<module> containers
  <livery>', lock bars, door seams, corner castings). The open bays are outside
  the enclosed volume (1.16 M units3; solve 0.429, published 0.50 by override).

Build (from this folder)
  sh ../../blender.sh --background --factory-startup --python build_colossus.py -- [--views ...] [--samples N]
  python compose_review.py
The driver writes the renders (ignored), colossus-geometry.blend and
geometry.json; compose_review.py writes top-overlay.png, the IoU into
geometry.json and colossus-geometry.reference.png. Close-ups: --views details.
