# The Cordon — Nouns

Status: DRAFT

Written: 2026-10-22
Updated: 2026-10-24 — the gap law is closed with gap nests (street mouths in built country) and is acceptance

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
gates are where they cross its wall (a road that only runs past a square
place's side opens it only where a street leaves, and a square place no road
grew into is given arms off the road past it); the garrison makes the rest of it at
setup: a gun tower beside each gate, firing slits and wall posts along its
runs, floodlights at its corners. Its wall is field grade: a Brute, a charge or
a ground colony's spines open it. Each redoubt is numbered across the line from
west to east, from 1.

**Construction.** What a redoubt is built from (`CordonConstruction`; owner,
2026-10-24). In open country every redoubt is the allies' **compound** above.
A town's line mixes it with a **fortified farm** (a farmhouse and a barn making
the front face, a second barn the flank, a stone yard wall round a worked
farmyard) and a **fortified school** (its courtyard block the front face, its
hall the flank, a ceramic yard wall round the paved playground with a sandbag
firing step along its open front); a city's line mixes it with a **walled-up
block** (a building on each corner of the crossroads, a double blast wall
across each street arm with a gate on the road, a gun in the wall's line beside
each gate and hedgehogs outside) and a **fortified tenement** (four courtyard
tenements built as one block behind the crossing, sandbags along the pavement
in front, a gun nest at each front corner). The compound is the proper military
post; the others are troops making do with what stood there. Each redoubt's
pick is a pure function of the density, its place in the line and the map's
seed, every choice equally likely except that the redoubt nearest the base
counts the compound twice, so most lines mix two or three kinds. A built
redoubt is raised on the finished map in place of the generated compound
(`CordonConstructions`), on the compound's own ground and never across the
terrace's cliff or ramps, and is recorded in the map's layout (`cordon-works-N`:
its construction, its bounds, its gun cells) for the battle to read. A city
redoubt with no crossroads worth walling up stays the compound, and a map whose
mix would cost it its Cordon is the same map with every redoubt the compound.

**Picket line.** The ground between two neighbouring redoubts, held by works
rather than walls, run from one redoubt's claimed edge to the next's at their
depth and facing the front, with a nest at each end of every run
(`fortified-lines.md`). The mission lays it through its layout
(`mission-layout.md`) once the redoubts stand; what it is built of follows the
density: a trench two deep with a sandbag parapet and wire in front, opened in
funnels, across rural and town country, its street mouths barricaded in a
town; in a city a sandbag course that runs along the house fronts where the
buildings stand across it, and barricaded street mouths. The garrison seats
its own light gun (`ALLY`) in every nest of the picket, so the works between
redoubts are covered from the first tick.

**The gap law** (`cordon-map-intent.md`; `CordonGapLaw`). No stretch of the
line is ground neither side's guns reach: on the best chain of guns from one
redoubt's guns through the picket nests between to the next redoubt's, no
step is longer than twice the line gun's reach plus its spread and splash at
each end (53.6 cells for the heavy machine gun every redoubt and nest
mounts). The nests a course digs at its run ends do not always meet it (a
road gap, a wide redoubt lawn, a course a town broke), so once the redoubts
are built, every pair still breaking it is given more **gap nests**
(`CordonGapNests`): each as far along the line as a gun's reach from the guns
already joined to the western redoubt, until the chain reaches the eastern
one, on open floor clear of every road and of the ways the line leaves open,
on the highest ground across the line. In open country that is the crest the
course is dug along. In a town or a city a nest at a **street mouth**, beside a
street that runs from the line toward the front and in view of it, is
preferred: the whole gap stood at mouths when that costs at most one nest
more, else a mouth wherever one costs none, because a range ring overstates
what a gun sees between buildings and a street is where it sees furthest and
where the swarm comes. A gap nest is a picket nest in every way: it is
sandbagged across its front and flanks where it stands off the trench, it is
recorded on its course with the rest, and the garrison mans it, the
ammunition trucks rearm it and the allied rules count it as they do the
course's own. The law is acceptance: a map whose line still breaks it,
nests and all, is not a Cordon map. Behind the middle of the line, the
redoubt with the most room has an unmanned **switch line**, a shorter trench
a garrison falls back to (beat L3). The beats' other set pieces stand where the layout found
room for them (`cordon-map-intent.md`): a farmstead in the near front, an
ammo dump in the base, a square in the far rear hamlet, a granary on the
colony's near side, an observation post forward of each flank redoubt and,
on the Road, sandbag positions either side of the corridor where it crosses
the line. A
picket line is broken by gaps the roads pass through, so the line is a set of
prepared positions, never a wall: routes through always exist, and works that
would seal the ground are not laid. A picket line is optional: one the town
breaks is still a Cordon. A picket team
mans its course under the same order a holdout's line team has, and falls back
into the nearer redoubt when it breaks.

