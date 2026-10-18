LASHER - CINEMATIC GEOMETRY (PASS 1 BLOCKOUT, LIGHT PASS 2, PASS 4 SCALE; ACCEPTED)

Purpose
A light, scripted Blender model of the vanilla Lasher as a Hegemony escort
frigate, for the Jangala prologue's C1 v2 shot, where real hulls hold
formation 1.5-3 km from camera and this one is instanced several times. It
follows the ship-model-pipeline skill (.claude/skills/ship-model-pipeline)
and the Hound package's conventions (../hound-geometry/) but shares no code
with another hull. Silhouette and true scale matter most; there are no
interiors, no hero detail, no damage states and no articulation beyond the
three turrets.

Hull identity
lasher (data/hulls/lasher.ship; ship_data.csv row "lasher": Frigate, Low
Tech, ammofeed system, 25-50 crew, 40 cargo, 25 fuel, FRONT shield 210 deg,
built-in ballistic_rangefinder). Sprite graphics/ships/lasher/lasher_base.png,
68x96, .ship centre (34, 35) from the bottom edge; lights layer
lasher_lights.png. There is no Hegemony skin: the Hegemony faction lists the
base hull (data/world/factions/hegemony.faction), so its livery is the base
sprite's terracotta and gunmetal. The variants (data/variants/lasher/) only
confirm the slots; no loadout is modelled. Install files are read where they
are (art-source/starsector_path.py finds the install on Windows and macOS)
and never copied into the repository.

Coordinates
+X bow, +Y port, +Z dorsal. One sprite pixel is one unit about the .ship
centre, so slot and engine XY from lasher.ship are used unchanged. Sprite
column c, row r (from the top) covers X 60-r..61-r and Y 33-c..34-c. Heights
are proposed art; the game gives none.

Scale (pass 4): 0.95 m per unit
Published through the shared sizing basis, ../ship_scale.py (owner's option C,
2026-10-18), never set here: METRES_PER_UNIT in build_lasher.py reads
ship_scale.PUBLISHED and geometry.json's 'scale' is ship_scale.record('Lasher'),
which holds the inputs, constants and contents. Inputs: 50 crew, 40 cargo,
25 fuel, frigate class. Enclosed volume is the union of
ship_scale.SOLIDS['lasher']: bow head, nose cap, spine, wing body and ventral
keel (the engine nacelles are drive pods and the gun and missile pods are
weapons, so neither counts): 32,793 units3. That solves to 0.949 (range
0.92-0.977), published as 0.95: about 90 m long, 65 m across the wings and
19 m keel to turret top. A 1.8 m person is 1.9 units. The driver re-measures
the union on every build and fails if it drifts more than 2% from
ship_scale.HULLS, so a shape change is carried into the shared table rather
than absorbed here.
Human fittings are sized in true metres at that scale (ls_hull VIEWPORT_M,
BRIDGE_BAND_M): viewport panes 1.0 x 0.8 m, the bridge glazing band 1.0 m.

Reading of the sprite (ls_hull, ls_pods)
- Nose cap (rows 1-12): gunmetal prow with raked top, chin and cheeks, and
  two dark sensor wells.
- Bow head (rows 12-31): shield-shaped orange head, widest at X 42, its
  shoulders falling away to a dome round the raised WS 007 turret ring, with
  gunmetal machinery pads at the shoulders.
- Spine: the central keel structure, Y +-9.5 through the neck, pinched to
  +-4.5 between the waist turrets, +-9 aft round the main drive; a dorsal
  ridge and a ventral keel give the side profile its depth.
- Wing body (rows 44-79): the broad orange body the waist turrets stand on,
  its deck falling outboard of Y 12, with an outboard armour strake.
- Gun pods (rows 32-44) and missile pods (rows 46-56): gunmetal blocks whose
  forward face forks round the hardpoint weapon, the gap centred on the
  slot (so the asymmetric WS 002 / WS 003 give slightly different prongs).
- Engine nacelles (rows 69-89): gunmetal blocks either side of the spine
  aft, raked aft and on their outer shoulders.

Light pass 2
Glazing exactly where lasher_lights.png lights: the bridge viewport arc
under a visor brow forward of the bow turret, three viewports on each
shoulder, one on each flank of the spine at the neck; the aft heat vents
(four glowing slats in two dark wells on the spine, the lights layer's aft
streaks). Plus a spine ridge and seam bands, strake joints, pod vent bands
and hatches, nacelle vent wells and ribs. Running lights follow ordinary
practice (the lights layer has none): red port wing tip, green starboard,
white stern, white prow strobe.

