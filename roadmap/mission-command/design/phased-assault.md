# Phased assault

Status: ACTIVE — direction for how a large attacking force lands, masses and
assaults a defended position. Slices 1 (foothold and muster), 2 (the mech drop) and 3 (per-stamp assault plans) are built and are standing behaviour (owner decision 2026-10-14); a mission may choose the trickle instead (`AssaultStyle.TRICKLE`, `TargetProfile.declaredAssault`).
Slices are on the mission-command board.

Written: 2026-10-13

Updated: 2026-10-20 — the defender counter-attacks a lost place (a fort as one case) in proportion to what it observed holds it, or not at all (`PlaceCounterattack`, scenes `place-counterattack`, `fort-counterattack`), reusing the company's rally line and, at a fort, its massed commit; earlier: a screen is weighed against the whole base of fire as it stands and is re-weighed until its clouds are thrown.

Read `conquest-command.md`, `forts.md` and `ai-nouns.md` first.

## Why

On a Conquest map with forts, the attacker trickles single squads down three
lanes. A squad that reaches a fort is cut down 3–8 cells from the bunker by the
wall nests and garrison; nothing has failed in command, the force is just
arriving one squad at a time against a position built to beat exactly that
(`forts.md`, "Built: command awareness"). A fort is meant to be hard. The answer
the owner chose is a doctrine change, not a weaker fort: land, mass, then
assault, and assault a known kind of base by a plan made for it.

## The three phases

1. **Foothold.** The opening is slapdash on purpose: take and hold the landing
   zone and probe the nearest enemy positions to learn what is there. This is
   roughly what Conquest command already does — single squads down lanes — and
   it stays, now with a purpose: hold the beachhead and discover.
2. **Muster.** Squads gather at the defended drop zone and combine into an
   **assault company** until it reaches critical mass for the objective in
   front of it. A company may be infantry only or mixed (mech lances,
   later other assets). While a company musters, the foothold keeps being held
   and probes continue; a muster is a deliberate pause, not idleness.
3. **Assault.** The company commits as one body against one objective. Against
   a stamped base (a fort, and later other authored installations) it follows
   an **assault plan** made for that stamp rather than leaving the approach to
   per-squad GOAP alone. GOAP still executes each squad's part; the plan says
   which part.

Phases are per objective, not per battle: one company can be assaulting a fort
while the next musters behind it.

## Laws

- **Fair knowledge.** Readiness and plans read only what the side believes:
  disclosed facts, sightings and memory. A plan for a fort applies once the fort
  is disclosed; until then the objective is ordinary ground.
- **Mass is measured, not counted.** Critical mass is a judgement of the
  company against what is believed to be in front of it (strength, nests,
  walls), with a timeout so a muster can never wait forever. The numbers are
  untuned and come from play.
- **Delivery is transport, not ownership** (`reinforcement-nouns.md`). A
  commander-called drop — including a mech lance — lands claimed for the
  commander that asked for it, like any other reinforcement.
- **Symmetric.** Nothing here is marine-only in its rules; the defender may use
  the same muster and plan machinery for a counter-assault.
- **A plan is data per stamp.** The plan for a battery fort lives with the
  battery fort's geometry (`FortPlan`: gates, nests, doors, belt openings), so a
  new stamp brings its plan with it.

## Assault plan

A plan is an ordered set of steps over roles, read from the stamp:

- **Base of fire** — elements (mechs are good at this) placed outside the wall
  with sight of the nests covering the chosen gate, suppressing them.
- **Breach / entry** — the assault element takes the chosen gate (or breaches a
  wall section with demolition) once the nests covering it are suppressed or
  dead.
- **Objective** — the element enters the aspect building chosen by
  `FortTactics` (bunker, plant, control room) and takes or wrecks it.
- **Hold** — part of the company holds what was taken against the counterattack.

Several plans per stamp are allowed (main gate, postern, breach), chosen by what
the company has (demolition, mechs) and what is believed about the defence.

## Capabilities and roles

**A company is judged by what it can do, not by what its squads are called.**
Every squad has a *capability profile*, a pure function of the equipment its
living members carry and the data that equipment is defined by (a weapon's range,
penetration and wall damage; a special's delivery and uses; a mech mount's rack
and what is left in it). Nothing in the derivation names a unit or an item, so a
new weapon is classified by what its data says it does to a wall or a nest.
The capabilities:

| Capability | Meaning |
|---|---|
| Suppression | fire that keeps heads down from reach |
| Direct anti-structure | flat fire that hurts an armoured nest, from outside its own reach |
| Standoff anti-structure | arcing fire that hurts a nest from well outside the nests' reach |
| Distance breacher | a weapon that opens a wall section from range over a number of rounds |
| Close breacher | a charge or cutter that opens a wall at its foot |
| Assault | bodies that can enter a building |
| Hold | bodies to stay and keep what is taken |

A squad rates on several at once. The rocket rack that is anti-structure fire is
also a distance breacher; a cannon mech is a breacher and a base of fire; a
satchel section is a close breacher and, with rifles, still an assault. Each
rating has a rate (how fast) and a reserve (how much before it is spent): a
cooldown tool or an unlimited magazine has an infinite reserve, a rack counts the
rounds it still holds, and an empty one rates as nothing. Fair knowledge: a side
knows its own kit and reads nothing of a hostile's.

**The plan declares what it needs; the executor matches.** Each role of a plan
(`BASE_OF_FIRE`, `BREACH`, `ENTRY`) asks for a rate and a reserve of the
capabilities it can use. Squads are assigned best-fit and scarcity-aware: the
need the company can least afford to fail picks first, and within a need the
squads that could fill the fewest other needs go first, so a squad that is the
only answer to one role is not spent on a role anyone could fill. A squad holds
one role at a time, with one exception: wall-breaking and being the base of fire
are done in turn by the same gun, breach first. The plan also picks its
*variant* by what the company can staff: the least-covered way in, preferring a
breach (from range if the company can, at the foot of the wall if not) over a
gate when the breach is no more covered and the company brought what it takes,
and a gate when it did not. Readiness is "can staff the chosen plan" as well as
mass: a company whose mass is there but whose plan has a gap waits for the squad
that fills it, for as long as one is coming, and goes in as it is after that.

