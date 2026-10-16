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
  lg_engines.py  engine block, outer housings, the ten drive units
  lg_fittings.py masts, sensors, radomes and the running lights
  lg_turrets.py  fifteen turrets: fixed barbette + trainable part on a pivot

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship
  centre, so every slot location is model XY unchanged.
  metres_per_unit = 0.65 (the shared Second Wind scale the cinematic uses):
  about 192 m long, 191 m beam, 49 m keel to crown.

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
  the hangar bays under the wings. Each wing arm (deck z 4, a 6-unit step
  below the torso's z 10) carries two hangar pods slung beneath it. The
  sprite's red-lit recesses are elevator wells through the arm into them.
  WS 012 port forward, WS 013 starboard forward: the hangar runs forward to a
  mouth in the pod's angled front under the arm's leading edge, facing
  forward and outboard (normal +-0.83/0.56). Slot angle +-10.
  WS 010 port aft, WS 011 starboard aft: closed at the head, mouth facing aft
  onto the notch behind the arm. Slot angle 170/190.
  Each record gives launch_direction (from the slot angle), the four launch
  points on the hangar floor (z -20.5; the .ship's own XY), the mouth centre,
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

Scale check (pass 4; geometry.json 'scale')
  Deck program CAPITAL "Battlecarrier" (CARRIER) crew 700-1500 cargo 300:
  30,827 m2 rooms x1.40 x3.2 m = 138,105 m3. Pressure hull (union of the
  torso, spines, dome, wing arms and hangar pods) about 1.72 M cubic units,
  re-measured on every build. At the skill's 55% usable that solves to
  0.53 m/unit (0.50-0.56 for 65-45%). 0.65 implies about 29% usable. It is
  kept, because it is the shared Second Wind scale that every hull is
  compared at in shot (confirmed by the C2 cinematic session); the gap is
  reported, not hidden.

Status
  Pass 1 blockout, reworked after the owner's first review (deep torso, wing
  arms, under-wing hangars). Engines, turrets, bridge and fittings have had
  their pass-2 detail. Hull and wings pass 2 next. Top silhouette IoU 0.975.
  reference-history/v1-blockout is the first, flat blockout;
  reference-history/v2-blockout is the reworked one, and pass 2's
  before/after sheet compares against it.
