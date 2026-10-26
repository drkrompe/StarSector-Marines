# The Cordon — how the commanders use the allies

Status: ACTIVE — the owner's picks are in (`cordon-answer-call`: a two-squad
relief; `cordon-urban-hammer`: two hammers; `cordon-beat-escort`: the
sector's armour, the company only where none is free). Both commanders are
built: the company's (`CordonRadio`, `CordonCompanyRules`, behind
`CordonCommand`) and the allied line command's (`CordonAlliedRules`, behind
`CordonArmour`, `CordonLogistics` and `CordonLineCommand`, which draws on the
base as a producer), and the company's lift (rule 10).

Written: 2026-10-23
Updated: 2026-10-24 — a convoy is Escorted only by its whole escort; the swarm on a truck calls the company

Step 4 of the owner's plan for the Cordon: how the AI commanders use the
allied forces and their tools. `cordon-nouns.md` is the model (*The allies
hold, the company decides*); `cordon-allied-forces.md` is what the allies
field at each density, over the shared catalogue (`allied-tools.md`);
`allied-command.md` is how a machine carries out a task once it has one. This
doc is the step between them: who decides what, on what they know, and how
the company and the allies hand work to each other. Strikes at the bores and
the line posture are `cordon-nouns.md`'s (Line posture).

## The two commanders