**Gap.** Where a road crosses the line: between two redoubts, where it is left
open in the picket works and the redoubts either side cover it, or through a
redoubt's own gates, where the redoubt holds it. The Road's corridor runs through one gap; in the Line the gaps are
where the swarm is funnelled and where columns come up.

**Garrison.** The allied troops in a redoubt (`ALLY`, under garrison
authority, never in the player's command pool, as the colony militia are). A
redoubt's garrison is three **wall teams** at the posts behind its slits, the
wall facing the front first, and the guns in its gate towers. A compound's wall
is dressed exactly as the colony's civil-defence compound is
(`CivilDefenceCompound.dress`); a wall the dressing cannot read as closed is
still manned, its teams inside it with no towers. Any other construction is
manned from its record: a gun seated in each nest its builders laid, and its
teams at its loopholes (the windows its buildings look out of) and its
sandbags, the front first, the rest of the seats in its yard. Wherever the
battle asks for a redoubt's gate, its guns or its yard (a truck's rear gate, the
gun-to-gun reading, a city redoubt's mortar team) it asks the same of every
construction: a built redoubt's rear gate is the gap in whatever walls its rear,
buildings and blast walls alike. The teams hold the redoubt's
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
**breaks** when more redoubts have fallen than the briefing allows. A redoubt
is **given up** when the allied command orders its garrison out (beat L3): it
no longer calls or falls, columns, carriers and armour no longer go to it, and
it never counts against the line, so a beat's order cannot spend the player's
allowance.

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
sent on to the next one. A column that has come no cell nearer its
destination for thirty seconds is asked whether it has **reached the front**.
The mission says what counts: the Cordon says none has, so its columns go on to
their redoubts; Broad Front says a column engaged on its own lane's ground has,
and it joins its lane there. On arriving it joins the garrison as wall teams, so a
redoubt a column reached stands with more than it started with. A column takes
about two minutes to march up from the rear, so the timetable sends each one
two minutes before a tide. Where the base has carriers forward (rural and town
country) a column walks to the base's muster yard and is carried up from there
(*Logistics base*); in a city it walks the whole way, in its two teams, to the
redoubt that needs it most, a calling one first and then the one with the
fewest standing. The column is scripted (`AlliedColumns`, which is
mission-neutral: the mission says where it enters, where it goes and what
joining means), not commanded by an allied commander; the full allied column
system (`marine-side-reinforcement.md` applied to `ALLY`) is a later story.

**Logistics base.** The allies' depot behind the middle of the line: a
fenced compound with a vehicle shed, stores and a muster yard
(`FortressProgram.logisticsBase`). A city's base mixes that **shed** with two
constructions (`CordonConstruction`, picked from the seed, each equally
likely): a **warehouse depot** (two freight warehouses on the road's front side
with trucks on their apron, an industrial fence round the yard) and a **car
park depot** (the lot under asphalt, vehicles parked in marked rows, a stockade
of crate stacks with sandbagged ways in). Either keeps the main road's
carriageway through it and paves the rest, kerbs and all, and works behind the
road from two open-air **gantries** (the auto shop's bay between lift columns),
machine berths in one vehicle bay, so it builds one machine at a time as the
shed does, its technicians working them from their flanks and heads and
turning, as a shed's do, to the tool chest, diagnostic and service run beside
each between spells of welding. The ammo
dump a beat stands in the base is left where it was laid. No base publishes a
capture beacon: the shed's motor pool reads as an armoury to the conquest's
compound layer, which put a defender's compound marker on the allies' depot
and would have sent the swarm's side its technicians' replacements; the
Cordon takes it off. Its shed's berths build what the density's
allied table lists (`cordon-allied-forces.md`), one machine at a time and round
again, at the pace its allied technicians weld them: cars, technicals and
carriers in open country, technicals and carriers with every third machine a
Hound in a town, Hounds alone in a city. The machine on the stocks is a real
hull or mech frame whose structure is how far built it is, gun empty until it
is finished, so a swarm that reaches the base stops production by killing the
crew or the machine. A finished Hound walks out of the shed on its own and
joins the allied mechs (*Allied armour*); a hull is never drawn out of its berth
onto ground a mech is standing on, nor laid on a berth one still stands in. Allied columns walk in off
the rear edge to the muster yard and wait there. A carrier free at the base
takes a waiting squad up to the redoubt that needs it most (a calling one
first, then the fewest standing), sets it down behind the rear wall to join
the garrison. In a town carriers are roadbound: one sets down only outside the
redoubt's **rear gate** (the gap in its rear wall nearest the wall's middle,
where the street through it leaves) and waits there between carries, never on
the flanks. A city sends no carrier forward: its base builds none and opens
with none, and its columns walk. Between carries a carrier is **support**: it stays behind the
redoubt it fed and does not chase calls; only when that redoubt falls or is
given up does it move, to the one a waiting squad would go to. When squads wait at the base, one carrier a squad
goes back for them, the nearest first, and the rest stay on the line. A
finished car or technical joins the allied armour with a post behind a
redoubt as its home.

The base is also the line's **producer** (`allied-tools.md`): besides the
shed's line on its pace, it holds a small reserve the allied line command
asks for by need, never on a timer. A sector left with no machines asks for
an armour pair (a Hound pair in a city), at most one on its way at a time; a
redoubt with a dry gun, or whose guns and tubes run low, asks for an **ammunition truck**, which
drives its supply road and refills every gun tower, picket nest and mortar
tube that redoubt holds. Each redoubt's guns spend rounds from that one stock;
a gun with none left fires rationed, at a quarter of its rate. A request while
the swarm is on the apron, or with nothing in stock, is refused, and asked
again a little later (`cordon-allied-command.md`, The producer). Outside a city the base opens with one carrier. **Supply roads** run from the
base's gate toward the line to each redoubt's rear gate across open country
(`mission-layout.md`), so the line has a road behind it, laid wherever it
reaches the redoubt's ground even with no other road near it; across a town's or a
city's ground its own streets are the supply road: a truck's way to a redoubt
whose road was not laid, or was cut off short of it, is the route a truck
drives through the streets from the base to the rear gate, and it sets out on
the first street outside the base. A redoubt no truck can drive to is sent
none.

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
doorway, on ground its footprint fits and can drive away from: every hull of
the allies' is set down on the main body of the map's ground for its width,
never in a pocket (the nearest cell that merely fitted once put the first slot
inside a walled pen beside the shed, and nothing from the base reached the
line); there are as many carriers as slots,
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
gun's range). Every manned redoubt keeps a **floor** of cover: one machine in
open country and towns, its Hound pair in a city. A new machine goes to the
redoubt furthest under its floor, a sector that lost its machines first, then
a calling one, then the fewest, the nearest breaking a tie; a machine whose
redoubt falls is given another the same way. In a town hulls are
**roadbound**: a sector's first hull stands on the street through or past its
redoubt, and each later hull's flank post is moved to the nearest street cell
within reach, the street nearest where it would have stood; not on the Road, where
the town's main street is the convoys' road and a hull parked beside it stood
in their way. They do not chase calls: on a front
hundreds of cells wide a reserve sent to whichever redoubt called last spent
the battle driving. The base's shed adds to them through the battle.
They are the start of the allies fighting their
own war (*The allies hold, the company decides*).

**Allied mechs.** In a town and a city the allies field Hounds under the same
command. A Hound's sector is a redoubt too, and it stands in that redoubt's
**street mouths**: where the streets leave the ground in front of it toward the
front, read along the row a few cells out from its front wall, nearest the
redoubt's middle first (open ground where no street crosses that row, as in a
town's fields). A mech is a machine of its sector as a hull is, counted to the
same floor and given its redoubt by the same rule. A city's line opens
with a Hound pair in each redoubt's mouths and a walking rifle team behind
them, holding the mouth, and a mortar team in the redoubt's yard whose
standing fire mission is the ground past the mouths (the owner's pick
`cordon-urban-allies`, the mech lance, with mortars). A field mortar never
fires a round with one of its own side under it, so the yard's tube fires on
what the side has seen out past the mechs and holds while the swarm is among
them; a town's Hounds come off the base's line. A mech pair holds a mouth
but not every alley: the company clears a block the swarm has got into.
What holds each sector, and what the base sends, by density:
`cordon-allied-forces.md` (over the shared catalogue, `allied-tools.md`).

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
the base. Only a truck at the gate takes people aboard: a truck still on its
way when the first arrives drives on to the gate, and the convoy waits for it
(a loading's length past its own, then turns for home without it, the
straggler driving back empty). It does not fight its way through: it stops when the road ahead is
blocked by the swarm, and goes on when it is clear. Past the gap, in the
swarm's country, it rolls only with an escort beside it: a convoy nobody
escorts waits at the line. A convoy is **lost** when all its trucks are
destroyed; its passengers die with their truck. The standard timetable sends
three convoys of two trucks, four minutes apart from minute one; each truck
brings out six of the town's thirty-six, and two convoys must come back out.

**Escort.** The allied armour escorts the convoys, and the company is the
backstop (*The allies hold, the company decides*). From the moment a convoy
sets out, hulls leave their sectors and drive with it, one either side of the
road a little ahead of its lead truck and a third further on, never on the
road itself, where a parked car is in the trucks' way. Escorts come only out
of the **surplus**: the nearest hulls whose sector stands above its floor, at
most one from any one sector, never a calling redoubt's last; what the convoy
cannot take that way it goes without. They go back to their sectors when it is
home. An ammunition truck and R1's dozer are escorted the same way, one hull
beside each, after the convoys, and only by an armed hull. A city fields no
hulls, so there a truck or dozer with no hull free takes a **walking escort**
out of the same surplus: a sector's Hound above its pair (a relief pair, a
Hound off the gantry walking up), or a street mouth's rifle team while the
sector's pair still stands (a team counts nothing toward the floor, so it is
never a sector's last cover); hulls first, since a walker holds its truck to a
walker's pace, and the team goes back to its mouth when the escort ends. **Escorted means
alongside**: such a hull waits where it was set down until its escort is
beside it, the pair drives at the slower one's pace, and it holds whenever the
escort falls behind; with no escort free, or one that never came, it goes
alone for good, and the company walks with it. The radio's Escorted counts an
armed hull, a Hound or a street team within reach. The company walks with a convoy only
where the armour does not escort it: where its escort went short (a hull for
every post, one of them alongside, or it is not escorted), has not come or has
died; a sector's hull it passes at the gap is not its escort. It clears the
road ahead of one the swarm has halted, and goes to one the swarm has reached
where nothing lies ahead of it (at the gate while it loads, from the side). One escort
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

