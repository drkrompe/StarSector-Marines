# Phased assault

Status: ACTIVE — direction for how a large attacking force lands, masses and
assaults a defended position. Slices 1 (foothold and muster), 2 (the mech drop) and 3 (per-stamp assault plans) are built, off by default
until the evidence below beats the trickle. Slices are on the mission-command board.

Written: 2026-10-13

Updated: 2026-10-14 — empty-rally defect fixed (no company around nobody, stalled recruits released, foothold only while a company exists); air strike is a capability a plan may use (see Air strike as a capability); the field mortar is a standoff anti-structure and suppression capability source (`special.field-mortar`); slice 3 built (per-stamp assault plans, `FortApproaches` / `FortAssault`), muster off by default; capability profiles and role assignment are built (plans choose their way in by what the company can staff, breaches are fired at a wall on purpose, see Capabilities and roles).

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
so a spotter inside sight of the nests is part of the plan's base of fire; there is no way yet to *assign* a fire
mission to a tube (open on `stories.md`). Evidence: scene `field-mortar` (a section with a tube against a fort's
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
with `ConquestCommand` as its adapter. **It is off by default**:
`battle.command.muster=true` turns it on, and off the attacker detaches one or
two squads at a time to a disclosed fort, which is the control. Scenes and
evidence turn it on explicitly; it stays off until it beats the control on the
fort fixtures (see "What the evidence says"). Phases are published per objective
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
  known, or with `battle.command.musterPlans=false`, orders every squad at the
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

## Air strike as a capability

A company that brings comms operators and a side with a declared air pool carries an **air strike** capability, read from `AirStrikeCapability` (pool stock plus living operators) rather than from hand-listed assets. A fort plan may spend it on a single nest or a gun it has sighted ahead of the assault; `AirStrikeDirector.strike` applies the same risk ceiling the AI uses, so a heavily defended fort is not softened from the air and the plan falls back to its ground steps. Role matching stays with the capability profile.

## Slices

1. **Foothold and muster** (built, off by default) — attacker phases, a rally point at the
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
