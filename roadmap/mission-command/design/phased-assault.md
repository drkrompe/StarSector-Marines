# Phased assault

Status: ACTIVE — direction for how a large attacking force lands, masses and
assaults a defended position. Slices 1 (foothold and muster), 2 (the mech drop) and 3 (per-stamp assault plans) are built, off by default
until the evidence below beats the trickle. Slices are on the mission-command board.

Written: 2026-10-13

Updated: 2026-10-14 — slice 3 built (per-stamp assault plans, `FortApproaches` / `FortAssault`), readiness counts the defence, lanes keep a share, a muster stands down rather than commit a sliver; `battle.command.muster` now defaults off.

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
  normal case in the scene; the Conquest fixtures never showed a lance call, so a
  Conquest comparison with a lance in play is still open.

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

What is open, in the order it matters: companies still stand at a rally with
nobody at it (recruits drift or are drafted back), which a stand-down contains and
does not cure; the lane share was a measured change and not a tuned number; a fort
with live nests is, for infantry alone, a position no plan takes, so the plan's
value depends on machines the Conquest fixtures never call; breach is exported,
chosen and ordered but nothing in the game shoots at a wall on purpose, so a
company sent to a breach reaches the wall and is no better off than at a gate.

Not built here: recruiting whole named-officer groups into a company, a company's
reaction to being counter-attacked at its rally, a defender-side use of the same
machinery (nothing in it is attacker-only), retreat of a company that is failing
but not yet broken, plans for any stamp but the two forts.

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
