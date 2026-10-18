HERMES - CINEMATIC GEOMETRY (PASSES 1-4 DONE; PASS 3 ACCEPTED)

Purpose
The company's boat. In the campaign the Second Wind comes with two wrecked
Hermes below decks; the company patches the first at the Boat Deck ("a
patched courier, four seats at standard fit, no upgrade path") and flies it
for its first contracts until Aeroshuttles replace it. ShipsBoats also makes
the Hermes the gig of any hull without a ground force. This package is a
scripted Blender model of it for cinematics, beside and inside the Second
Wind's bay, built by the ship-model-pipeline skill
(.claude/skills/ship-model-pipeline/SKILL.md):
  pass 1 blockout             snapshot in reference-history/v1-blockout/
  pass 2 plausible detailing  built here (one Opus agent per module)
  pass 3 owner review         accepted after the rework (see Pass 3)
  pass 4 real-world scale     0.11 m per unit (see Scale)
Clean state only. No damage states, textures or VFX. Source art only; it
changes no runtime art or gameplay.

Rebuild
  blender --background --factory-startup --python build_hermes.py
  python compose_review.py                       sprite overlay, IoU, sheets
  blender --background ../second-wind-geometry/second-wind-geometry.blend \
      --python bay_fit.py                        Second Wind bay fit (never saves her blend)
  blender --background hermes-geometry.blend --python check_clashes.py
Driver flags: --out --views --samples --no-save --cam NAME:P:T:SCALE
  --gear up|down, --doors-open DEG|full, --figure (1.8 m person outside the
  port boarding door; never saved), --state clean (wrecked/patched reserved).
  HERMES_HULL_DETAILS=0 builds the hull's shape without its pass-2 detail.
All views render the SAME mesh objects at 1920x1080; only the camera moves.

Hull identity (read from the read-only game install, 0.98a-RC8)
hullId hermes, "Hermes", FRIGATE, MIDLINE, designation Shuttle.
  Source: data/hulls/hermes.ship and its ship_data.csv row.
Sprite graphics/ships/hermes/hermes_base.png: 56x82, .ship centre 28,39
  from the bottom-left. Lights: hermes_lights.png (nose glazing band and
  four nose lamps only). hermes_base_old.png and hermes_substandard.png are
  alternative skins of the same outline; not modelled.
Crew 2-10, cargo 50, fuel 15. Built-in hullmod civgrade; system
  maneuveringjets; no built-in weapons. Codex: a small, dependable cargo
  hauler converted for transport duty among poor frontier worlds; crew and
  passenger facilities "charitably described as robust".
In this mod: ShuttleType.HERMES flies one fire team (capacity 4 =
  Squad.FIRE_TEAM_SIZE), one hardpoint, NIMBLE handling. BoatFitting refuses
  every fitting for the Hermes, so it stays a four-seat boat (the six-seat
  boat is the Aeroshuttle). The model is sized for two crew and four seats.

Coordinates
+X bow, +Y port, +Z dorsal. 1 unit = 1 sprite pixel about the .ship
centre: sprite pixel (c, r) from the top-left covers X 42-r..43-r and
Y 27-c..28-c. XY comes from the game; every Z is art interpretation.

Slots (exactly hermes.ship)
  WS 001  TURRET SMALL BALLISTIC (12, 18)   angle 45   arc 150  -> Turret 1
  WS 002  TURRET SMALL BALLISTIC (12, -18)  angle -45  arc 150  -> Turret 2
  WS 003  TURRET SMALL BALLISTIC (-18, 0)   angle 180  arc 210  -> Turret 3
  WS 004  HIDDEN SMALL SYSTEM    (-40, 0)   angle 180  arc 30   -> empty marker
  Engines 1-3 main drives at (-39, 10 / 0 / -10), width 8, angle 180;
  Engines 4-5 maneuvering jets at (-19, +-23), width 6, angles 150 / 210.
  Names: 'Engine {i} aft-facing bell / mouth lip / dark throat' and an
  'Engine {i} exhaust anchor' empty at each mouth, as on the Second Wind.
  The mod's battle model gives the Hermes one hardpoint; the model keeps
  all three of the hull's mounts, because a mount is never invented or
  dropped.

Modules (one per area, so parallel agents never share a file)
  hm_kit.py      materials, box/cylinder/mesh, prism/section/facet, surface
                 ray casts, bx/blocks, pivots (copied, never imported)
  hm_hull.py     spine (cockpit + troop bay) and its fairing, gun sponsons,
                 aft drive block
  hm_cockpit.py  glazed nose and lamps
  hm_engines.py  three main drives, two maneuvering jets
  hm_turrets.py  three small ballistic turrets
  hm_ventral.py  troop bay, boarding doors, tricycle gear, underside

Moving parts (built closed / extended; poses by rotating the named empty)
  Boarding doors  'Hull port/starboard boarding door hinge' (open_axis Z,
                  open_sign, open_angle_deg 100): the leaf swings out and aft
  Landing gear    'Gear nose hinge', 'Gear port main hinge',
                  'Gear starboard main hinge' (retract_axis,
                  retract_angle_deg); --gear up stows them, and each well's
                  two doors ('Gear <leg> port/starboard door hinge') close
                  by Blender drivers on the leg's rotation
  Every hinge empty and its properties are listed in geometry.json
  (moving_parts.pivots).

Scale (pass 4)
One real-world scale: 0.11 m per unit (METRES_PER_UNIT in build_hermes.py,
metres_per_unit in geometry.json). Every metric value derives from it. She
is about 9.0 m long, 5.7 m across the gun sponsons and 3.0 m tall on her
short gear. A 1.8 m person is 16.4 units.
The mod's deck program does not apply: DeckSizing.programFor reads any
FRIGATE as a ship with bridge, engine room, mess and a boat bay of its own
(10,013 m3 for crew 2-10, cargo 50; about 60 m long). In this mod the
Hermes is a boat that lives inside another ship's 16 x 10 m BOAT_BAY, so it
is sized from its own crewed program (one cell a metre, as the deck
program):
  flight deck, two seats abreast       2.0 x 2.0 m x 1.6 m     6.4 m3
  troop bay, four armoured seats       2.0 x 2.4 m x 1.9 m     9.1 m3
  stowage at the bay head                                       1.2 m3
  passage, flight deck to bay          0.8 x 1.2 m x 1.9 m     1.8 m3
  total                                                        18.5 m3
Three checks, all re-measured by the driver on every build (scale_record):
  - volume: the union of the spine (with its fairing), the gun sponsons and
    the aft block measures about 54,400 cubic units; at a 25% / 20% / 15%
    crewed share (cramped / nominal / roomy small craft; the rest is drives,
    tanks, gear wells, magazines and armour) the solved scale is
    0.111 / 0.119 / 0.131;
  - section: the spine's interior gives the troop bay 17 units of headroom
    and 20 units between the benches' backs; 1.9 m and 2.0 m need 0.112 and
    0.10 (the modelled bay has 17.5 units, 1.93 m);
  - the Second Wind's port bay (below) caps it at about 0.112.
0.11 satisfies the section and the bay. At 0.11 the crew space is about 26%
of the envelope: a cramped boat, which the codex's "robust" facilities
suggests anyway. Plausible range 0.10 (width-limited) to 0.112 (bay-limited).

Second Wind bay fit
bay_fit.py stands the Hermes in the Second Wind's port bay at both hulls'
own scales (0.11 and 0.65 m/u), measuring the bay from her named bay
objects; it never saves her blend. Numbers in geometry.json
(second_wind_bay_fit) and on hermes-bay-fit.reference.png:
  bay clear 14.8 m long x 7.9 m deep x 5.6 m high; Hermes 9.0 x 5.7 x 3.0 m
  on her gear. The Second Wind's port bay was deepened inboard for her (back
  wall SW y 15.55 -> 12.4, 2026-10-18). She stands broadside, bow forward, on
  her own gear between the two empty cradle beams, centred between the back
  wall and the door line: 2.9 m clear at each end, 2.6 m overhead, 0.45 m at
  the wall and 0.45 m inside a door on the starboard bay's line (inner face
  SW y 22.6, 6.6 m of depth), so she fits with a door closed (1.1 m more to
  the open mouth). The beam is still the binding dimension.

