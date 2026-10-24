# Forts — defences you have to take apart

Status: ACTIVE

Written: 2026-10-14

Updated: 2026-10-24 - every fort building but the magazine's store is windowed, a window every third cell along each wall that looks out over open ground, so a squad holding it fires out of every side (owner: "too many buildings are just a door"); the towers either side of each gate are rooms set into the wall and standing proud of it over the approach, slit where they look out of the works and held like any building (`GateTowers`, scene `gate-tower`); fort buildings are kit buildings in the lot's style under the ordinary roof, their bespoke roof art retired (owner picks); earlier: a sally port is barred again behind a sortie, in the face of an approaching enemy and by the player's bar, never on a body (`garrisonables.md`, sally doors); earlier: each gateless face of a fort's works carries a sally port its holder unbars for a sortie (the fort case of `garrisonables.md`, sally doors; scene `sally-port`); earlier: a lost fort is one case of the defender's place counter-attack: probed, sized from what was observed, retaken in proportion or not at all (`PlaceCounterattack`, `FortPlaces`, scene `fort-counterattack`); earlier: the four bastion nests stand mirrored about the works (a pair looks each way) instead of a mix of looks; earlier: prepared positions stand on concrete (a paved pad under and about each bastion nest and missile mount, revetment rings on permanent defence posts), field positions keep their sandbags and earth; earlier: nests carry cannons and machine guns, never flak; earlier: the anti-air aspect stands its missile mounts on the bastions: Locust on the two rear ones, Swarmer on the two front ones (`FortPlan.airTowers`), the control room keeps only its console (no flak, no Ridgewatch); earlier: the magazine berm is one course like every other hardened building; fort rooms wear their own roof art (ante-room annexes, the anti-air control room, an eleven-by-five plant roof, a blast door on the store) from the geometry-first loop; every aspect is housed in a room or small suite laid out by function and its place is the suite's floors, with the garrison seated at the stations (`FortRoom`, `FortRooms`); earlier: earthworks read as works, not stacked outlines: one protective element per thing (the belt is a continuous trench with traverses and nest pits, a hardened building one course of bags, the magazine one single-course berm, the gun pit one revetment with an offset traverse), audited against who ever stood in what; earlier: a bunker outlives its guns: with the platform destroyed it still changes hands, its radar still spots for its holder and commanders are still told of it; earlier: a fort's bastion nests stand in embrasures on the wall's outer face so they fire out of the works (`BastionSites`), and the anti-air aspect's two Ridgewatch batteries stand on the two rear bastions instead of in the yard (`FortPlan.airBatteries`), the yard keeping the console and the flak; earlier: a company that is flown at goes for the radar first (`FortAssault.Execution.radarFirst`); earlier: a fort's sensors are the radar on its fire-control bunker and spot for the side that holds it (the side's air director calls strikes through it, with no marine radio on the map), the anti-air yard leads with two Ridgewatch missile batteries, and a side that is flown at works the hostile bunker first (`FortSensors`, `SkyNeed`); earlier: the outer belt carries a fence line of industrial fence (breakable, with gaps) where it carried wire, and no wire art or fixture remains; earlier: a fort may carry an optional anti-air yard (`FortAspect.AIR_DEFENCE`: console, guided battery and flak; declared per kind, not yet by any mission) that the strike officer weighs and the command plan can open, and ships and gunships were measured against forts (`ship-vs-fort`); earlier: a fort exports its assault approaches (`FortPlan.approaches()`: each gate with the nests that cover it, an entry cell, its approach lane across the belt, and a breach section of wall) and the fort frame rides on the disclosed facts, so a company can be driven through base of fire, entry, objective and hold (`phased-assault.md`); the muster that does it is on by default (owner decision) though it has not beaten the trickle on the Conquest fort fixtures. Earlier: structure work is kept: mech and drone squads (they cannot enter a bunker) and works crews are never offered it, only named-officer groups are excluded (every campaign squad has an acting-NCO group), a squad on a fort task is not drafted into an uncontested compound capture, a maneuver against one contact is bounded to 1800 ticks, and a structure-task squad that closes no ground for 3000 ticks is benched for 9000 (the bunker take has not been completed by a starter company: squads die at 3-8 cells to nests and garrison, which is a strength gap and not a command defect); a fort is disclosed to a company by any of its works (wall, gates, bastion nests) coming into view, not only the platform or generator standing inside the wall, which a company beside the fort never saw; a works crew held by the ambient service is never offered fort work (a lone relief technician held the one take-the-bunker task for 22,900 ticks and never moved). Earlier: a shield fort's dome is sized from what it covers (its works at least, the keep when sited to protect it); every declared fort's ground is reserved and slid off programmed places before roads grow; the axis-only Conquest recipe carries the fort stage and a shortfall is logged; review frames no longer draw empty mission-card panels. Earlier: Conquest sites its declared forts as barriers (`FortSitingPolicy`, `FortSiting`, reserved ground); fort restraint, command awareness, building art by function, shield fort (slice 3); redoubt/citadel remain.

Read `ground-defences.md` first (the battery, the dome, nests, fire control, the placement
law), then the "Authored relationships within a compound" section of `compound-programs.md`,
which is the layout machinery a fort is built from. The local shield relay, which was once its
own story, is the shield fort below.

## What the owner asked for

Templated forts for the ground-defence emplacements: standing works that need ground forces
or overwhelming force to take out, and that can be shut down aspect by aspect.

## The problem with a bare structure

What ships today is a structure standing in a field: a battery with a fire-control radius
around it, a generator with a capture radius around it, nests scattered across open ground.
Each is one thing with one switch, so a battle against it is one decision — walk a squad
onto the switch or shoot the box. There is no approach, no inside, no choice of which part
to break, and nothing a ship and a squad do for each other.

## What a fort is

A **fort** is an authored compound layout built around one defensive purpose, from the same
rotatable-group machinery the maintenance outpost uses. It owns its local geometry — a wall
with gates, a protected interior, buildings with doors and windows — and a mission declares
which forts a battle carries the way it declares structures now. Map generation places what
was declared and connects the gates to the road network; it never decides how many.

A fort is made of **aspects**. An aspect is one function the fort performs, housed in one
place that can be reached, and each aspect can be shut down on its own:

| Aspect | Housed in | Shut down by | What stops |
|---|---|---|---|
| Guns | the battery platform | destroying it | the battery, permanently |
| Fire control | a bunker inside the wall | taking it (capture as today) or wrecking it | aim — the guns fall silent while it is not the holder's |
| Magazine | a hardened store | wrecking it (it goes up) | sustained fire: the guns fire out ready rounds, then stop |
| Power | a plant or capacitor bank | taking or wrecking it | everything that draws on it — the dome, the guns' traverse, fire control's sensors |
| Emitter | the shield generator | destroying it | the dome, permanently |
| Sensors | the radar on the fire-control bunker's roof (not a separate building) | taking the bunker (it then watches for the taker) or wrecking it | the fort spotting for its holder's air; without power the dish does not turn and the fort sees only by eye |

Capture is the existing rule: an aspect belongs to whoever holds its place unopposed, either
side may hold it, and a captured aspect works for its new holder. Nothing is special about
attacker or defender.

## Two ways in

**Ground forces.** Breach a gate or a wall, fight through the interior, and take or wreck
the aspects that matter. A squad that takes fire control silences the guns without
destroying them, and keeps them for its own side. This is the marine's job and the reason a
fort has an inside.

**Overwhelming force.** Ship-to-ground is a super weapon, and a fort is what makes that
cost something. Sustained ship fire and barrage overload the dome; while it is down, the
fort's walls and aspects are open to ship-grade fire, and enough of it wrecks them from
above. It costs time under the battery's envelope, flux, and a ship's hull — so it is a
real alternative, never a free one.

The two help each other. A squad inside cuts the power and the dome drops for the fleet; a
barrage that overloads the dome lets a squad's boats land inside the wall. A fort that can
be cracked only one way is a puzzle with one answer.

## The family

- **Battery fort** — the platform, fire control, a magazine, and power. Nests on the wall.
- **Shield fort** — the generator, power, and a control room: one node of the world's shield,
  covering its own ground. It is the local shield relay this direction absorbed. It is a node,
  not the planet's shield: a fire team does not switch the world-wide envelope off, it opens
  the local tactical footprint to fire support while the rest of the planet stays protected.
  Orbital Barrage is not denied by it; the dome absorbs barrage as ship-grade ordnance. Any
  faction or visual treatment of its buildings is a presentation layer and may not change what
  the fort does.
- **Redoubt** — a walled nest cluster with a small command post; anti-air and anti-infantry,
  no ship-grade guns.
- **Citadel** — a battery and a shield fort sharing one wall and one power plant, so cutting
  the power drops the dome over the guns at the same moment it slows them.

A mission declares forts by kind; a bare structure stays available to scenes, debug, and
missions that want one.

## Defaults (the owner may overturn these)

- **Power is local to a fort.** A citadel shares power because it is one fort; separate forts
  do not feed each other. A world-wide grid is a later choice, not a starting assumption.
- **Walls are breachable** by demolition and by ship-grade fire while the dome is down,
  using the existing wall-health machinery.
- **Aspects do not respawn.** A wrecked aspect stays wrecked for the battle; a taken one can
  be retaken.

## Defence in depth (standing direction)

A fort is never a wall round a pad. It is layered outside in, and a layer that is only a rectangle is a failed layer:

1. **Outer belt.** An irregular ring of continuous trench (traverses across it, nests in sandbag-lined pits), a fence line and nests with firing steps, standing off the main
   works across an open killing ground (the glacis). It is not a box: it kinks and re-enters so no stretch is
   enfiladed end to end, it opens in front of each gate where the approach lane crosses it, and it follows the ground
   it is given: a cell the city or a neighbour holds is simply not drawn. The belt is cover and obstacle by the existing
   semantics (sandbag and trench cells are walkable heavy cover; the fence line is the ordinary industrial fence, closed to
   movement and opened by the same wall-health and cover-damage machinery as any fence), never decoration. The fence
   stands four cells outside the trench, two rows thick, in runs with gaps and is left open across every gate lane: the gaps are the
   funnels that run an attacker into the nests' lanes, a run is slow to cross until a charge, shell, rocket or heavy
   body opens it (no breaching tool is invented for it), and a way through always exists. There is no wire and there
   are no mines (`fortified-lines.md`).
   Its trenches and sandbag lines are fighting positions, manned from the defender's patrol pool while the belt is
   pressed and empty otherwise (see `ai-nouns.md`).
2. **Main works.** A thick chamfered trace with an angled bastion at each corner, a nest in an embrasure on the outer face of each (a gun in the open floor of a bastion was boxed in by the wall round it and fired at nothing: measured 0 rounds and 0 of 4 lines of fire to marines ringed round the fort, against 130 rounds from a heavy-gun nest now), and at each
   gate a baffle, so the way in turns under fire, and two gate towers. Walls use the ordinary wall-health machinery.
   A **gate tower** (`GateTowers`) is a room set into the wall line beside its gate: its five-by-five ring stands two
   cells proud of the wall face over the approach and one cell into the yard, where its door is, and its ring is the
   wall's (the same strength, so a breach there is the same work, and the wall's material). It is slit on every ring
   cell that looks out of the works (three over the approach, one along the wall face, one into the gate's mouth),
   never into the yard, and its garrison place stands behind the slits, so a squad holding it fires out where a cap on
   solid wall shot at nothing (scene `gate-tower`: a squad ordered to hold it walks in and hits the approach; with its
   slits walled up the same squad hits nothing).
3. **Interior.** Buildings read by function, each with **one** protective element and never rings of rings: the
   fire-control bunker low and hardened inside one course of bags, the magazine apart from the guns inside one berm of a single course
   (the only building whose job is blast protection), the power plant with its plant standing in a fenced yard,
   and the battery in one revetted pit whose opening is screened by an offset traverse so the way in turns. Interior
   earthworks are cover and channelling, not defender positions: measured on the trial ground, defenders hold the belt
   and almost never a position inside the works, while attackers who have breached use the interior bags as cover.
   Trench is the preferred fighting position (cover from every bearing); sandbag is laid only where a position needs it
   (a traverse, a nest's pit, a firing line inside a gate). Paving exists only where vehicles drive; the rest is earth and cover.
4. **Siting.** The works and glacis need clear ground, loose stone is levelled, and forts never stand flush: a placed
   fort's whole footprint, belt included, is closed to the next.

New fort kinds follow the same order. Capture, aspects and mission authority are unchanged by it.

## Built: battery fort and aspects (slices 1 and 2)

- **Layout.** `BatteryFort` is the authored frame (works, gates, buildings, lanes) and `FortBelt` the outer belt; both
  rotate as one at four cardinal turns. `FortStage` stamps it on ground the city has left free (city is prior;
  roads and precinct claims are respected), turns the main gate to face the map centre where it can, and paves a lane
  from each gate toward the nearest road (ground paint only, not in the road graph). Fort walls carry wall-health, so
  they can be breached. The belt uses the earthworks sheet `fort-earthworks` (trench sections by connection mask, thick
  sandbag, hedgehog), produced through the geometry-first loop. The belt's fence is the existing industrial fence from the doodads sheet, not earthworks art.
- **Declaration.** `GroundDefences` carries declared forts by `FortKind` (BATTERY_FORT today). `MissionDefencePolicy`
  stays the only decider; Conquest's untuned first pass converts its declared batteries into forts
  (`CONQUEST_BATTERIES_AS_FORTS`, one line to turn off). Bare structures remain for scenes and debug, the DEBUG dial
  has a fort option, and fixtures round-trip forts (a legacy fixture reads as none).
- **Rooms.** An aspect is housed in a **suite**, not at a point: a main room that carries the 3x3 core and the holder's
  banner, and where the function wants one, an ante-room. Fire control is a command vault (the core between
  two banks of seated consoles) opening onto an operations hall of further banks; the magazine is a store with ammunition racks
  down both side walls and a loading bay of pallets in front of it; power is a generator hall (tanks and generators either side
  of the reactor, a switchgear bank, a walkway); the shield fort's control room is a vault with a small hall; the anti-air
  aspect is one small hardened control room with its flak outside its door. Rooms are authored data (`FortRoom`, built by
  `FortRooms`) fitted through the ordinary room-layout, fixture and console-bank guards (`RoomLayoutCheck`), so a room that
  would seal itself is refused rather than generated bare; only single-cell fixtures are authored because a multi-cell doodad
  does not follow the fort's pose. Every door that opens on the works carries one short offset bag screen; the rooms are
  their own hardening. The garrison stands at the stations: a defender-held inner-position node per crewed room, with a
  stand cell at every authored stool.
- **Place.** An aspect's place is the union of its suite's floors (`FortPlan.suite`, `Fort.Place.resolveSuite`), read from
  the authored frame rather than recovered from the map: holding the aspect means holding every room of it unopposed and a
  hostile body in any room (the ante-room included) contests it. Doors and walls are not the place. Tactics still aim at the
  main room's floor, so radar-first targeting and `DefenceStructureTactics` are unchanged. The emitter has no room and keeps
  its own rule.
- **Aspects.** Each aspect is a 3x3 core structure inside its main room, captured by presence like fire control was.
  Fire control lives in the bunker: the guns are silent while the bunker is not the holder's, and a wrecked bunker
  silences them for good. A wrecked magazine leaves a ready-round pool the guns fire out and then stop, and the
  magazine explodes through the existing blast machinery. Power taken, contested or wrecked slows traverse and fire
  to a quarter. Power is local to a fort. Either side may hold any aspect and a captured aspect serves its new
  holder; wrecked stays wrecked, taken can be retaken. State is drawn on the map beside the battery markers.
- **Commanders.** A fort's aspects are disclosed together when the platform or any of the works is sighted (bunker, magazine, power); see
  "Built: command awareness".
- **Tuning is untuned.** Numbers (12 ready rounds, 0.25 unpowered rate, core hit points) are first passes.

## Built: shield fort (slice 3)

- **Layout.** `ShieldFort` is a different fortress from the battery fort, not that one with the guns swapped. An
  octagonal trace with a round tower on each diagonal face (instead of pointed corner bastions), three gates (a main
  gate and two posterns, each with two gate towers and a baffle), and an **inner keep**: a two-cell ring wall round the
  generator open only at two opposite diagonal gaps, so an outer gate leads into a yard and never at the emitter.
  The buildings keep to the half of the works away from the main gate. It shares the belt machinery (`FortBelt` now
  takes a spec per kind) and the stamping stage (`FortStage`, driven by a `FortPlan` per kind; fort ids are unique
  across kinds on a map).
- **Aspects.** The generator is the **emitter** (no presence capture; destroying it ends the dome for good). The
  **power plant** feeds the dome: wrecked, contested or held by a side that is not the dome's holder, the dome is down
  exactly as an overloaded one is, and rises again after a reboot when power returns. The **control room** decides who
  the dome serves: whoever holds it holds the dome, and the generator changes sides with it (the dome reboots first). A
  governed dome ignores the old ring of ground round the generator. Power is local to the fort, and the rules are
  written for sides: a side that has taken only the control room has a dome with no power, because the plant is still
  the other side's to withhold; taking both gives it the dome. A wrecked control room leaves the dome with whoever held it
  last; a wrecked plant or emitter stays wrecked.
- **Authority.** `GroundDefences` declares shield forts by kind. Conquest's untuned first pass converts its
  declared shield generator into one shield fort (`CONQUEST_SHIELD_AS_FORT`, one line to turn off); the DEBUG dial has a
  shield-fort option; fixtures round-trip the declaration and a legacy fixture reads as none. A bare generator beside the
  keep remains for scenes and the old dial option.
- **Commanders.** A shield fort discloses two facts, both on sighting the generator or the works: the control room (where the dome
  is taken, held or retaken, the existing take/retake/defend roles) and the power plant (a `FORT_POWER` fact, work for the
  side that does not hold it). Fair knowledge only.
- **Map.** Aspect rings and labels draw as for the battery fort, with a "DOME UNPOWERED" line over the generator.
- **Untuned.** A control room that is merely contested does not drop the dome (only the plant does); the tower and
  keep sizes, wall hit points and the whole conversion are first passes.

## Built: command awareness (slice 5)

A fort is one defence with several aspects, so a commander does not treat each building as a separate task. For every
fort a side has been disclosed, `FortTactics` chooses which aspects are work, in what order, and whether the aim is to
hold or to wreck, from only the aspects that side knows of (wrecked aspects are not disclosed, so they are never
offered). `DefenceStructureTactics` merges that choice with bare-structure work under the same cap, and every task
carries a sentence of reason into the command plan summary. Nothing in it is special to attacker or defender: the picks
follow from who holds an aspect and who built it, relative to the side choosing.

- **Battery fort, against it.** Take the bunker: it silences the guns and keeps them. Once the guns are the side's own,
  the power plant (full traverse) and then the magazine (sustained fire) are taken to make them shoot properly. Where
  a demolition squad stands far nearer the magazine than the bunker, the magazine is wrecked instead, which rations the
  guns to a ready-round pool and then stops them.
- **Shield fort, against it.** Cut the plant first: one building, and the quickest way to drop the dome. The control
  room, which hands the dome over, follows once the plant is the side's own, or goes alongside it when two squads can
  be spared to hold both. A lone demolition squad wrecks the plant instead, because a plant taken and left is simply
  taken back, while a wrecked one is gone for both sides. Capture is preferred wherever the capturer can use the aspect.
- **Defence.** A pressed aspect is stood to at, the most critical first (bunker, power, magazine for a battery; plant,
  control room for a shield fort), and a single axis of assault draws a second squad. A lost aspect built for the
  side is retaken in the same order: the bunker restores the guns, the plant restores a dome. Once the most critical
  aspect itself is lost (the bunker of a battery fort, the plant of a shield fort) the Conquest defender sends no single
  squad: the fort is its counter-attack's, retaken in proportion or not at all (`conquest-command.md`, Defender command).
- **Law.** Demolition capability is an input to the choice (`Candidate.demolition`). It is read from the commander's own
  living squads: a squad is able to wreck while any member carries a contact-demolition satchel kit with a use left
  (`DemolitionKit`, the same definition the squad's own plant logic uses), which is own-force knowledge and never a
  read of an enemy. A wreck pick is only ever given to such a squad, and the order it receives is the ordinary attack
  move to the aspect's doorstep carrying a wreck permission (`ObjectiveAssignment.wreckAuthorised`), a flag and not a
  named target, so the knowledge laws are untouched. Where the choice is a take, no permission is given.
- **What discloses a fort.** Any one of the fort's exterior cells in the company's view (the works' wall with its gatehouses,
  the gates, the bastion nests) or the platform (battery) or generator (shield) discloses every aspect of the fort, so a
  commander only reasons about a fort the company has actually looked at. The platform and the buildings stand inside a
  wall nobody outside can see over, so the works are the part of a fort a company beside it really sees; a rule that
  waited for the platform left a fort a marine stood against for thousands of ticks undisclosed. The outer belt is not
  among the disclosing cells: the stage drops a belt cell wherever the city holds the ground, so a belt cell in view may
  be a city wall. A lane that never comes within sight of the works still leaves the fort undisclosed, and the commander
  has nothing to choose.
- **Who is sent.** A squad the ambient work service holds (a works crew at its bench, or stood down by trouble and due
  back) is never offered structure work, by either commander: the unit dispatcher does not run it while the work holds
  it, so an order to it is never carried out, and as the nearest candidate it would otherwise keep the task for good.
  Mech and drone squads are not offered it either (a mech cannot enter a bunker's interior), and only a group a named
  officer leads is excluded, not the acting-NCO group every campaign squad has. A squad on a fort task is not drafted
  into an uncontested compound capture, which used to swap the task's squad every few hundred ticks before it arrived.
  A squad that closes no ground on its task for 3000 ticks is benched from structure work for 9000 (the lane planner
  has it back), and a squad maneuvering on a flank against one contact gives the maneuver up after 1800 ticks and
  carries out its order, because a nest nobody can reach never dies.
- **Memory of a sighting.** A fort a company has once had in view stays disclosed to its commander when the squad looks
  away. Disclosure by sight alone made the control-room task appear and vanish as the squad walked in and out of view of
  the generator, and a squad whose order flickered between the task and the lane never arrived.

## Built: assault approaches

A kind of fort exports, with its geometry, the ways an attacker can go in (`FortPlan.approaches()`): each
gate as an approach (the first is the main gate, the rest are posterns) with the nests whose reach covers
its mouth, an entry cell four cells through it, and the approach lane the belt is left open across; and a
breach, a wall section far from every gate and as far as possible from every nest. A kind that has
nothing to add gets them derived from its own gates, nests and wall, so a new stamp is assaultable before
it authors anything. The fort's frame rides on the facts a side is disclosed, because a fort's wall,
gates and nests are exactly its exterior cells, which are what disclose it; the interior (doors, floors)
is not carried. What a company does with them is `phased-assault.md`: a base of fire on the covering nests,
the gate, the aspect, a hold. A breach is exported and chosen only by a company that can cut one, but no
weapon in the game aims at a wall, so it is no better than a gate yet.

## Built: fort restraint

A squad that merely sees a fort's emitter used to fire on it like any hostile contact (eight SMGs took it from 900 to
about a third of its hit points in a minute), permanently ending the dome and pre-empting the commander's choice between
taking the power, taking the control room and wrecking. Wrecking an aspect is permanent for both sides and capturing it
is usually the better prize, so **the cores of a fort's aspects are not opportunity targets**: the bunker, the magazine,
the power plant, the control room and the shield emitter. (`FortRestraint`)

- **What is restrained.** Only aspect cores. The battery platform (the guns), the wall nests and every unit stay ordinary
  targets, so a fort still defends itself. A bare shield generator or battery with no fort is unchanged.
- **Symmetric.** Nothing in the rule knows attacker from defender. Whoever is hostile to the core leaves it alone, and a
  held core is not an enemy to its holder anyway.
- **How.** A restrained core is left out of the hostile-combatant index, so it is not a candidate in target acquisition,
  in the opportunity pass, in a squad's threat awareness or in force counts, and a squad holds no contact that it may not
  shoot (so there is no standoff with an emitter). It is still in every other query, so rounds, fog and splash still find
  the body. Whole-neighbourhood pickers (satchel plant, rocket, mech targeting) ask the same rule before committing.
- **What lifts it, for a squad and a core.** The squad's executing order ends within six cells of the core and is either a
  commander's fort task that says wreck, or an order the player gave (a player pointing a squad at a building is asking
  for it). The squad is then offered the core as an ordinary candidate. Leave for one building is not leave for the next.
- **No exception for "it is shooting us" or "it is the only thing left".** A core never shoots, and a squad with nothing it
  may shoot waits for its commander; the battle's own objectives decide when it ends.
- **Out of scope.** Ships and aircraft choose targets by their own rules (a barrage can still destroy a core, which is the
  overwhelming-force route), as do grenade and round splash that lands near one.
- **Control.** `-Dbattle.fort.restraint=false` makes every core an ordinary target; the shield-fort scene runs the same
  view with the switch off to show that the squad would have shot it. The scene's commander loop now comes in by the main
  gate, past the keep.

## Built: fort buildings are kit buildings

A fort building is an ordinary installation-kit building in its lot's style (owner's pick): its walls, doors and
windows are the light installation wall dressed in the fort's works style (`fort-works.md`), and it takes the ordinary
liftable roof, opened by the cave-in machinery like any other, so capture inside a building, fog and the roof reveal are
the kit's. What a fort adds is authored per aspect on its core structure (`buildingArt` in the turret catalog): the
holder's banner over the building, larger once taken, and the magazine's blast door over its store door, damaged with
the building (core durability under 60%). The platform names the gate's leaves (`gatehouseArt`), drawn over each open
passage and each open sally port in the lot's style; the gate towers beside them are rooms like any other. A new fort
kind adds data and a layout, not renderer code. The garrison's stations are furnished with a round operator seat
(`fort-fittings.seat`, `FortRoomKit.STOOL`): single-cell, so it turns with the fort pose. A hardened building stands
inside no ring of bags: its door carries one short offset screen and the room is its own protection (see "Rooms").
Every fort building is windowed by the fort's own rule (`FortStage.FORT_WINDOWS`), not the compound's: a window every
third cell along each straight wall that looks out over open ground (out of the works or over the yard), moved a cell
along where a fitting stands inside it, never at a corner, beside a door or beside another window. A window is an
ordinary kit window, floor under the pane, so a squad holding the building stands in it and fires out. The magazine's
store stays blind by rule: an ammunition vault opens only onto its loading bay, which is windowed; the gate towers are
slit instead. `garrisonEvidence -Pcases=FORT_BATTERY_FORT,FORT_SHIELD_FORT` tallies each fort building's windows.
A taken plant keeps running for its new holder: only a contested point or a destroyed battery stops it. Its roof is the kit's, styled
by the kit-roof work and never per fort (owner). Not yet made: a bolder belt fence and a purpose-drawn Ridgewatch
launcher.

## Built: siting (mission-owned)

Where a fort stands is a mission-design decision as much as how many there are, so the declaration carries it:
`GroundDefences.siting` is a `FortSitingPolicy`. `LEFTOVER_GROUND` is the default and what every scene, debug dial and
unconverted mission gets: forts take the open ground the city has left, deep band first, nearest the map centre. On a
Conquest map that is the rear flank, and it is why an earlier evidence run saw no marine within 29 cells of a fort in
36,000 ticks. `COVER_THE_APPROACH` is Conquest's opt-in (`CONQUEST_FORTS_COVER_THE_APPROACH`, untuned, one line to
turn off): a fort is a barrier on the way in.

