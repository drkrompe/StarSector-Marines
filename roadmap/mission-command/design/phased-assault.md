# Phased assault

Status: ACTIVE — direction for how a large attacking force lands, masses and
assaults a defended position. Slices 1 (foothold and muster), 2 (the mech drop) and 3 (per-stamp assault plans) are built and are standing behaviour (owner decision 2026-10-14); a mission may choose the trickle instead (`AssaultStyle.TRICKLE`, `TargetProfile.declaredAssault`).
Slices are on the mission-command board.

Written: 2026-10-13

Updated: 2026-10-14 — standing fire missions and the mortar section are standing behaviour (their switches and dials are gone; timings are constants, sections-off is a per-simulation scene control); mortar use is a squad behaviour, the mortar section (see Mortar sections: as built); outranged sections never set a tube down (an outranged trigger was measured on A Cold Account and Cold Start and removed); the kit now reaches ground-start companies.

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
Sirocco for its marines, every other mission none. The landed lance is claimed
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
  Bulwark, two Hounds and a Sirocco) calls one per company, claimed and recruited as designed.

## Fort assault plans: as built (slice 3)

**The plan's data lives with the stamp.** `FortPlan.approaches()` exports the
ways in: each gate as an `AssaultApproach` (the main gate is the plan's first,
the others are posterns) carrying its mouth and outward step, an entry cell four
cells through it, the nests whose reach covers the mouth, and its approach lane
out across the belt (the cells the belt and wire are left open over), plus a
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
reaction to being counter-attacked at its rally, a defender-side use of the same
machinery (nothing in it is attacker-only), retreat of a company that is failing
but not yet broken, plans for any stamp but the two forts.

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
