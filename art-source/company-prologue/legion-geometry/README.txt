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
  lg_hull.py     fore and aft body, dorsal and tail spines, command dome,
                 bow blades, outer and inner armour belts
  lg_wings.py    the two sponsons and their four flight decks
  lg_engines.py  engine block, outer housings, the ten drive units
  lg_turrets.py  sixteen turrets: fixed barbette + trainable part on a pivot

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship
  centre, so every slot location is model XY unchanged.
  metres_per_unit = 0.65 (the shared Second Wind scale the cinematic uses):
  about 192 m long, 191 m beam, 33 m keel to crown.

Object names (stable identifiers; later passes key off them)
  Hull ...                  fore body, aft body, dorsal spine, tail spine,
                            engine block, port/starboard engine housing
  Bridge command dome
  Armour <side> bow blade / outer belt / inner belt
  Wing <side> sponson
  Flight deck <slot> deck / red light <k> / hood (forward decks only)
  Flight deck <slot> mouth, Flight deck <slot> launch point 1-4   (empties)
  Engine <i> drive can / aft-facing bell / mouth lip / dark throat
  Engine <i> exhaust anchor                                        (empty)
  Weapon <slot> barbette (fixed)
  Weapon <slot> pivot (empty, rests at the slot angle) with children
      Weapon <slot> turret house / turret shield / barrel <k>, or
      Weapon <slot> launcher house / launcher cell face
  Engines are numbered in legion.ship order (1-4 the small outer pairs,
  5/6 the 24-wide mains, 7/8 the 16-wide, 9/10 beside the tail spike).

Flight decks (geometry.json 'flight_decks')
  WS 012 port forward, WS 013 starboard forward: open above X 46-80, then
  under a hood to a mouth through the angled leading edge, facing forward
  and outboard (normal +-0.83/0.56). Slot angle +-10.
  WS 010 port aft, WS 011 starboard aft: open above X -64 to -14, mouth
  facing aft onto the notch behind the sponson. Slot angle 170/190.
  Each record gives launch_direction (from the slot angle), the four
  launch points at deck height (the .ship's own), the mouth centre, normal,
  width and height, and the deck extent. Fighter cards register to these.

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
  bodies, spines, dome and sponsons) about 1.37 M cubic units. At the
  skill's 55% usable that solves to 0.57 m/unit (0.54-0.61 for 65-45%).
  0.65 implies about 37% usable, a plausible share for an armour-1650 line
  carrier with sixteen barbettes and their magazines, but outside the
  skill's freighter-derived range. 0.65 is kept and the mismatch reported.

Status
  Pass 1 blockout + pass 4 scale. Top silhouette IoU 0.974.
  reference-history/v1-blockout holds the accepted blockout's close-ups.
