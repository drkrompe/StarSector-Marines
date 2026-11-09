# Derelict drone flocks

Status: IN PROGRESS — owner direction ("heavy emphasis on drone swarms (flocking
behaviors) and heavy mechs"). Slices 1, 2 and 4 built, slice 3's unit, tasks and
strike built; flocks play in the derelict ruins battle. Cost, counters and look
to come.

Written: 2026-11-09

Updated: 2026-11-09 — flocks in the derelict ruins battle: every roofed goal held
at its threshold, the outside as the flock's ground, one entrance per flock,
cradles. Earlier: owner picks in (`derelict-swarm-size`, `derelict-mech-line`,
`derelict-flock-counters`, all option a); roofed-room exclusion is a central
rule, and a strike on a roofed target is held at its threshold. Earlier the same
day: the flock built on the drone squad's anchor seam.

Read `derelict-lore-guide.md` (The flock, The flock and the Rav) first, then
`air-nouns.md` (the air body, fighters and drones), `tender-ship.md` (the wing
that already flies drones from a bay) and the drone package (`Drone`,
`DroneSeparation`, `DroneSwarmAction`, `DroneHub`).

## Goal

A **flock** is a new ground-battle unit: twelve to sixteen low-flying swarm
drones that move as one body by flocking, pour down streets, split round walls
and re-form, wrap a target and strafe it in passes. The network commands the
flock, never its drones. It is derelict security's signature
(`derelict-security-network.md`), and the model is general: nothing in it is
derelict-only.

## Model

- **Members and anchor.** A flock is a set of member drones and an **anchor**:
  a point its commander moves along a route. Each member steers to the anchor
  plus three local terms over its neighbours in the flock: **separation** (the
  shipped `DroneSeparation` push), **alignment** (match their heading) and
  **cohesion** (close on their centre). Weights are flock data, so a tight
  "wasp" flock and a loose "murmuration" are tuning, not code.
- **Low flight.** A swarm drone flies below the rooflines. It crosses low cover
  (rubble, sandbags, fences) and never walls, roofs or roofed interiors. Each
  member routes on the ground navigation for open cells (streets, courts, open
  doors, breaches), so a wall between member and anchor splits the flock by
  each member's own route, and cohesion re-forms it past the wall.
- **Tasks.** A commander gives a flock one task with an anchor route: **screen**
  (hold a station between a threat and what it covers), **probe** (fly a route
  and report), **strike** (close, wrap and strafe a target), **pursue** (follow
  a contact to a leash), **return** (go home to its cradle). The flock decides
  only how its members fly it.
- **Strike.** On strike the anchor sits on the target and the members take
  orbit slots round it, strafing when their nose comes on (the shipped orbit and
  fire-arc), the slots turning so the flock wraps the target rather than
  stacking on one side.
- **Loss and re-forming.** A member dies by the ordinary damage rules and falls
  where it is hit. A flock under a third of its strength returns to its cradle;
  a cradle merges what comes back and rebuilds lost drones at a slow rate while
  its node stands.
- **Sight.** Every member is a sensor: what any member sees is the flock's, and
  the flock's is its commander's. Smoke and walls block it as anyone's.
- **Countering it.** Nothing special: area weapons hit several members because
  they are close together; a flock cannot reach shooters in a roofed room; flak
  and point defence engage members as fliers. The counters fall out of the
  model; none is a rule.

## Roofs are the flock's limit

Owner picks (2026-11-09), all option a: flocks of twelve to sixteen, one per
zone, tight and darting (`derelict-swarm-size`); a derelict heavy line of its
own (`derelict-mech-line`); area fire and cover as the counters, flocks keeping
out of roofed rooms (`derelict-flock-counters`). On the last the owner added
that some ruins sites are megastructures the company explores from inside, and
ruins maps are to be hybrids: an exterior drop-off and an interior section in a
megastructure or bunker (`mapgen-nouns.md`, Ruins ground).

So roofed-room exclusion is a central rule, not an edge case:

- **A flock never goes under an intact roof**, on any map. The rule reads
  only the map's own roof (`CellTopology.isRoofIntact`) and walls, so it holds
  on ruins ground, a colony, a megastructure's halls or a bunker alike, with no
  map-specific code. A roof brought down opens its cells to the flock.
- **Flocks own the exterior and the approaches.** The interior of a
  megastructure belongs to what fits in it: the heavies, posts and the
  network's other units (`derelict-security-network.md`).
