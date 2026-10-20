WOLF (P) - CINEMATIC GEOMETRY (PASSES 1-2, 4 SCALE)

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

Pass 2 detail (wf_detail, wf_mounts)
Read off both sprites at 20x: sensor eyes and pale shoulder ports round the
bridge canopy, crimson cheek strips down the head, a second canopy on the
neck spine, sensor heads on the outriggers and their flank vent grilles
(lit orange only to starboard, as both sprites draw it), the main body's
two round emitter wells, lattice ribs and wells on the missile pods, the aft
hatch well and link frames, the engine block's collar, rails, centre stack,
finned drive housings and troughs, drive bezels, seam bands, and the pirate
skin's "heavily modified - and poorly maintained" as form: mismatched plates
welded over the armour domes and wing roots, weld beads round the domes.
Weapons come from the shared turret kit (../turret-kit), which gained the
ion cannon turret and the mining blaster and Annihilator hardpoints for this
hull: the ion cannon on a kit barbette (Turret 1 pivot / elevation), the
mining blaster as the bow claw's middle in a channel through the fork base
(the sprite's inner tines are its prongs), the Annihilators set into the pod
faces. Silhouette IoU 0.933 with the hardpoint guns left out of the top
render (the hull sprite draws no weapons; they stand proud of the bow and
pods as the game draws them).

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

Scale (pass 4): 0.76 m per unit
From the shared sizing basis, ../ship_scale.py (option C), never set here:
METRES_PER_UNIT reads ship_scale.PUBLISHED and geometry.json's 'scale' is
ship_scale.record('Wolf (P)'). Inputs: 30 crew, 50 cargo, 20 fuel, frigate
class. Enclosed volume is the union of ship_scale.SOLIDS['wolf-p']: head,
neck and its flanks, main body, centre hump, wing roots, aft body, dorsal
spine and ventral keel; never the bow fork, outriggers, missile pods, armour
domes or engine block (the drive section). After the owner's review ("don't
create a flying wing"; turrets need a hull thick enough to carry them) every
section was deepened twice (round 2: "too thin of a ship": decks raised,
keels lowered, chamfers scaled with the depth, about 26 units keel to spine;
round 3, "too flat on the top": the bow lowered onto the hull's centre line
and the top crowned over the spine, highest at the turret, falling fore and
aft; round 4, "the modules don't feel cohesive": head, neck, main body, aft
body and engine block are lofted through one plan, one top line and one
keel line with rounded superellipse sections, the missile pods, outriggers
and ventral keel faired the same way; round 5, the owner's reading of the ship: the side
pods became shallow swept missile wings rooted ahead of the drive section and
angling out to the WS 005 / WS 006 hookups, the body keeping to its core;
the drive section an octagon ahead of three long octagonal drive cans exposed
out the back; the bow a faceted wedge tapering to the forward mount. The
silhouette IoU falls to 0.89 by design; round 6, "that nose - still feels
weak", the missile pods want "modeled missile turret heads" and the PD
turrets "read like cylinders and are lifted quite high": a deep prow that keeps
the sprite's breadth to the outer tines, raked at the stem with a chin, two
armoured jaws either side of the WS 004 trough and a hood over its breech; the
bridge as a raised command deck on the head (glazed face over the bow gun, the
sprite's white canopy; roof running aft to the neck's sensor dome, the sprite's
lit oval); kit launcher heads on the wing tips; the merchant's PD turrets in
wells in low faired sponsons. IoU 0.90): measured 38,763 units3, which solves
0.758 (range 0.736-0.782), published 0.76: 72 m long (the deeper prow adds a
unit), 37 m across the wings, about 21 m deep. The bridge is not in SOLIDS, so
the scale holds while the owner decides 71 m against 86 m. The roster's
sprite-area estimate was 86 m: a deep hull holds its contents in a shorter
ship, so this is 18% under the estimate, outside its +-8%. The owner decides
between this and an override (as the Colossus has). The driver re-measures on every build and
fails if the union drifts more than 2% from ship_scale.HULLS.

Round 7, the detail pass ("Next it needs plausible detail pass"): wf_fittings.py
adds, read off both sprites and sized for a 72 m ship: panel seams draped over
the lofted decks, flanks, prow and bridge roof; deck hatches, a crew door in
each neck flank (1.0 x 2.2 m) and flank maintenance panels; the bridge's visor,
glazing frames, comms dish, whip antennas, sensor box and roof hatch; radiator
wells and heat-exchanger louvres on the drive section; RCS clusters at the bow,
wing roots and stern; a ventral sensor blister, a neck mast, a ventral docking
collar with two white docking lights, a white masthead on the bridge mast and
deck tie-down cleats; conduits along the wing roots; and, on the pirate,
welded patch plates on the jaws, neck and engine flanks. Nothing on the flanks
stands outside the plan: RCS blocks are sunk in the skin, panels are flush,
and fittings sit below the belt. IoU 0.9007 (0.9013 for the round 6 hull
alone); volume and scale unchanged; the antenna whips raise the overall height
to 40 units (30.6 m). The new prow, jaws, hood and bridge carry their own
Codex tiles in each livery (grey sheets 'grey-tops' and 'grey-bridge').

Variants
--variant merchant builds the convoy's own Wolf on the same hull and scale:
no pirate armour (domes, welded plates), the base hull's WS 002 / WS 003
turrets back in wells sunk into low sponsons faired into the aft flanks (the
base sprite's round wells; round 6 retired the full-depth drums), its own collection WOLF and asset wolf.blend,
geometry-merchant.json, the vanilla wolf_Assault loadout (heavy blaster and
Harpoons drawn with the kit's mining blaster and Annihilator forms for now:
built_as in the manifest). Its livery is ../wolf-merchant-surface.

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
wf_lights.py, wf_detail.py, wf_fittings.py (one module per area), compose_review.py (IoU, shot extents,
review sheet), compare_lineup.py (true-scale lineup against the Hound and
Colossus), geometry.json (identity, slots, engines, turrets, lamps, scale
record, asset contract, IoU, shot extents).

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
