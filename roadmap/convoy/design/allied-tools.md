# Allied tools: what a logistics base sends, by density

Status: DRAFT — the shared model behind the Cordon's and the heart's allied
forces; the heart's tool pick (`heart-allied-tool`) and the Cordon's urban
backbone pick (`cordon-urban-allies`) are out to the owner.

Written: 2026-10-23
Updated: 2026-10-24 — a delivery keeps to its escort (`EscortPace`)

`allied-command.md` is how the allies move and fight a machine once it stands
on the field (station, carry, the screened advance). This doc is the step
before it: what the allies have to fight with, where it comes from and at
what pace, and how a mission chooses. It is mission-neutral. The Cordon's table
is `cordon-allied-forces.md`; the heart's is `heart-allied-forces.md`.

## The laws

- **The allies hold, the company decides** (`cordon-nouns.md`, Settled
  choices). The allies fight their own war in lanes; the company is the
  backstop when a lane goes bad and the push that carries one through. A tool
  exists to make that true, not to win the battle for the player.
- **Allies never end a battle.** No tool's loss, and no base's loss, decides
  the outcome. They change how hard the company's job is.
- **The flier law runs both ways.** A Harrier pack flies only where the
  company or an ally has anti-air (`heart-nouns.md`). Turned round: an allied
  **air tool** flies only where the air is safe for it. Each mission states
  that gate as a beat or a condition (the heart: F3's corridor open, beat
  `air_corridor_open`). A mission that states no gate gets no air tools.

## The logistics base: the allies' tool shed

A **logistics base** is ground behind the allies' start line that the
mission's layout has laid: dumps, a vehicle shed or park, a muster yard and
the supply road(s) out of it. It is where every allied tool comes from.

- **Found by name.** The producer finds its base from the laid layout
  (`MapResult.layout`, `LaidLayout.find`), not only from a building. The
  heart's base is the pieces `ammo-dump-N`, `vehicle-park` and `supply-road`
  (`HeartLayout`); the Cordon's is its `ammo-dump` and `supply-<n>` pieces
  plus the shed's berths (`FortressProgram.logisticsBase`). A base with no
  shed still sends what does not need building (columns off the rear edge,
  hulls from a park, sorties).
- **Sends at a pace while it stands.** Each tool kind has a cadence the
  mission's table sets. A shed builds one hull at a time at its crew's pace;
  a park releases a stock it was given; a column walks on at its time.
- **Losing it stops it.** A base whose dumps are lost, or whose shed's crew
  or berth is killed, sends nothing more of what that part made. Tools
  already on the field fight on. It is a target for the swarm and a reason
  for the company to care about the rear, never an objective that ends the
  battle.

## The tool catalogue

Each tool is a **kind** (what it is), an **origin** (where it comes on), a
**task** (what the allied command does with it), and its **density fit**.

| Tool | Origin | Task | Fits |
| --- | --- | --- | --- |
| Armour pair or column (cars, technicals, tanks) | built in a shed, or released from a park | station (a sector; or an advance) | rural, town |
| Carrier lift (APC) | built in a shed | carry a waiting squad up, then station as support | rural, town |
| Walking infantry column | off the rear edge, to the muster yard | walk up and join a garrison, or screen an advance | all |
| Mech lance (a pair of Hounds) | built at a gantry, or walking on | allied mech station (`AlliedCommand.stationMech`) | town, city |
| Ammunition truck | loaded at a dump | drive a supply road to a named recipient (a **delivery**) | all, roadbound |
| Gunship orbit | a sortie called from the base | orbit over a place for a time; one at a time | all, behind the air gate |
| Mortar or gun battery (later) | walks or drives on, sets up | fire missions on what the line reports | all |

A tool is a body on the map, so it can be killed on the way, and the swarm
treats it as any other target. Its task is one `allied-command.md` already
owns, except the delivery (below) and the battery's fire missions.

## The density rule

Country decides what can move, so it decides what a base sends
(`cordon-nouns.md`, Density; `heart-map-intent.md`):

- **Rural: wheels.** Armour and carriers go anywhere across fields. The shed
  builds cars, technicals and carriers; carriers lift columns forward.
- **Town: mixed and roadbound.** Armour and carriers keep to the streets and
  the farm roads; infantry holds the house fronts; a mech pair may join where
  a lane runs through the town.
- **City: legs and mechs.** No forward carriers: a carrier in a city's
  streets is a target in a corridor. Columns walk, mechs stand where hulls
  cannot, armour (if any) is held to the boulevard behind the line. Air and
  guns matter more here, because nothing else sees over the blocks.

## How a mission picks

A mission's **allied table** reads the density and lists, per tool, whether
it is in, its count or cadence, its origin piece on the layout and its
first task. The table is the mission's whole statement of its allied forces;
no tool appears that the table does not list. A story beat may add a tool
(L2's ammo truck, R1's dozer) through the same producer, at its own time.

## The delivery hook

An **ammunition truck** is a delivery: a truck loaded at a dump drives a
supply road to a named **recipient**, and its arrival resupplies the
recipient through a callback the mission supplies. A truck lost on the road
delivers nothing.

- One mission-neutral hook, `SupplyDelivery`: a recipient (its name, the
  cell the truck drives to, when it counts as arrived, and an `arrived`
  callback) and the `AlliedDepot` the truck sets out from. The delivery waits
  while the depot cannot send; it is lost when the truck dies or the
  recipient is gone first. **The Cordon owns it.**
