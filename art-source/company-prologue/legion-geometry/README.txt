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
  "C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background ^
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
  lg_wings.py    the two wing arms and the four hangars slung under them
  lg_droop.py    the arms' anhedral: a shear applied after lg_wings builds
  lg_engines.py  engine block, outer housings, the ten drive units
  lg_fittings.py masts, sensors, radomes and the running lights
  lg_turrets.py  fifteen turrets: fixed barbette + trainable part on a pivot

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship
  centre, so every slot location is model XY unchanged.
  metres_per_unit = 0.61, derived for this hull (see Scale check): about
  179.9 m long, 179.3 m beam, 46.0 m keel to crown.

Object names (stable identifiers; later passes key off them)
  Hull ...                  fore body, aft body, ventral keel, dorsal spine,
                            tail spine, engine block, port/starboard engine
                            housing (and their pass-2 detail parts)
  Bridge command dome
  Armour <side> bow blade / outer belt / outer belt arm run / inner belt
  Wing <side> sponson
  Flight deck <slot> hangar / deck / red light <k>
  Flight deck <slot> mouth, Flight deck <slot> launch point 1-4   (empties)
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

Hangars (geometry.json 'flight_decks')
  The owner's first review asked for a thicker torso with wing-like arms and
  the hangar bays under the wings, then for the arms to taper and angle down.
  Each wing arm (deck z 4 at the root, a 6-unit step below the torso's z 10)
  thins outboard and angles down at 10 degrees from the torso wall (|Y| 80);
  it carries two hangar pods slung beneath it, which go down with it. The
  sprite's red-lit recesses are elevator wells through the arm into them.
  WS 012 port forward, WS 013 starboard forward: the hangar runs forward to a
  mouth in the pod's angled front under the arm's leading edge, facing
  forward and outboard (normal +-0.83/0.56). Slot angle +-10.
  WS 010 port aft, WS 011 starboard aft: closed at the head, mouth facing aft
  onto the notch behind the arm. Slot angle 170/190.
  Each record gives launch_direction (from the slot angle), the four launch
  points on the hangar floor (the .ship's own XY, heights carried down by the
  anhedral), the mouth centre,
  normal, width and height, and the hangar's extent. Fighter cards leave
  through the mouths.

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
  Habitable share about a third: fighter stowage for the Elite complement
  (6 Broadswords, 4 Hoplons, a spare per wing, servicing room, 25% for
  ordnance handling) measures 24.5% of the pressure hull in model
  units (fighters share the carrier's pixel scale, so this holds at any
  metres scale); armour 15%, reactors/flux/drives 12%, barbettes and
  magazines 5%, tanks and structure 10% are judged. Budget habitable share
  33.5%; published at 33%.
  Pressure hull (union of torso, spines, dome, arms and hangar pods) about
  1.72 M cubic units, re-measured on every build: solves to
  0.609 m/unit (0.597-0.629 for 35-30% habitable; a freighter's 55%
  would give 0.514). Published 0.61.
  Real-world check: 183 m long and 179 m wide, 391k m3 enclosed for 700-1500 crew; about 261 m3 per
  person at max crew. A Nimitz is 333 m with about 5,000 crew; fleet and
  light carriers run 200-260 m. The Legion is shorter than those but as wide
  as it is long, so it encloses supercarrier-class volume for under a third
  of a Nimitz's crew. The geometry sets the answer: a deeper torso lowers it.
  Fittings at 0.61: a 1.8 m person is 2.95 units; doors and hatches 3.4,
  rails 1.8. Fighters come out at about 20 m (Broadsword, Piranha) and 22 m
  (Hoplon), and the hangar tunnels are sized in units to clear them.

Status
  Pass 1 blockout, reworked after the owner's first review (deep torso, wing
  arms, under-wing hangars, anhedral). Engines, turrets, bridge and fittings have had
  their pass-2 detail. Hull and wings pass 2 next. Top silhouette IoU 0.975.
  reference-history/v1-blockout is the first, flat blockout;
  reference-history/v2-blockout is the reworked one, and pass 2's
  before/after sheet compares against it.
