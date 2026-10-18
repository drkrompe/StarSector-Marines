SHEPHERD (MORROW) - CINEMATIC GEOMETRY (PASS 1: BLOCKOUT)

Purpose
An editable Blender model of the vanilla Shepherd drone tender, built for the
campaign's Shepherd Recovery arc, where this hull is Morrow: grounded on
Basin's service cradle beside the Colossus wreck, her drone bay woken and
repaired, then flown out. It follows the ship-model-pipeline skill
(.claude/skills/ship-model-pipeline) and the Second Wind's conventions
(../second-wind-geometry/, ../second-wind-pipeline.md) but shares no code
with any other hull: sh_kit.py holds copies of the helpers it needs.
Pass 1 fixed the silhouette, sections, mass and slots; it is kept in
reference-history/v1-blockout/. No damage, condition states or surfacing.

Hull identity (read-only install, starsector-core)
data/hulls/shepherd.ship (hullId shepherd; the .ship and hullId agree),
ship_data.csv row "shepherd": Drone Tender, Low Tech, FRIGATE, crew 15-30,
cargo 100, fuel 40, mass 200, OMNI shield, system maneuveringjets. Built-ins:
civgrade, surveying_equipment, repair_gantry; built-in wing borer_wing (four
Borer drones). The description calls her the Ko Combine r450, with "internal
volume given to crew comfort". Sprite graphics/ships/drone_tender.png, 78x92,
.ship centre (39, 45). There is no lights layer for this hull in the install.

Coordinates
+X = bow, +Y = port, +Z = dorsal. One sprite pixel is one unit about the .ship
centre, so slot and engine XY from shepherd.ship are used unchanged. Sprite
column c, row r covers X 46-r..47-r and Y 38-c..39-c. Heights are proposed
art; the game gives none. The build checks every slot, engine, the centre and
the built-in wing against the installed shepherd.ship and stops on a mismatch.

Slots (exactly as shepherd.ship)
  WS 007  TURRET    SMALL BALLISTIC  (-3, -23)   angle -75, arc 200
          -> 'Turret 1 WS 007 ...', trained to -75 above the traverse gap
  WS 008  HARDPOINT SMALL UNIVERSAL  (27, -19)   angle 0, arc 20
          -> 'Hardpoint 1 WS 008 ...', in the repair gantry's forward bed
  WS 003  HIDDEN    SMALL LAUNCH_BAY (-3,19) (12,19) (26,19) angle 0, arc 5
          -> the drone bay: three dorsal openings, one per launch location
Each mount has '<prefix> pivot' and '<prefix> muzzle' empties.

Engines (shepherd.ship order; names as on the Second Wind)
  1 (-40, 20) and 2 (-40, -19), width 16, angle 180: the two main drives,
    each a whole burnt-orange drive pod on the aft crossbeam.
  3 (-22, 35) 140, 4 (-30, 33) 145, 5 (-22, -33) 220, 6 (-30, -32) 215:
    small maneuvering jets on housings at the crossbeam tips.
  Every engine: 'Engine <i> aft-facing bell', 'Engine <i> mouth lip',
  'Engine <i> dark throat' and 'Engine <i> exhaust anchor' (an empty on the
  mouth; its +X is the exhaust direction, also in geometry.json).

Drone bay
The port third of the hull (Y 11..27) is two hangar pods end to end and a
forward service block. 'Drone bay WS 003 opening <i> ...' is a dorsal well
with a coaming; 'Drone bay WS 003 opening <i> mouth' sits on the rim (normal
+Z) and 'Drone bay WS 003 launch point <i>' on the well floor. geometry.json
'drone_bay' exports the openings, the launch points and their anchors, the
launch direction (+Z, out of the well) and the departure heading (the slot
angle, forward), the way the Legion exports its flight decks.

Object names (stable identifiers; damage, surfacing and VFX key off them)
  Hull ...       central crew body, port spine, aft crossbeam, machinery
  Bridge ...     bridge cab, glazing band, habitat shell
  Mast ...       forward sensor boom, starboard sensor head
  Drone bay ...  hangar pods, service block, WS 003 openings, launch points
  Gantry ...     starboard repair gantry: frame, core, base deck, crane
  Frame ...      port and starboard outriggers, starboard turret deck
  Engine <i> ... drive pods, pylons, jet housings, bells
  Turret 1 WS 007 ... / Hardpoint 1 WS 008 ...

Condition states (named, not built)
Morrow appears grounded and damaged on the cradle (Salvage Rights, Cold
Start) and recovered (Working Passage onward). Those will be built as
second-wind-pipeline.md's layers: this clean model stays the base; a state
adds 'Scar <id> ...' objects and boolean cuts from a scar table with stable
ids and a per-state status (open -> patched -> replaced -> faded/gone);
wear is masks. Damage never renames, replaces or deletes a base object.
The driver reserves '--state'; geometry.json 'condition_states' records
what is planned and that nothing is built.

Build
  sh ../../blender.sh --background \
      --factory-startup --python build_shepherd.py -- [--out DIR] [--views ...]
  python compose_review.py [--dir DIR]
The first writes the renders, shepherd-geometry.blend and geometry.json; the
second writes top-overlay.png, the silhouette IoU (into geometry.json) and
the 1920x1080 sheet shepherd-geometry.reference.png. Close-ups: --views
details. check_clashes.py lists cross-module overlaps inside Blender.

Silhouette: top-view IoU 0.932 against drone_tender.png.