- **An escorted delivery keeps to its escort** (`SupplyDelivery.escortedBy`,
  `EscortPace`). The mission names the armed hull it has sent beside the
  truck; "escorted" means alongside, not assigned. The truck waits where it
  was set down until its escort is within eight cells, the pair drives at the
  slower one's pace (a little under it, so the escort can close up), and the
  truck holds whenever the escort falls more than fourteen cells behind. A
  truck the mission has no escort for sets out alone; one whose escort never
  comes goes on without it (ninety seconds waiting, thirty holding), and is
  offered no escort after (`EscortPace.alone`). The
  decision is one pure rule (`EscortPace.rule`), applied through the hull's
  speed cap, so any road hull can be paced: the Cordon paces its trucks and
  R1's dozer, and the heart's trucks can take the same seam.
- The Cordon is the first user: each redoubt's `GunStock` is a recipient, and
  its allied line command asks for a truck when the stock runs low; L2
  (`GunsDryBeat`) only empties one redoubt's gun towers, and the same request
  brings its truck.
- **What a truck rearms** (owner, 2026-10-23: trucks "might also rearm turrets
  or other things at the bases"): any place's guns through a `GunStock`, the
  rounds a place's gun towers, nests, turrets and field mortar tubes have on
  hand. A shot spends a round; a dry gun fires rationed (four times its
  cooldown) rather than not at all; a truck's arrival refills every gun and
  every tube a mortar team at the place serves, standing or packed (a tube
  that fired its last round packs, and its gunner's empty stash would never
  set it down again). A mission declares the stocks, sizes them and decides
  when a place is low enough to ask its producer for a truck. The Cordon's
  stock is a redoubt's gun towers, the picket nests nearest it and its yard
  mortar team, and it asks when any gun is dry or the pool is under 30% (`cordon-allied-command.md`, The
  producer).
- The heart's B5 is the second: a truck's arrival adds rounds to the
  walking barrage (`WalkingBarrage`), through the same hook.

## Code seam (proposal only)

The Cordon's `CordonLogistics` is an `AlliedDepot` (a machine sent from it
sets out from the threshold outside the shed's machine door, and nothing is
sent while the swarm is on the apron). It is still one base shape: a shed building its
density's line (`CordonAlliedTable`), carriers staged on its apron, columns
mustering in its yard. The shed's line is a list of machines, each a hull or a
mech chassis (`FabricationService.Machine`), walked by position, so the same
berths build a Hound where the table says (a town's every third machine, a
city's every one); a finished Hound walks out under the allied command's
mech station (`AlliedCommand.enlistMech`, `deployMech`). The heart schedules its
armour separately (`HeartLanes.scheduleArmour`).

The producer's seam is `AlliedProducer` (an `AlliedDepot` that also takes
`request(AlliedRequest)`, a tool to a named place for a reason, and answers
`onRoad(tool, place)`), so a commander draws on its stock by need rather than
every machine coming on a timer. The heart's commander and the Cordon's both
call it. `AlliedToolShed` is the producer: per tool a stock, a cap and a
restock pace, an optional gate (a sortie behind the air gate), and a maker
that sets one down at the depot's cell and sends it on; it refuses a request
with none in stock, the gate shut or the depot unable to send, and counts what
is on the road where until it arrives or is lost. `AlliedMakers` has the shared
makers (an armour pair or more, a Hound lance, an ammunition truck as a
`SupplyDelivery`, optionally telling the mission of each delivery so it can
escort and follow the truck); a mission with its own way of setting a tool
down (a lane's ground, a shed's berths and tug) supplies its own maker.

The Cordon's producer is built: `CordonLineCommand` holds an `AlliedToolShed`
over `CordonLogistics` as its depot, stocked from the density's table
(`CordonAlliedTable.Reserve`) with an armour pair or a Hound lance (its own
makers, which hand the machines straight to the sector that asked) and
ammunition trucks (`AlliedMakers.ammunition`). The shed's line of machines on
its pace is unchanged beside it.

Proposed: one mission-neutral **producer** (working name `AlliedToolShed`)
that takes a mission's table and a base found on the layout, and schedules
any tool kind from its own origin:

- a hull from a shed's berth (what `CordonLogistics` does today);
- a hull from a park, or a column, off the rear edge or a supply road;
- a mech from a gantry (today, from the shed's berths: see above);
- a sortie (a gunship orbit), behind the mission's air gate;
- a delivery, through the hook above.

Each tool it brings on is handed to the mission's `AlliedCommand` with its
first task. The Cordon's base becomes one configuration of it (the shed, the
apron slots, the tug and exit control stay as the shed origin's own
behaviour).

**The seam the heart builds on now:** `AlliedDepot`, one question: the cell
a machine sets out from, or none while the depot cannot send (overrun, cut
off). The heart's vehicle park answers it first (`HeartVehiclePark`, used by
`HeartLanes.sentFrom`). The shared producer will implement `AlliedDepot` as
well and slide under it; the heart's caller does not change.

## Boundaries

- Moving and fighting a tool once it stands: `allied-command.md`.
- Where each mission wants its allies, and its per-density table:
  `cordon-allied-forces.md`, `heart-allied-forces.md`.
- Making a hull in a shed: `FabricationService` and the shed's own model
  (`cordon-nouns.md`, Logistics base).
- A general allied commander that weighs the whole field is later work
  (`allied-command.md`, Boundaries).
