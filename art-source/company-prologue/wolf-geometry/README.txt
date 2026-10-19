WOLF (P) - CINEMATIC GEOMETRY

Purpose
A scripted Blender model of the vanilla Wolf frigate in its pirate skin, for
the Asharu battle cinematic (../asharu-battle/asharu-battle-shot.md): two fast
pirate frigates that cut across the convoy column. Seen close in S7-S9 (in S9
about 670 m away and about 190 px across), mid-distance in S6 and distant in
S4. It follows the ship-model-pipeline skill (.claude/skills/ship-model-pipeline)
and the Lasher package's conventions (../lasher-geometry/), sharing no code
with another hull.

Hull identity
Base hull wolf (data/hulls/wolf.ship; ship_data.csv row "wolf": Frigate, High
Tech, displacer system, 15-30 crew, 50 cargo, 20 fuel, FRONT shield 150 deg)
in the pirate skin data/hulls/skins/wolf_d_pirates.skin ("Wolf (P)", tech
Pirate, system displacer_degraded): "heavily modified - and poorly maintained -
by its pirate crew". The skin draws graphics/ships/wolf/wolf_pirates.png
(52x96, .ship centre (26, 42) from the bottom edge) and removes WS 002 and
WS 003, the aft pair of small energy turrets; the sprite paints crimson armour
domes over their rings. wolf_base.png, the unpainted hull, is read beside it
for form. There is no lights layer. Loadout:
wolf_d_pirates_Attack.variant, the pirates' goal variant: ion cannon (WS 001),
mining blaster (WS 004), Annihilator rocket pods (WS 005 / WS 006). Install
files are read where they are (art-source/starsector_path.py finds the
install on Windows and macOS) and never copied into the repository.

Coordinates
+X bow, +Y port, +Z dorsal. One sprite pixel is one unit about the .ship
centre, so slot and engine XY from wolf.ship are used unchanged. Sprite
column c, row r (from the top) covers X 53-r..54-r and Y 25-c..26-c. Heights
are proposed art; the game gives none.

Reading of the sprite (wf_hull)
- Bow fork (rows 1-10): a grey claw of five tines round the WS 004 channel.
- Head (rows 11-23): grey, crimson cheeks, a bridge canopy on the centreline,
  a grey outrigger either side (the starboard one with an orange heat glow).
- Neck (rows 24-36): crimson flanks round a grey spine with a sensor dome;
  two amber hull lamps at row 28.
- Main body (rows 37-58): the WS 001 turret ring, a central hump aft of it,
  two emitter wells, grey missile pods out to Y +-25, crimson wing roots.
- Aft body (rows 58-73): two crimson armour domes over the removed turrets.
- Engine block (rows 74-94): grey machinery with the three HIGH_TECH drives.

Slots (verified against the installed wolf.ship and the skin on every build)
  id      mount      size    type      location   angle  arc
  WS 001  TURRET     SMALL   ENERGY    (11, 0)    0      150   Turret 1
  WS 004  HARDPOINT  MEDIUM  ENERGY    (45, 0)    0      5     bow fork channel
  WS 005  HARDPOINT  SMALL   MISSILE   (11, 20)   0      5     port pod
  WS 006  HARDPOINT  SMALL   MISSILE   (11, -19)  0      5     starboard pod
  removed by the skin: WS 002 (-13, 12) and WS 003 (-13, -11), small energy
  turrets at +-90 deg, arc 200 (recorded in geometry.json, not built).
Engines, HIGH_TECH, angle 180, width 16, Z 0: Engine 1 (-36, 12), Engine 2
(-40, 0), Engine 3 (-36, -11). Turrets rest at their slot angles; the build
fails if a rested barrel overlaps the hull (BVH).

Scale (pass 4): 0.95 m per unit
From the shared sizing basis, ../ship_scale.py (option C), never set here:
METRES_PER_UNIT reads ship_scale.PUBLISHED and geometry.json's 'scale' is
ship_scale.record('Wolf (P)'). Inputs: 30 crew, 50 cargo, 20 fuel, frigate
class. Enclosed volume is the union of ship_scale.SOLIDS['wolf-p']: head,
neck and its flanks, main body, centre hump, wing roots, aft body, dorsal
spine and ventral keel; never the bow fork, outriggers, missile pods, armour
domes or engine block (the drive section). Measured 19,705 units3, which
solves 0.949 (range 0.92-0.977): 89 m long, 50 m across the pods, 16 m keel
to canopy. The roster's sprite-area estimate was 86 m; the build is 4%
longer, inside that estimate's +-8%. The driver re-measures on every build and
fails if the union drifts more than 2% from ship_scale.HULLS.

Shot checks
At 1920x1080 on a 36 mm sensor, port bow quarter from a little above, low sun
from the camera's left, no fill: S9's 28 mm at 670 m 189 px across, S6's
30 mm at 1.5 km 91 px, S4's 35 mm at 4 km 41 px.

Rebuild from this folder (outputs go to build/wolf-p/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_wolf.py -- \
    --views top,three-quarter,port-side,front,stern,details,shot --manifest
python3 compose_review.py --manifest --title "PASS 1 BLOCKOUT"
sh ../../blender.sh --background --factory-startup --python compare_lineup.py
(--views none --manifest rebuilds only the asset and geometry.json. Options
after "--": --out DIR, --views, --samples N, --res WxH, --no-save,
--cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE.) compose_review.py needs Pillow + numpy.

The cinematic asset
build_wolf.py saves wolf-p.blend: one collection `WOLF_P` with the root empty
`WOLF_P | root` parenting every object, anchors in `WOLF_P | anchors`. Every
modifier is baked. The built file is not tracked; it goes to the shared Drive
under starsector/builds/wolf-p/<short-commit>/.

Files
build_wolf.py (driver), wf_kit.py, wf_hull.py, wf_engines.py, wf_mounts.py,
wf_lights.py (one module per area), compose_review.py (IoU, shot extents,
review sheet), compare_lineup.py (true-scale lineup against the Hound and
Colossus), geometry.json (identity, slots, engines, turrets, lamps, scale
record, asset contract, IoU, shot extents).

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
