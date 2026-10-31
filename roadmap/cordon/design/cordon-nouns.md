# The Cordon — Nouns

Status: DRAFT

Written: 2026-10-22
Updated: 2026-10-31 — the pull-out zone is the north-east quarter of the base's goods yard, held ten seconds without a break to finish; the town's far-side bore opens mid-mission; the pull-out: once enough are out the player is told the company has done enough and pulls it out by ordering a squad to the pull-out zone at the logistics base, which it must hold behind the line to finish (*The pull-out*); two surge bores by the town; on the Road, the town compound's posterns east and front, with guards inside its other ways in; the line replaces a redoubt's lost teams from the base by need (*Replacements*); bores break out along the line as the evacuation goes on and the swarm surges at the fall-back (*Line bores*, *The final surge*); the town compound's wide rear gate and truck yard, the convoys turning in it and loading beside the staging area; a townsperson killed at the camp stays out; a truck lost on the road is made good with another trip; the trapped spread over the compound's rooms, the town guard inside with them, and the compound held as a whole; a gun on each corner of every redoubt and its wall teams on its front face; the picket line manned by allied teams; forty-eight trapped and up to five convoys called while any remain; the Road redefined: the win is people brought out; hold Ain Tafrant (the staging area inside the town gate, its compound dressed and defended), and once the town is empty or abandoned fall back behind the line to finish; the townspeople are trapped in the staging area and board the convoys one at a time; the air wing flies off the allies' own airbase lot, a field held in a rear corner. Earlier, 2026-10-25: a shed with no machine door re-rolls; a city's set pieces and its supply roads along the streets

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
grew into is given arms off the road past it). Its open yard is paved in the
concrete of its aprons (`CordonConstructions`), so it reads as built ground
inside a wall rather than the bare earth outside it. The garrison makes the rest of it at
setup: a gun tower beside each gate, a gun on each corner tower, firing slits
and wall posts along its runs (every other panel along the run facing the
front, a panel in three elsewhere), floodlights at its corners, and a flak
position in its yard (`CordonGarrisons`; on the redoubt's ammunition with its
guns). The gate towers stand where the roads cross the wall, so a redoubt whose
roads come in at its flanks had no gun on its front (Ain Tafrant's redoubt 1);
the corner guns, the line's heavy machine gun, cover both faces at each corner. The flak is what
meets the flier law over the line: the front's brood spires launch Harrier packs
with their bores' surges only while some of it stands (`colony-breach-nouns.md`,
Brood spire), and the packs hunt what it does not cover. Its wall is field
grade: a Brute, a charge or a ground colony's spines open it, but slowly, since a redoubt's
ground and the beats' set pieces are works (`combat-durability-nouns.md`, Fortified works). Each redoubt is numbered across the line from
west to east, from 1.

**Construction.** What a redoubt is built from (`CordonConstruction`; owner,
2026-10-24). In open country every redoubt is the allies' **compound** above.
A town's line mixes it with a **fortified farm** (a farmhouse and a barn making
the front face, a second barn the flank, a stone yard wall round a worked
farmyard) and a **fortified school** (its courtyard block the front face, its
hall the flank, a ceramic yard wall round the paved playground with a sandbag
firing step along its open front, the ground cleared round it the world's own bare
ground); a city's line mixes it with a **walled-up
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
A farm or school is bricked up as troops would: every door and archway in its
front face and flanks (an opening of three cells or fewer with wall at both
ends) is walled with its neighbour's wall, so the swarm comes over the
sandbags or through a breach, never in at a side door; the gaps its roads come
in by stay open.

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
room for them (`cordon-map-intent.md`): a farmstead in the near front (in a
city, a tenement standing there), an ammo dump in the base, a square in the far
rear hamlet, a granary on the colony's near side, an observation post forward
of each flank redoubt (in a city, the biggest building there, its front
windows sandbagged), open ground behind the line a bore may erupt on (in a
city, a cleared lot inside its blocks) and,
on the Road, sandbag positions either side of the corridor where it crosses
the line. A
picket line is broken by gaps the roads pass through, so the line is a set of
prepared positions, never a wall: routes through always exist, and works that
would seal the ground are not laid. A picket line is optional: one the town
breaks is still a Cordon. The garrison stands **picket teams** in each
stretch's trench (`CordonOuterPicket`): one for every fifty cells of the course,
wire and barricades counted, one to three (a hundred-odd cells between two
redoubts holds two), each at the middle of its share on the trench's fighting
positions nearest it, under the same order a holdout's line team has and
garrison authority, never the player's. Pressed as the outer picket is (half
down with the swarm on it, or outnumbered two to one), a team falls back along
its trench into the nearer redoubt still manned and joins its garrison; it
never calls, and nobody is sent for it. UNTUNED.

