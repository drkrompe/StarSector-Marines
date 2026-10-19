ONSLAUGHT (XIV) BATTLESHIP - CINEMATIC GEOMETRY
===============================================

A Hegemony picket flagship for the Jangala prologue's shot C1 v2
(second-wind-cinematic/corvus-approach/c1-v2-shot.md), seen in formation at
1.5-3 km. Built through the ship-model-pipeline skill. Undamaged, Hegemony
livery; flat base materials until a texturing pass. No hangars, interiors or
articulation: detail is at the mid-distance level the shot sees.

Identity (read-only install, never copied into the repo)
  data/hulls/onslaught.ship         weapon and engine slots, bounds, centre (144, 140)
  data/hulls/skins/onslaught_xiv.skin   XIV skin: removes and moves no slot
  graphics/ships/onslaught/onslaught_hegemony.png   livery and silhouette (the
                                    plain onslaught_base.png has the same alpha)
  data/variants/onslaught/onslaught_xiv_Elite.variant   turret fittings
  ship_data.csv row 'onslaught'     crew 750-1500, cargo 300, fuel 400
The build fails if the installed slots stop matching.

Build (Blender 5.2; renders, the .blend and export/ are ignored)
  sh ../../blender.sh --background --factory-startup --python build_onslaught.py -- [--views ...]
  python3 compose_review.py [--title T] [--before reference-history/v1-blockout]
  sh ../../blender.sh --background onslaught-geometry.blend --python export_onslaught.py
  sh ../../blender.sh --background --factory-startup --python verify_export.py
Checks: check_clashes.py (cross-module overlap), check_turrets.py (trainable
parts, buried lights) inside the built .blend.

Appendable asset (export_onslaught.py -> export/onslaught.blend + manifest.json)
  collection `ONSLAUGHT | clean`, root empty `ONSLAUGHT | clean | root` at the hull
  origin parenting everything; empties in `ONSLAUGHT | clean | anchors`. The root
  carries metres_per_unit: scale the root by it for metres. Modifiers baked.
  Builds go to Drive: starsector/builds/onslaught/<short commit>/.

Modules (one per area)
  os_kit       materials, primitives, prisms, curved sections, booleans, surface()
  os_hull      base masses (prow, core, the rounded belly under them, aft body
               with the stern machinery wells, tail, legs, waists) with arched,
               faceted tops and bellies; the prow gallery and the core citadel
  os_arms      the two gun arms: angular chined forearms lofted along X, rolled 16
               deg outboard and pitched 6 deg nose-down below the core so more
               guns bear; armour read off the sprite (orange upper courses, grey
               stepped rim blocks, turret housings, the Devastator drum), the
               orange shoulder they hang from
  os_stern     aft body with the machinery wells open astern, raked leg blades,
               tail; chined plates
  os_armour    the orange and grey Hegemony armour as sloped plates stepping down
               from the tiers, steps, fins, cheeks, keel bars
  os_bridge    spine, terraced command dome with the bridge glazing, aft dome
  os_machinery pipes, headers, pumps, valves and frames in the stern wells; the
               tail radiator
  os_engines   the six drive units
  os_turrets   24 turrets (barbette + pivot) and the two TPC hardpoints
  os_fittings  sensor mast on WS 025, antennas, dishes, running lights

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship centre:
  sprite col c, row r -> X 244-r, Y 144-c; slot locations are model XY unchanged.
  The owner asked for the hull "thick with large curving hull depth", then (round
  1) for curvature and terracing round the gun emplacements, and shallower arms.
  Every mass is a heightfield over its plan (os_kit.arched_mass): a rounded,
  faceted belly and an arched, crowned top; the core keeps the deep belly; the
  arms are shallow gun platforms; the galleries and the core terrace step the
  guns up toward the bridge.

Object names (stable identifiers)
  Hull ..., Armour ..., Bridge ..., Machinery ..., Fitting ...
  Engine <i> drive can / aft-facing bell / mouth lip / dark throat / shroud or
      drive pod; Engine <i> exhaust anchor (empty at the mouth, +X down the
      exhaust; custom props exhaust_direction, mouth_radius, slot_width). Drive
      glow emits from the anchor. Engines in onslaught.ship order: 1/4 quarter
      pods (angled 160/200), 2/3 beside the tail, 5/6 the mains.
  Weapon <slot> barbette (fixed); Weapon <slot> pivot (empty at the slot angle,
      props slot_angle, arc) with the trainable parts; WS 016/017 hardpoint
      housing, barrel and `Weapon <slot> muzzle` (empty).
  Light <where> <k>  running lights; colour and blink in geometry.json
      running_lights (red extremities, white edge line, 18% duty).

Scale (pass 4; geometry.json 'scale')
  From the shared ship_scale.py: contents (1,500 crew, 300 cargo, 400 fuel) over
  the capital machinery share against the measured enclosed volume (union of
  ship_scale.SOLIDS['onslaught'], 3.42 M units3, re-measured each build).
  Published 0.86 m/unit (solved 0.856): about 301 m long.
  Final by the owner's rule (a capital like the Legion, shared rule, no
  override); it moves only when the measured shape does. ship_scale flags it,
  as it flags the Legion (3.35x the Lasher's length; the sprites give 4.0x).
  Fittings in true metres: bridge glazing band 1.4 m in 1.1 m panes, crown hatch
  1.8 m, ledge service hatches 0.9 x 1.4 m; a 1.8 m person is 2.1 units.

Status
  Pass 1 blockout, pass 2 detail, pass 4 scale; pass 3 rounds 1-11 with the owner:
  thick curving brawler; tilted chined forearm arms with the sprite's armour (orange
  edge rail, dark sponson, deck channels); planed V underside; stepped skyline up
  to a faceted armoured citadel and down aft; gun noses leading; raked stern spurs;
  turrets set into the hull in flush rings; joins audited. IoU 0.956. Silhouette
  accepted by the owner after round 11.
  Detail pass 1 (owner: "detail on the hull, exposed machinery, piping, hatches,
  and armor"): os_detail_arms / _top / _hull / _stern add `Detail ...` objects
  only, seated by ray cast after the turrets and before the lights: plate seams
  and bolt rows, hatches and an airlock a side, louvres and heat shields, conduit
  runs from well to drive and along the chines, valve chests in the waist
  valleys, gimbal rams on the drives. Silhouette locked: IoU, seam audit,
  turret training and lights unchanged.
  Detail pass 2 (owner: "bolder: bigger, denser machinery and plating breakup so
  it reads at 1.5 km too"): a big layer under the pass-1 parts - armour plates of
  8-25 units in alternating tones on a near-black underlay (wide dark seams),
  segmented orange courses with dark joints, open machinery trenches and bays
  (waist valleys, arm decks, belly, stern wells) with tanks, pumps and pipe
  bundles of 1-3 units, radiator fin banks, bold drive collars. The waist trench
  stops short of the arm's own bay (os_detail_top.TRENCH_FORE). IoU 0.955;
  silhouettes gain edge pixels only; seam audit, turrets and lights unchanged.
  Awaiting the owner's review.