- **A strike on a target under a roof is held at its threshold**: the open cell
  where the target's way out by the ground first meets open ground, its doorway,
  breach or gate (`FlockField.threshold`). The flock waits there in a body and
  shoots what shows in it; it does not wrap the building or try the walls. A
  target that walks out is in the open and the strike wraps it as usual. A
  target in a room with no way out within the field's reach has no threshold,
  and the flock wraps the open ground round it.
- So the company's answer inside is to keep out of a door's line, or shut it;
  and a company coming out of a megastructure meets the flock at the door.
- **The outside is the flock's ground**: the largest body of open cells on the
  map (`FlockSystem.sky`, relabelled every `SKY_TICKS`). A light well or court
  inside a megastructure is open sky but reached only through roofed halls, so
  no flock is put up, stationed or held there.
- **Any goal under a roof is held at its threshold**, not only a strike
  target: a station, a probe point or a home inside. A flock launched at a
  point under a roof (a zone node inside the megastructure) goes up at that
  point's threshold, which becomes its home, so each interior zone's flock
  waits at an entrance. A side's flocks take distinct entrances where there is
  more than one (`HELD_CELLS`) and share a lone door. The threshold search
  spans the map, since a megastructure's deepest hall may be far from its
  door.

## Built

Everything is in the drone package; a flock is a drone squad whose
`DroneAnchor` is a `Flock`, so its members are ordinary drones on the shipped
swarm action, which hands them to `FlockFlight` (`DroneAnchor.flock()`).

- `FlockSteering`: the pure rule. A member's velocity is the capped, weighted
  sum of the `DroneSeparation` push, its neighbours' mean velocity, their
  centre, and its own way to the anchor (fading over the last few cells), all
  over the flock's own members only. `FlockWeights` holds the weights and
  distances; `WASP` is the derelict flock (tight, darting), `MURMURATION` a
  loose alternative nobody ships.
- `FlockField`: each member's own route to the anchor. One distance field from
  the open cell nearest the anchor, over cells a low flier may be in
  (`FlockSystem.open`: walkable and not under an intact roof), bounded to
  `FIELD_RADIUS` round the anchor, no diagonal cutting a corner. Members on
  either side of a wall descend it by their own sides, which is the split;
  cohesion closes the body again.
- `FlockFlight`: one member's tick. Its way (strike slot, else down the field,
  else straight), the flocking velocity turned into a short look-ahead goal
  for the shipped `AirSteeringSystem`, a slide that never lets it into a wall
  or a roofed cell, and its fixed gun fired through `TurretAim`'s fire arc, so
  it shoots only as its nose comes on. A swarm drone flies faster and turns
  harder than a hub drone (`FlockFlight.HANDLING`), carries 10 structure, a
  12-cell gun, a 0.8-cell air sight radius (walls are walls to it) and 30
  cells of sight, and is drawn small (`DroneLook.SWARM`, the shipped drone's
  art until slice 7).
- `Flock` and `FlockTask`: members, home, leash, task (screen, probe, strike,
  pursue, return) and the published roster, field and contacts. Screen and
  pursue are held on the leash; strike is not (the commander orders it
  knowing where the target is). `contacts()` is what any member sees.
- `FlockSystem` (ticked after the drone bays): forgets the dead, sends a flock
  under a third of its strength home, moves the anchor at `ANCHOR_SPEED` for
  its task and waits while the body is more than `LAG_CELLS` behind the open
  cell the field leads to, rebuilds the field when that cell changes, and
  publishes. `launch` puts a flock up; `BattleSimulation.flocks()` reaches it.
- On a strike the anchor sits on the target and each member flies its slot on
  a turning ring (`FlockFlight.orbitSlot`), pushed out to open ground when the
  ring crosses a wall (`openSlot`). A target under a roof is struck from its
  threshold instead (`Flock.holdingThreshold()`): the anchor sits there, there
  is no ring, and members fire on the target whenever they see it
  (`Flock.focusTarget()`).

- `FlockCradle` (slice 4): `launch` stands a cradle at the flock's home, the
  drone hub's structure (targeted and demolished as a hub is) with no hub
  behaviour. While it stands, its node stands (`Flock.nodeStands`, set by the
  network) and the flock is home, it rebuilds one lost drone every
  `REBUILD_SECONDS` into the flock. A dead cradle or node rebuilds nothing; the
  drones already up fly on. A flock wiped out entirely is gone. Docking
  (members landed and out of play while dormant) is not built: a dormant flock
  holds over its home. The cradle has no art of its own yet (slice 7).