The Road also has a **second road** through the line, from the base's gate
through the gap beside redoubt 2 to the compound's rear gate (beat R5),
carved where the country is open; the corridor is still the cheapest way,
whichever that is.

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
axis across the front. It is fought on the event world's own ground, its
biome's palette (verdant, arid, rock or frozen), with the colony laid out the
same whatever market raised the call; each biome has its own pooled maps. Every place is stated by its depth from the rear
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

**Density.** How built-up the country is follows the bloomed world's market
size (CordonPlan.Density; owner, 2026-10-23). **Rural** (size 4 and less) is
the country above. **Town** (5 and 6) grows the rear hamlets and the front's
places into small towns and puts a village on the line between the middle
redoubts, with fields still between them. **Urban** (7 and up) is built across
the whole width either side of the line: the redoubts stand as fortified blocks
among streets and the front is suburbs. Density changes the country, not the
places: the four redoubts, the base, the landing's country and, on the Road,
the compound are where they are at every density. Dense country is ground for
infantry and walkers more than for wheels, so a town's allies keep their
carriers to the streets and a city's Cordon fields mechs and walking columns
and no carrier forward (`cordon-allied-forces.md`).

The redoubts are programmed places (FortressProgram.fieldRedoubt, laid
square), so they claim first and the rest grows round them. Their roads are
short arms, and their gates are where roads cross their walls, so a road may
run through a redoubt rather than beside it: the redoubt then guards that
road's way through the line. A map on which a redoubt has no wall, or the
landing's field of fire is not clear, re-rolls; so does one whose logistics
base packed no shed, or whose apron a carrier cannot drive from to every
redoubt, onto its level (a redoubt on the crest is reached on its top, not
under its cliff) (a base fenced too narrowly for a carrier kept every one it built
standing on its apron, refused a route, with the shed's lane jammed behind).
That proof keeps clear of walls along cell edges as well as walled cells, as a
hull's body does.

**The line stands on a ridge** (`cordon-map-intent.md`, The line on the high
ground). A low ridge (`elevation-nouns.md`, Ridges) runs edge to edge along the
line: through each redoubt at the depth of its middle and straight between
neighbours, the course the picket lines are dug along, a cell to the front of
it. It is a layout piece raised in the works phase before the lines are laid,
so each picket trench, its parapet and its wire stand on the crest with the
front's cliff just beyond the wire, and its ramps (wide enough for a tank)
climb from the rear. **Each redoubt stands on the crest** as a terrace
(`elevation-nouns.md`, Terraces): its walls, yard and gates on one flat top,
the cliff a few cells beyond its lawn, and a ramp wherever a road crosses that
cliff, so the supply road climbs to the rear gate and the line's road comes up
from the front. **Its rear is always ramped**: it is supplied from the rear
(`TerracePlanner`, supply side), so a ramp a tank's width climbs its rear cliff
whether or not the supply road was laid to it, as near the middle as open
ground allows, and a redoubt that cannot have one cannot stand on the crest.
Carriers, armour and ammunition trucks come up behind a redoubt; with no rear
ramp (on most town maps, where no supply road was laid) they stood below the
cliff and no street way reached any rear gate. The band runs on into each redoubt's top rather than breaking
there, so the crest runs redoubt to redoubt and the picket courses are dug
crest to crest. Wherever any other road, a structure or a place crosses the
band it still breaks into a pass, and the swarm meets a cliff everywhere else.
A map on which a redoubt cannot stand on the crest is not a Cordon map. The
ground the terrace's cliff needs is kept from the other places before they
grow (`Landscape.kept`, a box round each redoubt's seed), in town as in open
country. In open country the band's ground is held before the places grow
(`Landscape.held`); in a town only the fields keep off it, since holding it
from the town's growth cost five pooled town maps their Cordon, so a town's
ridge rises only on the stretches its streets and houses leave open. A city's
height is its buildings: an urban line has no ridge.

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

**Each variant plays from its own pool of vetted maps at each density** (`story-map-pools.md`; `cordon-line`, `cordon-line-town`, `cordon-line-urban` and the Road's three):
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

The company's autonomous commander hears the allies' **radio**: a redoubt is
*breached* (the swarm inside its wall), *bled* (half its garrison down), and
whether its sector is *uncovered* (it had armour or mechs and has none left),
and the allies say whether help is coming (a carrier with a squad bound for
it, or a column marching to it). It answers a breach with a two-squad
**relief** that holds until the redoubt has been quiet for a settle window, a
bleed only when no help is coming, an uncovered sector by holding its posts
until a machine is back, the road's convoys and the optional objectives as
before (a beat's truck or dozer only when no allied hull escorts it), and the
base when it is overrun; outside a tide a third
of the company stays uncommitted. The uncommitted wait by density: the reserve
behind the middle of the line in open country, the hinge between the middle
redoubts in a town (up in its gap in a tide), two hammers behind the redoubt
pairs in a city. It knows where every redoubt stands and sees the swarm only
through its own squads. The rules, and the allied line command's for its own
machines: `cordon-allied-command.md`; posture and strikes:
`cordon-company-command.md`.

### Evidence

`commanderEvidence -Pmission=cordon-line` and `-Pmission=cordon-road` play
every seed of the variant's pool through production construction, side by
side, the checked-in fixture standing in for everything but the seed
(`-Pfixture` plays exactly one). The report opens on a matrix, one row a seed. Asserted mechanics: the
company lands, every tide the timetable reached came on its tick, a held line
lost no more redoubts than allowed, a broken one says how, and a battle that
ended did so through the Cordon's own outcome, never with it still running.
Read, not asserted:
the beats in order, each redoubt's state and garrison minute by minute, the
swarm's size, and how it ended. Review frames (`-PsnapshotEveryTicks`, and the
`cordon-mapgen` suite) name the Cordon's own places through one annotation set
(`CordonReviewAnnotations`): each redoubt with its number and state, the gap
redoubts, the bores, the logistics base, the town's compound, the landing field,
the layout's pieces with any objective rolled on them, and where a running
objective wants the company; never Conquest's keep, barracks or armory.

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
| `company_lost` | both | PLAYER | The company is wiped out (no Marine on the ground or still to come down); read before every other end rule |
| `cordon_held` | both | PLAYER | The battle is won |
| `objective_<key>_started` | both | PLAYER | A rolled optional objective starts (at setup) |
| `objective_<key>_succeeded` / `_failed` / `_lapsed` | both | PLAYER | It goes final (*Optional objectives*) |

## Optional objectives

The owner's twelve story beats (six a variant) are the Cordon's pool of
optional objectives (`optional-objectives.md`), keyed below. A battle rolls a
few of them against the set pieces its layout laid (`cordon-map-intent.md`):
two or three in rural and town country, one or two in a city, where fewer set
pieces stand. The owner's recommended beats (L1 to L3, R1 to R3) weigh twice
the others. The roll is the battle seed's own, so the briefing shows the set
the battle will run (`CordonSetup.briefingObjectives`). The board starts at
setup and steps with the sequence; a beat asks the company for squads at its
place, and the company's commander gives the beats at most half its squads,
after the convoys and before calling redoubts; a squad on a beat keeps it rather than being
re-chosen each frame. A beat's hulls (the ammo truck,
the dozer) are the allies', driven by their own command and escorted out of
the armour's surplus; the beat asks the company for its two squads only while
no allied hull is with them or on its way to them. Evidence forces a set
with `-Dbattle.cordon.objectives=all|none|<keys or codes>`.

| Code | Key | Needs | Runs | Succeeds | Fails |
| --- | --- | --- | --- | --- | --- |
| L1 | `stragglers` | farmstead | At 1:30 six people stand at the farmstead and wait for a Marine, then walk in through the nearest redoubt | The last is ten rows behind the line, someone alive | All die, or nobody came by the end |
| L2 | `guns_dry` | ammo dump | At 5:00 one redoubt's gun towers (seeded, never the switch line's) run out of rounds and fire at a quarter rate; the allied line command asks the base for a truck, which drives its supply road (in a city, its streets); a redoubt no truck can reach is never chosen, and with none the beat lapses | A truck reaches the redoubt: its guns and tubes are full again | A truck bound there dies or the redoubt is lost first |
| L3 | `switch_line` | switch line | At the second tide the switch line's middle redoubt is given up and its garrison ordered back | Most of those who set out are in the switch line | Too few are left alive for most |
| L4 | `bore_behind` | eruption site | At 9:00 a bore opens on the eruption site through the ordinary seam | Collapsed while a fissure | It grows to a bore (about three minutes) |
| L5 | `last_hamlet` | bell square | At 7:00 six people gather at the bell and wait for a Marine, then walk to the rear edge | All out | Caught (the first death), or nobody came |
| L6 | `burn_granary` | granary | From the start: a Marine holding it twenty seconds torches it, or any hand brings half its walls down | Burnt before the second tide: that tide comes at three quarters of its pressure | A Marine reached it and it stands at the second tide (lapses if none did) |
| R1 | `clear_road` | the wreck's held ground | The wreck is laid at setup only when rolled; the base's dozer, moved up behind the gap, drives onto the wreck and works each shut road cell until a truck's lane is open, then pulls aside | The road through it opens before the first convoy's lead truck reaches it | The dozer dies, or the convoy reaches a shut road (the dozer keeps on; routes are laid again when it opens) |
| R2 | `truck_down` | none | Convoy 1's second truck, loaded, breaks down halfway between town and gap; its people walk back through the gap | Four are behind the line | Fewer than four can be |
| R3 | `last_loading` | none | The last convoy waits two minutes longer at the gate | It leaves loaded | It is lost first |
| R4 | `militia` | none | When the last convoy is at the gate the town militia go to the gap and hold it (allied, never the player's) | One reaches the gap | All die on the way (lapses when none is left to ride) |
| R5 | `maw_on_road` | second road | At 5:30 a bore opens sixteen cells off the corridor past the gap, on the second road's side | Collapsed while a fissure | It grows: the convoys that set out after drive a corridor found again round it |
| R6 | `gap_falls` | gap positions | When a gap redoubt falls (the battle's own fall, not scripted) the company holds the gap | No convoy lost since the fall, to the last convoy, or to the end of a won Road | A convoy lost since, the gap closed, or the Road lost (lapses if every convoy was already home) |

R1 excludes R5 (a wreck on a road the convoys leave). Each beat is the
simplest faithful form of the owner's card: the ammo truck appears at the
base's gate rather than the dump, and "low" is a rationed rate of fire, not a
magazine (a truck the base cannot send yet is asked for again until one goes); the granary is torched by bringing its walls down through the
ordinary wall-damage path, not by fire; R2's broken truck is taken off the road
rather than left as a wreck, so the convoys after it still drive; the dozer starts behind the gap, not at the base (from
the base it lost the race to the first convoy on every long corridor), at the
nearest corridor cell there it can drive to the wreck from over the ground its
blade opens, facing along that route (set down wherever the corridor lay, in
town it stood where it could not turn onto the street, or in a yard walled all
round, and never set out), and a
dozer standing at a shut road cell pushes it at the blade's rate even where
its move order stopped it short of the blade bearing; it pulls aside onto
the first cell square to the road past the wreck, either side and further out
in turn, that keeps six cells clear of the corridor and that it can drive to,
and tries the next when its move order gives up on one or, still under way, it comes no
nearer for eight seconds, four places at most; only where no such place is reached over
open ground does it take one its blade pushes through to (in town none was, and it stood
on a street one hull wide while the outbound convoys halted at it) (a fixed step off the road put it in a wall, and it stood
wedged in the convoys' way); R4's militia
stay allied; R6 waits for a natural fall, since a scripted one would spend the
briefing's one allowed redoubt. A beat that never got its trigger lapses.

**Rewards and costs.** Rewards are modest against a Cordon contract (a
size-five world's pays about 25,000): credits and a little cargo. A failed
beat's cost is a named key the Rav Bloom arc reads downstream; nothing here
applies it.

| Key | Reward | Cost on failure |
| --- | --- | --- |
| `stragglers` | 3,000 cr, 20 food | `bloom.cordon.refugees_lost` 6 |
| `guns_dry` | 2,500 cr, 15 supplies | `bloom.cordon.redoubt_shaken` 1 |
| `switch_line` | 3,000 cr, 5 heavy machinery | `bloom.cordon.garrison_lost` 1 |
| `bore_behind` | 4,000 cr, 20 metals | `bloom.heart.satellites` 1 |
| `last_hamlet` | 2,500 cr, 15 food | `bloom.cordon.refugees_lost` 6 |
| `burn_granary` | 2,000 cr and the weaker tide | `bloom.heart.biomass` 1 |
| `clear_road` | 3,000 cr, 5 heavy machinery | `bloom.cordon.road_delayed` 1 |
| `truck_down` | 2,500 cr, 10 supplies | `bloom.cordon.townspeople_lost` 6 |
| `last_loading` | 3,000 cr, 20 food | `bloom.cordon.townspeople_lost` 12 |
| `militia` | 2,000 cr, grant `cordon.militia_joined` 1 | `bloom.cordon.militia_lost` 1 |
| `maw_on_road` | 4,000 cr, 20 metals | `bloom.heart.satellites` 1 |
| `gap_falls` | 3,500 cr, 20 supplies | `bloom.cordon.gap_lost` 1 |

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
