SHEPHERD (MORROW) - CINEMATIC GEOMETRY (PASS 2 DETAIL + PASS 4 SCALE; OWNER REVIEW NEXT)

Purpose
An editable Blender model of the vanilla Shepherd drone tender, built for the
campaign's Shepherd Recovery arc, where this hull is Morrow: grounded on
Basin's service cradle beside the Colossus wreck, her drone bay woken and
repaired, then flown out. It follows the ship-model-pipeline skill
(.claude/skills/ship-model-pipeline) and the Second Wind's conventions
(../second-wind-geometry/, ../second-wind-pipeline.md) but shares no code
with any other hull: sh_kit.py holds copies of the helpers it needs.
Pass 1 fixed the silhouette, sections, mass and slots. Pass 2 added the detail
the sprite and lore imply, after two owner notes: the port drone bay is an
open work bay exposed out of the hull, and the ship has real verticality
(fairly thick: about 40 units keel to mast, 31.5 m). Pass 4 set the scale.
No damage, condition states or surfacing. Reference renders (ignored, kept
on disk): reference-history/v1-blockout/ (the first, flat blockout) and
v1b-thick-blockout/ (the thickened blockout the detail pass started from,
rendered with the current cameras); compose_review.py writes
pass2-before-after.png against the latter.

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
The port third of the hull (Y 11..27) is an open work bay recessed into two
hangar tubs end to end, closed forward by the workshop service block: a
sunken deck (z -5) with transfer rails, a cyan-lit inboard back wall with
servicing arms, cable trays and tool racks, arch ribs over the open top and
an outboard coaming. Each WS 003 location is a launch station:
'Drone bay WS 003 opening <i> ...' is its deck plate, hazard kerb, launch
cradle, charging contact and umbilical; 'Drone bay WS 003 opening <i> mouth'
sits at the top of the bay above it (normal +Z) and 'Drone bay WS 003 launch
point <i>' on the cradle. The aft bay's aft wall is the crossbeam. geometry.json
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
details (adds the underside view).

Detail by area (pass 2; one Opus subagent per module - Sol 6.1 was not
offered in this harness)
  Hull     segmented armour modules, seams and welds, dorsal hatches and an
           access trunk, two airlocks (turret deck; bow ladder), the spine
           walkway and conduits, the machinery well with intake stacks and a
           reactor hatch, stern red/green lamps; ventral keel skids and eight
           'Hull keel cradle shoe <i>' she rests on when grounded, a ventral
           cargo hatch and a survey blister.
  Bridge   raked framed glazing under a brow visor, side and aft windows,
           roof hatch and rails, an antenna mast, running lamps; the habitat
           shell with ribs, viewport rows and an observation strip; the
           lattice sensor boom; the survey head (radome, lidar, dish).
  Gantry   lattice towers and girders, a travelling bridge crane with hoist
           and hook, two welding arms, fabricator, compressors, tool racks,
           cable reels, floodlights, an operator cab; lattice turret deck
           and starboard outrigger.
  Engines  banded drive pods with saddle bands, thrust yokes, radiator wells
           and the sprite's hatch, coolant trunk and hazard placards; bells
           with liner, throat and injector rings, gimbal ring and actuators;
           four jet blocks with verniers.
  Mounts   WS 007 naval turret (barbette, chamfered gun house, trunnions,
           twin barrels with jackets and muzzle brakes, sight, ready box and
           feed), lifted 1 unit so the barrels clear the crossbeam through
           the whole arc; WS 008 armoured universal hardpoint on the bed.
Checks: check_clashes.py (cross-module overlaps: the remaining hits are
deliberate embedding) and check_turret_arc.py (WS 007 at rest, both arc
limits and every 10 deg; no clashes), both run inside Blender after a build:
  blender --background --factory-startup --python build_shepherd.py       --python check_turret_arc.py -- --no-save --views ""

Scale (pass 4): 0.78 m per unit
METRES_PER_UNIT in build_shepherd.py and metres_per_unit in geometry.json;
every metric value reads it. About 67 m long, 55 m across, 31.5 m keel to
mast (the hull 20 m keel to deck). Derived from what she carries: the mod's
deck program for a FRIGATE "Drone Tender" with 15-30 crew and 100 cargo
(DeckProgramProbe; the designation resolves to the WARSHIP role) is 2,331 m2
of rooms; its notional 160 m2 HANGAR is dropped because the open bay is sized
from the Borer wing instead, leaving 2,171 m2 x1.40 circulation x3.2 m =
9,726 m3. The pressure hull (crew body, spine, machinery, bridge cab, habitat
shell and the two drive-pod casings, measured as a union on every build) is
about 45,900 cubic units. The r450 gives "internal volume to crew comfort",
so she is solved at the roomy 45% usable: 0.78. Range 0.69 (65% usable),
0.73 (a freighter's 55%), 0.84 (pods hold no crew space). Cross-checks: a
1.8 m person is 2.3 units; the 1.35 x 2.4-unit crew doors are 1.05 x 1.87 m;
the open bay deck is 27.8 x 9.4 m with 9.9 m to the crown (about 2,700 m3
against the ~570 m3 four Borers and a spare need on servicing cradles); each
launch station clears 6.6 m against an assumed 4.4 x 4.0 x 1.8 m Borer (no
Borer model exists yet). This is not the battle-map figure: there Morrow
blocks 24 x 20 cells (landed-ships-true-scale.md, shepherd-recovery.md),
0.261 m per unit, a tactical-readability convention for one-cell infantry.

Silhouette: top-view IoU 0.935 against drone_tender.png (0.932 at pass 1).
