LEGION (XIV) BATTLECARRIER - CINEMATIC GEOMETRY
================================================

The Hegemony patrol hull for the Jangala prologue's shot C2 PATROL ABEAM
(second-wind-cinematic/patrol-abeam/c2-layout.md), built through the
ship-model-pipeline skill. Damage-free: a well-kept Hegemony patrol.

Identity (read-only install, never copied into the repo)
  data/hulls/legion.ship          weapon, launch-bay and engine slots, bounds
  data/hulls/skins/legion_xiv.skin XIV retypes WS 000/001 to MISSILE and
                                  WS 002-007 to BALLISTIC; built-in 'fourteenth'
  graphics/ships/legion/legion_xiv.png  sprite (300x308, centre 150,149);
                                  legion.png is the plain-livery fallback
  data/variants/legion/legion_xiv_Elite.variant  fittings (Squall, Hurricane,
                                  heavy autocannon, flak, Vulcans; Broadsword
                                  and Hoplon wings); legion_Strike (Piranhas)
  ship_data.csv row 'legion'      crew 700-1500, cargo 300, 4 fighter bays
No .ship file other than legion.ship exists for this hull; there is no
lights layer for it. The build fails if the installed slots stop matching.

Build
  sh ../../blender.sh --background ^
      --factory-startup --python build_legion.py -- [--views ...] [--samples N]
  python compose_review.py
build_legion.py writes the renders, legion-geometry.blend and geometry.json;
compose_review.py writes top-overlay.png (silhouette IoU into geometry.json),
the C2 composites (c2-entry/abeam/exit.png over a flat Jangala stand-in, with
the window glass outlined and the abeam silhouette extent recorded) and
legion-geometry.reference.png.

Modules (one per area; each later-pass agent edits only its own)
  lg_kit.py      materials, primitives, prisms, booleans, surface() ray-cast
  lg_hull.py     the torso (fore and aft body, ventral keel), dorsal and tail
                 spines, bow blades, outer and inner armour belts
  lg_bridge.py   the command dome
  lg_wings.py    the two wing arms, each with one fly-through hangar under it
  lg_droop.py    the arms' anhedral: a shear applied after lg_wings builds
                 (hangar parts are built level and skip it)
  lg_ventral.py  the belly: keel girder, armour courses, magazine hatches,
                 cargo well and clamps, boat ports, tanks, radiators
  lg_engines.py  engine block, outer housings, the ten drive units
  lg_fittings.py masts, sensors, radomes and the running lights
  lg_turrets.py  fifteen turrets: fixed barbette + trainable part on a pivot

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship
  centre, so every slot location is model XY unchanged.
  metres_per_unit = 0.99, from the shared sizing basis (see Scale check):
  about 292 m long, 291 m beam, 87 m keel to crown (the belly's docking
  clamps hang open lowest).

Object names (stable identifiers; later passes key off them)
  Hull ...                  fore body, aft body, ventral keel, dorsal spine,
                            tail spine, engine block, port/starboard engine
                            housing (and their pass-2 detail parts)
  Bridge command dome
  Armour <side> bow blade / outer belt / outer belt arm run / inner belt
  Wing <side> sponson / hangar / armour plates / armour plates tan /
      armour backing / armour frames / hatch doors / underside girders /
      hangar frames / hangar roof frames
  Flight deck <slot> deck / mouth frame / well coaming / ceiling lights /
      well lights / amber lamp <k> / box wall / red light <k>
  Wing <side> transfer gallery / transfer door frames / transfer bays /
      transfer bay <k> inner door / traverser <k> / service lines /
      gantry rails / gantry crane / recovery barrier / catwalk /
      pod armour courses / pod keel girders / pod docking clamps / ...
  Wing <side> transfer door <k> slide, traverser <k> slide, gantry crane
      slide, recovery barrier hinge                     (keyable empties)
  Flight deck <slot> mouth, Flight deck <slot> launch point 1-4   (empties)
  Flight deck <slot> blast deflector hinge (keyable empty) / blast
      deflectors / control booth / well lift
  Ventral ...               belly parts (lg_ventral)
  Engine <i> drive can / aft-facing bell / mouth lip / dark throat
  Engine <i> exhaust anchor                                        (empty)
  Fitting <side> ...        masts, sensor arrays, radomes
  Light <side> <where> <k>  running lights (geometry.json 'running_lights':
                            position, colour, role, blink period/phase/duty)
  Weapon <slot> barbette (fixed)
  Weapon <slot> pivot (empty, rests at the slot angle) with children
      Weapon <slot> turret house / turret shield / barrel <k>, or
      Weapon <slot> launcher house / launcher cell face
  Engines are numbered in legion.ship order (1-4 the small outer pairs,
  5/6 the 24-wide mains, 7/8 the 16-wide, 9/10 beside the tail spike).

