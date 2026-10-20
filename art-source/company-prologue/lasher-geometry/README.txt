LASHER - CINEMATIC GEOMETRY (PASSES 1, 2 + DETAIL PASS, 4 SCALE ACCEPTED; PASS 7 EFFECTS)

Purpose
A light, scripted Blender model of the vanilla Lasher as a Hegemony escort
frigate, for the Jangala prologue's C1 v2 shot, where real hulls hold
formation 1.5-3 km from camera and this one is instanced several times. It
follows the ship-model-pipeline skill (.claude/skills/ship-model-pipeline)
and the Hound package's conventions (../hound-geometry/) but shares no code
with another hull. Silhouette and true scale matter most; there are no
interiors, no damage states and no articulation beyond the three turrets.
The Asharu battle sees it closer (1-2 km, and nearer in passing), which is
what the detail and cohesion pass below is for.

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

Detail and cohesion pass (ls_detail, owner review 2026-10-19)
"Lasher hull needs detail, sections seem 'disconnected'." The light pass
left the gun pods, missile pods and nacelles as blocks set against the
spine and wing. ls_detail runs after the structure, pods and drives and
adds parts only (no base part is renamed or moved):
- Join fairings: armour wedges carrying the spine's plating over the groove
  onto each gun pod, ramping the gun pods' and missile pods' aft walls and
  the missile pods' inboard walls down onto the wing, and fairing the
  nacelles' forward walls into it; a gunmetal saddle with two conduits on
  brackets in each spine-nacelle groove; a bridge frame tying each gun pod
  to its missile pod; buttresses and a rail on the pinched spine; armour
  collars seating the three barbettes.
- Frames: transverse gunmetal ribs cut from the hull itself (a thin slab of
  every host at one station, grown out of its skin), so one frame runs
  unbroken across spine, fairing, pod and wing: the nose join (X 49) and
  the nose (53.6), the neck (28.7), across neck, fairings and gun pods
  (18.1, 23.5), the missile pods (5.8), spine and wing (-3.1), spine and
  nacelles (-9.2, -23.3).
- Surface detail at a 90 m hull's scale: draped armour panels on the wing
  (their gaps are the panel seams), side panels and a belt on the bow head,
  cheek and rake panels on the nose, side panels and ribs on the pods,
  shoulder panels, cooling fins, an aft grille and a belt on the nacelles,
  hatches with dark rims, vent grilles, drive bezels round the nacelle
  bells and a collar where the main bell leaves the spine.