**The allied line command** decides where the allies' machines and squads go:
which sector a hull or mech holds, which redoubt a carrier takes a squad to,
which hulls escort a convoy or a beat's truck, where a walking column goes. It
is the Cordon's, not `AlliedCommand`'s: the mission-neutral command only
carries the decisions out (station, carry, a mech's station). Today the
decisions are spread through `CordonArmour.advance`, `CordonLogistics.advance`,
the columns' orders (`CordonLogistics.columnOrders`) and the beats that drive
their own hulls (`GunsDryBeat`, `ClearRoadBeat`).

**The company commander** (`CordonCommand`) stands in for the player in
evidence. It commands only Marines, hears the allies' radio and sees the swarm
through its own squads (`cordon-nouns.md`, Fair knowledge). Its job is to
decide when to lean on the allies and when to act itself.

**The player is better than this commander, and that is allowed.** A
commander's failure in evidence is a commander fault to fix in the commander,
never a reason to retune the allies, the swarm or the timetable. The rules
below are what a careful player does, written so they can be tested one at a
time; they are not tuned to make `cordon-line` evidence come out held.

## What they did before the allied line command, against the table

The table (`cordon-allied-forces.md`) gives the company a role by density:
**rural, the reserve; town, the hinge; urban, the hammer behind the mechs.**

The allied side:

- **Nothing reads the density.** Every Cordon fields the rural set: four cars
  and technicals, one carrier, the shed's line of carriers and cars
  (`CordonSetup.BASE_LINE`). A town's armour is not held to its streets, a
  city's carriers drive into its blocks, and no Hound stands anywhere. The
  table itself is step 3, built in parallel; this doc assumes it lands.
- **The armour keeps sectors** (`CordonArmour.emptiest`: fewest hulls first,
  calling then nearest breaking a tie), which is right.
- **Escorts can strip a sector.** A convoy's three guard posts each take the
  nearest hull from anywhere (`CordonArmour.escort`), with no cap per sector.
  The gap's redoubts' hulls are nearest, and the gap is where the Road's tide
  falls.
- **Carriers chase calls.** Between carries a carrier goes to the nearest
  calling redoubt (`CordonLogistics.support`), the very behaviour the armour's
  sectors replaced because on a front hundreds of cells wide it spent the
  battle driving.
- **Nobody allied escorts a beat's hull.** L2's ammo truck and R1's dozer ask
  the company for two squads (`GunsDryBeat.call`, `ClearRoadBeat.call`); the
  armour beside the road they drive does not move.
- **The radio said only "calling".** The company could not tell a redoubt with
  the swarm inside its wall from one that has bled half its garrison with a
  carrier already bringing a squad, nor a redoubt whose sector lost its
  armour or mechs.

The rules below replace all of this; the list stays as the reason for them.

The company side:

- **One reserve behind the middle at every density**, 24 cells back.
- **A single call takes the whole company.** Every squad not on the road or a
  beat attack-moves to the nearest calling redoubt, so one call on redoubt 2
  sends all six squads, the reserve is empty, and a second call is answered
  only by whoever happens to be nearer it.
- **A relieved redoubt is left at once.** A redoubt stops calling the moment a
  Marine reaches its gate (Relieved), so the squads turn back for the reserve
  with the swarm still at the wall, and it calls again when a body is next
  inside.
- **No answer to the rear, a tide, or a lost sector.** The base being overrun,
  the tide's beat and a sector losing its machines change nothing.

## The radio: what the company is told

Everything the company acts on is something the allies would say over the
radio, or something its own squads see. The call grows a reason and the
allies say what they are already doing about it:

| Radio item | Said when | Ends when |
| --- | --- | --- |
| **Breached** (redoubt N) | a swarm body is inside the wall | none is inside for the settle window |
| **Bled** (redoubt N) | half the garrison is down | a column or carried squad joins it |
| **Uncovered** (redoubt N) | its sector had armour or mechs and has none left | a machine stands on its post again |
| **Help coming** (redoubt N) | a loaded carrier or a column is bound for it | it arrives or is lost |
| **Base overrun** | the swarm is on the apron (`CordonLogistics` cannot send) | the apron is clear |
| **Tide** | the tide's beat (heard, never foreseen) | the tide's length is over |
| **Escorted** (a convoy, truck or dozer) | an armed allied hull is alongside it (a truck or dozer: an armed hull, a Hound or a street team within 14 cells; a convoy: the armour gave it a hull for every post, and one is alongside) | none is, or a convoy's escort went short |

Breached and Bled are the redoubt's existing Calling state split by its two
causes; Uncovered is new. The company is never told what the swarm is doing
beyond that.

## The allied line command's rules

Pure decisions over a frozen picture of the line (redoubts with state,
standing garrison and the machines on their posts; hulls, mechs and carriers
with where they are and what they are doing; squads waiting at the base;
convoys and beat hulls on the road; the density), returning tasks the
`AlliedCommand` carries out. In order:

1. **Every manned redoubt keeps a floor of cover.** Rural and town: one hull
   on its flank. Urban: its mech pair in its street mouths (pending the
   owner's `cordon-urban-allies` pick). A new machine off the shed or gantry
   goes to the redoubt furthest under its floor, an Uncovered one first, a
   calling one breaking a tie (today's `emptiest`, with the floor added).
2. **Sectors, never calls.** A hull, mech or carrier keeps its redoubt. It is
   re-homed only when that redoubt falls or is given up. A carrier between
   carries stands behind the redoubt it last fed, not the latest caller.
3. **Escorts come out of the surplus.** A convoy first, then a beat's truck or
   dozer, each takes the nearest hulls above their sector's floor, at most one
   hull from any one sector, and never a calling redoubt's last hull. What it
   cannot take that way it goes without, and says so (*Escorted* is false), so
   the company knows to walk with it. A convoy is *Escorted* only when the
   armour gave it a hull for every post and one is alongside a truck: a
   sector's own hull standing beside the road at the gap is not going with it,
   and one technical at the compound's gate is not an escort of three
   (`CordonAlliedRules.convoyEscorted`). The hulls go home when the escort ends.
   In a city, which fields no hulls, a truck or dozer with no hull free takes a
   walking escort out of the same surplus (`CordonAlliedRules.escorts` takes
   hulls, mechs and teams alike): a Hound from a sector above its pair, or a
   street mouth's rifle team while its pair stands, since a team counts nothing
   toward the floor and is never the last cover. Hulls are offered first; the
   truck keeps to a walker at the walker's pace, and a team goes back to its
   mouth when the escort ends.
   Only an armed hull escorts, and one that does not close on its truck (boxed
   in at the base's door, say) is stood down for two minutes and the next taken.
   **Escorted means alongside, not assigned.** A truck or dozer with an escort
   coming waits where it was set down until the escort is beside it, then the
   two drive at the slower one's pace, and it holds whenever the escort falls
   behind; one that has none free sets out alone at once, and one whose escort
   never comes goes on without it (after a minute and a half waiting, half a
   minute holding). R1's dozer does not wait: staged forward to beat the first
   convoy to the wreck, it sets out at once and keeps to its escort only once
   under way. Gone alone is final: it is offered no escort again, and the
   company is called to walk with it, rather than waiting for each new escort in
   turn while the guns it carries for stand dry.
4. **Carriers lift allied squads first.** A waiting column squad goes to the
   neediest redoubt (calling first, then fewest standing, today's `neediest`),
   and while it is on its way that redoubt has *Help coming*. Only a carrier
   with no allied squad waiting may take a Marine (the company's lift, below).
5. **Density shapes the posts, not the rules.** Town: a sector's first hull
   stands on the street through or past its redoubt, and every other post is
   moved to the nearest street cell within reach (roadbound; not on the Road,
   whose streets are the convoys'), and carriers set down at
   the rear gate only. Urban: no carriers; columns walk to the redoubt with the
   fewest standing; mechs stand in the street mouths the swarm comes through.
6. **The allies never end the battle and never take the company's work.** They
   do not clear a block, retake ground or walk out past their posts.
7. **The base is asked, not timed, for what the line lacks** (*The producer*,
   below): cover for an uncovered sector, an ammunition truck for a redoubt
   running low. The shed's own line still builds on its pace.

Built: rules 1 to 5 and 7 (`CordonArmour.advance` puts every new machine
where `nextPost` says, the nearest breaking a tie, and takes escorts through
`escorts`; `CordonLogistics` keeps carriers behind the redoubt they fed and
sends waiting squads by `carrierDuty`). Rule 5: the town's rear gate for
carriers, the city's walking columns and mechs in the mouths, and in a town
the sector's first hull on the street through or past its redoubt (within
twelve of its walls, `CordonArmour.throughStreet`) and every later hull's flank
post moved to the nearest street cell within sixteen
(`CordonAlliedRules.roadbound`), but not on the Road: there the town's main
street is the convoys' road, and hulls parked on the streets beside it stood
in their way and the dozer's. A beat's hull (L2's ammunition truck, R1's
dozer) wants one hull beside it, a convoy one a post; the truck and the dozer
keep to theirs (`EscortPace`, rule 3).

## The producer

The base is the line's producer (`allied-tools.md`, Code seam): an
`AlliedToolShed` over `CordonLogistics` as its depot, held by
`CordonLineCommand`. The line command asks it by need, each decision a pure
rule in `CordonAlliedRules`:

- **Cover** (`wantsCover`): an Uncovered sector asks for an armour pair of
  the density's hull (technicals, in open country and towns) or, in a city, a
  Hound pair walking up from the base (`coverTool`), at most one on the road
  to a sector at a time. The machines are that sector's from the moment they
  set out.
- **Ammunition** (`wantsAmmunition`): each redoubt's guns (its gun towers,
  the picket nests nearest it) and its yard mortar team's tubes share one
  `GunStock`. When the rounds they hold together (one pool, tubes' rounds
  counted as rounds) fall under the table's share, or any of the guns runs dry,
  the line asks for an ammunition truck (`AlliedMakers.ammunition`), which
  drives to the redoubt's end of its supply road and refills every gun and tube
  there, packed tubes included. L2 only empties the redoubt's gun towers in
  that stock; the same request does the rest.
- **The truck's way.** A truck sets out from the base's end of the redoubt's
  supply road and drives to its other end. Where none was laid (a city's
  ground, a town's blocks) or the laid stretch is cut off short of the
  redoubt, its way is the city's streets: the vehicle router's route for a
  truck from the base's door to the redoubt's rear gate, streets cheapest
  (`CordonSupplyStreets`), and the truck sets out from the first street cell
  on it outside the base from which it can drive the rest. Every way is proved
  at setup as a move order is planned, every bend one a truck can make
  forward, and the truck is set down facing along the way's first leg (set
  down facing the redoubt, it stood at a street's edge facing a wall: the two
  city trucks that sat at their start all battle); a redoubt with no such way is never sent a truck (one sent anyway
  stood where its way ran out), and evidence names each redoubt's way (road,
  streets or none).

A request with none in stock, or while the swarm is on the apron, is refused
and asked again ten seconds later. The reserve (`CordonAlliedTable.Reserve`)
is sized so an ordinary battle barely notices it: one cover in stock and one
more every four minutes; two trucks and one more every two minutes; a gun
holds 400 bursts, about fifteen minutes of a heavy MG firing without pause,
and a truck is asked for under 30% of the redoubt's rounds. Evidence reads how
often any of it is used: on the 2026-10-23 pools no ordinary battle asked for
a truck (every truck was L2's, and every one sent outside a city arrived),
cover was asked for in about half the open-country and town battles and
granted almost always, and a sector stood uncovered for seconds, not minutes.
A city's L2 used to lapse, for want of a supply road; it now picks among the
redoubts its streets reach, and lapses only where they reach none.

## The company commander's rules

Pure decisions over the frame (`CordonCommand.Frame`, grown by the radio
items above), in priority order. A squad already on a duty keeps it while the
duty stands, rather than being re-chosen each frame, and one a more urgent call
drew off goes back to its duty when the call ends, ahead of any squad sent in
its place (a duty keeps its nearest squads first).

1. **Squads the player or a lease owns stay theirs** (today's rule).
2. **The Road's convoys.** Clear the road ahead of a halted convoy, or where
   the swarm is on one of its trucks with the road ahead open, at the gate
   while it loads say (two squads); walk with a convoy that is not *Escorted*
   past the gap (three). It is already "allies first, the company where they
   are not". Two squads hold the compound's gate from the moment a convoy
   enters the road until it turns for home, so the town's end is held when the
   trucks load there rather than walked to after the swarm reaches them.
3. **A breached redoubt: a relief detachment.** The two nearest free squads
   go at once and fight in behind the rear wall. They **hold until the
   redoubt is settled**: holding, nothing inside its wall and nothing in their
   own sight within reach of it for twenty seconds (built on the wall alone
   for now: twenty seconds with no swarm body inside). Then they go back. (Today
   they turn back the moment one touches the gate.)
4. **A bled redoubt: the allies' first.** With *Help coming* the company sends
   nobody. Without it, it is answered as a breach.
5. **An uncovered redoubt: hold the posts.** Two squads go to the sector's
   posts (the flank in open country, the street mouths in a city) and hold
   them until a machine is on its post again; this is the urban table's
   "walks forward to a redoubt that lost its mechs", and it holds in every
   density.
6. **The optional objectives**, at most half the company (today's cap and
   order). A beat whose hull is *Escorted*, or has an escort on its way that
   it waits for, asks for nobody; one the armour had no hull for asks for its
   two squads.
7. **The base overrun** (rural and town, where the base feeds the line): two
   squads go to the apron until it is clear.
8. **The reserve keeps a floor.** Outside a tide at least a third of the
   company (two of six squads) is left uncommitted by rules 3 to 7; a duty
   that would break the floor waits, the most urgent first (breached, then
   uncovered, then bled, then the rear). In a tide the floor is gone and every
   squad may be sent.
9. **Where the uncommitted wait, by density.** Rural: one reserve behind the
   middle of the line (today). Town: the hinge, the village's gaps between
   redoubts 2 and 3; in a tide the hinge squads move up into those gaps and
   hold them, since armour held to the streets cannot. Urban: the hammer, two
   halves of the company each behind a pair of redoubts (pending
   `cordon-urban-hammer`).
10. **The lift.** A duty more than a hundred cells' walk from a squad asks the
    base for a lift, in rural and town country only, where walking to the
    carrier and riding from it (counted at three times walking pace) beats
    walking the whole way. The base grants it with a carrier that rule 4 of the
    allied command leaves free. The squad walks to it, rides, and is
    set down behind the redoubt; no carrier, no lift, and the squad walks. The
    carrier must seat the whole squad (a squad rides together or walks), and an
    allied squad waiting at the base always comes first. The squad boards under
    the company's own authority, so the player's order still takes it off the
    ride. A lift whose duty ends, or moves half a lift's worth (an escorted
    convoy moves all the while), before the drop sets the squad down where it is; a lift that leaves the squad no nearer its drop (the carrier
    could not get there) bars that squad from asking for a minute, and one called
    off bars it for twenty seconds. Read as `company lifts N asked, N granted, N
    set down` in the line command's readings.
11. **Who walks, and who is taken off.** A duty that walks with people or
    fetches them (a convoy's escort, a beat such as the stragglers) takes the
    nearest free infantry before any machine: a machine keeps to the roads and
    cannot lead anyone out, so it goes only where no infantry is free. A squad
    walking to its duty that has not moved ten cells in thirty seconds, while
    still more than twelve cells off, has stalled (its own going, since a
    convoy's escort cell runs ahead of it and is never closed on): the duty goes
    to the next nearest, and the stalled squad is kept off that duty for a
    minute. A squad on its duty, or riding to it, never stalls.

### Handing work between them

| What happens | The allies do | The company does | Handed back when |
| --- | --- | --- | --- |
| A redoubt bleeds | a carrier or column brings a squad (*Help coming*) | nothing while help comes | the help joins |
| The swarm is inside a wall | the sector's machines fight from their posts | a two-squad relief | the redoubt is settled for twenty seconds |
| A sector loses its machines | the next machine built goes there first | two squads hold its posts | a machine is on its post |
| A convoy or a beat's hull is on the road | surplus hulls escort it | walks with it only where it is not *Escorted*; clears its road when halted or when the swarm is on a truck | it is home, or a whole escort reaches it |
| The base is overrun | sends nothing, its machines fight on | two squads to the apron | the apron is clear |
| A tide | nothing new: sectors hold | the reserve's floor drops; the town's hinge moves into its gaps | the tide is over |

## As pure decision functions

Each rule above is a function of small records and is unit-tested on
hand-built ones (under a second each, no map or battle):

- **The radio** (`CordonRadio`, working name): redoubt states, garrisons and
  the allies' tasks in, the items of the table above out. The one place the
  company's knowledge is decided, so fair knowledge is tested where it is
  made.
- **Allied decisions** (`CordonAlliedRules`): `coverFloor(density)`,
  `uncovered`, `nextPost` (where a new machine goes), `escorts` (one per
  sector, above the floor, never a calling redoubt's last), `carrierDuty`,
  and when to ask the base: `coverTool`, `wantsCover`, `wantsAmmunition`,
  `helpComing`.
- **Company decisions**: `duties(radio, beats, roadBlocks)` (the ordered list
  of what wants squads and how many), `assign(duties, squads, floor, tide)`
  (who goes, keeping a squad on its duty), `settled(redoubt, sightings,
  since)`, `reservePosts(density, redoubts, squads)`, `liftWorth(squad,
  duty, carrier)`.

`CordonCommand.plan`, `CordonArmour.advance`, `CordonLogistics.advance` and
`CordonLineCommand.advance` are the shells that freeze the picture, call these
and issue the orders.
The lift's seam is `AlliedCommand.lift`: where `carry` seats its riders back as
a count through the mission's `Delivery` (right for militia), a lift puts the
squad itself aboard through `VehicleTransportService`, so its identity, kit,
wounds and pace come back as they went.

## Evidence

`commanderEvidence` on `cordon-line` and `cordon-road`, at each density once
the pools exist, read rather than asserted: squads committed per call, time
from a breach to the relief's arrival, how often the reserve fell under its
floor outside a tide, sectors uncovered and for how long, lifts asked and
granted, escorts taken per sector. The matrix's *line command* column reads
the producer: requests made, granted and refused by tool, trucks delivered and
lost, dry gun-minutes, road hulls escorted, escort hull-minutes taken and gone
short, and minutes each sector stood uncovered. The asserted mechanics stay the Cordon's
own (`cordon-nouns.md`, Evidence). A rule is accepted on the unit test of its
function; a battle reads whether the whole holds together, never whether the
line held.

## The owner's questions

- **`cordon-answer-call`: how much of the company answers one call.** The
  whole company (today); a two-squad relief while the reserve holds
  (recommended); or the allies answer and the company waits unless the call
  outlasts them.
- **`cordon-urban-hammer`: where the company waits in a city.** One hammer
  behind the middle; two hammers behind redoubts 1-2 and 3-4 (recommended); or
  spread behind every redoubt.
- **`cordon-beat-escort`: who escorts L2's truck and R1's dozer.** Picked: the
  sector's armour, the company only where none is free (the convoy's rule).
  Built.

## Boundaries

- Carrying an order out (routes, boarding, a mech's standable ground):
  `allied-command.md`.
- What the allies field at each density: `cordon-allied-forces.md`,
  `allied-tools.md`.
- Line posture, the readiness gate and strike detachments: `cordon-nouns.md`
  (Line posture), built on the company's rules here.
- The general allied column that replaces the scripted one:
  `cordon-allied-columns.md`.