Hangars (geometry.json 'flight_decks', 'hangar_fit')
  The owner asked for a thicker torso with wing-like arms and hangars under
  them, then for the arms to taper and angle down, then: "each wing is a fly
  through hangar under it", then for hangars that plausibly work (fighter
  sizes, refit, hangar to central hold). Each wing arm angles down 10 degrees
  outboard of the torso wall (|Y| 80); under it hangs ONE LEVEL hangar,
  'Wing <side> hangar', X -64..104, open at both ends: floor z -26.5, roof
  following the arm's underside (-8.7 inboard, -12.0 outboard), lane 42 wide
  and 13.9 high at its lowest. Hangar parts are built in place and skip the
  anhedral shear; the arm above them is sheared.
  WS 012/013 launch from the forward mouth (the angled leading edge), WS
  010/011 from the aft mouth at X -64, which also takes recoveries.
  Flow: recovery into the aft mouth -> aft station -> traverser (sideways,
  nose forward) -> transfer door 1 -> lock across the transfer gallery into
  the keel -> central hold, refit at the same deck height -> door 3 ->
  forward station -> launch. Door 2 (amidships) is for barrier catches and
  crane lifts. Doors 34 x 10 units (33.7 x 9.9 m), locks 36 deep. The
  'Wing <side> transfer gallery' runs X -58..72.5, |Y| 58..79.6 on the lower
  flank and counts as pressure hull.
  Every fighter the Legion can carry, at its own scale, clears the lane, both
  mouths and the doors: 'hangar_fit' in geometry.json, printed at build
  (tightest: Hoplon, about 4.8 m of height on its dolly).
  Keyable for later shots (rest closed, stowed, parked): the transfer doors,
  traversers, recovery barrier, gantry crane and blast deflectors, each on a
  named empty; the moves are in flight_decks[].keyable.

Turrets (geometry.json 'weapon_slots')
  Every turret trains by rotating 'Weapon <slot> pivot' about its local Z;
  it rests at the slot angle. WS 007 (starboard dorsal heavy autocannon,
  arc 160 about -20 deg, so it can lay on Second Wind at -90) is the one
  flagged keyable_for_c2. The geometry is the shared turret kit's
  (turret-kit/turret_kit.py): guns elevate on 'Weapon <slot> elevation',
  barrels recoil on 'Weapon <slot> recoil <k>', the Vulcans' barrels spin on
  'Weapon <slot> rotor', and 'Weapon <slot> muzzle anchor <k>' marks each
  bore or missile. Each barbette's foot runs down to the lowest deck under
  its ring (turret-kit/check_seating.py finds none floating).
  The c2-lit views render the C2 cameras under studio light for review; the
  plain c2 views keep the shot's own dark lighting.

C2 (geometry.json 'c2')
  The model's axes are C2's world axes. Closest approach puts the starboard
  extreme 105 m from the glass (centre 200.6 m out) and the crown 3 m below
  eye level. The c2-* cameras are c2_core's cabin camera at C2 3.0 / 8.0 /
  11.4 s; c2-* renders use c2_core.SUN, planet-shine and no ambient.
  At 0.99 m/unit the hull is 1.62x longer than when C2 was framed (0.61),
  so it overfills all three frames (abeam about 1906 px wide against the
  1636 px layout target). The shot re-frames to the hull; that is the
  cinematic session's call (c2_core flank distance), not a model change.

Scale check (pass 4; geometry.json 'scale')
  The shared sizing basis, ship_scale.py and ship-scale.md (owner's choice
  2026-10-18, option C): contents over the capital machinery share, against
  the measured enclosed volume. Contents: 1,500 crew x 300 m3 x the capital
  factor 1.45, cargo 300 x 2.0 m3, fuel 300 x 1.5 m3, and hangars for the
  Elite complement (Broadswords and Hoplons at their own sizes, a spare per
  wing, x3.0 the bare box). Machinery share 0.58. Enclosed solids (torso,
  spines, dome, arms, wing hangars, transfer galleries, ship_scale.SOLIDS)
  about 1.68 M cubic units, re-measured on every build.
  Solves 0.995 m/unit (0.967-1.024 for 273-328 m3 a person). Published 0.99
  (lg_kit.METRES_PER_UNIT). 292 m long and 291 m wide: a Nimitz is 333 m.
  Fittings are true metres at any scale (lg_kit.PERSON = 0.61/0.99, since
  they were drawn at 0.61): a 2.1 m door is 2.1 units tall, a rail 1.1 m.
  Hangar mouths, lanes and transfer doors are sized from the fighters and
  do not rescale; every fighter fits with more room than before.

Status
  Pass 2 detailing done on every module, then a hangar-logistics and belly
  round (level hangars, transfer to the central hold, the underside). Top
  silhouette IoU 0.974. Awaiting owner review (pass 3).
  Known: WS 002/003's arc sweep grazes the belt arm run at inboard train
  angles (rest angle clear); the belt arm run crosses the well shafts; two
  mast pods touch the forward mouth frames; the forward booth glazing is
  over-bright (emissive bridge glazing).
  Renders are build outputs and are not committed; rebuild to see them.