Owner accepted the detail pass on 2026-10-20 ("This works").
Every part is seated by ray-casts against the frozen hull (ls_detail.Field),
none joins kit.hull (so the barbettes and lamps seat as before), and none is
an enclosed volume, so the scale is unchanged. It also fixed a pass-2 fault
the owner saw: the strake seams were never lowered onto the sloping wing
(ls_hull.shear worked in the part's own coordinates) and hung up to three
units above it like stray antennae. Silhouette IoU 0.950 (was 0.953).

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
Turrets rest at their slot angles; the build fails if a turret meets anything
anywhere across its arc (below).

Turrets (the shared turret kit, owner 2026-10-20)
"Not using approved kit turrets": the three turrets are the turret kit's
(../turret-kit/turret_kit.py), armed as lasher_Standard.variant, the stock
fit: a light machine gun (lightmg) on each of WS 005, WS 006 and WS 007.
Each stands on a kit barbette (ring 4.6 units, the sprite's turret ring)
seated on the highest hull under its ring, its foot run down to the lowest
surface under it, inside the detail pass's armour collar. ls_mounts lifts a
barbette in 0.4-unit steps until the trained turret meets nothing across its
whole 270-degree arc (25 angles, BVH against every other part within reach)
and fails the build if no lift clears it. The waist pair (WS 005 / WS 006)
need a 3.0-unit drum, because their arcs swing the gun across the spine and
its ridge; the bow mount (WS 007) clears at 1.0 on its ring. geometry.json
records seat, lift, foot and the sweep for each. Train: rotate `Turret <i>
pivot` about its local Z. Elevate: rotate `Turret <i> elevation` about its
local Y (negative lifts). The gun's recoil slide is `Turret <i> recoil 1`.
The hardpoints keep this package's own placeholder guns: the kit has no
light assault gun or heatseeker rack yet.

Stable object names
  Hull ...                       structure, glazing, vents, seams
  Pod <side> gun|missile ...     weapon pods
  Engine <i> drive can / aft-facing bell / mouth lip / dark throat
  Engine <i> exhaust anchor      empty at the slot (bell mouth), +X down the exhaust
  Engine <i> drive glow anchor   empty at the throat, +X aft, for drive glow
  Turret <i> barbette             fixed (kit barbette; '... plinth' when deep)
  Turret <i> pivot               empty; turn about hull +Z to train; custom
                                 properties slot, weapon, rest_angle_deg, arc_deg
  Turret <i> house ..., gun ..., recoil 1 ...   trained (turret kit)
  Turret <i> elevation           empty; turn about local Y to elevate
  Turret <i> muzzle anchor 1     the turret kit's muzzle, +X along the bore
  Anchor turret <i> barrel 1 muzzle              on it; the VFX kit's declared muzzle
                                 (one barrel each; barrel 2 went with the old twin guns)
  Weapon <slot id> anchor / breech / barrel / launcher / tube <k>
  Anchor weapon <slot id> muzzle                 hardpoint muzzle
  Light <side> <where> <k>       running lights; origin = lamp position

The cinematic asset
build_lasher.py saves lasher.blend: one collection `LASHER` with the root
empty `LASHER | root` parenting every object, and the anchors in the child
collection `LASHER | anchors`. Every modifier is baked (no live booleans or
bevels), flat shaded, about 32,100 triangles and 309 meshes, thirteen
materials: Hegemony armour and gunmetal (each with light procedural wear on
object coordinates), recess, bridge glazing, heat-vent glow, three
running-light colours and the turret kit's weathered paints for the guns. No cameras, lamps or review rig are saved. Link or
append the collection and instance it with a collection-instance empty
scaled by metres_per_unit; verify_asset.py does exactly that three times
and checks the contract. The built file is not tracked: it is copied to the
shared Drive under starsector/builds/lasher/<short-commit>/.

Liveries
--livery hegemony (default) builds the original asset unchanged: collection
LASHER into build/lasher/. --livery independent is the Asharu convoy's escort
scheme: pale warm-grey armour from the independent faction colour (150,150,150)
with a slate-blue accent on the wing strakes, spine ridge, gun-pod spine
plates and drive lips (the kit turrets keep the weapon's own paint) (ls_kit.LIVERIES). Texture only: the geometry,
names and anchors are the same. It builds its own collection,
LASHER (independent), root LASHER (independent) | root, into
build/lasher-independent/, so both liveries can be appended into one scene.
verify_asset.py, export_effects.py and review_effects.py take either build
(pass the blend path, or --out for the review). The tracked geometry.json
stays the Hegemony asset's; the independent build writes its own beside the
blend. Drive: builds/lasher-independent/<short-commit>/.

Owner review
Accepted as built on 2026-10-18 at 0.95 m per unit (build f264ff7).
reference-history/v2-accepted/geometry.json records the accepted state.

Effects (pass 7)
The shared VFX kit (../second-wind-vfx) dresses the Lasher through this
folder's hull profile, vfx_hull.py: the five LOW_TECH drives in vanilla
orange on the `Engine <i> exhaust anchor` empties (1 centre, 2-3 port, 4-5
starboard), the three kit turrets' declared muzzles (`Anchor turret <i>
barrel 1 muzzle`), and the four running lights (red and green wing tips,
white stern, white prow strobe). One state, clean. export_effects.py runs
`swfx.apply(state='clean', groups=thrusters, smoke, guns, lighting)` on the
built lasher.blend and saves the kit's `SWFX` collection beside `LASHER`;
link or append `LASHER` alone for the bare ship, or both in one load for the
effects, which ride the ship's anchors. A shot sets the lighting rig and
fires the guns through the kit's knobs (second-wind-vfx/README.md, Controls).
review_effects.py appends both into an empty scene under c1_core's sun and
renders fx-stern-quarter, fx-port-side and fx-shot-1500m.

Silhouette check
compose_review.py lays the sprite into the top camera frame and compares
alpha masks: IoU 0.950 after the detail pass (0.6% of the sprite uncovered,
4.6% model outside it, mostly the hardpoint barrels and running lights the
hull sprite does not draw, chamfer overhang at the pod corners, and the
pod bridge frames and aft fairings at the pods' edges); 0.953 before it.

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
sh ../../blender.sh --background --factory-startup --python export_effects.py
sh ../../blender.sh --background --factory-startup --python review_effects.py
(About a minute on the M5 at 24 samples.) --views none --manifest rebuilds
only the asset and geometry.json. Options after "--": --out DIR, --views,
--samples N, --res WxH, --no-save, --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE.

Files
build_lasher.py (driver), ls_kit.py, ls_hull.py, ls_pods.py, ls_engines.py,
ls_detail.py, ls_mounts.py, ls_lights.py (one module per area), vfx_hull.py and
export_effects.py (the VFX kit profile and export), review_effects.py,
compare_lineup.py (true-scale lineup against another hull), compose_review.py (IoU,
shot extents, review set; python3 with Pillow + numpy), verify_asset.py,
geometry.json (identity, slots, engines, turrets, lights, scale record,
asset contract, IoU, shot extents).

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