Slots (verified against the installed lasher.ship on every build)
  id      mount      size   type       location   angle  arc
  WS 001  HARDPOINT  SMALL  MISSILE    (11, 27)   0      5
  WS 002  HARDPOINT  SMALL  BALLISTIC  (24, 16)   0      5
  WS 003  HARDPOINT  SMALL  BALLISTIC  (24, -15)  0      5
  WS 004  HARDPOINT  SMALL  MISSILE    (11, -27)  0      5
  WS 005  TURRET     SMALL  BALLISTIC  (4, 10)    60     270   Turret 1
  WS 006  TURRET     SMALL  BALLISTIC  (4, -10)   -60    270   Turret 2
  WS 007  TURRET     SMALL  BALLISTIC  (37, 0)    0      270   Turret 3
  WS 008  HIDDEN     SMALL  SYSTEM     (-15, 29)  160    60    anchor only
  WS 009  HIDDEN     SMALL  SYSTEM     (-15, -29) 200    60    anchor only
Engines, LOW_TECH, angle 180, Z 0: Engine 1 (-34, 0) width 16 (main),
Engines 2-5 at (-28, 14), (-26, 19), (-28, -13), (-26, -18) width 8.
Turrets rest at their slot angles; the build fails if a rested barrel
overlaps the hull (BVH).

Stable object names
  Hull ...                       structure, glazing, vents, seams
  Pod <side> gun|missile ...     weapon pods
  Engine <i> drive can / aft-facing bell / mouth lip / dark throat
  Engine <i> exhaust anchor      empty at the slot (bell mouth), +X down the exhaust
  Engine <i> drive glow anchor   empty at the throat, +X aft, for drive glow
  Turret <i> barbette, traverse gap      fixed
  Turret <i> pivot               empty; turn about hull +Z to train; custom
                                 properties slot, rest_angle_deg, arc_deg
  Turret <i> gunhouse, mantlet, barrel <k>, barrel <k> muzzle anchor   trained
  Weapon <slot id> anchor / muzzle anchor / breech / barrel / launcher / tube <k>
  Light <side> <where> <k>       running lights; origin = lamp position

The cinematic asset
build_lasher.py saves lasher.blend: one collection `LASHER` with the root
empty `LASHER | root` parenting every object, and the anchors in the child
collection `LASHER | anchors`. Every modifier is baked (no live booleans or
bevels), flat shaded, about 8,900 triangles and 128 meshes, eight
materials: Hegemony armour and gunmetal (each with light procedural wear on
object coordinates), recess, bridge glazing, heat-vent glow and three
running-light colours. No cameras, lamps or review rig are saved. Link or
append the collection and instance it with a collection-instance empty
scaled by metres_per_unit; verify_asset.py does exactly that three times
and checks the contract. The built file is not tracked: it is copied to the
shared Drive under starsector/builds/lasher/<short-commit>/.

Owner review
Accepted as built on 2026-10-18 at 0.95 m per unit (build f264ff7).
reference-history/v2-accepted/geometry.json records the accepted state.

Silhouette check
compose_review.py lays the sprite into the top camera frame and compares
alpha masks: IoU 0.953 (0.9% of the sprite uncovered, 4.0% model outside it,
mostly the hardpoint barrels and running lights the hull sprite does not
draw, and chamfer overhang at the pod corners).

Shot check (C1 v2)
c1_core's camera (50 mm on a 36 mm sensor) at 1920x1080 under its sun
(SUN_IN_CAMERA, no fill), the hull abeam and a little above: 155 x 47 px at
1.5 km, 79 x 24 px at 3 km. The brief's 20-40 px corresponds to a wider
lens or a longer range than this camera; the numbers scale linearly.

Rebuild from this folder (outputs go to build/lasher/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_lasher.py -- \
    --views top,three-quarter,port-side,front,stern,details,shot --manifest
python3 compose_review.py --manifest
sh ../../blender.sh --background --factory-startup --python verify_asset.py
(About a minute on the M5 at 24 samples.) --views none --manifest rebuilds
only the asset and geometry.json. Options after "--": --out DIR, --views,
--samples N, --res WxH, --no-save, --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE.

Files
build_lasher.py (driver), ls_kit.py, ls_hull.py, ls_pods.py, ls_engines.py,
ls_mounts.py, ls_lights.py (one module per area), compose_review.py (IoU,
shot extents, review set; python3 with Pillow + numpy), verify_asset.py,
geometry.json (identity, slots, engines, turrets, lights, scale record,
asset contract, IoU, shot extents).

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
