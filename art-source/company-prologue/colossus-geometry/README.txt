COLOSSUS (HEAVY FREIGHTER) - CINEMATIC GEOMETRY (PASS 1 BLOCKOUT)

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

Build (from this folder)
  sh ../../blender.sh --background --factory-startup --python build_colossus.py -- [--views ...] [--samples N]
  python compose_review.py
The driver writes the renders (ignored), colossus-geometry.blend and
geometry.json; compose_review.py writes top-overlay.png, the IoU into
geometry.json and colossus-geometry.reference.png. Close-ups: --views details.
