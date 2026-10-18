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
  os_hull      prow, core, two gun arms, aft body with the stern machinery wells,
               tail, aft legs, waists; plated flanks, keel bars
  os_armour    the orange and grey Hegemony armour bands, steps, fins, cheeks
  os_bridge    spine, terraced command dome with the bridge glazing, aft dome
  os_machinery pipes, headers, pumps, valves and frames in the stern wells; the
               tail radiator
  os_engines   the six drive units
  os_turrets   24 turrets (barbette + pivot) and the two TPC hardpoints
  os_fittings  sensor mast on WS 025, antennas, dishes, running lights

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship centre:
  sprite col c, row r -> X 244-r, Y 144-c; slot locations are model XY unchanged.
  The owner asked for the hull "thick with large curving hull depth": every mass
  turns under on a rounded bilge and the keel line curves up fore and aft.

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
  Published 0.45 m/unit (solved 0.452): about 158 m long, 121 m beam, 52 m deep.
  PROVISIONAL: ship_scale flags it (a capital under 4x a frigate's length); the
  owner's capital crew factor (1.45) is due in ship_scale.py and will move it.
  The geometry is in hull units, so only the root's scale changes.
  Fittings in true metres: bridge glazing band 1.4 m in 1.1 m panes, crown hatch
  1.8 m, ledge service hatches 0.9 x 1.4 m; a 1.8 m person is 4.0 units.

Status
  Pass 1 blockout (IoU 0.964), pass 2 mid-distance detail (IoU 0.9636), pass 4
  scale (provisional). Awaiting owner review (pass 3).
