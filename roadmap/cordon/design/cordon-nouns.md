# The Cordon — Nouns

Status: DRAFT

Written: 2026-10-22

The Cordon is the second movement of the Rav Bloom arc (`bloom-mission-arc.md`,
Mission 2): a world's military has thrown a line of redoubts across the country
in front of a bloom, and the company is hired to help it hold. One mission, two
variants, each a single battle: **the Line** (the company is the mobile reserve
behind the redoubts while the region behind them is evacuated) and **the Road**
(the company keeps a relief road open through a gap in the line to a town cut
off in the bloomed ground). The campaign picks the variant from how Colony
Breach went; the player picks only whether to take the beat.

This doc owns the Cordon's map, its allies, its people on the move, its beats
and its end conditions. The swarm is Colony Breach's swarm: bores, tiers,
surges, packs, harvest, creep, spore and ground colonies, strains and growth
stages are `colony-breach-nouns.md`'s, reused and never restated here. Read that
doc first; every one of its laws holds here unless this doc names an exception.

## The fantasy

The colony was not saved, or not all of it. Weeks on, the bloom has grown out
over the farmland round it and the world's own garrison has dug in across the
country: a row of redoubts on the roads the swarm would use, picket works
between them, guns in the redoubts. They will hold where they stand; they will
not move. The company is the hand that moves. In the Line it runs from one
redoubt to the next as each one starts to go, plugs the gap, and when the line
can spare it goes out between the lines to put a bore down. In the Road it
walks ahead of the relief convoys through the gap, clears the road, pickets
the bores that bear on it, and keeps the gap open until the last truck is back.
Either way the allies are the line and the company is what keeps it a line.

## Vocabulary

**Front.** The bloomed side of the map: the country in front of the line and
the colony the bloom came out of, overgrown with creep, its bores already grown
(the bloom has had weeks). The swarm owns it; nobody lives there.

**Rear.** The near side: farmland and roads behind the line, the landing field
the company comes down on (an `OPEN_FIELD` landing, `precincts.md`, never in
bloomed streets), and the roads allied columns come in on and people go out on.

**The cordon (the line).** The row of redoubts across the map's long axis
between the front and the rear, with picket works between them. The line is
what the allies hold. It runs across the whole map, edge to edge, so there is
no way round it, only through it.

**Field redoubt.** One strongpoint of the line: a small walled work laid out
square on its site (the planned redoubt kind of `forts.md`, a nest cluster with
a small command post and no ship-grade guns), sized for an allied garrison.
It is a place in the map's plan, as the civil-defence compound is in the
colony's (`colony-breach-nouns.md`, Redoubt), so roads run up to it and its
gates are where they cross its wall; the garrison makes the rest of it at
setup: a gun tower beside each gate, firing slits and wall posts along its
runs, floodlights at its corners. Its wall is field grade: a Brute, a charge or
a ground colony's spines open it. Each redoubt is numbered across the line from
west to east, from 1.

**Picket line.** The ground between two neighbouring redoubts, held by works
rather than walls: a sandbag course with a nest at each end, the outer belt of
`forts.md` run out from one redoubt to the next (`fortified-lines.md`). A
picket line is broken by gaps the roads pass through, so the line is a set of
prepared positions, never a wall: routes through always exist. A picket team
mans its course under the same order a holdout's line team has, and falls back
into the nearer redoubt when it breaks.

**Gap.** Where a road crosses the line: between two redoubts, where it is left
open in the picket works and the redoubts either side cover it, or through a
redoubt's own gates, where the redoubt holds it. The Road's corridor runs through one gap; in the Line the gaps are
where the swarm is funnelled and where columns come up.

