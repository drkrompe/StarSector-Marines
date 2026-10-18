HERMES - CINEMATIC GEOMETRY (PASSES 1, 2 AND 4; AWAITING OWNER REVIEW)

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
  pass 3 owner review         next
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
  --gear up|down, --ramp-open DEG|contact, --figure (1.8 m person at the
  ramp toe; never saved), --state clean (wrecked/patched reserved).
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
  hm_hull.py     spine (cockpit + troop bay), wing body, aft drive block
  hm_cockpit.py  glazed nose and lamps
  hm_engines.py  three main drives, two maneuvering jets
  hm_turrets.py  three small ballistic turrets
  hm_ventral.py  troop ramp, troop bay, tricycle gear, underside

Moving parts (built closed / extended; poses by rotating the named empty)
  Troop ramp      'Hull ventral ramp hinge'   (custom props: open_axis,
                  open_sign, contact_angle_deg)
  Landing gear    'Gear nose hinge', 'Gear port main hinge',
                  'Gear starboard main hinge' (retract_axis,
                  retract_angle_deg); --gear up stows them
  Others are listed in geometry.json under moving_parts.

Scale (pass 4)
One real-world scale: 0.11 m per unit (METRES_PER_UNIT in build_hermes.py,
metres_per_unit in geometry.json). Every metric value derives from it. She
is about 9.0 m long, 5.7 m across the gun sponsons and 3.5 m tall on her
gear. A 1.8 m person is 16.4 units.
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
  - volume: the union of spine, wing body and aft block measures about
    46,000 cubic units; at a 25% / 20% / 15% crewed share (cramped / nominal
    / roomy small craft; the rest is drives, tanks, gear wells, magazines and
    armour) the solved scale is 0.117 / 0.126 / 0.139;
  - section: the spine's interior gives the troop bay 17 units of headroom
    and 20 units between the benches' backs; 1.9 m and 2.0 m need 0.112 and
    0.10;
  - the Second Wind's port bay (below) caps it at about 0.112.
0.11 satisfies the section and the bay. At 0.11 the crew space is about 30%
of the envelope: a cramped boat, which the codex's "robust" facilities
suggests anyway. Plausible range 0.10 (width-limited) to 0.112 (bay-limited).

Second Wind bay fit
bay_fit.py stands the Hermes in the Second Wind's port bay at both hulls'
own scales (0.11 and 0.65 m/u) and measures the bay from her named bay
objects. Results are in geometry.json (second_wind_bay_fit) and
hermes-bay-fit.reference.png. See the sheet for the current numbers.

Condition states (reserved, not built)
The campaign needs the Hermes wrecked (as found below decks) and patched
(the courier the company flies). They will follow second-wind-pipeline.md's
contract: --state wrecked|patched builds the clean model plus a scar table
of 'Scar H<nn> ...' objects with stable ids and per-state status; damage
never renames, replaces or deletes base objects. Only 'clean' builds today.

Review images (1920x1080)
  hermes-geometry.reference.png   top over sprite with IoU, port side, bow,
                                  port quarter
  hermes-bay-fit.reference.png    size and fit against the Second Wind's bay
  renders/                        every standard view, close-ups, the ramp
                                  and gear poses, a 1.8 m figure
  pass2-before-after.png          close-ups, blockout against detailed
  reference-history/v1-blockout/  the accepted blockout