- Launch fills from the open ground reachable from home
  (`FlockSystem.openAround`), sharing cells when there are few, so a cradle at
  a cramped entrance still puts its whole flock up.

**In the derelict ruins battle.** `DerelictSecurity.install` (the heavies
session's, `derelict-security-network.md`) launches one flock of
`DerelictSecurity.FLOCK_SIZE` (14) per zone at the zone's node and tasks it
through `FlockSystemPort`; the rules above put each interior zone's flock at an
entrance. `createSnapshots -Psnapshot=derelict-flocks-ruins-look` plays the
extensive derelict fixture (`ruins-derelict-extensive-v1.json`), prints each
flock's home and the member-ticks under a roof (0 over the battle), and records
the flocks at the megastructure's entrances and in the fight.

The seam with the network (`derelict-security-network.md`): the network
launches one flock per zone with `FlockSystem.launch`, homed on the zone's node
(the cradle point once cradles exist) and leashed to its perimeter plus
`FlockTasking.LEASH_CELLS`. The director reads and tasks flocks only through
`FlockCommandPort` in `battle.derelict`, which `FlockSystemPort` implements over
`Flock` with this package's `FlockTask`: a report per flock (anchor, strength
as a share of `strength()`, each of `contacts()` at its cell as seen
(`contactCells()`) on the scan tick (`contactTick()`), its strike target as the
pin) and one task with an anchor or target. It never touches a member.

Evidence: `FlockSteeringTest`, `FlockFieldTest`, `FlockFlightTest`; the
`derelict-flock` scene (`sceneEvidence -Pscene=derelict-flock`): `split`
(probes through a roofed building, splits round it, re-forms past it),
`strike-open` (wraps a fire team on all four sides and strafes),
`strike-indoors` (the team in a roofed room, hidden from its open door by a
partition: the flock holds the door's threshold for the whole wait and no
member is ever under the roof), `rebuild` (a flock cut to half strength goes
home and its cradle rebuilds it) and `rebuild-no-cradle` (the control, cradle
destroyed: nothing rebuilt). Unit tests also `FlockCradleTest` and
`FlockSystemTest`.
`createSnapshots -Psnapshot=derelict-flock` adds the look shot `flock-strike`
on generated colony ground.

Not built yet: crossing low cover (the field reads walkable cells, so sandbags
and fences are walls to a flock for now); screen, probe, pursue and return
scenes; docking; the cost profile (slice 5); counters (slice 6); the look
(slice 7).

## Slices

1. **Flock steering.** A pure steering function: a member's next velocity from
   its state, its neighbours' and the anchor, with separation, alignment and
   cohesion weights. Unit-tested on small inputs: members spread from a point,
   align to a shared heading, close on a moving anchor, and never stack.
2. **Low flight and routing.** Swarm drones as air bodies held below roof
   height, routed on open ground. A `sceneEvidence` scene: a flock sent past a
   building splits round it and re-forms; a flock never enters a roofed room.
3. **The flock unit and its tasks.** The flock entity (members, anchor, task,
   strength), its tasks and the strike's orbit slots. Scenes for screen, probe,
   strike and return.
4. **Cradles.** The drone hub as a flock's home: launch, recovery, merging and
   slow rebuild while its node stands.
5. **Cost.** Measure the flock's per-tick cost with four flocks of sixteen on a
   vast map (`profileSwarmFlow` or a sibling profile), and bound the neighbour
   search to the flock's own members.
6. **Counters measured.** `loadoutMatchup` or a scene: a flock against a squad
   in the open, the same squad indoors, and with a squad automatic and grenades.
   This confirms the counters fall out of the model; it is not a balance target.
7. **Look.** Swarm-drone art in the Domain and contingency looks, and the
   flock's sound (a rising swarm whine), through picks.

## Constraints

- Not the Rav. Flocks never use the Rav's prey field or its spawning; a flock's
  numbers are its cradle's.
- The flock is commanded only by a mission director or commander through tasks;
  no member is ever ordered on its own.
- No knowledge a member did not see.

## Done

A flock plays in a derelict expedition and the owner has seen it pour, split,
re-form and strike. Fold the model into `air-nouns.md`, add a row to the air
`shipped.md`, and delete this story.