**Outer picket** (`CordonOuterPicket`; owner, 2026-10-25). One allied team
out in front of each redoubt, put there to be lost: the line's eyes while the
company is still landing and moving up. It stands on the road the swarm comes
down, a little under thirty cells out from the redoubt's front wall in the
column of its first street mouth (a street's cell before open ground, nearer
the wall where that row has none), and keeps some twenty-five cells off every
bore, under garrison authority and never in the player's command pool. What it
sees the player sees (an allied body casts the side's fog as a Marine does),
and the battle hears it make contact. It holds its post until it is pressed
(half its team down with the swarm on it, or outnumbered two to one where it
stands), then withdraws down its road to its redoubt (the cell inside it nearest
the middle that the team can walk to), and whoever gets in joins
the garrison; most of it dies on the way. It never calls, and neither the allies
nor the company are sent for it. The point is what the player thinks about in
the opening, not the reading: lost pickets feed the swarm's harvest, and the
first surge is drawn forward to them.

**Gap.** Where a road crosses the line: between two redoubts, where it is left
open in the picket works and the redoubts either side cover it, or through a
redoubt's own gates, where the redoubt holds it. The Road's corridor runs through one gap; in the Line the gaps are
where the swarm is funnelled and where columns come up.

**Garrison.** The allied troops in a redoubt (`ALLY`, under garrison
authority, never in the player's command pool, as the colony militia are). A
redoubt's garrison is three **wall teams** at the posts behind its slits and
the guns in its gate towers and on its corners: the twelve posts nearest the
front, two teams side by side along the front face, each holding its own
stretch, and the third at the posts behind them (interleaved round the whole
redoubt, three of redoubt 1's twelve stood on its front wall). A front slit that
opens into a room built against the wall seats a post in the room. A compound's wall
is dressed exactly as the colony's civil-defence compound is
(`CivilDefenceCompound.dress`); a wall the dressing cannot read as closed is
still manned, its teams inside it with no towers. Any other construction is
manned from its record: a gun seated in each nest its builders laid and one at
each corner of its ground (the open cell nearest the corner, never inside a
building or where it would cut the ground in two), its face loopholed as a
school's is, and its teams at its loopholes facing out (the windows its
buildings look out of toward the front, or across a flank from its front half)
and its sandbags in its front half, the front first, the rest of the seats in
its yard (a farm's teams once stood at its farmhouse's back windows over its own
yard and in the sandbag ring of its rear gun nest); each wall team
holds its own stretch of those posts, side by side along the face, rather
than the redoubt as a whole (told to hold the whole redoubt round its middle, a
building's teams left their windows for the yard before the swarm came). Wherever the
battle asks for a redoubt's gate, its guns or its yard (a truck's rear gate, the
gun-to-gun reading, a city redoubt's mortar team) it asks the same of every
construction: a built redoubt's rear gate is the gap in whatever walls its rear,
buildings and blast walls alike. The teams hold the redoubt's
ground and fight from the slits: an order that draws them to the middle of the
yard leaves the wall empty and the gates open. The garrison is kitted as the
colony's best (troops dug in for weeks). A garrison is what makes a redoubt
stand; the company never commands it.

**Forward observer.** The world's military is organized, and its redoubts
call air: each redoubt's garrison carries one radio, in its front wall team
(the first team behind the slits facing the front), on a soldier other than its
leader (`air-nouns.md`, Forward observer). Through it the military's **air
wing** (two bombers and two fighters, flying off the *Air base* below) strikes a swarm
mass that observer has in sight, never within ten cells of an allied or company
soldier, and never a Harrier pack overhead. Its officer reads the anti-air from
the colonies the side has seen, so a mass in front of a bore whose colonies
nobody has seen costs aircraft. A redoubt whose observer is killed calls no more
air; the columns, the street teams, the town's militia, the refugees and the
company carry no radio onto the wing's net. Both variants, every density.

**Air base** (`CordonAirBase`). Where the air wing lives: the allies' own
airbase lot, a field (a strip, three berths and two sheds) the map holds in a
rear corner from before anything grows (`cordon-map-intent.md`, The air base;
`CordonPlan.airBase`), its strip along the edge toward the front. It is an
allied lot: it publishes no compound for the swarm to take and no pad the
company lands on. The wing is based on it as The Burn's is
(`AlliedAirWing.baseOnLot`): one airframe in each shed, bombers first, rolling
out and down the strip for every sortie and landing and taxiing home after; the
rest on the apron's berths, lifting off them. The aircraft there are the allies'
own parked units: drawn, seen by the swarm and killable where they stand
(`air-nouns.md`, A based pool). The allies post four technicians on the apron
out of the lot's sheds, who work every turnaround (about twenty-five seconds'
servicing, longer when an airframe comes home shot up); where none can be posted
the berths' abstract hands do it. One shot down is replaced on its berth after
the wing's own time (a minute for a bomber, forty-five seconds for a
fighter), and one burned where it stood leaves its hulk and the berth is out for the battle. A
map that laid no lot falls back to a row of four hardstands on open ground some
twenty-four cells behind the logistics base, clear of the corridor, the roads,
the company's pads and the base's apron, every nose toward the front, and the
log says so (`cordon: the map laid no airbase lot`); a rear with no room even
for that lays no base, and the wing flies in over the rear edge. Besides the
observers' calls, the base's operations room tasks the wing's **own sorties**, allied command and never the player's: the first a
minute in, then one at most every twenty seconds, with one airframe of each
kind left on the apron for the radio. It strikes only what the side can fairly
know, most urgent first: the swarm massed on a redoubt calling for relief (the
call is radio, the bodies must be in an allied soldier's sight within thirty
cells of that redoubt's middle), then the densest swarm mass some allied soldier
sees, then a bore (known from the start; a bomber only, the bore nearest the
redoubt worst off first). The airframe is the one the ground calls for
(`AirTasking`), never a fighter against works or armour; never within the
director's ten cells of a friendly soldier; nowhere struck in the last minute;
and nowhere whose believed anti-air would cost more than the director's
ceiling (the director's estimate, losses there in the last three minutes
counted in), so the bores' unseen spore colonies take what they take. Both
variants share it. UNTUNED throughout: air helps the line, it does not hold it.

**Roving observer team** (`air-nouns.md`, Roving observer). Now and then the
military sends a two-man observer team up from the rear. Its vantages are the
gaps beside the manned redoubts, watched from six cells past a side wall and
six back toward home: the redoubts calling for relief first, then the one with
the least garrison standing. A team watches one, moves on after a while or
when the swarm is on it, and is gone when its observer dies.

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

**Line bores (the Road)** (`CordonBoreEscalation`; owner, 2026-10-31). The
bloom breaking out close in front of the line as the evacuation goes on, so
the pressure builds along the whole line, not only at the town and the gap.
Sites are planned at setup, one per **anchor**: each redoubt's front and the
middle of each stretch of picket line between two, west to east. An anchor's
site is a seeded open cell eighteen to forty-two cells in front of the front
wall there (in the guns' reach: ground the line must answer) and within twenty
across; never within sixteen cells of the convoys' road, twelve of a redoubt's
wall, sixteen of the safe camp or thirty of the town's wall, never behind the
line, and sixteen apart from every other site and every bore standing. An
anchor with no such cell has none. They open furthest from the gap first, the
far redoubts' fronts and stretches before the gap redoubts', so the pressure
spreads away from the road; the first four are **scheduled**, the rest kept for
the surge. The town has two **town sites** (owner, 2026-10-31): one on the
town's far side, further into the front, and one on its far flank from the
road (east on seed 7), each thirty to fifty-four cells off the town's wall and
within ten past its extent along it, on ground the line sites' other rules
allow. The far-side one is scheduled in the middle of the evacuation, after
the second line bore, so the pressure grows across the mission ("we need
growing pressure"); the flank one is the surge's first. A scheduled site is
due at its step's time or sooner when the evacuation runs ahead of the clock:
3:00 or the first convoy home or a quarter of the briefing's number out; 5:30,
two convoys or half; the town's far side at 7:00, three convoys or three
fifths; 8:30, three or three quarters; 11:00, four or the whole number
(UNTUNED). Each starts at least
forty-five seconds after the one before. A due site **stirs** for twenty
seconds first (ground thrown up at it, a beat, a beacon on the HUD), then opens
through the ordinary bore seam as a fissure that grows as any bore does. On
Ain Tafrant (seed 7) the scheduled sites open before redoubt 1, before
redoubt 4, north of the town, between 1 and 2 and between 3 and 4; the surge's
east of the town first, then before redoubts 2 and 3 and between them, beside
the gap (`cordon-line-bores` snapshots).

**The final surge (the Road)** (`CordonBoreEscalation.surges`). The swarm
going at the line once the town no longer needs the company, so the fall-back
is a fighting retreat. It comes at once when the player orders the pull-out
(*The pull-out*) or the town is abandoned; once the town is empty and its last
truck has left the gate; and at twenty-five minutes whatever the town is
doing. It calls a tide on every living bore, stirs the sites left (at most six:
the town's flank first, then the line's nearest the gap, five seconds apart, each
opening eight seconds after it stirs),
calls every bore again once they are open, and holds the swarm at the tide's
strength for two minutes, then at a floor of two fifths (UNTUNED). It changes
no end rule: it opens bores only in front of the line and off the road, so the
company can always walk back behind it. The allies hold their posts through
it and do nothing more (owner, 2026-10-31).

**The pull-out (the Road)** (`CordonPullOut`; owner, 2026-10-31). The player's
choice to leave once the company has done enough. Pulling out is **offered**
once at least the briefing's number of townspeople are out and no truck is
loading at the town's gate (`done_enough`): the fall-back row tells the player
"You've done enough: pull back before the line is overrun" and beacons the
**pull-out zone**, the walkable ground of the north-east quarter (high x, high
y: up and right on the map) of the logistics base's fence, two cells in from it: the concrete goods
yard's quarter east of the camp on the base's west side, less the ground within
five cells of the muster yard the reinforcements gather in (owner,
2026-10-31). Where the map records no
base fence, that quarter has no open ground or is not wholly behind the line,
it is the square eight cells round the rally point behind the gap. The **order** is any of the player's own
squad orders, the command layer's ordinary move or hold, whose target cell is
in the zone; it counts only while pulling out is offered (`pull_out`), starts
the fall-back (`fall_back`) and brings the final surge. The company's
autonomous commander never gives it: it falls back only once the town is
finished, and then to the zone. The zone is the **trigger point**: the company
has **withdrawn** (`company_withdrawn`) once it is falling back, no Marine on
the ground is ahead of the line (four rows behind its mean row) and Marines
have **held** the zone for ten seconds without a break: the count runs while
any Marine stands in it, starts again whenever it empties, and the fall-back
row counts it down. After a pull-out no convoy holds up the end: one still
on the road is left to come home or not, and only the people already out
count. On Ain Tafrant (seed 7) the zone is cells 299 to 321 across and 119 to
141 up, the goods yard's north-east quarter behind the middle of the line
between redoubts 2 and 3, less its corner at the muster yard (300, 120): 463
walkable cells (`cordon-line-bores` snapshots).

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
fewest standing. On the Road the line also calls squads up by need
(*Replacements*). The column is scripted (`AlliedColumns`, which is
mission-neutral: the mission says where it enters, where it goes and what
joining means), not commanded by an allied commander; the full allied column
system (`marine-side-reinforcement.md` applied to `ALLY`) is a later story.

**Logistics base.** The allies' depot behind the middle of the line: a
fenced compound with a vehicle shed, stores and a muster yard
(`FortressProgram.logisticsBase`). A town's base is that shed or, as likely,
the town's **goods yard**, under worn asphalt: a goods shed on the front side
of the yard's road (or of its own drive across the middle, where the town's
road only reaches the gate or runs along the fence), flatbeds and cargo trucks
on its apron beside a cargo loader, the freight waiting to go out stacked in
rows; behind the road the yard's workshop and the same two open-air gantries as
a city's depots; past them the yard's vans and tankers in marked bays along the
rear fence and a fuel corner; an industrial fence round it all. A city's base mixes that **shed** with two
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
again a little later (`cordon-allied-command.md`, The producer). On the Road
it also holds the line's **replacements** (below). Outside a city the base opens with one carrier. **Supply roads** run from the
base's gate toward the line to each redoubt's rear gate across open country
(`mission-layout.md`), so the line has a road behind it, laid wherever it
reaches the redoubt's ground even with no other road near it; across a town's or a
city's ground its own streets are the supply road. A city's are laid as such:
each the way along its streets from the base's gate to the rear gate, the widest
first (the boulevard before a back street), cleared of the props in its
carriageway, and no barricade the works lay closes it (`mission-layout.md`,
Road). A truck's way to a redoubt whose road was not laid, or was cut off short
of it, is the route a truck drives through the streets from the base to the
rear gate, and it sets out on the first street outside the base. A redoubt no
truck can drive to is sent none.

The base controls its exits so nothing piles up in the shed. A finished
machine waits on its berth until the one before it has driven clear of the
machine door; it is then drawn out into the service lane in front of its
berth facing the door (the shed's tug: a berth's mouth is no wider than the
hull, and a hull cannot turn out of it alone) and driven out. One still inside
the door when its time to get clear is up (a carrier steers too wide to be
proved a way out of a lane narrower than its clearance) is towed out onto the
threshold by the tug when that ground is clear, and goes on from there; one that
stood in the lane stopped the shed, since the next machine is drawn out into
that same stretch. One out of the door but still standing in front of it when
its time is up is towed the same way, clear onto the apron, and the next waits
until it has: let out on top of it, the shed's machines piled up on one cell
there for the battle. A carrier waits
on a **staging slot** of its own on the apron outside the door, never in the
doorway, on ground its footprint fits and can drive away from: every hull of
the allies' is set down on the main body of the map's ground for its width,
never in a pocket (the nearest cell that merely fitted once put the first slot
inside a walled pen beside the shed, and nothing from the base reached the
line), each slot apart from the others and off the threshold (two slots
snapped to the one cell near both that a carrier fitted, and the second
carrier waited on the threshold for good, turning every later hull aside at
the door); there are as many carriers as slots,
and an APC beyond that fights with the armour. Every machine drives straight
out to a **threshold** a few cells beyond the door first, then on to its slot
or its post behind a redoubt: a carrier steers too wide to turn from the lane
straight onto a slot. Two carriers sent to
one redoubt set down side by side, not on one spot.

**Replacements (the Road)** (`CordonLineCommand`, `CordonAlliedRules.replacement`;
owner, 2026-10-31). Infantry the line asks the base for by need, on top of the
timetable's columns: the timetable feeds the line before each tide, and
replacements make good what a redoubt has lost, through the long Road after the
last column (the timetable's columns stop at minute ten; the Road runs to
twenty-five and more). A redoubt whose garrison is a whole team's worth of men
short of what it was manned with at setup (four of twelve), counting squads
already on their way to it, asks for that many squads, at most two a call; the
line makes one call at a time, to the redoubt most teams short (a calling one
breaking a tie), at most one every forty-five seconds, with at most two
replacement squads on their way at once. They come out of a small **pool** at
the base: two squads, one more every three minutes, never more than two. A
request with the pool empty, or with the swarm on the base's apron, is refused
and asked again ten seconds later, as the producer's are. Each squad is one
allied team walking in off the rear edge where the timetable's columns do (the
entry nearest the muster yard) to the muster yard, where a carrier takes it to
the redoubt that called for it (with no carrier forward, a city's, it walks to
the redoubt itself) and it joins the garrison. A fallen or given-up redoubt is
never refilled: it asks for nothing, and a replacement bound for one that falls
on the way goes to the redoubt that needs it most. While replacements are on
their way to a redoubt it has *Help coming*. The pool is small on purpose, so
the allies hold the ordinary pressure and still sag in the tides and fall to the
final surge. The Line keeps its timetable alone until the owner says otherwise
(`CordonAlliedRules.replacesLosses`). UNTUNED throughout
(`CordonAlliedTable.REPLACEMENTS`).

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

**Cut-off town (the Road).** Ain Tafrant: the colony's own town in the front,
beyond the line, where the people Colony Breach left behind are **trapped** in
its civil-defence compound. Its people are the Road's score, and holding them
is the Road's first objective (*Hold Ain Tafrant*; owner, 2026-10-31). They
wait in the **staging area** (`CordonStagingArea`): the open ground beside the
truck yard, every open cell inside the wall reached from the one nearest where
a truck stops to load (off the yard), within six cells of it. Nothing is seated
on it or beside it.

**Truck yard.** The compound's gate furthest toward the rear (the corridor's
gate) is widened to nine cells (`Fortification.GateYard`; a gate over ten is no
gate to the wall reading, which dresses no tower beside it), and inside it the
map lays a **truck yard** (`CordonGateYard`, read as `CordonSequence.yard()`):
paved ground thirteen cells along the wall by nine in from it, with seven cells
of open ground beside it on the hand with more room, where the staging area
stands. Laid before the compound packs, so its buildings stand round it; opt-in
on the Road's compound brief alone. A convoy's trucks drive in on the gate's
axis, run to the yard's far side, swing across and come back toward the gate to
their **stop** beside the staging area, so they stand to load facing out, the
second truck drawn up behind round the yard; loaded, they drive forward out of
the gate (the corridor is carried on into the yard, `CordonGateYard.extend`).
No defender stands in the yard. Untuned: the yard's size and the gate's width.
The yard is laid behind the gate furthest toward the rear that it fits behind.

**Posterns.** The compound also opens a **postern** in the middle of its east
side and of its front (`GateYard.posterns`: three cells wide, across the glacis
and through the wall, four cells kept clear of buildings behind it; owner,
2026-10-31: more doors for the swarm to come through and for the company to
cover). They are opened whether or not a road comes to that side, so every Road
map has them, and are for people only: the convoys use the yard's gate alone.
With the roads' own gates the compound keeps up to five ways in.

The **townspeople** are trapped civilians waiting in **groups** (owner,
2026-10-31: one bunch looks wrong, and spread they make the player choose what
to hold): the first truckload (twelve) in the staging area by the yard, the
rest shared evenly over the compound's largest rooms (up to four buildings with
six open cells or more, `CordonTownGarrison.rooms`), what a room has no room
for back by the yard (with no staging area found, all in the compound behind
the militia, fourteen cells past the gate). The nearest walk out first, so the
groups by the yard board first and the far rooms last.
They never walk off or flee on their own: one jostled off its place walks back
to it. While a convoy loads they walk out to its trucks and **board** one at a
time, each boarding taking one off the field and putting one more aboard that
truck. They are prey: one the swarm kills before boarding is a townsperson
lost, as one aboard a destroyed truck is. Any the compound has no room to
stand up stay a count and board from it at the same pace.

