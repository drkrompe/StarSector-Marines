SHEPHERD (MORROW) - CINEMATIC GEOMETRY (PASSES 1-4 DONE; PASS 3 ACCEPTED 2026-10-18; PASS 5 DEFERRED; PASS 6 IN REVIEW)

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
Pass 3 (owner review of pass 2) fixed what read wrong against the sprite:
  - bow: the central body's nose is a superellipse in plan with tangent
    facets over and under it, and the section has two shoulder facets;
  - repair gantry: the sprite's starboard bow is a long rounded machinery
    drum, not a cage, so the lattice frame, travelling crane, floodlights and
    top-deck plant are gone; the core is that drum on cradle saddles over a
    rounded base deck, the WS 008 bed a round pedestal on its crown, and
    the outrigger's open bracing is now a dark armour skirt;
  - drone bay: one continuous open deck with no arch ribs, no bulkhead
    between the tubs and only a low kerb outboard; the port outrigger sits
    under that kerb. Round 3: the sprite's rounding and lip - the back wall
    sweeps round a rounded aft-inboard corner (R 3.5), across the aft end
    on the crossbeam's face and round a smaller outboard corner (R 2) into
    the kerb, one swept rim with a cove fillet at the deck and a tan
    bullnose lip on top; the grey kerb cap and its hazard bars are gone;
  - bridge: the sprite's centre is one long curved bridge hull (X 4..35.6,
    Y -10..+5), lit cream on its starboard crown; it replaces pass 2's
    invented cab and ribbed habitat pod. Round 2: a flat crown and full
    flanks, then a steep raked windscreen (X 27.4..30, the sprite's dark
    band) of framed forward-facing panes under a brow visor, wing windows
    either side, and a chin rounding down into the bow. Round 3: the chin
    is drawn down onto the bow's own skin and lands on it on a tangent,
    flanks first, so no flat underside or lip stands off the bow; the
    survey head stands on a chamfered plinth whose foot follows the skin,
    with a welded base flange and a gusset under its cantilever, in place
    of a round column on a flat flange tilted on the curve;
  - WS 008: kept, recessed into a well in the drum's forward crown with a
    barrel trough, and given finer mount detail (traverse race, trunnions,
    elevation rams, blast boot, ported brake, sight head, feed conduit).
    Round 2: the well is finished as a weapon pocket - a continuous bolted
    armour coaming laid on the skin, a lined trough with a ribbed blast
    plate under the muzzle - with no hazard paint (no crew on the crown,
    no crush zone on a +-10 deg hardpoint).
reference-history/v2-pass2-detail/ holds the pass-2 renders it started from.
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
The port third of the hull (Y 11..27) is an open work bay: one deck cut
through two hangar tubs end to end, closed forward by the workshop service
block and open above and outboard. It has a sunken deck (z -5) with
transfer rails, a cyan-lit inboard back wall with servicing arms, cable
trays and tool racks, and only a low kerb and handrail outboard. Each WS 003 location is a launch station:
'Drone bay WS 003 opening <i> ...' is its deck plate, hazard kerb, launch
cradle, charging contact and umbilical; 'Drone bay WS 003 opening <i> mouth'
sits at the top of the bay above it (normal +Z) and 'Drone bay WS 003 launch
point <i>' on the cradle. The deck's aft end is the crossbeam. geometry.json
'drone_bay' exports the openings, the launch points and their anchors, the
launch direction (+Z, out of the well) and the departure heading (the slot
angle, forward), the way the Legion exports its flight decks.

Object names (stable identifiers; damage, surfacing and VFX key off them)
  Hull ...       central crew body, port spine, aft crossbeam, machinery
  Bridge ...     bridge hull, pale crown plating, glazing band, roof fittings
  Mast ...       forward sensor boom, starboard sensor head
  Drone bay ...  hangar pods, service block, WS 003 openings, launch points
  Gantry ...     starboard repair gantry: drum core, base deck, hardpoint bed, rails, cab
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
  Bridge   (pass 3: one long curved bridge hull, see above) pale starboard
           crown plating, plating seams, a wraparound glazing band with
           mullions under a brow lip, flank viewports, roof hatch, air
           handler, antenna mast, running lamps; the lattice sensor boom;
           the survey head (radome, lidar, dish).
  Gantry   (pass 3: replaced by the machinery drum, see above) frame
           bands, crown plate and hatches, outboard covers and grilles, an
           aft manifold and piping, an operator cab; lattice turret deck
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
mast (the hull 20 m keel to deck). She is on the shared sizing basis
(../ship_scale.py, option C, ../ship-scale.md): contents of 30 crew at
300 m3, 100 cargo and 40 fuel over the frigate machinery share (46%),
against the enclosed volume of ship_scale.SOLIDS['shepherd'] (crew body,
spine, machinery, bridge hull, the drone-bay tubs and the workshop block;
42,356 cubic units, re-measured on every build and checked against the
shared table). The drone bay is open to space, so its air is not enclosed
and no hangar term is charged. That solves 0.74 (range 0.72-0.76, 64 m).
The published figure stays 0.78, her pass-4 solve from the deck program at
45% usable for crew comfort, by the owner's choice (2026-10-18);
ship_scale.PUBLISHED carries it. Cross-checks at 0.78: a 1.8 m person is
2.3 units; the 1.35 x 2.4-unit crew doors are 1.05 x 1.87 m; the open bay
deck is 30.8 x 9.4 m with 9.9 m to the crown (about 2,840 m3 against the
~570 m3 four Borers and a spare need on servicing cradles); each launch
station clears 6.6 m against an assumed 4.4 x 4.0 x 1.8 m Borer (no Borer
model exists yet). This is not the battle-map figure: there Morrow
blocks 24 x 20 cells (landed-ships-true-scale.md, shepherd-recovery.md),
0.261 m per unit, a tactical-readability convention for one-cell infantry.

Silhouette: top-view IoU 0.936 against drone_tender.png (0.932 at pass 1,
0.935 at pass 2).

PASS 6 (2026-10-18)
Pass 5 (condition states) is deferred by the owner: texture first. Surfacing
lives in ../shepherd-surface/ (see its README). Owner, on the round 1 renders:
the command deck windows looked wonky with the ribbon angling. The windscreen
was laid out at constant section angle, so on a nose that narrows forward its
mullions fanned and twisted and the band wrapped down the shoulders. It is now
six flat panes and their mullions at constant plan Y, vertical from ahead,
stopping at the shoulders. Owner, second look: still a rainbow from the front;
it must be horizontal. The head, sill, brow visor and sill rail are now level
lines too: the glazing is laid out in height and plan Y and each point found
on the rake (sh_bridge._yz), so from ahead the windscreen is a level rectangle
and on the rounded nose its head simply sits further aft at the shoulders. The wing windows carry the flanks.
build_shepherd.py renders on the GPU when there is one (--factory-startup
leaves Cycles on the CPU).