- **Intent (`FortSiting`, pure geometry).** Two ends of the battle, the landing and the keep. Battery forts are aimed at
  the way between them (about half way, spread across the approach a fort's width apart, staggered in depth); the shield
  fort is aimed at the keep, ranking sites by the weight of garrison compounds a dome would cover and then by nearness.
  A fort that cannot have its exact ground slides along the line of advance before it leaves it, and a site behind the
  landing or past the keep is taken only when nothing on the approach fits. All figures are untuned first passes.
- **Reserved ground.** The middle of a Conquest map is a grid of roads and claimed places by the time forts are stamped, and
  no pocket is left to fit a fort in. So a sited mission reserves ground for **every** declared fort while the precinct
  skeleton grows, before the town: road arms do not grow into it (`GrownTrunkPlan.grow` takes a keep-clear mask) and zoned
  town claims give it up (`FortReservations`). Programmed places (keep installation, landing, lane compounds) are prior
  and never give ground up, and they are not known until growth has run, so the reservation first estimates their ground
  from their programs and slides each target off it (`FortSiting.slide`): a battery fort along the line of advance before
  it leaves it, a shield fort to the nearest free ground to the keep. The slid targets are published on the context
  (`BspKeys.FORT_TARGETS`) and both the reservation and the stage read the battle's two ends from the plan's own seeds
  (`FortReservations.anchors`), so the ground kept clear is the ground the fort is aimed at. (An earlier version aimed the
  stage at the keep's building, tens of cells from the seed the reservation used, and the second battery fort fell back to
  the rear field.)
- **Placement rules.** Prior claims, roads, doorways, landing berths, posts and the one-fort-one-footprint margin all still
  bind. A sited fort lets a road cross its glacis (the belt simply is not drawn there) but never its works, and its main
  gate faces the landing rather than the map centre. The declared count stands where ground allows: a fort whose reserved
  ground is gone slides along the approach in the stage too, then falls back to leftover order and records no target. A
  declaration the map cannot meet (no open ground of a fort's size) is logged as a warning, never dropped quietly.
- **Dome reach.** A shield fort's dome is sized from what it covers rather than one fixed figure: over its own works at
  least (`FortPlan.worksRadius`) and, when sited to protect the keep, over the whole of the keep's footprint, measured from
  the generator to its farthest corner and capped at `FortSiting.MAX_DOME_RADIUS` (untuned). The fort declares it on the
  emitter's `FortTag`; the simulation sizes the governed dome to it (`ShieldDomeService.cover`). A bare generator keeps the
  structure's own 16-cell dome. Flux capacity, dissipation and overload time are **not** scaled with the area: a wider dome
  has the same pool, so it covers more ground and is no harder to overload per square cell. That is a number for play, not
  settled here. Measured on the two Conquest fixtures the fort stands 68 and 79 cells from the keep and its dome now
  reaches over it (previously 66 to 115 cells off with a 16-cell dome, covering none of the garrison).
- **What it does not do.** Battery guns fire at warships only, so ground marines are not shot by them; a sited battery fort
  matters to the ground battle as a place to take, and to a ship overhead as a hazard. A shield fort cannot stand on a
  programmed place's ground, so it stands beside the keep's installation, not within it. The axis-only Conquest recipe
  (an axis and no precinct plan) carries the fort stage, but that city leaves no open ground of a fort's size on the
  generator seeds tried, so a declaration there is logged as unmet; siting is meaningful on precinct-planned maps.
- **Evidence.** `createSnapshots -Psnapshot=fort-siting` draws both Conquest fixtures, forts declared, leftover against
  sited, with landing, keep, lanes, batteries' envelopes and domes marked (1920x1080). `commanderEvidence -Pmission=conquest`
  with a fixture declaring `declaredForts` and `declaredFortSiting` replays them; `FortOutcomes` reports nearest marine,
  disclosure tick and aspect holders per fort.

## Built: optional anti-air aspect

A fort may carry an **anti-air aspect**: a plotting console in a small hardened control room with a Locust battery on each rear bastion and a Swarmer tower on each front one
It is a fort *aspect* like the others (`FortAspect.AIR_DEFENCE`), declared per kind
(`GroundDefences.airDefended`, fixture field `declaredFortAirDefence`) and absent unless a mission asks. No shipped mission
declares it yet, and no mission should until it is a design decision about that mission's air.

  banks of seated consoles, two operators sit at them, and nothing else stands in the yard. Its place is the room's floor. The two Swarmer towers (`aa-swarmer`, guided four-round salvos, reach 50) stand in the pockets of the two front bastions (`FortPlan.airTowers`), so every corner of the works carries anti-air.
  Each plan (`BatteryFort`, `ShieldFort`) places the room clear of its buildings, lanes and centrepiece.
  The two **Locust missile batteries** (`aa-locust`, thirty-round salvos at reach 70) are not in the yard: the plan
  (`FortPlan.airBatteries`) stands them in the pockets of its two rear bastions, the two farthest from the main gate, so the
  airspace they cover is as wide as the works and no single strike silences both. `FortStage` stamps all of it as ordinary posts
  tagged to the fort. Art reuses the console base and the missile-AA base and mounts; a Locust launch vents its blowback gas and smoke behind the tubes (authored in the weapon's `fx.launch`). The Ridgewatch battery remains the authored mission battery and is not stood by forts.
  A prepared position stands on a concrete pad: the works' paving is laid under the missile mounts (their body and a ring) and under each bastion nest, after the yard is dressed (a sealed footprint is not walkable, and the dressing skips such cells). Belt nests are field positions and keep sandbags and earth. Art still wanted: a paler fort-concrete revetment variant, since `road.revetment` is blue-grey.
- **Rules (written for sides).** The console is the aspect's core and is held by presence like the others. A wrecked
  console silences every mount for good; a console contested, or held by a side the mount is not friendly to, silences
  them; a fort without power runs them at the quarter rate; taking the yard turns every mount to the taker, who is then
  shot at by nobody and shoots the old holder's aircraft. Mounts are restrained like cores (`FortRestraint`), so a squad
  taking the yard does not shoot the guns it is taking. Air line of fire is not blocked by the fort's own works
  (`TurretAim.fortAirFireLineClear`), because the yard sits inside walls that would otherwise shut its guns in.
- **Knowledge.** The side's strike director learns a yard's mounts when the works are sighted, not when each mount is
  (`AirStrikeDirector.sightForts`): a fort is disclosed whole by its exterior, as for ground command. `AaRisk` counts a
  known, standing yard's mounts by their real reach (the guided battery covers far more than the fourteen-cell bubble),
  so an officer estimates the pass honestly, and a yard's wreck or flip lowers the estimate. A fort whose works nobody has
  sighted is a surprise, as ever.
- **Command.** `FortTactics` offers a hostile yard as work for the side that would fly over it: last by default, **first**
  when the sky is shut (`AirSupportFacts.blocked`: a strike is on offer and every works cell assessed is above the
  director's ceiling). A company's plan therefore opens the sky it needs before asking for a strike the officer will
  refuse. Sorties are not re-routed around a yard's envelope; the officer only declines. That is a gap, not a decision.
- **Evidence.** Scene `fort-air-defence` plays a fort with and without the yard against a forced strike, a declined
  strike, a wrecked console, a lost power plant, a taken yard and a shield fort. Mechanics are judged; the bomber's hull
  is a reading and not a verdict, because the fort's existing nests drain a lone bomber that crosses the works without
  any help from the yard. The yard's marginal value is reach (a guided round at 62 cells from a battery with reach
  75), the officer's refusal and craft that stay (see below), not a lone bomber's survival.

## Built: the fort spots for its side's air

A fort's **sensors** are the radar on the roof of its fire-control bunker, the building that already has a radar in its art. They are
not a separate aspect with a building of their own: the bunker is where the fort's aim, and so its eyes, live, and a sixth building would
have been a parallel noun for the same thing. A battery fort therefore has sensors; a shield fort has none yet (its control room has no
dish).

- **An observer for the holder's air pool.** The radar is an operator for whichever side holds the bunker (`FortSensors`), through the same
  `CommsOperators` that a marine's radio is, so the side's air director calls a strike onto what the fort can see exactly as it would
  for a radio operator's sighting, and the player's air power would too, were a fort in the player's hands. It needs no marine with a
  radio on the map.
- **Fair knowledge.** The fort designates only ground it has in sight: inside its reach and over a line of real ground, in which the
  fort's own works are looked over (the dish stands above its roof) and anything else blocks as it blocks a rifleman. It vouches for no
  belief, so it never calls on ground it cannot see. The director now also picks its target from what an operator sees: it used to take
  the densest concentration anywhere on the map and then ask whether anyone could see it.
- **State, written for sides.** Taking the bunker makes the radar watch for the taker; a contested bunker watches for nobody; a wrecked
  bunker stops it for good. Destroying the gun platform does not: the guns are an aspect of their own, so a bunker whose guns are gone
  still changes hands, its radar still spots for its holder, and commanders are still told of it and of the plant that turns the dish
  (not of the magazine, which fed only the guns; `FortressBattery.fireControlStands`). Without the plant the dish does not turn and the fort has only the eye of its watchkeepers
  (`FortSensors.EYE_RANGE_CELLS`, against `RADAR_RANGE_CELLS`); a taken bunker whose plant is still the other side's is dim for the
  same reason. A call holds the fort's net for a cooldown, as it holds a marine's radio.
- **Control.** `FortService.setSpotting(false)` switches the calling off for one battle (the fort still sees); it is the comparison
  control the scene and the trial fixture field `fortSpotting` use.
- **A reason to open the sky.** A side whose units have lately had a hostile armed aircraft in view (`AirSupportFacts.flownAt`, its own
  eyes, not the enemy's pool) is told it is flown at (`SkyNeed`). `FortTactics` then works a hostile battery fort's bunker first, ahead of
  even a yard that shuts its own sky, by taking it rather than wrecking the magazine instead (which blinds nobody); and a fort off the line
  of march is still a company's fort when its radar spots for aircraft flying at the side, or its yard shuts the sky to the strikes the
  side could place (`ConquestCommand.opensTheSky`). Mechanics, not tuning: the window (`FLOWN_AT_WINDOW_TICKS`) and the ranges are
  UNTUNED.
- **Radar first.** A company that has been flown at, with the hostile bunker disclosed, skips its set-up steps and goes straight at the bunker
  (`FortAssault.Execution.radarFirst`); a muster being bombed commits at half of what it believes the objective takes, and a plan
  already on another aspect turns to the bunker. Measured in `phased-assault.md` ("Radar first"): the company aims at the radar by
  tick 2,000 instead of 6,200 and the fort's defence dies, but the bunker room is not yet held.

## Measured: ships and gunships against forts

Scene `ship-vs-fort` (readings, not balance; one seed per loop; forts are unchanged). An Onslaught under its own director
stood 38 cells off a fort with a garrison and an unarmed spotter; a Mudskipper gunship was called over the garrison with
two invulnerable scouts to sight it.

- **Bare garrison (control).** The ship killed all eight defenders and took no hull damage.
- **Battery fort.** The ship destroyed three nests and the garrison and the battery put 78 ship-grade rounds into it; it
  was destroyed at tick 1252. The same fort with the anti-air yard (66 rounds) lost the ship to 13% hull and 7 nests and 4
  aspects; the yard fires on aircraft only, so the difference is dilution of the ship's fire across more structures and
  seed noise, not the yard.
- **Shield fort.** The dome took 38 to 45 rounds and overloaded three or four times while the ship lost nine nests and
  three or four aspects, killed the garrison, peaked at 38 to 47% flux and kept its whole hull. A dome with its own works is
  a ship's fort to take apart; the battery fort is the one that costs a capital.
- **Gunship.** Over a battery fort without a yard the gunship (circling sixteen cells out for forty seconds) fired 36 rounds
  and fell at tick 468 to the nests; with the yard it fell at tick 291 after four yard rounds and fired 20. A yard
  shortens a craft that stays by about 40%, which a lone bomber crossing at speed never shows.

What follows for design: a yard is for the air that loiters (gunships, orbiting strike craft, boats that have to land) and
for the officer's estimate; nests are what hurts a fast pass. Neither makes a fort safe for a capital; a battery fort is
the dangerous fort for a ship and a shield fort is the dangerous fort for a ground company.

## Built: sally ports

A fort's garrison stages in its works and mans its belt, and before sally ports the belt beyond a face with no gate was
reached by walking out of a gate and round the walls: from the pool's staging cell to the belt stretches on the battery
fort's gateless faces the walk was up to 3.8 times the straight line (113 cells), on the shield fort's north face up to
4.3 (80 cells), and the garrison arrived as the attack did. Each kind now authors a **sally port** on every face without
a gate (`FortPlan.sallyPorts`: the battery fort's two faces opposite its main gate and postern, the shield fort's face opposite its main gate): two cells
along the wall and as deep as it, wall like the wall round it, placed off the gates' faces, the nests and the rooms.

A port is the fort's case of a sally door (`garrisonables.md`, sally doors) and keeps its rules: it serves the side
that holds every aspect of the fort and nobody while the fort is split or contested; it is unbarred when that side has
people in the works ordered to ground beyond the port's face (or home through it), never with a hostile at its mouth;
unbarred it is built passage, and the same side bars it again behind the sortie, in the face of a hostile come up to it,
or on the player's bar, never on a body standing in it; a port left unbarred when the fort is lost stays so for whoever
holds the works next; barred, it is breached as any wall is. An unbarred port carries the gate's leaves folded back over
its passage, and a barred one is drawn as its wall again (`FortBuildingRenderSystem`). With every port unbarred the battery
fort's worst stretch falls to 3.0 times the straight line (74 cells) and the shield fort's to 2.7 (72); a Warden
reaches every stretch either way, and the Bulwark reaches none from the staging cell, which stands in a room.

## Slices

1. **Fort layout family** — one battery fort as an authored compound layout (wall, gates,
   platform, fire-control bunker, magazine, power), placed when a mission declares it.
   Fire control moves from a radius around the platform to its bunker.
2. **Aspects as dependencies** — power feeds guns, sensors and dome; the magazine feeds
   sustained fire; each aspect's state is readable on the map.
3. **Shield fort** (built) — see above; it folded the local shield relay story in.
4. **Redoubt and citadel.**
5. **Command awareness** (built) — see below.
6. **Optional anti-air aspect** (built) — see above.
7. **Art** — earthworks and the fort's buildings with their aspect states and animated parts are done; the shield fort's own buildings follow the same data.