**Garrison.** The allied troops in a redoubt (`ALLY`, under garrison
authority, never in the player's command pool, as the colony militia are). A
redoubt's garrison is three **wall teams** at the posts behind its slits, the
wall facing the front first, and the guns in its gate towers. The wall is
dressed exactly as the colony's civil-defence compound is
(`CivilDefenceCompound.dress`); a wall the dressing cannot read as closed is
still manned, its teams inside it with no towers. The teams hold the redoubt's
ground and fight from the slits: an order that draws them to the middle of the
yard leaves the wall empty and the gates open. The garrison is kitted as the
colony's best (troops dug in for weeks). A garrison is what makes a redoubt
stand; the company never commands it.

**Redoubt states.** A redoubt is **holding** while its garrison stands and no
swarm body is inside its wall. It is **calling** (for relief) once a swarm
body is inside the wall or half its garrison is down: the company hears the
call. It is **relieved** when a Marine reaches its gate while it is calling:
the call is answered, and it calls again only when a swarm body is inside its
wall once more. A relieved redoubt whose garrison is gone is held by the
company while Marines stand in it. It has **fallen** when its garrison is dead
and a swarm body is inside the wall. A
fallen redoubt is not re-raised in the battle (see *Settled choices*). The line
**breaks** when more redoubts have fallen than the briefing allows.

**Tide.** The crisis the battle is timed round: every living bore's next
surge brought forward to the same tick, and the swarm let out at the tide's
strength for the tide's length. Between tides the swarm presses at the
**ordinary pressure**: the director holds the field at a share of the living
bores' capacities, so the line is worn rather than overrun, and the tide is
what breaks redoubts. A tide is heard, not seen coming.

**Eruption.** The bloom breaking out anew in front of the line: a new bore
opens in the near band in front of a redoubt (each eruption before a different
redoubt, in a seeded order) through the ordinary bore seam, at the smallest
tier, and grows, surges and spreads creep as any bore does, so the director's
target rises with it. Tides make the crises; eruptions make the front worse
between them, so a battle that holds early is not over.

**Timetable.** What the briefing holds the battle to: when the tides come and
how long each runs, the ordinary and tide pressures, when bores erupt, the
window, and how many redoubts may fall. The standard timetable is three
one-minute tides at minutes four, eight and twelve of a fifteen-minute window,
the swarm at a fifth of its strength between tides and two thirds in one, an
eruption at minutes six and ten, one redoubt allowed to fall. A fifth is what
the allies hold alone: with no orders from the player the line holds between
tides and breaks in the second (`cordon-line` evidence, 2026-10-22); at a third
the garrisons bled out before the first tide came.

**Allied column.** Allied troops that arrive during the battle along a rear
road, on a timetable set at the briefing: a few teams of riflemen. A column
is the allies' answer to a redoubt calling: it goes to the redoubt holding
with the least garrison standing when it sets out, on either line; the Road's
convoys are a different thing, sent for the town's people, not the line. A
Road without columns had nothing to make good what each tide took from its
redoubts, and broke in the second tide. If that redoubt falls before the column gets there, it is
sent on to the next one. On arriving it joins the garrison as wall teams, so a
redoubt a column reached stands with more than it started with. A column takes
about two minutes to march up from the rear, so the timetable sends each one
two minutes before a tide. The column is scripted (`AlliedColumns`, which is
mission-neutral: the mission says where it enters, where it goes and what
joining means), not commanded by an allied commander; the full allied column
system (`marine-side-reinforcement.md` applied to `ALLY`) is a later story.

**Logistics base.** The allies' depot behind the middle of the line: a
fenced compound with a vehicle shed, stores and a muster yard
(`FortressProgram.logisticsBase`). Its shed's berths build the allies' cars,
technicals and carriers one at a time, at the pace its allied technicians weld
them; the machine on the stocks is a real hull whose structure is how far
built it is, gun empty until it is finished, so a swarm that reaches the base
stops production by killing the crew or the hull. Allied columns walk in off
the rear edge to the muster yard and wait there. A carrier free at the base
takes a waiting squad up to the redoubt that needs it most (a calling one
first, then the fewest standing), sets it down behind the rear wall to join
the garrison. Between carries a carrier is **support**: it stays behind the
redoubt it fed and answers the nearest call as the armour does, beside the
armour rather than on it. When squads wait at the base, one carrier a squad
goes back for them, the nearest first, and the rest stay on the line. A
finished car or technical joins the allied armour with a post behind a
redoubt as its home. The base opens with one carrier.

The base controls its exits so nothing piles up in the shed. A finished
machine waits on its berth until the one before it has driven clear of the
machine door; it is then drawn out into the service lane in front of its
berth facing the door (the shed's tug: a berth's mouth is no wider than the
hull, and a hull cannot turn out of it alone) and driven out. One still inside
the door when its time to get clear is up (a carrier steers too wide to be
proved a way out of a lane narrower than its clearance) is towed out onto the
threshold by the tug when that ground is clear, and goes on from there; one that
stood in the lane stopped the shed, since the next machine is drawn out into
that same stretch. A carrier waits
on a **staging slot** of its own on the apron outside the door, never in the
doorway, on ground its footprint fits; there are as many carriers as slots,
and an APC beyond that fights with the armour. Every machine drives straight
out to a **threshold** a few cells beyond the door first, then on to its slot
or its post behind a redoubt: a carrier steers too wide to turn from the lane
straight onto a slot. Two carriers sent to
one redoubt set down side by side, not on one spot.

**Allied armour.** The allies' cars and technicals behind the line, under the
allies' own command (`allied-command.md`), not the player's. Each has a
**sector**: one redoubt it fights beside, out past a side wall and a few cells
back from the front, where its gun reaches the ground in front of the wall (from
behind the rear wall it reached nothing; a redoubt is deeper than a machine
gun's range). The redoubt with the fewest hulls gets the next one, and a hull
whose redoubt falls is given another. They do not chase calls: on a front
hundreds of cells wide a reserve sent to whichever redoubt called last spent
the battle driving. The base's shed adds to them through the battle.
They are the start of the allies fighting their
own war (*The allies hold, the company decides*).

**Refugees (the Line). People walking out of the region behind the line to
the rear edge: groups of civilians leaving the rear villages on the
briefing's timetable and walking the country road off the map. They are the
Line's score, as evacuees are Colony Breach's, and they are prey: the swarm
harvests a refugee it reaches as it harvests a colonist. They are walked, not
commanded: each member walks to the rear edge below its village and leaves the
battle there. A group is **out** when its last member leaves the map, **caught**
when the swarm first kills one of them. The standard timetable sends six
groups of six, two minutes apart, from the two rear villages in turn.

**Cut-off town (the Road).** The colony's own town in the front, beyond the
line, where the people Colony Breach left behind are still holding out in its
civil-defence compound with what is left of the militia (two teams, seated
inside the gate). Its people are the Road's score: a count the compound holds,
which ride out as a count.

**Corridor.** The relief road from the rear edge through the line to the
cut-off town's compound: the way the convoys drive (*The map*). Nothing is
parked or built on it, so a convoy can always drive it if nothing is standing
in the way.

**Convoy (the Road).** An allied column of trucks that sets out from the
logistics base where the corridor passes it, drives the corridor to the town,
waits at the compound's gate while the townspeople board, and drives back to
the base. It does not fight its way through: it stops when the road ahead is
blocked by the swarm, and goes on when it is clear. Past the gap, in the
swarm's country, it rolls only with an escort beside it: a convoy nobody
escorts waits at the line. A convoy is **lost** when all its trucks are
destroyed; its passengers die with their truck. The standard timetable sends
three convoys of two trucks, four minutes apart from minute one; each truck
brings out six of the town's thirty-six, and two convoys must come back out.

**Escort.** The allied armour escorts the convoys, and the company is the
backstop (*The allies hold, the company decides*). From the moment a convoy
sets out, the hulls nearest it leave their sectors and drive with it, one
either side of the road a little ahead of its lead truck and a third further
on, never on the road itself, where a parked car is in the trucks' way. They
go back to their sectors when it is home. The company walks with a convoy only
where no armed allied hull is with it, which is where the armour has not come
or has died, and clears the road ahead of one the swarm has halted. One escort
is the convoy's: a hull or a marine near any of its trucks lets the whole
column roll.

A truck drives a route laid along the corridor once: each stretch of the road
a route the planner proves drivable for a truck; where it proves none (a
country lane's corner tighter than it will prove a truck's turn through) a way
the truck's footprint fits along; and the road's own cells only where there is
not even that. The footprint test there counts walls on cell edges, a fence
along a plaza's side, which a truck's body collides with though both cells are
open. A truck that makes no headway along an open route is proved a fresh way
from where it stands to the road a stretch further on and given the rest from
there: headway, not standing still, since a truck circling a point it cannot
turn onto is moving all the time. On a map whose corridor does not pass the
base, trucks come on a few cells in from the rear edge, since a hull set down
on the edge row has no route out of it. Nothing is parked within a few cells of
the corridor.

**Line posture.** The briefing's choice of how far the company goes out, the
Cordon's version of the hive posture (`colony-breach-nouns.md`, Hive posture),
frozen at launch: **Hold** (the company only relieves and escorts),
**Opportunistic** (the commander may send a strike detachment at the bore
pressing hardest when the readiness gate is open), **Push** (bores in front of
the line are objectives). The readiness gate keeps its three conditions with
the Cordon's inputs: (a) the line or the road has held for a sustained window,
(b) the strength left behind covers every calling redoubt and every group of
people on the move, (c) a capable detachment exists.

## The map

One plan for both variants (CordonPlan), 560x336, mission-owned, the long
axis across the front. It is green colony country whatever world the event
names (the VERDANT palette, as the colony is laid out the same whatever
market raised the call). Every place is stated by its depth from the rear
edge, which is the north or the south (the seed's choice), as a fraction of
the ground the landing's country leaves:

| Band, from the rear edge | Depth | What stands there |
| --- | --- | --- |
| Country | the open-field landing's country (about 80 cells) | the landing field, farmland as RuralLayout surveys it, the country road to the rear edge |
| Rear hamlets | 0.04 to 0.2 | two hamlets, west and east, the refugees' homes |
| The cordon | 0.36 to 0.42 | four field redoubts in a row, one in each quarter of the width, numbered west to east |
| The front | 0.55 to 0.92 | the colony (a frontier town) in the middle, its installation and suburb either side |

The places are grown small on purpose: a town's or a hamlet's stock arms run
a third of the map's short side, and at 560x336 that grew one town across the
whole map with the redoubts buried in its streets. The colony's streets reach
a twentieth to a ninth of the short side and a hamlet's less, so the map
reads as country with places in it and the line stands in open ground.

The redoubts are programmed places (FortressProgram.fieldRedoubt, laid
square), so they claim first and the rest grows round them. Their roads are
short arms, and their gates are where roads cross their walls, so a road may
run through a redoubt rather than beside it: the redoubt then guards that
road's way through the line. A map on which a redoubt has no wall, or the
landing's field of fire is not clear, re-rolls; so does one whose logistics
base packed no shed, or whose apron a carrier cannot drive from to every
redoubt (a base fenced too narrowly for a carrier kept every one it built
standing on its apron, refused a route, with the shed's lane jammed behind).
That proof keeps clear of walls along cell edges as well as walled cells, as a
hull's body does.

The Road keeps the colony's civil-defence compound on the town's near side
(depth 0.6 to 0.7). Its **corridor** (CordonCorridor) is found on the
finished map, not carved: the cheapest way a truck can drive from where the
country road leaves the rear edge to a street at the compound, every cell
open with room either side and road five times cheaper than open ground. It
keeps to the country road and the streets where they go its way and crosses
country where the generator left no road (it prunes links between places
that carry nobody to a map edge). On the first seeds it runs 76 to 91% on
road and wanders through a hamlet, a redoubt and the bloomed suburb on its
way; a single paved relief road is open work (cordon-battle.md). A map
with no drivable way re-rolls, and so does one where a truck's edge-aware mask
(the one its convoy lays routes on) does not connect the corridor's ends.

**The front is grown at setup** (CordonFront). The front is cut across the
width into one slot per bore, west to east (six by default: bore, maw, bore,
bore, maw, bore). Even slots stand 60 to 100 cells in front of the line, odd
ones 100 to 165; a bore-tier creep reaches fifty cells and a maw's eighty, and
every bore stands at least its own creep's reach and some twenty cells more from
each redoubt's middle, taking the deep band where the near one has no such
ground, so the creep's edge lies in front of the line and only now and then
against a redoubt's wall, never behind it. Measured from each redoubt rather than
the line's mean row, since one redoubt can stand twenty cells in front of the
rest: a near maw at the mean distance grew over the redoubt in front of it, and
the swarm began the battle at its walls. A bore keeps fourteen cells off the Road's
corridor and seventy off the town's compound (the town is besieged, not yet
eaten). Each bore opens through the ordinary seam and is fed to its tier one
body at a time, so its age mix is a fed bore's. Then the creep is **matured**:
every source grown to its full reach at once and filled, walls crusted, as
creep that has stood for weeks (CreepField.mature), and the trees under it
die. No mother bore stands on this map: she is the heart's (Mission 3). How
many bores and at what tiers will follow what Colony Breach left
(cordon-campaign.md): bores it collapsed stay collapsed, and biomass it fed
shows as higher tiers.

**Each variant plays from its own pool of vetted maps** (`story-map-pools.md`):
a few seeds that pass every test above on their first generation, never
re-rolled, each with a note and an optional patch. The re-roll loop is left
only for trying a seed that is not pooled yet. `storyMapPools` regenerates
every pooled seed and fails when one no longer carries the whole Cordon, and
also checks four redoubts west to east spanning the map.

cordon-mapgen (createSnapshots) renders both variants on three seeds:
the whole map with the redoubts, places, bores and corridor named, the line,
a redoubt and the colony at closer zooms; story-map-pools renders every pooled
map whole.
## Flow

### The Line

1. **Landfall.** The company comes down in the rear fields. The line is
   holding; bores in front are quiet between surges. Allied columns are on
   the timetable.
2. **The first surges.** Each bore surges on its own clock at the nearest
   prey, which is the redoubt in front of it, so pressure lands along the line
   where the bores are, not down one corridor. Refugees start leaving the rear
   villages.
3. **The tide.** At set minutes every living bore surges at once, heard as a
   tremor along the front: the crisis the battle is built round. A redoubt
   under a tide calls for relief; the company has to choose which call to
   answer.
4. **The allies answer.** A column comes up the rear road and reinforces the
   redoubt holding with the least garrison. A redoubt the company relieved and
   a column reinforced stands; one nobody reached falls, and the swarm comes
   through its gap at the refugees' road.
5. **Raids.** Between tides, posture allowing and the gate open, a strike
   detachment goes out between the lines at the bore pressing hardest.
6. **The end.** The last refugee group is off the map (or caught) and the
   evacuation window has closed: the Line is won if no more redoubts fell than
   the briefing allows. It is lost the moment the line breaks, or when the
   swarm holds the refugees' road in force for a sustained window, or when the
   company is wiped out.

### The Road

1. **Landfall.** The company comes down in the rear fields, near the
   corridor's entry. The gap is open; the town is holding.
2. **Clear ahead.** The first convoy enters on the timetable and drives the
   corridor. The company walks ahead of it through the gap and pickets the
   bores that bear on the road.
3. **Ambush.** A surge reaches the road while a convoy is on it: the convoy
   halts and the company has to clear it on. A convoy whose road is not cleared
   is eaten where it stands.
4. **The town.** A convoy at the compound's gate waits while its share of the
   townspeople board, then turns for home. The town's own militia hold the
   compound while it waits.
5. **The tide.** As in the Line, at set minutes every bore surges at once; on
   the Road the tide falls on the gap and the town. The two redoubts either
   side of the gap call; if both fall the gap is closed.
6. **The end.** Won when enough convoys are back out with their people (the
   briefing's number) and the gap is still held. Lost when too many convoys
   are lost to reach that number, when the gap closes, or when the company is
   wiped out.

### The swarm without a rescue

The Cordon has no cohort and no shelter, so the swarm director runs in its
bore-only form (`colony-breach-nouns.md`, Swarm director): bore-sourced from
the first tick over the bores the front was grown with, no perimeter waves, and
no rescue to end it, so it spawns while a bore lives. Two rules keep the
pressure along the whole line rather than at one end: due bores are served in
the order of fewest surges so far, and a bore's pulse is sized to the share the
director is held at, so a big bore held back to a fifth of its strength still
surges instead of waiting for headroom it never gets.

### The company

Until `cordon-company-command.md`, the company's autonomous commander is the
minimum the evidence needs: the company stands as the reserve behind the middle
of the line, each squad goes to the calling redoubt nearest it, and returns to
the reserve when no redoubt calls. It hears every call and knows where every
redoubt stands; it sees the swarm only through its own squads.

### Evidence

`commanderEvidence -Pmission=cordon-line` and `-Pmission=cordon-road` play
every seed of the variant's pool through production construction, side by
side, the checked-in fixture standing in for everything but the seed
(`-Pfixture` plays exactly one). The report opens on a matrix, one row a seed. Asserted mechanics: the
company lands, every tide the timetable reached came on its tick, a held line
lost no more redoubts than allowed, a broken one says how. Read, not asserted:
the beats in order, each redoubt's state and garrison minute by minute, the
swarm's size, and how it ended.

## Beats

All beats are prefixed `cordon.`. Numbered beats index redoubts west to east
from 1, refugee groups and convoys in departure order from 1, and bores in
`SwarmBores` order. These ids are the contract between the sequence, the audio
and the evidence; change them here first. Bores' own beats (grew, collapsed,
surge, fed, harvest, impale) are Colony Breach's, published under this prefix
with the same keys.

| Beat | Variant | Audience | When |
| --- | --- | --- | --- |
| `landfall` | both | PLAYER | First Marine on the ground |
| `line_contact` | both | PLAYER | First swarm body inside any redoubt's reach |
| `tide_N` | both | PLAYER | The Nth tide: every living bore surges at once |
| `eruption_N` | both | PLAYER | The Nth eruption: a new bore opens in front of the line |
| `redoubt_calling_N` | both | PLAYER | Redoubt N calls for relief |
| `redoubt_relieved_N` | both | PLAYER | A Marine reaches redoubt N's gate while it calls |
| `redoubt_fell_N` | both | PLAYER | Redoubt N falls |
| `column_entered_N` | the Line | OBSERVER_ONLY | The Nth allied column comes on at the rear edge |
| `column_arrived_N` | the Line | PLAYER | The Nth allied column reaches the logistics base (or, with no base, its redoubt) |
| `carried_N` | both | PLAYER | The Nth squad a carrier took up from the base joins a redoubt |
| `refugees_moving_N` | the Line | OBSERVER_ONLY | Refugee group N leaves its village |
| `refugees_out_N` | the Line | OBSERVER_ONLY | Refugee group N is off the map |
| `refugees_caught_N` | the Line | PLAYER | The swarm reaches refugee group N |
| `evacuation_complete` | the Line | PLAYER | The last group is out or caught and the window has closed |
| `convoy_entered_N` | the Road | PLAYER | Convoy N enters the map |
| `convoy_through_gap_N` | the Road | OBSERVER_ONLY | Convoy N passes the gap outbound |
| `convoy_ambushed_N` | the Road | PLAYER | A swarm body reaches convoy N |
| `convoy_at_town_N` | the Road | PLAYER | Convoy N is at the compound's gate |
| `convoy_loaded_N` | the Road | OBSERVER_ONLY | Convoy N's people are aboard; it turns for home |
| `convoy_out_N` | the Road | PLAYER | Convoy N leaves the map with its people |
| `convoy_lost_N` | the Road | PLAYER | Convoy N's last truck is destroyed |
| `gap_closed` | the Road | PLAYER | Both redoubts beside the gap have fallen |
| `convoys_lost` | the Road | PLAYER | Too many convoys are lost for enough to come out |
| `convoys_short` | the Road | PLAYER | The window closed with too few convoys out |
| `line_broken` | both | PLAYER | More redoubts have fallen than the briefing allows |
| `cordon_held` | both | PLAYER | The battle is won |

## Laws

- **Colony Breach's laws hold.** Every body comes out of a living bore;
  collapse is permanent; bores are fought, not scripted; biomass drives growth;
  surges, not trickles; air cannot clear the map; the swarm senses, it does not
  know. A tide is the ordinary surge brought forward on every living bore, never
  a spawn from nowhere.
- **The allies are the line; the company commands none of them.** Garrisons
  hold their redoubts by garrison authority; columns and convoys follow their
  timetable. The player can order only the company.
- **The line is prepared positions, not a wall.** Every gap is a route; a
  picket line can be broken through by whatever a body already carries.
- **Fair knowledge.** A redoubt's call is radio, so the company knows which
  redoubt is calling without seeing it; it does not know what is coming until
  it sees it. Bores are known from the start: they stand in plain sight across
  open country and the garrison has been watching them for weeks.
- **People are the score.** The Line is won on refugees, the Road on
  townspeople; bores collapsed are a bonus recorded on the outcome and carried
  to the heart (fewer satellite bores).
- **Deterministic.** Same seed, same cordon, same timetable, same tides.
- **One story.** The cordon sequence is the battle's one story sequence.

## Settled choices

The owner's answers to the flow cards (2026-10-22):

- **The allies hold, the company decides:** the allies fight their own war
  in the manner of an RTS or MOBA lane. A rear logistics base builds their
  light armour and APCs in a vehicle shop, reinforcement squads walk in off
  the map to it, APCs carry them up to the line and stay there as support,
  and the player's company is the backstop when a lane goes bad and the push
  that carries one through. The allies alone hold the ordinary pressure and sag
  in the tides: the ordinary share is set to that (*Tide*).

- **Where the Line's refugees walk:** out of the rear villages to the rear
  edge along the country road, behind the line.
- **Whether a fallen redoubt can be retaken:** no; a fallen redoubt is a hole
  the next column cannot fill.
- **How the swarm escalates:** both tides on the timetable over the bores'
  own surges and new bores erupting in front of the line (*Eruption*).
- **How the Road's convoys run:** three convoys on a timetable, each one out
  and back.

## Boundaries

- The swarm, bores, creep, harvest and strains: `colony-breach-nouns.md`.
- The arc, the campaign hooks and how one beat feeds the next:
  `bloom-mission-arc.md`.
- Precinct plans, the open-field landing and rural country: `precincts.md`,
  `mapgen-nouns.md`.
- Forts, the redoubt kind and fortified lines: `forts.md`,
  `fortified-lines.md`.
- Garrison manning: `garrisonables.md`.
- Convoys and corridors: `convoy-nouns.md`.
- The campaign event and its exactly-once lifecycle: `campaign-event-nouns.md`.