Owner decisions open
  - The troop bay's forward hatch (X ~19.5) meets solid hull; the flight
    deck's aft bulkhead is at X 30.7. A crawl passage between them, over the
    nose-gear well, is not modelled yet.

Condition states (reserved, not built)
The campaign needs the Hermes wrecked (as found below decks) and patched
(the courier the company flies). They will follow second-wind-pipeline.md's
contract: --state wrecked|patched builds the clean model plus a scar table
of 'Scar H<nn> ...' objects with stable ids and per-state status; damage
never renames, replaces or deletes base objects. Only 'clean' builds today.

Review images (1920x1080; outputs, never committed - regenerate them)
  hermes-geometry.reference.png   top over sprite with IoU, port side, bow,
                                  port quarter (compose_review.py)
  hermes-bay-fit.reference.png    size and fit against the Second Wind's bay
                                  (bay_fit.py, then compose_review.py)
  pass2-before-after.png          six close-ups, blockout against detailed
  renders/                        standard views and close-ups;
                                  renders/gear-up/ (--gear up),
                                  renders/doors-open/ (--doors-open full --figure)
  reference-history/v1-blockout/  the accepted blockout: rebuild commit
                                  624cd3ea8 with --out to regenerate it

Pass 2 summary (IoU 0.969 -> 0.966; each module within its 0.002 budget)
  hull     plate modules and seams, louvres, the sprite's cream horseshoe
           arms round the aft turret, grey-blue shoulder blocks, red side
           hatches, service covers, tie-downs
  cockpit  six-pane framed windscreen on the lights layer's crescent, a
           flight deck with two seats and a console, roof escape hatch,
           radome, pitots, nose cap
  engines  whole drive units on a bolted stern thrust plate: shroud cans,
           gimbals, actuators, banded bells; canted jet blocks in pockets
  turrets  naval small mounts in dark ring wells (barbette, collar, gun
           house, mantlet, twin jacketed guns, feed boxes, sight)
  ventral  lined troop bay with four harnessed seats, lockers and a litter;
           longer ramp with curbs, treads and rams (replaced in pass 3);
           detailed tricycle gear with doors; belly strakes, vents, antennas
  check_clashes.py: every cross-module hit is a part sunk into a base hull
  solid, the jet housings buried in the shoulder blocks, or the aft arms'
  roots trimmed flush to the troop-bay lining.

Pass 3 (owner review, accepted 2026-10-18; IoU 0.966 -> 0.956)
  stance   short, stubby gear: the keel sits 0.33 m off the ground and every
           block shares the spine's keel, so the boat sits flat
  entrance the ventral ramp is gone; a boarding door on each flank amidships
           (the sprite's notch, X -3..4) in a shallow recess in the fairing's
           raked flank, with a passage inboard to the troop bay's aft end
  shape    read again off the sprite: the gun sponsons are flat-decked grey
           octagons over raked walls; aft of X 10 the spine's cream top flares
           into one crowned fairing rolling down to the wing edge (it is part
           of the spine, not a separate block); the drive block is unchanged
  detail   pass-2 hull detail refitted: the old grey-blue shoulder blocks and
           side hatches are gone, the sprite's grey-blue vents sit in pockets
           in the fairing deck, the sponsons keep seams and covers
  check_clashes.py: the remaining hull/ventral hits are flush contact (the
  bay and passage liners against the walls they line).
