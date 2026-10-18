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
  metres_per_unit = 0.61, derived for this hull (see Scale check): about
  179.9 m long, 179.2 m beam, 53.9 m keel to crown (the belly's docking
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
  crane lifts. Doors 34 x 10 units (20.7 x 6.1 m), locks 36 deep. The
  'Wing <side> transfer gallery' runs X -58..72.5, |Y| 58..79.6 on the lower
  flank and counts as pressure hull.
  Every fighter the Legion can carry, at its own scale, clears the lane, both
  mouths and the doors: 'hangar_fit' in geometry.json, printed at build
  (tightest: Hoplon, about 1.3 m of height on its dolly).
  Keyable for later shots (rest closed, stowed, parked): the transfer doors,
  traversers, recovery barrier, gantry crane and blast deflectors, each on a
  named empty; the moves are in flight_decks[].keyable.

Turrets (geometry.json 'weapon_slots')
  Every turret trains by rotating 'Weapon <slot> pivot' about its local Z;
  it rests at the slot angle. WS 007 (starboard dorsal heavy autocannon,
  arc 160 about -20 deg, so it can lay on Second Wind at -90) is the one
  flagged keyable_for_c2.

C2 (geometry.json 'c2')
  The model's axes are C2's world axes. Closest approach puts the starboard
  extreme 105 m from the glass (centre 200.6 m out) and the crown 3 m below
  eye level. The c2-* cameras are c2_core's cabin camera at C2 3.0 / 8.0 /
  11.4 s; c2-* renders use c2_core.SUN, planet-shine and no ambient.

Scale check (pass 4, warship form; geometry.json 'scale')
  Each hull gets its own scale from what it is (owner, 2026-10-16). Crew and
  ship spaces: deck program CAPITAL "Battlecarrier" (CARRIER) crew 700-1500
  cargo 300 is 30,827 m2 of rooms; less its notional 3.2 m HANGAR rooms
  (2,160 m2), x1.40 circulation x3.2 m = 128,428 m3 habitable.
  Habitable share: the requested warship share is 30-35%; 33% is
  published. The itemised budget is looser. Fighter stowage for the Elite
  complement (6 Broadswords, 4 Hoplons, a spare per wing, servicing room,
  25% for ordnance handling) at the fighters' own 0.50 m/px scales
  (fighters-geometry) is about 52,700 m3, 13.5% of the hull at 0.61; armour
  15%, reactors/flux/drives 12%, barbettes and magazines 5%, tanks and
  structure 10% are judged. That leaves 44.5% habitable, which alone would
  solve to about 0.55. 0.61 stands on the warship share; whether the
  Legion should read bigger or smaller than that is the owner's call.
  Pressure hull (union of torso, spines, dome, arms, wing hangars and the
  transfer galleries) about 1.68 M cubic units, re-measured on every build:
  solves to 0.614 m/unit (0.602-0.634 for 35-30% habitable; a freighter's 55%
  would give 0.514). Published 0.61.
  Real-world check: 183 m long and 179 m wide, 391k m3 enclosed for 700-1500 crew; about 261 m3 per
  person at max crew. A Nimitz is 333 m with about 5,000 crew; fleet and
  light carriers run 200-260 m. The Legion is shorter than those but as wide
  as it is long, so it encloses supercarrier-class volume for under a third
  of a Nimitz's crew. The geometry sets the answer: a deeper torso lowers it.
  Fittings at 0.61: a 1.8 m person is 2.95 units; doors and hatches 3.4,
  rails 1.8. Fighters at their own scales are about 16-18 m (31 Legion
  units of span for a Hoplon-sized craft), inside the hangar tunnels.

Status
  Pass 2 detailing done on every module, then a hangar-logistics and belly
  round (level hangars, transfer to the central hold, the underside). Top
  silhouette IoU 0.974. Awaiting owner review (pass 3).
  Known: WS 002/003's arc sweep grazes the belt arm run at inboard train
  angles (rest angle clear); the belt arm run crosses the well shafts; two
  mast pods touch the forward mouth frames; the forward booth glazing is
  over-bright (emissive bridge glazing).
  Renders are build outputs and are not committed; rebuild to see them.