The compound is held as a redoubt is (`CordonTownGarrison`): its wall dressed
as the colony's compound's (`CivilDefenceCompound.dress`: a gun tower with a
heavy machine gun beside each gate, slits along the runs, floodlights), and its
**defenders**: what is left of the town militia (two teams of four, the ones
beat R4 rides out) at the posts behind the slits, the side facing the front
first, interleaved so each team holds the whole front wall, and the town guard
(three more) inside: a team just inside each way in the convoys do not use (up
to two, so the swarm's other doors are watched), then one with each of the
largest rooms of people (the staging area where there are fewer), each told to
hold the ground round it; never in the truck yard, on the staging area or beside it, nor within three
cells of where the corridor meets the town. Allied, under garrison authority,
never in the player's command pool. A wall the dressing cannot read is still
manned, its teams in the yard; a compound with no wall found at all has its
staging area round the road's end and its teams past it. Twenty rifles and two
guns hold the opening and the first convoy's loading; nobody reinforces the
town, so the tides bleed it, and the company has to come up and hold the
staging area as it goes (owner's call on its strength).

**Town flak.** At town density the compound also stands **flak** against the
swarm's fliers (owner, 2026-10-31: "put flak turrets in the central compound
to defend from fliers"): two to four anti-air flak positions (the redoubts'
flak, `CordonTownGarrison.flakSites`), allied and under garrison authority
like the gate towers, and the swarm can destroy them. They stand on open
courtyard ground inside the wall, each open on all four sides and touching no
building, so never in a room of people nor on a doorstep; never in the truck
yard or the ground beside it (with a cell to spare), on the staging area or
beside it, within three cells of where the corridor meets the town, in a gate
or postern or the four cells in from it, nor beside a defender. They are
spread to cover the whole compound: each next site the one that leaves the
farthest cell inside the wall nearest a gun, at least a pair, then more until
every cell inside is within thirteen cells of one (untuned; well inside the
gun's reach, so neighbouring arcs overlap over the rooms).

**Town states.** With its people in several rooms no one room is the town,
so the **compound** is what is held: **held** while no swarm body is inside
its wall, **pressed** while one is and a defender or a Marine stands inside it
(or within two cells of its wall), and **overrun** once the swarm has been
inside with nobody holding it for ten seconds (untuned); overrun is final, and
it **abandons** the town (`town_abandoned`): every townsperson still trapped
dies, no more convoys set out and a convoy on the road turns home. A room the
swarm reaches before then loses its group, one by one. An overrun compound is
also how the company **pulls out**: nobody but the company and the defenders
can hold it, so a company that leaves the town to the swarm has abandoned it (owner's question: whether an explicit pull-out
order is wanted as well). The town is also finished when nobody is left in it
(`town_empty`). Apart
from that the town **calls** over the radio as a redoubt does, once a swarm
body is inside its wall or half its defenders are down (*Town calling*,
`cordon-allied-command.md`): the company is wanted at the staging area before
it is pressed.

**Camp (the Road).** The safe point behind the line where the convoys bring
the townspeople: a small allied encampment where the convoys set out from and
come back to, beside the logistics base on its west side (its east where the
west has no room), level with the base's middle, with an apron of ten cells
between camp and base where trucks are set down and vehicles are built out and
drive away (owner, 2026-10-31: "to the left of the logistics base with a gap
for vehicles to spawn"; untuned). Its own road runs out of its middle to the
apron, where the trucks stop and the people step off, and on the cheapest way a
truck drives to the corridor; that road is the convoys' corridor from then on.
Where the base has no room or no way beside it, the camp stands on the corridor
seventy corridor cells behind where it crosses the line. Either way it is well
behind the company's fall-back ground and keeps off the base's muster yard. It
is a square twenty-five cells across with the road running into or through
it, cleared at setup of its trees and loose props: a sandbag ring on its edge,
whole round with its corner pieces and broken only where the road crosses it;
the line's heavy machine gun on each inner corner (the redoubts' corner gun);
an aid station, a field HQ and a supply dump on its rear half either side of
the road; and two allied teams of six, one along its side toward the front and
one along its rear (`CordonCamp`, found at setup clear of the landing field,
the base's berths and door and the air base). It holds against the swarm's
ordinary pressure, which the line stops short of it, and can fall to a tide
that breaks through (owner, 2026-10-31: "it would be fun if the enemy could
break through"; untuned). A convoy home stops on the road through
it and lets its people down one every two seconds: each steps off as a
civilian, is **out** from that moment, and walks in to wait at the camp's rear
for the rest of the battle. Its emptied trucks pull back down the road behind
the camp and wait there; the next convoy called sets out in them (a new truck
comes on only when none waits), so pick-ups and drop-offs are one road from the
camp to the gate and back. The people waiting in the camp are civilians the
swarm can reach and kill. A townsperson the swarm kills in the camp stays
**out** and is not lost: once they reach the drop-off the company's job is done
(owner, 2026-10-31). R2's passengers walk to the camp and are out when they reach
it.

**Corridor.** The relief road from the rear edge through the line to the
cut-off town's compound: the way the convoys drive (*The map*). Nothing is
parked or built on it, so a convoy can always drive it if nothing is standing
in the way.

**Convoy (the Road).** An allied column of trucks that sets out from the
camp (*Camp*; with none, from where the corridor passes the logistics base),
drives the corridor to the town, waits at the compound's gate while the
townspeople board, and drives back to the camp to let them down. Only a truck at the gate takes people aboard, one every two seconds
while its seats last: a truck still on its way when the first arrives drives
on to the gate and boards whoever is left when it gets there. The convoy turns
for home when every truck is at the gate and full, or at once when the town has
nobody left to board, or at the **loading cap** (a minute from the first truck
at the gate) with whoever has boarded, a straggler that never came driving back
with what it has, or empty. It does not fight its way through: it stops when the road ahead is
blocked by the swarm, and goes on when it is clear. Past the gap, in the
swarm's country, it rolls only with an escort beside it: a convoy nobody
escorts waits at the line. A convoy is **lost** when all its trucks are
destroyed; its passengers die with their truck. Convoys of two trucks, six
seats each, are **called while people are trapped**: the first at minute one,
each after it once the one before has passed the gap toward the town, so long
as the town has more people than the seats already on their way, and never
more than **five** in all. Forty-eight are trapped, so getting everyone out
takes four full trips, the fifth there for seats lost to a truck or a short
loading. No convoy sets out to an empty town or one given up. The **last
convoy** is the one that reaches the gate as the fifth or with seats for
everyone left (R3 and R4 wait on it).

A town **abandoned** (*Town states*) calls no convoy again, and one on its way
or loading turns for home with whoever is aboard (`CordonConvoys.abandonTown`).

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
on the edge row has no route out of it. A truck is set down at the first
cell a little past its entry from which it can set out whichever way its first
leg points (set down on a corner a fence's width off its turn, one stood there
all battle), the entry itself where none can. Nothing is parked within a few
cells of the corridor.

The Road also has a **second road** through the line, from the base's gate
through the gap beside redoubt 2 to the compound's rear gate (beat R5),
carved where the country is open; the corridor is still the cheapest way,
whichever that is.

**A well-found company.** The Cordon is fought late in the campaign, so the
company it stands up itself is the same strong one as the heart's (owner pick,
2026-10-25): veterans and elites in heavy suits with Milspec or Masterwork
issue (`InfantryLoadoutRolls.lateCampaignSquad`, `heart-nouns.md`). A campaign
launch lands the player's own company over those seats, and its machines
through Mech Support; a fixture's `declaredMechDrops` brings down a Marine lance
for debug launches and evidence, called just after landfall and claimed by the
company's commander. The Line and Road evidence fixtures declare one.

**Line posture.** The briefing's choice of how far the company goes out, the
Cordon's version of the hive posture (`colony-breach-nouns.md`, Hive posture),
frozen at launch: **Hold** (the company only relieves and escorts),
**Opportunistic** (the commander may send a strike detachment at the bore
pressing hardest when the readiness gate is open), **Push** (bores in front of
the line are objectives). The readiness gate keeps its three conditions with
the Cordon's inputs: (a) the line or the road has held for a sustained window,
(b) the strength left behind covers every calling redoubt and every group of
people on the move, (c) a capable detachment exists. A battle no briefing chose
plays Hold. The gate is read at every command pulse (`CordonStrikes`): held means
no redoubt unsettled and no road blocked for twenty seconds; the strength is the
company's squads less the detachment against the squads every duty wants
(relief of a calling redoubt, escort of a group on the move, the road's
clearing: the company's duties in `cordon-allied-command.md`); a capable squad
carries a satchel, is a machine or fires on structures. Opportunistic needs all
three, raids only between tides and sends one squad; Push waives the held
window, raids in a tide too and sends two. The bore pressing hardest is the
living bore whose nearest manned redoubt is worst off (unsettled, then bled,
then uncovered), the bore nearer the detachment breaking a tie. The detachment
stays the same squads until its bore collapses, it is lost, or the gate closes
and it is recalled; nobody is sent again for fifteen seconds after a recall.
Posture governs only what the commander does unprompted: the player can order
any squad at a bore.

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
base packed no shed, whose shed has no machine door (its door read as the
muster, and the base built one machine all battle; an open-air depot's
gantries have none to find), or whose apron a carrier cannot drive from to every
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
open with room either side and road five times cheaper than open ground, and
a turn made on a cell with no room to turn on (two cells open all round) much
dearer: a truck drives a lane three wide straight but cannot turn into one,
and a corridor that turned into an alley wedged every convoy at its mouth. It
is found on the ground as the battle will stand it, every nest a gun is seated
in closed: a corridor found past a nest's open cell ran its trucks into a
chicane between that gun and a redoubt's wall. It
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

The win is people (owner, 2026-10-31). The company **holds Ain Tafrant** (the
staging area inside the town's gate, where its trapped people wait) while the
convoys **bring the people out**; once the town is finished, empty or
abandoned, it **falls back behind the line** to end the mission. The town holds
more people than three trips carry and at most five; the briefing's number is
half of them, rounded up (owner's call). The line and the gap are the allies';
a gap that closes stops the convoys, which costs people, not the battle.

1. **Landfall.** The company comes down in the rear fields, near the
   corridor's entry. The gap is open; the town's defenders hold it alone.
2. **Clear ahead.** The first convoy enters on the timetable and drives the
   corridor. The company walks ahead of it through the gap and pickets the
   bores that bear on the road.
3. **Ambush.** A surge reaches the road while a convoy is on it: the convoy
   halts and the company has to clear it on. A convoy whose road is not cleared
   is eaten where it stands.
4. **The town.** A convoy at the compound's gate waits while its share of the
   trapped townspeople board from the staging area, then turns for home. The
   town's militia and guard hold the compound from its wall; when the swarm
   gets inside, or half of them are down, the town calls, and as they bleed
   the company has to come up and hold the staging area itself.
5. **The tide.** As in the Line, at set minutes every bore surges at once; on
   the Road the tide falls on the gap and the town, and between tides bores
   break out along the line (*Line bores*). The two redoubts either
   side of the gap call; if both fall the gap is closed (`gap_closed`, told,
   not an end: the convoys stop at it).
6. **The town is finished, or the company pulls out.** Everyone is out or
   dead (`town_empty`), or its staging area is overrun and the town abandoned
   (`town_abandoned`, the rest die); or, once enough are out, the player is
   told the company has done enough (`done_enough`) and orders a squad to the
   pull-out zone (`pull_out`, *The pull-out*). The fall-back starts
   (`fall_back`), and the swarm surges at the line (*The final surge*; after
   `town_empty`, once no truck is left at the gate).
7. **The end.** The company falls back behind the redoubt line, every Marine
   on the ground four rows or more behind its mean row, and holds the pull-out
   zone for ten seconds without a break (`company_withdrawn`). Once it has, and (unless
   it pulled out) no convoy is still on the road, the Road ends:
   won (`cordon_held`) when at least the briefing's number of townspeople came
   out, else lost (`people_short`). A wiped company loses first
   (`company_lost`). No window ends the Road; a hard cap of thirty minutes
   reads the same verdict wherever the battle stands, so it cannot run for
   ever. Redoubts that fall, a closed gap and lost convoys decide nothing by
   themselves.

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
machines: `cordon-allied-command.md`; posture and strikes: Line posture
above.

### The player's picture

The shared mission picture (`CordonChecklist`) gives the HUD rail, the map's
beacons and the briefing survey the same rows. On the Road the rows follow
its phases. The first is **Hold Ain Tafrant**: beaconed at the staging area
(its cells the beacon's footprint), saying how it stands (held, pressed,
overrun) or that the town is calling, tallying the townspeople still trapped,
a loading convoy's boarding (aboard of seats) and the defenders standing, and
asking for squads while the swarm is in it or the town calls; complete once the
town is empty, failed once it is abandoned. The second is **Bring the people
out**: beaconed at the gap, the town's gate and each convoy on the road,
tallied as townspeople out of the town's people against the briefing's
number, and saying which convoy is held and why (no escort near its trucks
past the gap, or the swarm on the road ahead); its focus is a held convoy,
then one on the road, the gate while one loads, and the gap while the next is
still to come; complete once enough are out, failed once too few are left
alive. Once pulling out is offered the third row is **You've done enough**,
the warning, beaconed at the pull-out zone (its cells the footprint) and telling
the player to order a squad there; once the company is falling back it is
**Fall back behind the line**, beaconed there until the company is behind the
line and holding the zone. The Road
has no row for the line itself; from the first line bore stirring it has a
row **Bores along the line**, beaconed at each stirring site with the time
until it opens and at each open line bore, its focus the site opening soonest,
which once the final surge comes says so and asks for the company back behind
the line. On the Line the row is the
line: any redoubt calling, the first its focus, the redoubts lost against those
allowed, and the window's time left. Tides are never foreseen on it, as on the radio. The rolled beats
follow in the Optional section, in roll order: each beaconed where it is
while it runs (where it calls the company, else its own place: the gate, the
gap, the wreck or dozer, the bore's site, else its set piece), saying when it
wants Marines now, and struck once failed or lapsed. A beat with no place yet
(a truck that has not gone down) is listed without a beacon.

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
| `picket_contact_N` | both | PLAYER | The swarm reaches outer picket post N |
| `picket_back_N` | both | PLAYER | Outer picket N's survivors are inside their redoubt and join its garrison |
| `picket_lost_N` | both | PLAYER | Outer picket N's last man dies |
| `tide_N` | both | PLAYER | The Nth tide: every living bore surges at once |
| `strike_committed_N` | both | OBSERVER_ONLY | The commander sends a strike detachment at bore N; at every commit |
| `strike_recalled_N` | both | OBSERVER_ONLY | The readiness gate closed and the detachment at bore N is recalled |
| `eruption_N` | both | PLAYER | The Nth eruption: a new bore opens in front of the line |
| `line_bore_stirring_N` | the Road | PLAYER | Line bore site N (in opening order) stirs: it opens shortly |
| `line_bore_N` | the Road | PLAYER | Line bore site N opens |
| `final_surge` | the Road | PLAYER | The final surge: the town no longer needs the company and the swarm goes at the line |
| `redoubt_calling_N` | both | PLAYER | Redoubt N calls for relief |
| `redoubt_relieved_N` | both | PLAYER | A Marine reaches redoubt N's gate while it calls |
| `redoubt_fell_N` | both | PLAYER | Redoubt N falls |
| `column_entered_N` | both | OBSERVER_ONLY | The Nth allied column on the timetable comes on at the rear edge |
| `column_arrived_N` | both | PLAYER | The Nth allied column, a replacement squad among them, reaches the logistics base (or, with no base, its redoubt) |
| `reinforcements_N_K` | the Road | PLAYER | Redoubt N's Kth call for replacements: they set out from the rear edge ("Reinforcements marching to redoubt N") |
| `carried_N` | both | PLAYER | The Nth squad a carrier took up from the base joins a redoubt |
| `air_sortie_N` | both | OBSERVER_ONLY | The air base sends the Nth sortie of its own tasking (not an observer's call) |
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
| `town_contact` | the Road | PLAYER | First swarm body within twenty cells of the town's wall |
| `town_calling` | the Road | PLAYER | The town first calls for help (swarm inside its wall, or half its defenders down) |
| `staging_pressed` | the Road | PLAYER | The swarm is first inside the compound's wall (the id is kept from when the staging area was the town) |
| `staging_overrun` | the Road | PLAYER | The swarm has been inside the compound with no defender or Marine in it for ten seconds |
| `town_empty` | the Road | PLAYER | Nobody is left trapped in the town: the town is finished |
| `town_abandoned` | the Road | PLAYER | The compound is overrun: the town is abandoned and everyone still in it dies |
| `done_enough` | the Road | PLAYER | Enough townspeople are out and no truck is loading: the company may pull out |
| `pull_out` | the Road | PLAYER | The player ordered a squad to the pull-out zone: the company pulls out |
| `fall_back` | the Road | PLAYER | The town is finished or the company pulled out: it falls back behind the line |
| `company_withdrawn` | the Road | PLAYER | Falling back, every company Marine on the ground is behind the line and Marines have held the pull-out zone ten seconds |
| `people_short` | the Road | PLAYER | The Road is lost: it ended with fewer townspeople out than the briefing's number |
| `gap_closed` | the Road | PLAYER | Both redoubts beside the gap have fallen (told; not an end on the Road) |
| `line_broken` | the Line | PLAYER | More redoubts have fallen than the briefing allows |
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
| L1 | `stragglers` | farmstead or a city's tenement | At 1:30 six people stand at the farmstead and wait for a Marine, then walk in through the nearest redoubt | The last is ten rows behind the line, someone alive | All die, or nobody came by the end |
| L2 | `guns_dry` | ammo dump | At 5:00 one redoubt's gun towers (seeded, never the switch line's) run out of rounds and fire at a quarter rate; the allied line command asks the base for a truck, which drives its supply road (in a city, its streets); a redoubt no truck can reach is never chosen, and with none the beat lapses | A truck reaches the redoubt: its guns and tubes are full again | A truck bound there dies or the redoubt is lost first |
| L3 | `switch_line` | switch line | At the second tide the switch line's middle redoubt is given up and its garrison ordered back | Most of those who set out are in the switch line | Too few are left alive for most |
| L4 | `bore_behind` | eruption site (a city's cleared lot) | At 9:00 a bore opens on the eruption site through the ordinary seam | Collapsed while a fissure | It grows to a bore (about three minutes) |
| L5 | `last_hamlet` | bell square | At 7:00 six people gather at the bell and wait for a Marine, then walk to the rear edge | All out | Caught (the first death), or nobody came |
| L6 | `burn_granary` | granary | From the start: a Marine holding it twenty seconds torches it, or any hand brings half its walls down | Burnt before the second tide: that tide comes at three quarters of its pressure | A Marine reached it and it stands at the second tide (lapses if none did) |
| R1 | `clear_road` | the wreck's held ground | The wreck is laid at setup only when rolled; the base's dozer, moved up behind the gap, drives onto the wreck and works each shut road cell until a truck's lane is open, then pulls aside | The road through it opens before the first convoy's lead truck reaches it | The dozer dies, or the convoy reaches a shut road (the dozer keeps on; routes are laid again when it opens) |
| R2 | `truck_down` | none | Convoy 1's second truck, loaded, breaks down halfway between town and gap; its people walk back through the gap to the camp | Four reach the camp | Fewer than four can be |
| R3 | `last_loading` | none | The last convoy waits two minutes longer at the gate, its people (the wounded) boarding slowly enough to fill its seats over that time | It leaves loaded | It is lost first |
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
  and back (since 2026-10-31, the owner: the win is people, so up to five are
  called while people are trapped). A truck destroyed on the road is made good:
  each two lost (or part of two) adds a trip past the five, which sets out from
  the camp no sooner than forty-five seconds after the loss (untuned), so lost
  trucks never count against the cap.

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