**A shortfall is published.** A company states the plan variant it would take and
the needs it cannot meet (for example "base of fire needs direct structure fire:
40 of 40 short"). The commander's lance call reads it: a company whose plan lacks
a base of fire or a breach asks for machines whether or not it is short of mass.

**A breach is a real act.** An order may carry a wall cell. A squad ordered at a
wall spends its own kit on that cell and on nothing else: a mech mount that does
wall damage turns its torso onto the cell and fires when the cell is in reach and
in line; a carried launcher fires a round; a marine with a satchel sets it on the
cell; a cutter cuts it. Every gate the weapon has elsewhere still applies. The
support mech's cannon carries the wall damage that makes it a reliable distance
breacher over a number of shells (see `mechs-nouns.md`).

The cell may instead be open ground a hostile **structure** stands on (a swarm
bore, an emplacement, a fort core): the order is then to demolish that body, and
only a satchel serves. A carrier within the charge's reach, with the same slack a
wall allows, sets it on the body itself through the ordinary charge service;
everything else in the squad fights the body through the ordinary firing passes.
This is how a squad is pointed at a structure, since the contact-range reflex
alone never walks a sapper to one (`colony-breach-nouns.md`, Bore).

### Mortar teams as a capability source

A nest outranges a rifle squad, so the base of fire needs something that kills nests from beyond their reach.
The **field mortar** (`special.field-mortar`; see "Crew-served fire support" in `ai-nouns.md`) is that capability
in infantry form: catalog data gives its deployed weapon a reach beyond a nest's, a minimum range, splash and
structure/wall damage, so the capability profile reads it as **standoff, indirect anti-structure with
suppression** from data alone, with no plan-side special case. Today the tube fires on its section's own belief,
so without an assigned mission a spotter inside sight of the nests is part of the plan's base of fire. A fire mission can
now be assigned (see "Fire support in plans: as built"). Evidence: scene `field-mortar` (a section with a tube against a fort's
wall nests, a rifle-squad control at the same distance, and a loop where the deployed team is rushed).

## Mech lances arrive without the player

Today a marine mech lance only arrives through the player's Mech Support power.
A muster that wants mechs needs a commander-called drop: the same delivery (a
lift carrying a lance) claimed for the attacking commander, landing at the
defended drop zone, paid from the mission's own allowance rather than the
player's command points. The player's power is unchanged.

**As built.** The call is `MechDropService.call` (reached from a battle control
as `callMechDrop(side, claim, hintX, hintY)`; `BattleView.mechDrops()` answers
`lancesRemaining`, `canCall` and `landingZone` without spending anything). The
caller supplies its own mission claim and a drop-zone cell (the muster's rally
point, or `MechDropService.centroidOf` the marine landing pads); the service
chooses the nearest viable landing cell, flies the delivery that
`MechSupport` flies (`AirDeliveryPower.dispatchCarrier`, `MechSupportPayload`,
one shared lance loader), and answers `COMMITTED`, `NO_ALLOWANCE` or
`NO_LANDING_ZONE`. The marines' lance descends from orbit as the marine lift
does. A mission declares the allowance (`MechDropAllowance`, a lance count and
a composition of up to four variants) on the target profile beside its ground
defences; Conquest declares an untuned two lances of Bulwark, two Hounds and a
Warden for its marines, every other mission none. The landed lance is claimed
for the caller and is an ordinary machine squad the commander plans. A DEBUG
briefing dial states the allowance, and its "at once" value calls the first lance
ten seconds in, claimed by the marine commander, so the delivery can be watched
without a muster that asks for it. The `mech-drop` scene is the acceptance.

## Foothold, muster and assault: as built

Slice 1 lives in `AssaultMuster` (pure planning over frozen inputs, symmetric)
with `ConquestCommand` as its adapter. **Muster, with the per-stamp plans, is how the
phased assault works** (owner decision 2026-10-14, on the trial-ground results; the
Conquest fort fixtures still disagree); there is no switch. A mission may declare
`AssaultStyle.TRICKLE` (`TargetProfile.declaredAssault`, carried to that battle's own
`ConquestCommand`), and then the attacker detaches one or two squads at a time to a
disclosed fort. No shipped mission does; it is the control of the comparisons below.
Plans are part of the muster and cannot be turned off separately. Phases are published per objective
in `ConquestFrontSnapshot` (`CompanyState`: stage `FOOTHOLD`, `MUSTER` or
`ASSAULT`, the plan step, squads, strength against the strength believed needed,
rally and objective cells, commit deadline, reason), and in the command trace as
the assignment reasons `MUSTER_FOOTHOLD`, `MUSTER_GATHER`, `ASSAULT_COMMIT`,
`ASSAULT_COMPANY_RALLY` and the plan's `ASSAULT_BASE_OF_FIRE`, `ASSAULT_STAGE`,
`ASSAULT_ENTRY`, `ASSAULT_HOLD`, plus company notes in the plan summary (each
step change is a note). The trace analysis counts all of them as company
orders. The battle-wide `Phase` is unchanged: a company's stage is per
objective and must not churn every squad's stability.

- **Objective.** A disclosed fort with an aspect still hostile-held is a muster
  objective; its aspects stop being solo structure tasks. Nothing else is yet.
  The believed strength it takes is a fort baseline, a share per hostile contact
  the side's own belief places within 40 cells of it (the believed garrison), and
  the believed defence in front of the way in (below), grown by a quarter for
  each company already broken against it. Every figure is marked UNTUNED in
  code; none was set to hit a target.
- **Readiness counts the defence.** The fort's works are disclosed with the fort
  (a wall, gates and nests are its exterior cells, the same ones that disclose
  it), so a company judges its need against them: each nest believed live that
  covers the way in the company would take adds 8 weighted marines, each other
  nest 2, a gatehouse to walk through 6 (a wall to cut 4). A nest never seen is
  believed live; one seen alive, now no longer a contact, with a company squad
  in sight of it, is believed dead for good. The way in a company would take is
  the least covered gate, nearest the company on a tie; a breach is only
  considered for a company carrying demolition and a machine fire element, and
  only when it is strictly less covered than every gate. A battery fort with its
  nests up asks for about 50 marines instead of 24 before its garrison is
  counted.
- **Foothold.** While any company exists one squad near the drop zone (the
  beachhead compound's cell, else where the first squads were first seen) holds
  it with an ordinary rally order, sticky, never part of a company. Probes and
  lane work by every other squad go on as before.
- **Muster.** Free squads (not engaged, not capture-committed, not crew, not a
  garrison, not in a named-officer group) are recruited nearest-first to the
  rally point, up to 75 per cent of the free squads, until their weighted
  strength (a mech counts three marines) reaches the believed need. **Each lane
  keeps half its infantry squads out of any company.** The first muster took
  squads off the lanes freely, and on the fixtures that cost captures (south
  12 against 19); a flat floor of two squads per lane was tried first and shut a
  small force out of mustering at all (three lanes of two or three squads left
  two to recruit), so the floor is a share of what each lane has. The rally
  is the drop zone when the objective is near, else a staging cell 90 cells
  short of the objective (outside a fort's belt of nests and their reach), slid
  to reachable ground and pulled back toward the drop zone while a believed
  hostile is near it. Squads in contact are never recruited, and "contact" is
  engagement (under fire, broken, a member able to engage), not belief in a
  distant hostile: every squad shares the sighting of a fort's garrison, and
  treating that as contact kept the whole force out of any company. The
  company commits when the strength at the rally reaches the need; when every
  squad it can get has arrived and it is still short it waits a 900-tick grace;
  and in any case at a timeout (4500 ticks, extended per recruit by its walk).
  **A timeout that finds under half the need at the rally stands down instead:**
  the company is dissolved, its squads go back to the lanes, and the fort is
  left alone for 3000 ticks before a company forms again, at most twice per
  fort; the third timeout commits with what it has, so a muster still never
  waits forever. (Committing three marines to a fort was a gift to its nests.)
  **A company never stands at a rally with nobody at it.** A company forms only when a squad can actually be recruited
  (a squad is free before it is recruitable: a lone infantry squad is its lane's whole share), and one whose recruits are
  all gone with no one to replace them dissolves at once rather than at its timeout. A recruit that has not closed on the
  rally by 3 cells in 1200 ticks (held in a fight it was not sent to, a path that does not close) is released back to the
  lanes and not taken again for 3000 ticks, instead of being waited for until the company stands down with nobody
  there. The walk allowance is 24 ticks a cell, which is what a squad measures (the first guess of 12 timed recruits
  out before they could arrive). The foothold element holds the drop zone only while a company exists; with none it
  stays on the lanes.
- **Assault, without a plan.** A company against an objective whose works are not
  known, orders every squad at the
  one aspect `FortTactics` ranks first (`DefenceStructureTactics.planMassed`):
  infantry to the doorstep, a machine squad to a standoff ring outside the
  structure's reach as base of fire, demolition squads authorised to wreck where
  the pick is a wreck. `AssaultCoordinationSystem` fix and flank apply as for
  any attack move. A squad more than 24 cells ahead of the company's rearward
  median holds (for at most 300 ticks per genuine regroup, stragglers beyond 48
  cells are not waited for, nothing is held in contact or inside 32 cells of the
  objective): the command group's bounded rally, measured toward the objective.
  A company left under a quarter of what it committed is broken, counted, and
  the next muster believes the fort takes more.
- **Lances.** A company short of its believed need asks for one lance
  (`ConquestCommand.afterCommit`, through `CommandEffects`, because a strategy
  never holds the live battle); the muster waits up to 1800 ticks for it
  rather than cutting its grace short. The landed lance is an ordinary machine
  squad claimed by `conquest-attacker` and is recruited like any free squad.
  Readiness now counts nests, so a company short of the (much larger) need is the
  normal case in the scene. The checked-in-style fort fixtures never showed a lance call
  because they declare no allowance (`declaredMechDrops` is absent, so a fixture that predates mech drops declares
  none, deliberately); the wish was computed and the call silently refused. It now says so in the trace
  ("wanted a mech lance: the mission allows none"), and a fixture that adds the Conquest allowance (two lances of
  Bulwark, two Hounds and a Warden) calls one per company, claimed and recruited as designed.

## Fort assault plans: as built (slice 3)

**The plan's data lives with the stamp.** `FortPlan.approaches()` exports the
ways in: each gate as an `AssaultApproach` (the main gate is the plan's first,
the others are posterns) carrying its mouth and outward step, an entry cell four
cells through it, the nests whose reach covers the mouth, and its approach lane
out across the belt (the cells the belt and its fence line are left open over), plus a
`BREACH`: a wall section away from every gate and as far as possible from every
nest, open ground outside and yard two layers in. The default derives them from
the kind's own gates, nests and wall (`FortApproaches`), so a new stamp can be
assaulted before it authors anything and overrides the method when it has more
to say. The battery and shield forts have three and four. The fort's frame
(`FortFrame`) places them on the map at any quarter turn, and a fort's frame
rides on the facts a side is disclosed (`DefenceStructureFact.works`), which is
exactly what its exterior cells already disclose.

**The executor** (`FortAssault`, run by `AssaultMuster` once a company commits)
only assigns roles, places and timing; every squad's part is an ordinary order
its own GOAP carries out (attack move to a cell, an assembly rally, an area hold),
and nothing names a hostile identity or position. Roles: machine squads are the
fire element, and so is a third of the infantry when there are at least three
infantry squads (never the squads with satchels); the rest is the assault element.
Steps, each bounded:

1. **POSITION.** The fire element goes to firing positions with sight of the
   covering nests: for infantry 15 to 20 cells from a nest (a rifle reaches 22,
   the nest's turret stands half a cell off its cell), for a machine 25 to 28 (just
   outside the nests' 24, inside a mech weapon's 30 and more), on the nest's
   outer side and 7 or more cells clear of the approach lane. The assault element
   gathers at an assembly cell on the lane at least 30 cells from every nest
   believed live. Ends when half the fire element is in place, or at 1500 ticks.
2. **SUPPRESS.** The fire element engages. The assault element waits until it has
   assembled and either every covering nest is believed dead, or the fire element
   is gone, or the suppression has run 1200 ticks. There is no suppressed state in
   the simulation, so "suppressed" is operational: dead, or the clock.
3. **ENTRY.** The assault element takes the way in (attack move to the entry
   cell); a fire element with no live covering nest left follows. Ends when half
   the assault element is there, or at 1800 ticks.
4. **OBJECTIVE.** The assault element goes at the aspect `planMassed` ranks first,
   taking or wrecking it as slice 1 did. When that aspect stops being the pick
   (taken, wrecked), it holds there for 450 ticks (`HOLD`, an area defence) while
   the fire element comes in, then goes at the next pick. The fire element keeps
   its places while a covering nest lives.

A fort whose pick cannot be reached dissolves the company; a company with no
assault element left is left to the broken-company rule.

**What the evidence says.** The `assault-muster` scene (one battery fort, a
stationary garrison, twelve squads of ten spread along the approach, field
rifles, serial scheduler, so the numbers repeat) records four loops: the control
trickle, the slice 1 muster (plans off), the plan, and the plan with a lance
called. All four lose most of what they send and none takes the fort; the plan
loses 76 of 120 against the control's 80 and the slice 1 muster's 85, positions
its base of fire, holds the assault through 1200 ticks of suppression and sends
it through the gate (positioning at tick 4426, suppression 5326, entry 6526,
objective 6976). It does not kill the nests: two of the fort's eight nests die in
every loop, by whoever reached them first, and the covering nests wear by about a
tenth. Rifles against a nest's armour is the reason, and that is the finding: the
base of fire that can answer a nest is a machine, and a company's machines come
from a lance the muster asks for and the fixtures never gave.

The Conquest fixtures that declare forts (36,000 ticks; captures / held, marine
losses; control, slice 1, plan): `reinforced-south-forts-sited` 23 / 18 (215),
18 / 17 (203), 21 / 15 (210); `full-strength-west-forts-sited` 9 / 3 (428),
14 / 3 (423), 16 / 2 (426). The unsited `-leftover` fixtures, control and plan:
south 29 / 24 (142) and 24 / 18 (206), west 12 / 5 (439) and 10 / 4 (413). What
it moves: the control never touches a fort in any of them; the muster does. On
the sited south fixture slice 1 took a battery fort's power plant and magazine and
the plan destroyed a battery platform; on the sited west fixture the plan wrecked
a bunker (tick 16260). What it costs: on three of the four fixtures the control
holds more compounds, so **the muster does not yet beat the control and stays
off**. The checked-in matrix, which has no forts, is byte-identical with the
muster on, off, and before this work.

**Empty rallies, found and fixed.** The sited fixtures at 36,000 ticks spent 27,150 company-ticks (south) and 9,000 (west)
with a company at a rally and no squad at it: late in a battle the marine force is a handful of squads, one infantry
squad per lane is that lane's whole share and cannot be recruited, yet a company formed on "a free squad" anyway and
stood out its 4500-tick timeout, over and over, and (a second kind) recruits sent 95 to 139 cells to a rally were pinned in
fights on the way or drifted away from it until the company stood down with "0 at the rally". Measured on main with the capability roles
merged, muster on, 36,000 ticks, before then after the fix (captures / held, marine losses; empty-rally company-ticks;
companies formed / committed / broken / stood down): south-sited 22 / 18 (211), 34200, 13 / 3 / 1 / 6 to 21 / 16 (209),
0, 6 / 1 / 0 / 4; west-sited 10 / 2 (439), 19125, 4 / 1 / 0 / 2 to 10 / 3 (434), 0, 2 / 2 / 2 / 0. With the Conquest
allowance declared (two lances): south-sited 12 / 5 (202), 27525 to 20 / 17 (175), 150; west-sited 8 / 3 (446),
3750 to 10 / 6 (424), 0. Empty rallies go to zero; the gain is largest where the empty companies had been holding
the force off the lanes (south with lances), and the no-lance south is level within noise. The muster still does not
beat the trickle everywhere, so it stays off.

What is open, in the order it matters: the lane share was a measured change and not a tuned number; a fort
with live nests is, for infantry alone, a position no plan takes, so the plan's
value depends on machines the fort fixtures only see when they declare an allowance; a company that commits on its
third timeout can still send a sliver. Breach is now fired at on purpose (see "Capability roles: as built"); whether a
breach beats a gate on Conquest is unmeasured.

Not built here: recruiting whole named-officer groups into a company, a company's
reaction to being counter-attacked at its rally, retreat of a company that is failing
but not yet broken, plans for any stamp but the two forts. The defender's side of it
is the place counter-attack (`conquest-command.md`, Defender command), which takes the
staging line and, at a fort, the massed commit from here but not the company: it has no
foothold, no lane shares and no stamp plan, because the place it goes for is its own.

## Capability roles: as built

**Profiles.** `CapabilityProfile.of` sums `Armament`s (a primary weapon, a mech
mount with the rounds in its rack, a special with its remaining uses) into a
`Rating(rate, reserve)` per `AssaultCapability`. `CommandFrame.freeze` builds one
per squad from the living members and the frozen row carries it
(`CommandSquadState.capabilities`); fixtures that do not itemise kit get
`CapabilityProfile.assumed`, so earlier tests and scenes keep their meaning.
Every threshold (minimum structure range, penetration, wall damage, standoff and
breach reach) is untuned and chosen to separate the catalog's current classes.

**Roles.** `AssaultRoles.assign` matches a plan's `Need`s to profiles as above and
returns the roles with any `Gap`s. `FortAssault.assess` evaluates the ways in with
it: the least-covered gate, or a breach (distance first, then close) when it is no
more covered and staffable, falling back to the gate and then to whichever way the
company comes closest to staffing, with its gaps. The `Execution` runs the staffed
roles; a breacher with no walking body becomes the base of fire once the wall is
down, and the others go in with the assault. `AssaultMuster` recruits squads that
fill a gap first, reports `variant` and `shortfall` on `CompanyState`, and holds
READY back from a company whose plan is unstaffed while a filler is coming.

**Wall fire.** `WallBreachSystem` carries out an order's `breachCell` for each
squad: mech mounts through `HeavyWeapons.fireMountAtPoint`, a launcher through
`InfantryWeapons.fireSecondaryAtPoint`, a satchel through
`SatchelChargeService.plantAtCell`, a cutter through the ordinary breach damage. A
carrier works from the squad's arrival footprint rather than on his own path
(untuned simplification), and no aim or channel animation plays for these shots.
The same pass fires a mech's mounts at the wall a player's machine assault names
(the wall sheltering a believed occupant of a held place; `ai-nouns.md`, machine
assault), and a vehicle's turret does the same through its point fire.

**Evidence.** The `assault-muster` scene's `breach-cannon` loop (one cannon mech
and rifle squads) takes a distance breach: the mech opens the wall section in a
handful of shells while the nests that cover the breach are still up, then is the
base of fire. Its `breach-charges` loop (rocket squads for the nests, charge squads
for the wall) takes a close breach and cuts the wall with one charge. Both report
the wall cells, the tick the first opened and the cut went through, the rounds
spent, nests killed, entry tick and marines lost. Neither is a claim that a
breach beats the gate; the muster stays off.

What is open: the standoff-demolition variant (arcing fire and a charge laid from
range) is not built; the satchel carrier can be caught by his own blast if the
sapper does not clear it (the friendly-hazard avoidance is relied on, not
measured); a mech's standoff position can land inside a nest's reach; all
figures above are first passes.

## Fire support in plans: as built

**Both are capabilities read from data.** The tube reads as standoff indirect anti-structure fire (`Delivery.EMPLACED`: range, rate, damage and wall damage from the deployed weapon, rounds from the structure's ammo). A comms radio reads as `AIR_DIRECTION` (`Delivery.CALLED`: radio range, one call per call-plus-cooldown, an infinite reserve because the pool, not the radio, holds the stock). Nothing names an item.

**A fire mission is part of an order.** `ObjectiveAssignment.fireMission` carries a cell and a radius. `FortAssault`'s base-of-fire role gives a squad that carries a tube a mission on a nest covering the way in (the one it was placed against while the company believes it live) and places it at its standoff from the nests rather than at its own sight of them. `FieldMortarService` reads the mission off the squad's order each tick: the tube fires at the best body on the mission ground in its band (a structure before a man, nearest the centre, lowest id) and otherwise fires on its section's belief as before. A section given a mission is ordered to set up where it stands, so the gunner's own settling, not every squadmate's, releases the deploy. A tube set down for a mission stands at least 26 cells from any hostile structure (a nest reaches 24).

**Fair knowledge, and one stated exception.** The plan reads only the fort's disclosed works and its own kit. A mission on ground the side has not seen is fired at **structures on that ground without a sighting**, because a disclosed fort's works are knowledge the side was told with the fort and a structure does not move. A **man** on the mission ground is fired at only when some squad of the side believes in him (confidence at least 0.3). This is wider than "something on the side has seen it" for structures, deliberately. A round with no spotter lands at the blind-fire accuracy penalty.

**A nest the lane squads killed first is learned from sight.** `NestBelief` reads only *emplacement* contacts (`CommanderContact.structure`), so a garrison man standing beside a nest no longer keeps a dead nest "seen alive". A nest never seen alive is believed dead when an own squad has held a clear line to it inside 16 cells for 90 ticks with no emplacement contact on it (a gun that stood there would have been seen and would have fired); a nest seen alive and then gone under a witnessing squad is believed dead as before. Plans, fire missions and strike wishes drop a dead nest and take the next believed live one, and a lane squad's standing mission skips ground whose nest the side believes destroyed (only while the muster, which holds the belief, is on).

**A tube is re-crewed.** When the gunner falls, the nearest living squadmate within 10 cells of the tube who carries no special takes it up (`FieldMortarService.recrew`): he is issued the item, is held at it as the first gunner was, and the tube keeps its phase and rounds. Only when nobody can does the tube go with its gunner.

**A standing fire mission outside plans.** `ConquestCommand.withStandingFireMissions` gives a squad that carries a tube, has an order of its own and is not in a company's fire role a mission on the nearest disclosed hostile works between 30 and 46 cells from it, so a mortar section on the lanes fires on what the side knows is there. A squad within 30 cells of any works is given none. It is standing behaviour and independent of the muster; the only control is per-commander (`ConquestCommand.withoutStandingFireMissionsForEvidence`), used by the `assault-muster` loops that judge a plan's own fire missions, which standing ones would clear first. A marching squad holding such a mission halts its crew and leashes the rest when a target comes into the tube's band (see "Mortar sections: as built").

**An air strike is a request, not an act.** `AirStrikeCapability` (pool stock and the believed anti-air risk over the target) is a role filler for suppression and nest kill. The plan only wishes: `FortAssault.Execution` emits a `StrikeWish` for each believed-live nest covering the way in that was counted for a strike while the company positions or suppresses, and `AssaultMuster` collects them. `ConquestCommand.afterCommit` asks the pool through `CommandEffects.airStrike`, naming the squads that carry an observer; the pool picks which comms-capable member calls it. The AI director's `RISK_CEILING` applies, so a nest believed too defended is refused (`TOO_DEFENDED`), remembered, and not asked again for a while. A company short of base of fire may count an available strike toward it. A company with no observer cannot ask, so the muster prefers a squad that carries a radio when the side has bombers on offer and one is within 40 cells of the nearest alternative. The SUPPRESS wait is extended while a strike is in flight.

**Evidence.** `assault-muster` loops `plan-mortar`, `plan-air`, `plan-mortar-air` and `combined-arms` (frames from `createSnapshots -Psnapshot=assault-muster`). Scenes run on the serial unit scheduler: the earlier `breach-charges` swing (3080 ticks and 40 lost against 5571 and 53) was the parallel scheduler's thread timing, and `sceneDeterminism` now replays ten loops byte-identically. One deterministic run each, marines lost and nests killed (3 of 8 by the ground in every control): trickle 70 and 3; slice 1 65 and 3; plan 42 and 3; breach-charges 41 and 3; plan-mortar 56 and 4 (one by mortar, three rounds on the mission, tubes 29 cells from the nearest live nest); plan-air 32 and 5 (two by air; four strikes asked and launched, no airframe lost); plan-mortar-air 53 and 5 (one each); combined-arms 83 and 4 (one by mortar, two rounds on the mission, wall cut at tick 4379, three strikes asked and one launched). These are one fixture, not balance. The mortar loops disable standing missions, which would otherwise clear the nearest nest before the company forms.

**Conquest (muster and plans on, against the trickle).** `commanderEvidence -Pmission=conquest -PmaxTicks=36000` on the `-lance` fort fixtures with a three-bomber marine pool declared. reinforced-south: marines lost 189 against 216, defenders lost 376 against 401, captures 15 with 12 held against 14 with 14 held. full-strength-west: marines lost 445 against 444, defenders lost 464 against 384, captures 15 with 4 held against 10 with 1 held. No fort fell in any of the four. The muster took one distance breach on south and two posterns (gates) on west; the trickle makes no plan. No strike was asked and no fire mission issued: no squad in these fixtures carries a radio or a tube. Muster plus plans therefore does not yet beat the trickle on captures held and losses together, and the forts are not to be weakened to make it so; making it the default is not proposed from this evidence. What would change it is fire support that Conquest squads actually carry; see the next section.

## Conquest fire support kit: as built

**Mission-declared, like the lances.** `MissionDefencePolicy.declareSupportKit` gives a Conquest's marines a `SquadSupportKit` (every sixth squad of the lift is a mortar team, every fourth carries a comms radio; UNTUNED, first-pass) and `declareAirPool` an AI-directed marine pool of three bombers and two fighters with regeneration, beside the two mech lances. Every other mission declares none. The kit is on `TargetProfile.declaredSupportKit` and in fixture JSON (`declaredSupportKit`), and it reaches the force two ways: a factory force rolls it (`InfantryLoadoutRolls.playerSquad` with the flags, the dice unchanged), and because a launch replaces every seat with the named company's own equipment, `SupportKitIssue` issues it again after the overlay into the designated squads' seats that carry no special (the first seat a tube, the one before the launcher a radio). A company that starts on the ground (Cold Start) has no lift to count by, so `SupportKitIssue.issueGround` counts its squads in commitment order under the same seat rules. A fixture that predates the declaration declares none, so the checked-in matrix and the older fort fixtures play as before until they state it.

**Evidence** (`commanderEvidence -Pmission=conquest`, 36,000 ticks on the fort fixtures, 18,000 on the matrix; captures / held, marine losses, defender losses; kit declared with the lances and pool). Muster plus plans against the trickle on the same fixture: reinforced-south sited 25 / 19 (202, 449) against 21 / 14 (244, 425), the earlier no-kit muster 15 / 12 (189, 376); full-strength-west sited 6 / 3 (429, 531) against 7 / 3 (426, 540), the no-kit muster 10 / 5 (441, 508); the checked-in forts fixture (with lances added) 10 / 6 (226, 466) against 9 / 6 (232, 416), the muster wrecking two aspects. The matrix without forts and without muster, no kit then kit: reinforced-south 27 / 23 (191, 444) then 26 / 20 (115, 427); full-strength-west 7 / 7 (421, 374) then 9 / 8 (413, 462), so the kit does not hurt ordinary Conquest. Marine fire support in those runs: the air pool is used a great deal (22 to 41 calls per fort run, 6 to 11 airframes lost and replaced, 0 to 7 nests killed by air), tubes very little (14 or fewer marine rounds on the south and checked-in fixtures, 52 to 80 on west, 10 to 16 of them on an assigned mission, 3 to 4 nests killed by mortar on west). Ways in: muster took a distance breach on both south fixtures and one breach and one gate on west. Not one fort fell in any run. Muster ahead on one fixture of three and level on the others is not a win everywhere, so it stays off; see the proposal in `conquest-command.md`.

**Why tubes set up so seldom: outranged sections never halt.** A tube waits for a halted section, three quiet seconds and a target in band. Against Longbow skirmishers (32 cells to the company's 22–24) that moment does not come: in A Cold Account the gunner had targets in the tube's band for about 2,700 ticks and was moving or under fire for every one of them, so the tube was carried and never set down. An experimental rule that let a section believing in hostiles in the tube's band, and none inside its gunner's rifle reach, set up at once was measured and then removed rather than left as a switch: Corvus `COLD_ACCOUNT`, one mortar team in two squads, four seeds (base / tube / tube with the switch, marine losses and kills of 38): seed 0 48·10 / 48·10 / 11·35, +1 26·29 / 26·37 / 27·36, +2 48·13 / 48·13 / 10·34, +3 10·33 / 10·33 / 10·37. The switch turned both wipes into wins, but with 3 to 17 rounds per battle; in the turned seeds the tube sets up on first contact (tick ~507) and the company is no longer caught by the single blast that cost the baseline five men in one tick, so this is a change of tempo as much as of firepower. Cold Start (two squads, one tube), eight seeds: the bare company wins 4 with about 6 of 24 alive, the tube with the switch wins 7 with about 13 (the recovered Hound alone wins 8 with about 10, Hound and tube 8 with about 18). Eight seeds of one chapter and four of another are a lead, not a verdict, and the mortar section (below) now opens on an assigned fire mission instead; an outranged trigger would be one more broad trigger to measure against that table before it ships.

What is open: nest kills by mortar and air are a 90-tick heuristic, not a damage ledger; the kit periods and pool are first guesses; the campaign decides whether a player's company carries a tube or radio of its own, and the mission-issued kit only fills seats the company left empty.

## Evidence lives on the trial ground

Phased-assault evidence is taken on the synthetic trial ground (`commanderEvidence -Pmission=conquest-trial`, see `conquest-trial-ground.md`): one landing, one battery fort pinned on the lane (ON_PATH) or 75 cells east of it (OFFSET), one objective, the keep. Conquest numbers (the 560x336 city map, whose generation and commander logic still have many faults) are rough signal only and are not the gate for turning the muster on. The trial fixtures take `muster` (false declares the trickle; `musterPlans:false` and the retired `standingFireMissions` are rejected), `supportKit` (`"conquest"` or `{mortarTeamEvery, observerEvery}`), `mechDrops` (lances of the Conquest composition) and `airPool` (`{bombers, fighters, regen}`); omitted fields leave production defaults, and the neutral trial profile declares none of the mission's kit, lances or pool unless a fixture states them. Reports add fort wall cells opened, company lifecycle counts from the trace notes, and the fire-support and fort-outcome blocks.

**The default trial force cannot muster.** Four twelve-seat Valkyries are four squads: one is the beachhead garrison and the other three are each a lane's whole share, so no squad is ever recruitable and no company forms. The comparison below uses sixteen six-seat Aeroshuttles (96 marines, sixteen squads, about 103 with a lance) against the default 60 defenders; the default matrix is unchanged.

Variants: (a) trickle, production defaults; (b) muster and plans; (c) b plus the Conquest squad kit (mortar team every sixth squad, radio every fourth) and a three-bomber, two-fighter regenerating pool; (d) c plus two lances. Seeds 41–43, one deterministic replay each, serial scheduler. ON_PATH at 18,000 ticks (everything was still moving at 9,000); OFFSET at 9,000.

**Fighter strafe and cover (built; evidence in `sceneEvidence -Pscene=fighter-cover`).** The pool's fighters now fly two errands beside the bomber's strike: a radio-called **strafe** (the existing gun-run flight, chosen by `AirTasking` for open ground under a dozen combatants, left to the bomber for works, armour or a massed group) and **cover**, which `ConquestCommand` asks for through the same comms operator over a company's centroid while it musters with a squad landed or assaults (renewed every 2,400 ticks; the director keeps one fighter back and refuses a second patrol within forty cells). A cover fighter holds a ring, chases hostile armed aircraft inside sixty cells with nose guns, and secondarily takes a gun pass at the densest sighted hostile group when no friendly is within eight cells. The matrix above, twelve seeds per placement, kit and lances declared, a pool of three bombers and two fighters (fighter regeneration 25 s after, 45 s before — a second change the comparison carries): ON_PATH 12 of 12 won both before and after (mean 9,055 against 8,903 ticks, marine losses 41.8 against 46.7, defender losses 51.2 against 51.7, nests killed 78 against 78, by air 27 against 23); OFFSET 12 of 12 both ways (6,140 against 6,083 ticks, losses 12.0 against 10.5, nests 27 against 27). After the change ON_PATH launched 105 bomber, 12 strafe and 22 cover sorties, losing 46 bombers, 11 strafing fighters and no cover fighter. Read honestly: **no effect on the battle shows** and the marine-loss difference is inside seed noise. The trial defenders field no aircraft, so cover has nothing to fight and only spends fighters the strafe would use; the air-to-air guns are exercised in the scene, not on the trial ground. The question to ask next is a threat gate on the commander's cover request, or a defender air pool on a trial fixture.

| ON_PATH, 18,000 ticks | objective taken | keep taken (battle won) | marine losses of ~103 (per seed) | defenders killed of 60 (per seed) |
|---|---|---|---|---|
| (a) trickle | 2 of 3 | 1 of 3 (tick 12,420) | 96, 94, 64 | 17, 5, 54 |
| (b) muster + plans | 3 of 3 | 2 of 3 (9,780; 6,810) | 72, 57, 21 | 47, 56, 52 |
| (c) b + kit + pool | 3 of 3 | 2 of 3 (7,800; 11,730) | 33, 73, 90 | 48, 58, 49 |
| (d) c + 2 lances | 3 of 3 | 3 of 3 (9,090; 10,470; 8,100) | 65, 37, 56 | 55, 57, 46 |

| OFFSET, 9,000 ticks | battle won | marine losses (per seed) |
|---|---|---|
| (a) | 3 of 3 (about 6,150) | 26, 33, 24 |
| (b) | 2 of 3 | 79, 26, 32 |
| (c) | 3 of 3 | 48, 32, 31 |
| (d) | 3 of 3 | 52, 26, 55 |

Readings. With the fort on the lane the trickle loses nearly the whole force (64–96 of ~103) and wins one seed late; every muster variant takes the objective on all three seeds and wins more often, and (d) wins all three with fewer losses than (a) on every seed. Each of (b)–(d) formed and committed exactly one company per run, none broken or stood down. With the fort off the lane the trickle simply walks past it and is as good as any variant, so the muster adds nothing there and costs losses on one seed of (b). **A company now musters only for a fort in the way**: the straight line from the drop zone to some compound still to take crosses its works and belt plus a clearance margin. Off the lane no company forms; on the trial ground's default matrix that took offset-43's keep at 6,270 ticks with 10 marines lost instead of 8,640 and 37, and the rifles-only offset control from a timeout to a win, while the on-path fixtures, where the fort is in the way, played identically. No fort aspect was taken in most runs (a few wrecked: the magazine or a platform); the fort's wall opened in (c) and (d) (tens of cells, air strikes and breach fire) while (a) and (b) opened at most one cell. Nests killed: 4–8 of 8 in every variant, but by the ground in the trickle and (b) and by air in (c)/(d) (1–5 per run); none by mortar. Pool calls were 8–21 per run with 2–7 airframes lost and replaced; tubes were set down 1–5 times per run, 5–28 rounds, 0–9 on an assigned mission. Strikes asked by the company: 0–5.

Three seeds is small and the fixtures are a controlled construction, but the direction is consistent: **on the trial ground the muster beats the trickle where the fort is in the way, and lances plus air win the most.** **Flipped on by owner decision (2026-10-14):** the muster (with plans) is standing behaviour, with no switch, on the trial result, although the Conquest fort fixtures still disagree (ahead on one of three); `-Dbattle.command.muster=false` keeps the trickle control.

**Mortar setup, diagnosed.** Counting each `tryCommitDeploy` call for the two tube gunners over 9,000 ticks (8 runs, kit declared, muster on): the gunner was **not settled** in 69–90% of calls (the section is marching: a gunner holding a path never sets up, and a standing mission does not stop a walking section), the section believed nothing worth a tube in band in 7–24%, the section was under fire in 1–3%, other-members-moving in 1–3%, and the danger ring was never reached. Fewer than 0.1% of calls had an assigned mission at all: the force has only two tube squads in sixteen, standing missions are only given while a squad is 30–46 cells from disclosed works, and a marching squad passes that band in a few pulses (one squad received them on 7 pulses in 9,000 ticks). Plans do assign missions to the base-of-fire squads, and those are the runs with mission rounds. An experiment that held a lane infantry squad in place (bounded 1,500 ticks, then a cooldown) while it held a standing mission changed three of twelve runs and produced no mission rounds in any, so it was removed; (with lances in play it also exposed a mech route search that repeated a whole-map flood for an unreachable goal; fixed in the clearance planner). The answer that replaced it is the next section: not holding the whole squad but detaching a crew and leashing the rest.


## Contested sky on the trial ground: measured

Kit + air fixtures (sixteen six-seat Aeroshuttles, `supportKit` conquest, two lances, attacker pool of three bombers and two fighters with regeneration), six seeds (41-46) per cell, real hull speeds (`InstalledHullEngines`), defender radios every second infantry squad. Cells: (a) no defender air, (b) a defender pool of three bombers and two fighters called through those radios, (c) (b) plus the fort's anti-air yard. Fort on the lane played to 18,000 ticks, offset to 9,000.

| fort | cell | marine wins | mean ticks | mean marine losses | attacker airframes lost (mean) | defender sorties launched (mean) | defender bomb sorties (total) | air-to-air kills (total) |
|---|---|---|---|---|---|---|---|---|
| lane | a | 6 of 6 | 8,545 | 38.0 | 5.7 | 0 | 0 | 0 |
| lane | b | 5 of 6 (one timeout) | 10,395 | 56.8 | 6.8 | 1.3 | 3 | 4 |
| lane | c | 6 of 6 | 9,259 | 56.5 | 7.3 | 0.8 | 0 | 0 |
| offset | a | 6 of 6 | 6,050 | 10.7 | 0.3 | 0 | 0 | 0 |
| offset | b | 6 of 6 | 6,115 | 10.7 | 0.8 | 0.2 | 0 | 1 |
| offset | c | 6 of 6 | 6,155 | 8.8 | 1.7 | 0.2 | 0 | 0 |

Readings, not rates beyond six seeds a cell:

- **The sky is barely contested.** Defenders launched one sortie a run on the lane and a sixth of one offset, mostly cover patrols (the director's reason to ask is a hostile armed aircraft over its units); only 3 defender bomb sorties flew in 12 lane runs, two of them landing on marine groups (40 and 46 marines within twelve cells at the call). The cause is the radios, not the stock: of the defenders' operators (typically 2-5 at the start) few survive to see a marine group, so the pool sits full. Attacker airframes still die, almost entirely to the ground AA bubble (typically 3-7 a run on the lane, none to fighters in the no-defender-air cells), not to defender fighters (4 air-to-air kills in 12 lane runs).
- **Marine losses rose with defender air on the lane** (38.0 to 56.8, one timeout) and the (c) mean equals (b)'s, but six seeds with one timeout (seed 41, 90 marines lost, 11 attacker airframes lost) is a seed-dependent reading and not a measured effect of defender air. The offset fort was untouched.
- **Bombers downed before release: zero, either side**, in the trial and in the `fighter-cover` sweeps. The tail chase turns misses into kills (23 of 24 raiders downed against 0 for head-on lead pursuit at real speeds) but all of them during or after the stick; see `air-nouns.md` Air support pool.
- **The yard is the plan's cost, not its target.** With the yard the attacker lost more airframes on the lane (7.3 against 5.7) and the marine plan never went for it as a goal: the yard ended with all three mounts standing in some runs and wrecked in others, as a by-product of the push on the fort. Whether it is worth taking is unanswered by this evidence: a company that opens the sky first is still the open question.

Not claimed: defender strikes on a landing zone or muster at scale (none were seen; the fixture field `defenderAirPool` and `ContestedAirProbe` are the instrument for it), and any balance conclusion; a stronger defender signal needs more radios surviving, which is a mission-design choice.

## Radar first: measured

The spotting result above had the company reach the bunker late. **Why it was late was the plan's step order, not the muster.** On the lane fort the company formed at tick 1,351 to 1,426 and committed at 1,726 to 2,101 in every run, then spent 4,000 ticks in position, suppress and entry before its objective step took aim at the bunker (tick 6,001 to 6,301, four runs of six; the other two never reached the step).

What is built. A company that has been flown at (`SkyNeed.flownAt`, the side's own sight of hostile armed aircraft, sticky for the run) and has a hostile bunker disclosed (`FortTactics.hostileRadar`) skips the set-up steps and goes to its objective step, breachers going in with the assault (`FortAssault.Execution.radarFirst`). A muster that is flown at commits at `AssaultMuster.AIR_RAID_COMMIT_SHARE` (half) of what the objective is believed to take, without waiting for a filler or the timeout, and a stand-down does not outlast it. A plan already on another aspect turns to the bunker instead of holding the one it was on, while that one is still work.

**It found a command defect on the way.** The first version jumped to the objective step and left the breach role unconverted; that step orders only assault and fire squads, so seven of the eight squads of a committed company got no order and fell back to their lane. Fixed in the same jump (`convertBreachers`), with a unit that fails without it.

Same fixture as above (kit, two lances, attacker pool, defender pool through radios on every second infantry squad, fort anti-air yard, fort spotting on), seeds 41-46, base `6e83d8266`, one run each:

| fort | | marine wins | mean marine losses | mean defender losses | nests killed (mean) | defender sorties (mean) | first aim at the radar | bunker taken / wrecked |
|---|---|---|---|---|---|---|---|---|
| lane | before | 0 of 6 | 95.5 | 15.3 | 3.7 | 16.2 | tick 6,001-6,301 in 4 runs | 0 / 0 |
| lane | after | 0 of 6 | 94.8 | 40.3 | 5.5 | 11.5 | tick 1,801-2,101 in 6 runs | 0 / 0 |
| offset | before | 5 of 6 | 22.5 | 40.2 | 2.2 | 2.8 | no company | 0 / 0 |
| offset | after | 5 of 6 | 29.0 | 40.8 | 2.7 | 2.8 | tick 4,126 in 1 run | 0 / 0 |

Read honestly: **the company now arrives and the fort's defence dies (defender losses 15 to 40, nests 3.7 to 5.5, whole courtyards cleared by tick 5,000), but no bunker is taken and no lane run is won.** Defender sorties fell from 16 to 11.5 a run but marine losses did not move. The offset fort is untouched except one seed where a company formed for the sky and lost 83 against 44. The blocker is now the take itself: the bunker room is 35 cells and no defender ever stood in it, yet at most one marine was ever inside at once and none at the end (`BunkerProbe` in the trial report, `bunker` block). The company walks the doorstep order, clears the fort and drifts out through the east gate without holding the room for the twelve seconds a take needs. That is the next thing to look at; it is not a muster or step-order question. (Answered below in "Taking the room: measured".)

## Taking the room: measured

**Why nobody went in.** It was not the doorstep: the cell the company was sent to lay on the bunker's floor. It was the order. Every squad of the company carried an attack move to that cell, and squads under one attack move share their contact and divide it into a base of fire and a maneuver round it (`AssaultCoordinationSystem`). The contact they shared was the **gun platform**, twenty cells off the bunker, so for 1,200 ticks and more the company stood five to eight cells from the bunker's door fixing and flanking the guns, and nobody stepped onto the floor. In four of six runs the company eventually killed the platform first, and that ended the take outright (below).

**What is built.** A company squad (infantry, not wrecking) within `DefenceStructureTactics.OCCUPY_REACH_CELLS` (12) of the building an aspect is held in is told to **occupy** it: an area defence of radius 2 on the building's deepest floor cell, which lies wholly on the floor, so its members walk in and fight from where the take is counted. It keeps the order while within a six-cell margin and is sent back by attack move beyond it. A squad further out keeps the attack move, now to that deep floor cell rather than the doorstep ring: an attack move is done within two cells of its cell as the crow flies, and a cell beside the wall is that close to the courtyard on its far side. The floor is read from the fort's disclosed works frame (`floorOf`), never from a sighting, and applies to every aspect held in a building (bunker, plant, magazine, control room), not to the open anti-air yard.

Same fixture as "Radar first" (seed 41-46, lane fort, 18,000 ticks), base `15b0aa98e`, one run each, both columns with the corrected report (below):

| | marine wins | mean marine losses (of ~105) | mean defender losses (of 65) | nests killed (mean) | defender sorties (mean) | bunker taken | first marine on the floor | platform destroyed while the bunker stood |
|---|---|---|---|---|---|---|---|---|
| before | 1 of 6 | 94.3 | 30.7 | 4.5 | 10.0 | 0 of 6 | 4,500 / 5,880 in 2 runs (peak 1 and 4 inside) | 4 of 6 |
| after | 5 of 6 | 51.7 | 59.5 | 7.8 | 4.8 | 6 of 6, ticks 3,060-3,900 | 2,700-3,540 (peak 19-35 inside) | 0 of 6 |

After the bunker the company works down the fort in every run (anti-air yard 4,020-4,860, plant 5,040-5,910, magazine 6,450-7,230) and the keep falls at 8,850-11,010 in the five wins; seed 42 times out with the fort taken and the keep standing. One defender-loss figure in the before row is negative (seed 42, reinforcements outnumber losses); the mean includes it. Not a balance conclusion: one run a seed, and the fort is unchanged.

**Two things found on the way, both outside command:**

- **A dead gun platform froze the bunker (fixed).** `FortressBatterySystem` skipped a destroyed battery before its fire-control tick, so once the platform died the bunker's capture never advanced again, while `FortSensors.observer` still had the radar spotting for the side that held it, and `BattleSimulation.defenceStructures` dropped every aspect of that fort from the command's facts, so the company's orders lapsed with no trace note. That was how four of six lane runs ended their assault before the occupy order. Now a fort's bunker stays a place while it stands (`FortressBattery.fireControlStands`): its capture still ticks, and the command is still told of it and of the plant that turns its radar, not of the magazine, which fed only the guns (see `forts.md`, "the fort spots for its side's air"). The lane matrix cannot reach this path any more (twelve seeds, 41-52: the bunker falls at 3,000-3,900 in every run and the platform never dies first; seeds 41-46 replay unchanged), so it is pinned by the `battery-fort` scene loop `guns-destroyed-bunker-taken`: on the old code the bunker never changes hands, the radar keeps spotting for the defenders and the command is told of nothing; with the fix all four verdicts pass.
- **The trial report read the wrong holder.** For fire control the authority is the battery's `FireControl`, not the place's own control, and the report's aspect lines and holder-change list read the latter, so a taken bunker read DEFENDER. `FortOutcomes` now reads the battery; `BunkerProbe` adds the take tick, the platform's death and a timeline of marines on the floor and near it with the capture state.

## Contested sky with the fort spotting: measured

Same kit + air fixture (sixteen six-seat Aeroshuttles, `supportKit` conquest, two lances, attacker pool of three bombers and two fighters, defender pool of three bombers and two fighters through radios on every second infantry squad, the yard now two Ridgewatch batteries and flak), seeds 41-46, lane fort to 18,000 ticks and offset to 9,000. The control is `fortSpotting:false` (the fort still sees; nothing is called through it; the director's sight-filtered targeting stays). One run each, so six seeds a cell.

| fort | fort spotting | marine wins | mean ticks | mean marine losses | defender sorties (mean) | defender bomb sorties (total) | sorties on LZ / fort / open ground | marines within 12 cells at the call (mean) | calls through the fort | attacker airframes lost (mean) | plan went for the bunker (runs) | bunker taken / wrecked | yard taken / wrecked |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| lane | off | 6 of 6 | 9,505 | 47.8 | 0.8 | 0 | 0 / 3 / 2 | 3.6 | 0 | 2.3 | 2 | 0 / 0 | 0 / 0 |
| lane | on | 0 of 6 (all timeouts) | 18,000 | 95.8 | 18.2 | 60 | 0 / 38 / 71 | 19.3 | 108 | 2.0 | 5 | 0 / 1 | 0 / 2 |
| offset | off | 6 of 6 | 6,830 | 24.5 | 1.0 | 0 | 2 / 1 / 3 | 9.2 | 0 | 1.7 | 0 | 0 / 0 | 0 / 0 |
| offset | on | 5 of 6 (one timeout) | 6,745 | 21.0 | 3.0 | 2 | 2 / 9 / 7 | 9.2 | 12 | 2.0 | 0 | 0 / 0 | 0 / 1 |

- **The sky is contested now, on the lane.** Every defender call in the spotting cells went through the fort (108 of 108); the radios the defenders carry were not what called. Defender bomb sorties went from 0 in six runs to 60, each on a concentration the fort had in sight, with 19 marines within twelve cells at the call on average (a heuristic, not a damage ledger). Marine losses doubled (47.8 to 95.8) and no lane run was won in 18,000 ticks. Offset the fort is far from the line and the effect is small (3.0 sorties a run, five of six won).
- **Whether the fort is worth taking for the sky: on this evidence yes, and the attacker does not manage it.** The plan now treats the hostile bunker as the first work when its units are flown at, and a company took aim at it in five of six lane runs, but only at tick 6,000 to 16,500 (the company reaches its objective step late, after base of fire and entry), and it took the bunker in none and wrecked it in one; the yard was wrecked in two. The fort is the only thing that stops the sorties and the company reaches it too late.
- **Not a balance conclusion.** The defender pool, regeneration and the 48-cell radar are first-pass; forts are not weakened for this. Marine losses are not attributed to bombs; only exposure at the call is measured. The off cells also carry the director's sight-filtered targeting (an earlier cell reached 0 to 3 sorties with the omniscient pick), which is why off differs a little from the earlier table. Evidence: `commanderEvidence -Pmission=conquest-trial` with fixture field `fortSpotting`; scene loops `fort-spots`, `spots-control`, `spots-wrecked`, `spots-unpowered`, `spots-taken` in `fort-air-defence`.

## Mortar sections: as built

**The behaviour.** A squad whose gunner is marching, and whose assigned fire mission has a target in the tube's band, opens a **mortar section**: a crew of about four (the gunner's fire team, topped up to four with the nearest squadmates) halts and sets the tube down, and **the rest of the squad is leashed to the tube** (8 cells; the crew stands within 3.5 so the loader serves it at the authored rate). The leashed marines may fire at what is already in range with a clear shot and start nothing; a marine outside the leash walks back to it. The section holds a **window** (30 seconds from activation; the tube needs four to set down and three to lift) after which the crew packs up and the squad **reforms and goes back to its order**, unless the tube is **engaged** (it has had a target in the last 8 seconds), which holds the deadline ten seconds ahead of itself up to a **cap of 90 seconds from activation**: the whole fourteen-round magazine at the crewed four-second cycle is 56 seconds, plus set-down and lift, so a section that has not emptied its magazine by then has stopped being the reason the squad is standing there. A section ends when its tube is gone, with a per-squad cooldown (45 seconds) so a squad does not set up every few seconds along its march. A squad under fire ends its section at once (the leash is not a reason to stand in the open being shot at), a broken fire team is let go by the leash (the morale law first), and a squad under a player's order or with a marine under direct control is never leashed. A section also closes early once its standing tube has had no target for 25 seconds (the tube's own idle pack). The timings are named constants in `MortarSections`, not properties. Only a marching gunner opens a section: a halted squad is served by the ordinary set-up, unleashed.

**What opens one is the narrowest trigger.** Only an assigned fire mission with a target in band. Opening for a believed hardened target (a nest, a hub, a machine), or for any belief a tube would set down for on a halted section (the trigger the first design asked for), were built and measured, and the broad ones lose; they are no longer in the code, and the table keeps their rows as the reason.

**Evidence on the trial ground** (`commanderEvidence -Pmission=conquest-trial`; fixture of the previous section, variant c: muster, plans, Conquest kit and pool, sixteen six-seat Aeroshuttles; seeds 41 to 76, 36 per placement, one deterministic serial replay each; ON_PATH 18,000 ticks, OFFSET 9,000; `old` is sections off, measured when that switch existed):

| trigger | placement | battles won | marine losses (of ~103, mean) | median keep tick | tubes set down | tube rounds | rounds on a mission | sections (seconds held) |
|---|---|---|---|---|---|---|---|---|
| old | on path | 35 of 36 | 47.1 | 8,580 | 131 | 484 | 24 | 0 |
| any | on path | 31 of 36 | 53.1 | 9,240 | 197 | 611 | 20 | 113 (3,245) |
| hardened | on path | 33 of 36 | 44.7 | 8,340 | 165 | 585 | 51 | 56 (2,121) |
| **mission** | on path | 36 of 36 | 45.2 | 8,430 | 136 | 510 | 57 | 7 (353) |
| old | offset | 33 of 36 | 39.6 | 6,600 | 90 | 276 | 39 | 0 |
| any | offset | 30 of 36 | 45.0 | 7,200 | 153 | 589 | 58 | 101 (3,416) |
| hardened | offset | 28 of 36 | 45.9 | 6,690 | 118 | 390 | 64 | 37 (1,233) |
| **mission** | offset | 33 of 36 | 40.3 | 6,540 | 95 | 320 | 73 | 8 (344) |

Readings, honestly. The broad triggers do what they were built to do (tube set-downs up by half or more, tube rounds up by a quarter on the lane and more than doubled off it), and it does not pay: nest kills by mortar do not rise, 20 and 22 of the `any` sections were ended by the squad coming under fire, and the battle is lost more often with more marines. Why is not measured; the likely cost is a squad standing leashed for half a minute while the rest of the plan (the muster's rally, a company's timing) waits for it. **The mission trigger leaves the battle where it was (36 of 36 against 35 of 36 on the lane, 33 against 33 off it; the loss differences are inside what a seed does) and raises the rounds fired on an assigned mission from 24 to 57 and from 39 to 73**, which is the use the plan and the standing mission were written for. It opens a section in only 15 of 72 runs and never more than one: the two tube squads of sixteen rarely have the mission target in band while marching and outside a nest's 26 cells. Nest kills by mortar stay at 2 to 6 of about 250 nests (the attribution is a 90-tick heuristic) while the air pool kills 2 to 5 per run, so **none of this makes the mortar a nest killer**; it makes it do its assigned job more often. A first sample of three seeds was mixed and was not believed; thirty-six per placement is the sample above. A twelve-seed variant that closes a section after eight idle seconds instead of waiting out the window did not separate from its parent for `any` or `mission` and is not adopted.

**Scenes.** `field-mortar` loops `section-march` (a twelve-marine squad with a fire mission on a battery fort marching on its gate: the crew halts at 60 cells, sets down in four seconds, fires, the rest stays within the leash, the window runs out, the squad goes on), `section-expiry` (a ten-second window engagement may not extend), `section-engaged` (the same window an engaged tube extends to a cap) and `section-control` (sections off on that simulation alone, the one remaining control: the squad marches into the fort and loses all twelve, no tube is set down). Frames: `createSnapshots -Psnapshot=field-mortar`. Unit tests: `MortarSectionWindowTest` (the window, the extension, the cap) and `MortarSectionTest` (the activation, the leash, the stage machine, the cooldown, the mission trigger, idle close).

What remains: the broad trigger would want a different leash (a smaller crew-only hold, the rest marching on) to stop costing the muster its squads; a tube squad is two in sixteen and the mission is given only 30 to 46 cells from works, so sections are rare; the shells land on men because nothing steers a tube to a nest it has not been given. With the broad trigger the `plan-mortar-air` loop failed (a section shelled the plan's nest while the company was still forming, so no tube had a target when it arrived); the default mission trigger passes it unchanged.

## Breachers reach the wall: measured

**Why nobody got there.** Three faults compounded, found by following the breacher squads of `assault-muster` tick by tick. (1) The plan knew only the four bastion nests; the fort's outer belt carries four more guns of the same kind (`FortBelt`), so the breach section "farthest from every nest" was farthest from half of them and a close breacher's place at the mouth read as outside every nest's reach. It now reads `FortPlan.defenceNests` (bastion nests, then belt nests), for covering sets, the breach pick and the nest belief; absent belt nests are resolved by the existing unseen-in-clear-view rule. (2) A breacher was ordered to its place on the first pulse, in the position step, ten cells ahead of the base of fire: a ten-man satchel squad was whittled to one by nests 4 cells off its route before suppression began. A breacher is now held with the assault at the staging cell until the covering nests are believed dead, there is no fire element, its place is outside every live nest's reach, or the suppression window has run (`FortAssault.Execution.breachMayGo`). (3) The wall's clock (`BREACH_TIMEOUT_TICKS`) ran from the suppress step, so a breacher on the far side of the fort could be timed out while still walking; it now runs from a breacher standing at its place, the walk bounded separately by distance (`BREACH_WALK_TICKS_PER_CELL`).

**Readings** (`sceneEvidence -Pscene=assault-muster`, six seeds, all loops pass). The breach moved to the east face (mouth 174,205). Before, no breacher came within 24 cells in any run and no charge was set. After: the cannon mech was in reach of the wall with the round ready in 3 of 6 seeds (12345, 777, 31337), and on 777 cut the wall through at tick 5812; the satchel squads then still died on the walk (released at the suppression limit; superseded by "Satchel squads keep holding"). That is the fight, not a stall: the report now carries each breacher squad's course (`no-breacher-stood-idle-short-of-the-wall-in-no-contact`, `a-close-breach-waits-for-the-base-of-fire`) and no verdict asserts arrival, since arrival depends on surviving the nests. Trial ground, lane, kit and air fixture, seeds 41 to 46, 18,000 ticks, before then after: battle won 5 of 6 then 6 of 6; bunker taken at a mean of 9,250 then 7,330 ticks; marine losses mean 60 then 38; wall cells opened 46 to 111 then 46 to 95. One run each, not a balance conclusion.

## Satchel squads keep holding

A close breacher (satchel or cutter) is not released by the suppression window; only a distance breacher is. A held sapper advances along its walked route while no believed-live nest reaches and sees the cell, and waits at the last such cell. It goes in only when the stretch beyond that is survivable: every covering nest on it is believed dead, suppressed (a friendly fire squad has stood engaging that specific nest for the settle time), blinded (a standing smoke cloud on the line to every exposed cell, with enough life left for the crossing and the charge dwell), or cannot see the route. The company works the nests that are not yet answered with its cheapest available enabler: an engage by a spare fire squad, a smoke screen thrown by grenadiers, or an air strike. An enabler already in motion is waited on, not asked again. Effects feed back through the nest belief, so a nest believed dead meets the precondition.

A breach role is only as good as the kit behind it. Until a breacher is let go it is re-read each pulse against the live profile of its living members: a squad whose carrier fell while it was held is taken off the breach, another squad of the company that carries the kit takes the role, and with none the company changes approach rather than hold an empty squad or send it to a wall it cannot open. A breacher already let go is left alone, since a charge it has set has spent its kit by design.

The bound is mechanical: when nothing has progressed for a suppression window plus one strike flight, the company changes approach rather than spending the sappers, to a staffed distance breacher, otherwise the least-covered gate. Smoke is rarely feasible at fort scale because a cloud stands twelve seconds and a walk across the exposed stretch is longer; the planner declines it then.

**Readings** (`sceneEvidence -Pscene=assault-muster`, `breach-charges`, six seeds; seed 4242 fails its existing plan-variant verdict, the company took the main gate). Two seeds went in: 12345 released at tick 5026 after 3,150 held, cut the wall at 5860, closest approach 2 cells; 777 released at 5326, cut at 6130. Seeds 99, 31337 and 20261016 held to the end of the run with no sapper lost; 12345 and 777 each lost the one sapper squad on the plant. `combined-arms` (fire and air) released on 20261016 and cut at 5655, and changed approach to the gate on seed 99 at 6301. Trial ground lane, kit and air fixture, seeds 41 to 46: identical before and after, because that company carries no satchels. What remains: holding is not yet converted into a decisive approach on three of six seeds, and a stronger enabler (suppression that is mechanical rather than a settle time) is not modelled.

## Smoke screens and the partition

**The finding that started it.** A grenade cloud lasts twelve seconds; crossing an exposed stretch and planting a charge takes longer, so the planner declined smoke every time and smoke was never used. The owner's direction: a longer screen is useful, and it is double-edged, because whatever it covers is stranded from support on the far side of it. A screen is a **partition**, not just a blindfold for the enemy.

**The long screen.** No long-burning smoke item ships: the mission-kit smoke pot was cut for the first release, and a squad's screener kit now carries smoke grenades. `SmokeGrenadeSpec.thinSeconds` still lets a long cloud thin before it goes (its radius holds for `coverSeconds`, then gives back its rim cell by cell down to a core of 40% of the radius, `SmokeFieldService.CORE_FRACTION`), so a future long screen is data. Mechanics are unchanged: a cloud adds transient opacity to its cells, cutting line of sight both ways and degrading accuracy through it (`SmokeObscuration`); it does not stop bullets. No wind drift yet.

**The partition is a fact.** `SmokePartition` answers, for any set of standing clouds and a tick, which sight lines are cut (`cuts`), which pairs of positions are cut from each other (`cutPairs`), and whether a place is isolated from a set of others (`isolates`). `SmokeCloudFact` carries what a cloud *holds* (its full radius and the seconds it stays whole), so planners and fair-knowledge observers read the same fact; a thinning cloud is not counted as whole. `SatchelApproach.blinded` now delegates to it.

**The planner weighs both edges.** `SatchelApproach.weigh` refuses a proposed screen that newly cuts a line the plan relies on: `BLINDS_BASE_OF_FIRE` when it cuts a fire squad's line to the nest it is suppressing, and `STRANDS_SAPPERS` when it would sever the sappers' route from the support they stand on. Only cuts the proposal *newly* makes count, so an existing screen does not veto itself. A refusal is recorded (`smokeRefusedBlindsBase`, `smokeRefusedStrandsSappers`) and the company falls through to its next enabler. The screen is offered only when some throwing squad's `CapabilityProfile.screenSeconds` covers the crossing; grenades (12 s) cover none at fort scale, so until a long screen ships the planner falls through to its next enabler there.

**What a screen is weighed against.** The suppressions a screen must not cut are every fire squad placed against a nest believed live, answered yet or not, read from the cell the plan put it on and from where it stands now (`SatchelApproach.reliances`). A first version counted only nests already read suppressed, from the planned cell alone, so a screen ordered while the base of fire was still walking in was thrown across lines nobody yet relied on, and the squad was not where the plan said anyway. A screen is also re-weighed every pulse until its last cloud is thrown, because a throw is ordered many pulses before it is made while the base of fire moves; a screen that would now cut a relied-on line is withdrawn (clouds already standing stay). The evidence reading `ownFireBlindedTicks` is the base of fire's own line to its placed nest only; every marine with smoke on its line to a nest (a sapper walking inside the cloud, an assault squad waiting behind it, which is the screen working) is `anyMarineBlindedTicks`. The first reading conflated them, and its 10,000 to 17,000 ticks were mostly the second.

**Kit.** `SquadSupportKit` gains `satchelEvery` and `smokeEvery` (default 0, so existing fixtures are unchanged), issued by `SupportKitIssue` into seats nobody else holds, on the named-roster path and on a factory-rolled Conquest force alike (`BattleSetup.populateConquest` used to issue only the tube and the radio, so a fixture declaring sappers on the trial ground declared them for nothing); fixtures carry them in `supportKit` (`{"base":"conquest","satchelEvery":3,"smokeEvery":3}` on the trial ground).

**Evidence.** `sceneEvidence -Pscene=smoke-screen` is the mechanics: a sapper squad crossing 64 cells to a heavy-MG nest, open versus screened versus a screen laid across the base of fire versus a thinned screen, with verdicts that the screen stood before the crossing and through it, that sight is cut in both directions, that the base's line is cut only when the screen is placed across it, and that the sappers cross inside the cloud. The headless renderer did not draw smoke at all until `HeadlessBattleSprites` loaded the smoke sheets; the GIFs now show the cloud. In `assault-muster` the `breach-charges-pot` loop gives every third squad pots. Six seeds (99, 12345, 777, 31337, 4242, 20261016) after the weighing above: base-of-fire blinding `ownFireBlindedTicks` is 0 on all six (it was 150 to 1,005 ticks per seed before, on the corrected reading); pots stood on four seeds (1 to 2 clouds) and were refused 14 to 43 pulses per seed for cutting a line the company relied on; `anyMarineBlindedTicks` (sappers and assault squads inside or behind a cloud, which is the screen working) is 0 to 5,040. No sapper was released *because* of a screen (`satchelReleasedBlinded` is 0 on those six and on twelve further seeds, 1 to 11 and 13): the nests die to ordinary fire, or the screen is refused, before the pot is thrown. That is the mechanism working, not a defect: smoke is the second-cheapest enabler and the fort puts the nests' lines across the base of fire's. `FortAssaultTest` pins the other half, that a nest whose sight to the route a standing cloud cuts for longer than the crossing reads blinded and releases the sapper, and that a cloud with less life than the crossing does not. Wall opened: 4242 only (tick 7,442) with pots, against 12345 and 20261016 without. Read these as findings, not as a verdict on smoke: it is a tool the planner now owns, weighs honestly, and rarely needs on this fort.


**Next, in order.** Mortar smoke rounds (a screen laid at range with the existing `FieldMortarService` machinery, so no one walks into the cloud's edge to lay it). Drift and thinning by wind. A defender use of the same fact (a defender screens a gap to strand an attacking company from its base of fire; the symmetric half of the partition). Using the partition between strongpoints to decide which garrisons can reinforce which. A pot economy (an acquisition source and cost) so it can leave mission-kit.

## Slices

1. **Foothold and muster** (built, on by default) — attacker phases, a rally point at the
   defended drop zone, assault companies formed from squads, a readiness judgement
   (counting the defence) with a timeout and a bounded stand-down, and commit; the
   beachhead is held and the lanes keep half their squads.
2. **Commander-called mech drop** (built) — the attacker can call a lance to the drop
   zone from its mission allowance; the lance joins a company.
3. **Fort assault plans** (built, off with the muster) — per-stamp plan data for the battery
   and shield forts and a plan executor driving a company through base of fire,
   entry, objective and hold.

Evidence for each is a scene (a single fort and a company) plus the Conquest
fixtures that declare forts, compared before and after.
