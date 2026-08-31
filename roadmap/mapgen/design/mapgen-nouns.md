# Map generation nouns

Status: ACTIVE — the recipe/context/stage model is shipped; bounded content expansion remains open.

Written: 2026-08-23

Updated: 2026-08-30 — added vehicle circulation: a corridor is a width
contract rather than a centerline, reserved before packing where pedestrian
circulation still is not, and validated by whether a hull fits rather than by
whether cells connect.

Updated: 2026-08-30 — an airbase lot owns the ground it repaves, and is reserved
against the stampers that follow it; a fortress ward refuses a lot over its
keep; the lot turns four ways and comes in three sizes that grow along the
frontage rather than into the depth.

Updated: 2026-08-28 — replaced the blanket runtime-construction exclusion with
the no-islands law construction must satisfy and added the passable
field-revetment profile that law admits; added the Conquest fortress ward,
packed walls as authored rather than left over, family-neutral room fittings,
and the obligations a replacing stage carries; pointed at the compound-program
direction; shipped
shared-edge windows for Conquest bunkers and ordinary building shells,
widened compound firing aprons while preserving functional parcel members,
added role-readable compound dressing and authored multi-cell civic room
programs with wall- and aisle-aware fixture rotation, guaranteed standable
interior anchors, and made pocket sealing yield to a compound.

Map generation turns a deterministic request into a validated tactical world. It
owns authored spatial intent; runtime systems own subsequent mutation and play.

## Generation request and result

A **generation request** selects a seed, a map family, and an optional target
profile. The seed is part of the contract: identical request inputs and recipe
composition produce the same map. A stage must not add an RNG draw for a silent
or absent feature, and unordered iteration must never decide an outcome.

A **map result** is the finished tactical contract: terrain and movement
topology, reachable spawns, points of interest, tactical nodes, buildings,
defense posts, landing pads, decorative placements, and the authored road or
biome data that battle consumers need. Recipe-internal analysis such as station
graphs and tactical-region maps may remain in the context until an external
consumer justifies result plumbing. Final validation protects connectivity,
deployability, doors, and other cross-system assumptions. Decoration may
express a place but cannot secretly create its movement, cover, or wall rules.

## Recipe, context, and stage

A **recipe** is a named, immutable arrangement of stages for one map family.
A **stage** is a stateless transformation of a fresh **generation context**.
The context is the run-local mutable spine: it contains the seed-owned random
stream, spatial work products, and typed shared facts. Recipes describe the
pipeline at a high level; a stage owns its own algorithm and may publish only
facts that later stages actually need. This makes a new map family an explicit
composition choice rather than a widening conditional inside the generator.

## Tactical space

The tile grid and its topology are tactical authority. A cell owns standability;
a shared cardinal edge owns the transition between two standable cells. A thin
edge barrier may therefore divide adjacent usable cells without consuming
either cell, and zone connectivity must honor that closed transition. Open-city
generation starts from traversable terrain and adds structures; station
generation starts from solid space and carves a reachable interior. A city road
graph is planning and visual structure, not the authority for unit movement. A
station graph is the authored relationship between rooms and corridors, used to
reason about reachability and roles without making incidental tile shapes into
API.

Edge passability is navigation topology, not by itself a structural wall. A
production thin barrier must publish one profile from which movement, ballistic
interception, visibility, directional cover, durability, and presentation all
derive. The law is that the profile is one identity, not that any particular
combination is forbidden: closing an edge only for A* would create a secret
obstacle, and drawing a full-height wall without closing its edge would create
dishonest scenery, but a chest-high revetment that a soldier steps over, shoots
over, and takes cover behind is honest precisely because it declares all three.

Runtime mutation is admitted in both directions, under different obligations.
**Destruction is free**, because it only ever makes the world more permissive: a
path that was valid stays valid, a zone that was connected stays connected, and
a unit standing somewhere legal is still standing somewhere legal.
**Construction must not create an island.** That is the harm the model exists to
prevent — a unit cut off from its objective, or sealed inside a region with no
way out — and it is what a runtime placement has to answer for, since a new
obstacle can partition the walkable graph that everything already standing on it
depends on. A construction that does not touch navigation cannot cause it: cover,
presentation, and structure are invisible to walkability, zones, the
navigation mesh, and retained paths, so such a placement needs no check and no
flush. A construction that *does* block navigation must prove, before placing,
that it neither partitions the walkable graph nor strands a unit — before,
because a repair afterwards is already too late for the unit inside. That proof
must be reachability as the pathfinder computes it. Zone connectivity is not a
substitute: `ZoneDetector` floods on cell walkability alone while the pathfinder
honours edges, so a zone-graph answer can say "connected" about a region no unit
can actually walk out of.

Only the non-blocking case is built. `MapEditor.placeDeployedBarrier` is the
single runtime construction seam and enforces the condition mechanically, by
refusing any profile that blocks movement; the carried cover screen in
`progression-nouns.md` is its one consumer. A future movement-blocking placement
extends that seam with the reachability proof rather than bypassing it.

A **shared-edge barrier** is that authored identity. It is stored once on a
canonical east- or north-facing edge, while reciprocal lookup from either
adjacent cell returns the same feature. Both cells must already be walkable and
the transition initially open: the feature divides usable space instead of
smuggling a cell wall into edge metadata. Its profile defines sight and direct-
projectile policy, directional cover and vertical catch, structure, and
appearance together. Canonicalization also retains the adjacent authoring cell
whose structure owns the feature. That structural side orients presentation
today and can resolve through the map's building registry for later destruction
objectives without turning visual thickness into collision.

Two profiles exist. A transparent firing **window** blocks movement, passes
sight and direct rounds, supplies low cover to both adjacent positions, and can
be broken by structural blast damage; it is authored by generation. A **field
revetment** is chest-high cover that leaves the transition open — passable,
transparent, shoot-through, low cover to both sides, its own structure to spend —
and is therefore the profile a runtime placement may use. Destruction of either
removes the identity and its cover; for a profile that had closed its edge it
also opens the reciprocal transition through the map editor, so zones, retained
paths, vantage caches, and the region mesh advance together at the normal
topology flush. A profile that never closed one skips that flush, because there
is nothing derived to advance.

Diagonal traversal consumes the neighboring cardinal transitions as well, so a
unit cannot slip around a closed barrier endpoint in one diagonal step.
Arbitrary angled or within-cell dividers remain outside this model: a shape
that partitions one cell into disconnected interiors would require explicit
subcell regions or a finer low-level lattice, not more meanings on a cell edge.

The greedy navigation mesh is derived runtime structure, not generated map
authority. It combines compatible walkable cells into deterministic rectangular
regions, preserves doorways and closed edges as seams, and publishes passable
boundary intervals between regions. Generation and runtime destruction mutate
cells or shared edges; the ordinary navigation-topology flush then rebuilds the
mesh alongside zones and geometry-dependent caches. A generator must never
author a region directly or depend on a particular greedy decomposition.

**Room purpose** is a carve-time semantic label, such as a room, corridor, or
special facility. Consumers ask the purpose instead of rediscovering regions
from coordinates. It must remain an authored fact, not a post-hoc convention.

**Tactical regions** express larger-scale city texture and positional reasoning
such as overwatch; they do not replace tile topology. Buildings and compounds
are coherent tactical places with circulation, a readable identity, and a
reason for their interior geometry.

A tactical node's **anchor** is the stable identity of a place and may sit on
an intentionally non-walkable wall or turret mount. An authored **stand
position** is different: it is an exact walkable member cell belonging to that
place, such as the protected cell behind a bunker firing aperture. Consumers
prefer authored stand positions before deriving nearby cells, and later
generation stages must not overwrite their enclosing authored footprint.
Because an anchor carries identity rather than standability, a consumer that
needs somewhere to stand — or a room to resolve — derives that cell from the
place's footprint instead of reading it off the anchor.

A late stamper that overwrites earlier structure can strand walkable ground
behind it. Such an **orphaned pocket** is normally filled in solid, because a
room with no way in is scenery, and cutting it a new entrance would scatter
openings through authored wall geometry. A pocket holding a compound is the
exception: it is breached open, because a compound is a mission's win condition
and burying one does not cost a room, it makes the mission unwinnable. The
general law is that structure yields to a place the mission depends on, never
the other way round — and that declining to bury such a place is not enough on
its own, since ground nobody can walk to is as useless as ground that is gone.
Pocket reachability follows shared-edge passability, not cell adjacency: a
window recess is not an entrance. Sealing removes any sparse barrier identity
touching the discarded cells, while a required breach destroys any barrier it
crosses before publishing the open route.

A point of interest's **interior anchor** is the opposite promise: the cell a
mission objective is placed on, and therefore standable whenever the footprint
encloses a standable cell. A footprint carved solid encloses none, and its
interior anchor falls back to the exterior interaction anchor. The guarantee
cannot hold where the anchor is authored, because later stages keep changing
the map underneath it — furnishing drops a fixture on the cell, wall stampers
paint over the footprint. It is therefore reconciled once against the finished
grid, after the last stage to touch it. An anchor that fails the guarantee does
not merely look wrong: mission layouts filter candidate sites on it, and
room-scoped objectives resolve no room at all from a blocked or doorway cell.

An ordinary compound's wings are sized by the partition rather than by their
purpose, and a purpose that needs a minimum footprint has no way to ask for one.
`compound-programs.md` holds the direction that inverts this — a program that
sizes the place, as a deck's does — with the measurement that motivates it and
the parts of the deck model that deliberately do not transfer. The Conquest
fortress ward is built that way already; every other compound is not.

Parcel ownership is established before content fills. A filler may own one leaf
or an already-claimed compound, but it must not infer a multi-leaf building by
overwriting roads or neighboring fills after dispatch. Cross-leaf structures
therefore require an explicit planning stage that publishes the claimed
footprint and its circulation obligations.

Ordinary hollow building shells place shared-edge windows on facade runs
belonging to tactically usable rooms. The former facade wall cell becomes a
walkable, building-hinted firing recess; only its outside transition is closed,
and that recess is retained as the barrier's structural-owner side. Secured
storage and infrastructure rooms may deliberately remain opaque. Compound
perimeter walls remain thick structural cells and place apertures in adjacent
two-cell pairs only on sufficiently long straight runs with walkable firing
space on both sides. Compound buildings retain a two-cell internal apron to
support oblique firing angles, while gates, hardpoints, corners, and reserved
road circulation remain clear. The two models are intentional: ordinary facade
glass is a narrow boundary with presentation projected inward, while a compound
aperture belongs to a heavy wall cell. The apron expands the minimum parcel
claimed by a walled compound; it may not reduce an intended member below a
hollow shell with a standable interior and doorway.
Compound dressing is clustered by role and place: logistics props explain each
military wing, while planters and seating define shared domestic space. Hard
fixtures and visual-only clutter both preserve gates, building thresholds,
two-cell circulation, road reservations, and final walkable connectivity.
Purpose-built interiors express their function through grouped equipment rather
than a single symbolic prop: command rooms organize console banks around a
planning focus, server rooms organize repeated racks around a service aisle,
and both retain doorway, firing-position, and room-connectivity obligations.
Civic headquarters follow the same authored-program rule as ship mech labs:
their two-cell spine, opposing entrances, room depths, and fixture groups are
designed as one floor-plan family rather than discovered by random door and
prop placement. A qualifying headquarters lot is at least 15 by 13 cells. Each
office must fit its workstation-and-records group, each reception wing its
counter, the conference room its central table, and the server room repeated
racks around a service aisle. Multi-cell fixtures rotate at placement time so
their backs meet supporting walls, their working faces meet open room cells,
and centerpieces are centered by their complete footprint. On the compact
footprint, office facade windows yield to that required room capacity;
reception and conference facades retain the building's firing apertures.

## Vehicle circulation

A **vehicle corridor** is the one road across a map a vehicle is guaranteed to
be able to drive: a band of walkable cells running the full length of the
traversal axis, from the defender's rear map edge, through the fortress, out
into the city. It is authored before anything is built on it, and no later
stamp may close a cell of it.

Three road nouns are easy to confuse and mean different things. The **road
graph** is a centerline skeleton — one cell thick — used to choose among
places: where a convoy may enter, which junction is a plausible drop. The
**road reservation** is the cell mask stampers consult before closing ground
— and it is no longer only about roads. The vehicle corridor widens it, and so
does the ward airfield: a runway is no more closable than a street, and a
stamper deciding whether it may put a gun somewhere should need one answer
rather than a list of exemptions that grows every time somebody reserves
something. The name is historical; the meaning is *ground another part of the
map is relying on staying clear*.
The **corridor** is a *width contract*: not where the road is, but how much of
it a hull can occupy. Only the third answers the question a ground
reinforcement actually asks.

That distinction is the defect the noun exists to name. Conquest shipped for
a long time with the road preserved and unusable: the wall stamper honored the
reservation perfectly, but the reservation was the graph's centerline, so the
city's main street crossed the fortress wall as a single cell. Every check that
asked whether a road was preserved said yes. Measured over forty maps, infantry
walked through all of them and a vehicle could get from the rear edge into the
city on twelve.

**Vehicle circulation is reserved before packing; pedestrian circulation is
still cut from what packing leaves.** This deliberately inverts the deck
family's law — `ship-interiors-nouns.md` 11, rooms are packed and circulation
is cut from the leftovers — for vehicles only, and the scope is the point. Two
things separate a vehicle route from a deck corridor. It has an **external
contract**: a convoy enters off-map at a known edge point and has to reach the
city, so the route is owed to a consumer outside the generator rather than
discovered inside it. And its **minimum width is a large enough fraction of the
place** that it cannot be found in leftovers — a one-cell walking lane can be,
five cells cannot. Reserving one spine is not partitioning, which is what the
deck law forbids: the fortress ward still packs its buildings into what remains
and still cuts its own foot traffic from the yard.

**The corridor is not a new road.** The trunk skeleton already lays an arterial
along each axis before the partition runs, and the one parallel to the traversal
axis already spans the map edge to edge — which is why every generated map
publishes exactly one road-graph exit on the defender's rear edge. The
guaranteed rear entry has always been there structurally. What was missing is
that nothing downstream was obliged to leave it drivable. So the corridor picks
that trunk out and states its band as a fact the rest of the pipeline must
honor, rather than letting each later stamper rediscover the road from a mask
that does not describe its width.

**A place that must be reached is told which road it keeps, not left to work it
out.** The fortress ward used to derive its through route — shortest path over
whatever crossed its band, dilated a cell — and that yielded a lane two cells
wide whenever the path hugged the edge of the street carrying it, because the
dilation could only keep cells the old road mask already held. The ward then
demolished the rest, so the stage preserving the road was the stage narrowing
it. A derived route inherits its width from geometry; an authored one states it.

**Width is stated in what a hull needs, not in cells.** Five walkable cells
because the fielded vehicle erodes to a radius-1 footprint and therefore needs a
clear three-by-three: three is the arithmetic minimum and leaves exactly one
drivable line, which one wreck closes and any stamp that clips one cell severs.
Five leaves three, and matches the secondary trunk's own width so the fortress
road reads continuous with the street it joins rather than pinching at the gate.

**A gate on the corridor is found, not punched.** Because the wall stamper skips
reserved cells, the wall already has a corridor-wide opening where the road
crosses it. What that opening needs is not carving but *identity*: without a
gate node nobody garrisons the one entrance a vehicle can use, while gates
placed by dice elsewhere along the same wall each get their defenders.

**Validation asks whether a hull fits, not whether cells connect.** Walkable and
drivable are the two things this layer exists to stop conflating, so the
standing check measures with the vehicle's own footprint erosion and asserts the
narrowest drivable width across the fortress — not merely that a path exists.
It runs on both traversal axes, because the original defect was four times worse
on one of them and a single-axis check would have hidden it.

## City, station, and ship families

City recipes compose a trunk, parcels, circulation, zoning, purpose-specific
fillers, tactical linking, and final validation. The conquest recipe also
places its fortress and defense structure, while legacy/preview city generation
can use the same spatial vocabulary without importing campaign concerns.

A **ward** is the exception to parcel-first fill, and the conquest fortress is
the one place that has it. Rather than being filled leaf by leaf and then walled,
its band is laid out from a program of authored building footprints, packed into
depth bands measured from the attacker's approach, furnished by the shared room
fittings, and opened as parade ground wherever packing leaves ground over — and
the wall is drawn around that result. The order is the point: a wall stamped
first can only ever enclose whatever the fill happened to leave, however good the
wall is. `compound-programs.md` owns the model, the measurements behind its
sizing rules, and what remains before an ordinary compound can be built the same
way.

**Room fittings are family-neutral.** A furnishable room is a floor, a pose, its
doors, and its purpose; a deck compartment and a packed fortress building both
present one, and the same authored fitting furnishes either. A machine berth is
likewise a map fact rather than a shipboard one — a fortress vehicle shed
publishes the same berths a mech bay does, and what occupies one remains the
host's decision from a roster.

An **airbase lot** is the first thing generation places that is a *facility*
rather than a building or a piece of ground: a runway along its approach edge, an
apron with berths on it, hangars behind them, and a fence round the whole thing
with a gate front and back. Front to back it is laid out in the order an
aircraft moves through it, and the pieces are sized against each other rather
than against whatever was left over.

**A lot is claimed, not found.** An airfield built out of what a packer left
behind gets the shape leftovers have, and leftovers are shallow — measured on a
packed ward, unclaimed yard runs eleven to forty-six cells wide at a depth of
eight and all but vanishes at ten. That is enough for a marked apron and nowhere
near enough for a facility, which is why the earlier airfield was one. The lot is
therefore reserved out of the ward before packing and the buildings pack around
it.

**A facility has to be reserved against what comes after it, not only against
what packs around it.** The lot is marked unbuildable while the ward packs
itself, but that array belongs to the packer and dies with the stage — and four
stampers run later. Told nothing, each asks only whether a cell is *walkable*,
and an apron is open ground: measured across the conquest matrix, thirty-three
of forty-eight maps had a guardpost standing on the airfield, sixty-four in
all, with the compound-perimeter lookouts accounting for most of them. A
lookout is placed on the first walkable cell outside its compound's
attacker-facing edge, so a compound beside the field planted one on the runway
every time. Publishing the lot into the reservation, and making that scan step
over reserved ground rather than stop on it, takes it to zero — at a cost of
about four percent of the map's guardposts, most of which move rather than
disappear.

Ordering is what makes widening the reservation safe: every *filler* that reads
it has already run by the time the ward lays its lot, so a late widening cannot
change how the city was built, only what may be stamped onto it.

That reservation is the thing an earlier attempt got wrong, and the difference is
*where*. An apron claimed in the middle of a ward reshaped every placement around
itself and produced a map where every building had its door and its route and no
heavy vehicle could reach the defender rear at all — walkable, connected, fully
checked, and impassable to the one thing that mattered. A lot pinned to the
ward's lateral end takes width off one side instead of cutting the ward in two,
and it yields outright to the one road the ward keeps: a base that would sever
that route is not sited at all.

**The ward has to be able to afford it.** Sizing is not a preference here but an
arithmetic fact — a ward's width is capped by the map's own, and at the previous
full-strength scale the spare inside it was exactly the old apron and nothing
more. A facility costs a thousand cells; the map grew to pay for it, because the
alternative was a third of the fortress's buildings going unplaced. A host
reserving a lot checks what it wants to take against the program's building
ground and declines rather than starving the packing.

Berths belong **on the apron**, never inside a shed. A hangar is where an
aircraft is worked on and a berth is where one waits to fly; a berth indoors
makes its crew walk through a building to board and hides the aircraft from the
fight the airfield exists to be part of. The sheds are real buildings — a wall
ring with an aircraft-wide opening onto the apron, not a marine-wide door — and
they are worked inside, because what makes a shed read as a maintenance hangar is
the tooling in it, and because the people who work there are units who need
somewhere to be. The kit lines the walls and the middle stays clear, which is
both how a real one is arranged and what keeps the shed crossable.

**Depth budgets are exact, and being one row out is invisible.** The stack behind
the runway — strip, margin, berths, taxiway, sheds — has to land the sheds' back
wall inside the fence. One row further and the fence overwrites it, leaving two
hangars with three walls each: something that looks almost right in a render and
is open at the back. The same class of error put an earlier shed flush against
the front of its own paving, where its mouth opened onto the far side of the
fence and the crew had to leave the base to reach an aircraft parked in the
middle of it. Reachability from outside the lot is asserted on both traversal
axes, because a mirrored layout is where that hides.

**A lot owns everything on the ground it repaves, and a host may not hand it a
place the mission depends on.** Those are one rule seen from both ends. Paving
is replacing, so the general obligation on a replacing stage applies to a
facility exactly as it does to a ward: the cells, and also the building ids,
kind hints, wall masks, doorways, authored edges, decorative placements, points
of interest and tactical nodes recorded about them. A building id left under
fresh tarmac is not merely stale — a roof is drawn wherever a cell carries one,
and a kind hint left behind is flooded back into one at finalize, so a lot laid
over a shell produced a slab of roof standing on the apron with no walls under
it and the demolished building's capture marker on top. The stage that drew it
had cleared the ward correctly and the lot had paved correctly; the ground in
between belonged to neither.

The other end is where the fix belongs. Clearing alone would have the facility
quietly delete the mission's own command post, which is the harm the
yields-to-a-place rule exists to prevent, so the host declines the site
instead — the fortress ward refuses a lot over its keep the same way it refuses
one across its kept road, and falls down its size ladder or goes without.
Measured over forty-eight generated Conquest maps, eleven laid a lot over the
keep and three of them have no ward airbase once that is refused. The two
halves are complementary rather than redundant: with the ground properly owned,
a host that makes this mistake anyway fails generation loudly at the
one-central-keep check rather than shipping a marker floating on an apron.

A lot **reserves the ground outside its own fence**, not just the ground under
it. A fence on the boundary of its reservation is one a building can be packed
flush against, and the way past the base is then whatever the packing happened to
leave — including nothing. A facility that blocks travel round it has made the
map worse in exchange for reading well. It also carries **a gate on every side**:
front and back are how the base is used, and the two ends are how everybody else
gets past it.

**A facility is made of distinct surfaces, and there are four of them because a
reader has to tell them apart at map zoom.** The apron is asphalt; the runway is
a *different* tarmac rather than the apron with paint on it, because a strip is a
different piece of civil engineering from the ground beside it; buildings take an
indoor floor, since what makes a shed read as a building from above is that the
surface changes at its wall; and the clearance outside the fence is the city's
own sidewalk. Reaching for a ground kind by how it looks today is how a verge
ends up paved in the polished tile every civic interior uses — a surface that
says "indoors" everywhere else does not stop saying it out here.

**Markings are laid on a surface, not made of it.** A runway or a berth painted
as a ground *kind* is only visible while it contrasts with the ground beside it,
and the ground palette is not any one feature's to hold still: a re-export of the
floor sheet turned a marked strip and the apron round it into the same colour
without touching a line of the airbase. Paint goes on top, as floor, which is
also how a ship's deck marks a machine bay — a striped edge round a clear middle,
because the middle is where the thing stands and a filled rectangle would be
drawn over by it.

The lot comes in **sizes**, and they are the same laws at every scale rather
than one design per scale. Berths on the apron and never inside a shed, a marked bay in each
shed with the work arranged round it, a gate on every side, clear ground
reserved outside the fence, markings laid as floor — those are properties of an
airbase, not of a big one, and every one of them is asserted at every size on
both traversal axes. What changes is how much of it there is. The full field is
an installation: a runway, three berths, two sheds, a tower and a vehicle park.
The compact site is two berths, one shed, and no strip at all, in about two
fifths of the ground. The station is the full field grown: a longer strip, a
third shed, and a wider taxiway. Every size publishes the bay inside each shed
as well as its apron berths — the bay is on the map either way, and whether an
aircraft is based in one is the host's call rather than the lot's.

**A bigger size grows along the frontage, never backwards.** Depth is the
scarce axis and the only one a larger lot can realistically be refused for: a
fortress ward comes out roughly eight times wider than it is deep, so a variant
three rows deeper than the full field was declined on every map while two
hundred cells of width sat unused beside it. Length is what a strip wanted in
the first place. The cost to the rest of the map is within noise — measured
over six seeds, walkable ground moved by nine cells in forty-one thousand and
the landing-pad count did not move at all.

**Measure a generation question at the size the game generates at.** A sweep
for that variant across twenty-four seeds at 240x168 found no runway on any of
them, which reads as the whole feature being unreachable. Conquest generates at
`MapScale.LARGE`, and at the real size every one of those seeds lays exactly one
strip. An answer measured at the wrong scale is an answer about a different map.

The lot also **turns**. Its pieces are all placed in its own frame — along the
frontage and into the depth, never in map x and y — so facing it any of four
ways is a change to two accessors and everything follows. A site anywhere but a
fortress ward fronts onto whatever it was built along, and that can be any
direction.

**A quarter turn is a rotation; a half turn must be a mirror.** Props are drawn
lit from one direction, so turning a vehicle through a hundred and eighty
degrees lights it from underneath and it reads as upside down. Mirroring turns
it round and keeps the light where it was. A vertical flip through a half turn
is the horizontal mirror that does it.

**A site with no runway is not a diminished airfield; it is what most airbases
are.** Aircraft that land vertically need somewhere to stand and somewhere to be
worked on, and a strip is what gets added when something has to roll. The
compact size therefore fits where the full one cannot — a city block, a
compound's yard, a map that is not a fortress — and it is the shape a player's
own arrival wants: a berth, its servicing, and a fence round the lot.

**Whether a base is operational is the host's call, not the lot's.** The
geometry is identical either way; what changes is who picks its berths up. A
fortress ward publishes garrison berths, which supply an air arm and can be
taken to stop it. A city block publishes a civil landing pad and no airbase
node, so nothing flies from it and nothing gates on holding it — it is a place
a mission can put somebody down, and a walled lot with hard cover to fight over
once they are. Building a second, cosmetic airbase to get the second behaviour
would be two things to keep in step for no reason.

**A city landmark is promoted from what is left, after the compounds have
claimed.** Promoted rather than rolled per block, because only a handful of
blocks can hold one and a per-block roll would put it on a minority of seeds and
never where it fits best. After the compounds, because compound seeding takes
the largest leaf it can find — which is the same leaf a landmark wants. Run
before it, the promotion is silently overwritten on every seed: nothing appears
and nothing complains.

**A city landmark too big for a block is a compound claim, not a second
mechanism.** Compounds already take two or three adjacent leaves and dissolve
the street frames between them into one interior, which is exactly what a lot
wider than a block needs. An airbase is therefore one more thing a claim can be,
seeded after the bases and before the claims run — after, so it does not fight
the military base for the largest leaf; before, so its own claim still gets to
grow.

**Sizes are a fallback ladder, so a short claim is a smaller base rather than a
hole.** A claim's rectangle is whatever its members turned out to be and not
what was asked for, so a filler that only knew the large size would paint
nothing on a claim that came up short — a gap in the city with no error. The
seed demotes to the block-sized kind when it cannot claim at all, and the filler
falls back through the sizes when the claim is smaller than hoped. The worst
case at every step is a smaller airbase.

**A claim's bounding box is not the ground it owns.** Members are rarely a neat
rectangle — two leaves offset, or three in an L — so the box drawn round them
contains cells belonging to leaves that are *not* members, and those leaves are
filled by their own fillers either side of this one. Building in the box put a
city building through the airbase's own fence: the perimeter ran across a wall
and the building carried on outside the site, and nothing detected it because
both halves were individually correct. The ground a claim may build on is cells
inside a member leaf, plus cells inside no leaf at all — the street frames
between members, which belong to nobody. Everything else is somebody's block.

**Nothing stands in a doorway, and what happens to the thing standing there
depends on whose ground it is on.** A door is cut by whichever pass built the
wall it is in; the ground outside it is dressed by a pass that runs later and
knows nothing about it. Measured over ten cities, thirty of six and a half
thousand doorways opened straight into something solid — a crate, a bollard, a
compound's own fence — and none of them was placed in error. Only a
reconciliation after every pass has finished can see it, which is what it is:
not a rule imposed on the passes that place things, because any of them may
legitimately want that cell and none can see what the others did.

Scenery in front of a door is scenery in the wrong place, so it goes and the
cell is walkable again; removing it can only add connectivity, which is the
whole of that argument. **A door onto a facility's own made ground never opened
onto the street.** A perimeter is not clutter, and punching a hole in it to
honour a neighbour's door gives a civilian building a private way into a fenced
lot — so the threshold becomes wall instead, and only when it is a dead-end
stub, which is a local check that the seal cannot cut anything off. A threshold
with two ways out joins two places and is left exactly as it was: a cosmetic
rule does not get to sever a map. Thirty blocked doorways became one, and no
cell anywhere became unreachable.

**A biome is the ground a place is built on, not the ground a place is made
of.** The beach override repaints outdoor ground as sand so a shore reads as one
continuous strand, which is right for a road, a park or a yard and wrong for a
facility: an apron, a runway and painted berth markings are engineering, and
sanding them left a fenced airbase floored in beach with each berth's outline
eaten away in ragged patches. Nothing was misbehaving — the terrain pass runs
after the fill that laid them, and both halves were doing their job. So a
facility **claims the surface it made**, and terrain passes leave those cells
alone; the shoreline pass already kept a road reservation dry for the same
reason, and this is that exemption generalised. Only what is inside the fence is
claimed. The verge outside is ground the lot *reserved* rather than ground it
*made*, and a shore that reaches the fence line and stops is exactly right.

**Clearance belongs to the placement, not to the size.** What the reservation
outside the fence buys is a way past the base, and whether that has to be bought
depends on where the lot is going. A lot carved out of a larger reservation has
no guarantee of one and must reserve it; a lot filling a city claim is already
bounded by the streets its members front onto, and charging it two cells all
round is asking for a lot four cells wider than the one the claim can hold. That
arithmetic is invisible: the claim measured at exactly the compact size, the
placement search found nowhere for it, and every seed quietly fell back to the
smallest base. The size says what it wants by default; the host that knows the
ground says what it gets.

**A gate is only a gate if it opens onto something.** On a compact lot a shed's
back sits against the perimeter, and a gap cut in the fence there is a doorway
into masonry — it reads from outside as a way in and is not one. A side closed
by a building is closed honestly, so the rule is that every side is either gated
or built against, and at least two are gated: two is what makes a lot a
through-route rather than a cul-de-sac, and four is only available when nothing
is built against the fence.

**A rank of berths is centred; a single one is not.** Centring one berth on a
small lot puts it in the middle of the only open ground there is and leaves a
useless margin all round it. Set into a corner it leaves one continuous piece of
apron, which is where the vehicles go and where anyone crossing the lot walks.

**Ask the lot whether it fits; never restate its size.** A host that encodes
what an airbase needs as its own pair of numbers will disagree with the lot
eventually, and the way it disagrees is that the host promotes a block the lot
then declines — a city with no airbase and no error. The same mistake in
another form is choosing an orientation from the block's proportions: that
reasoning assumes a lot wider than it is deep, and the compact sizes are not.
Try each facing and take one that fits.

The lot knows nothing about where its rectangle came from, so a fortress ward, a
city compound, or a future installation map can each reserve one and hand it
over.

**Wedging is a hull's virtue and a compound's defect.** A packer scores a
position by how tightly it wedges against something already solid, which is
correct inside a vessel: a void between two compartments is wasted displacement.
On open ground the same reward chains every building into one continuous slab,
and nothing the packing checks notices — each room keeps its door and its route,
so every test it applies passes. What it cannot see is the cost of crossing the
place, which is why a place packed on open ground states how freely its rooms
may be glued to one another. The bound that matters is the ground's own size: a
run of wall longer than half the extent it stands on has no way round inside the
place at all, and only a single building may exceed that on its own.

**One door makes a room reachable; it does not make it a fight.** Reachability
is all packing checks, so a building large enough to matter can come out with a
single entrance — widened to two cells, which reads as two doors on the same
wall — and be cleared by holding that one doorway. The attacker never chooses an
approach and the defender never covers more than one, so the interior might as
well not be there. A place therefore says how much floor earns another way in,
and each further one is cut on a face the room does not already have: another
door beside the first changes nothing, while one on the far wall means the
building can be entered from two sides, flanked, or given up at one end and held
at the other. A hull keeps one hatch per compartment; how a deck is cut belongs
to the ship family.

**On open ground that rule has to be asked twice, because the yard does not
exist yet when the buildings are placed.** A ward is packed into solid earth and
its yard is opened afterwards out of whatever the buildings did not take, so at
the moment each building goes down there is nothing on any of its other sides
for a further door to face. The search therefore found none, for every building
in every ward, and the law delivered exactly one face everywhere it was supposed
to apply — the vehicle shed included, which is the building the reasoning above
is written about. So the family that opens ground asks again once it has opened
it, before it stamps its walls and before it furnishes: same law, same
faces-not-count rule, later moment.

**A further door goes only where the room says a door may go.** A fitting states
that in two parts and they are different questions. Its **hookups** are how the
room is *entered* — read before placement, because they decide where the room
may go, and scored by the placer. Its **further doors** are where a door may
later be *added*, read by nobody until the ground around the room is open.
Folding the second into the first would put positions nobody was going to cut
into every placement score, moving rooms on every deck to serve them. A fitting
that states neither keeps the way in the packing found it: it furnished its
floor without knowing where a hatch might land, so a door cut elsewhere opens
onto whatever happens to be standing there — and nothing downstream notices,
because the room is still walkable through the door it already had.

**A wall is left over on a hull and authored on a map.** A packer carves a
room's floor and leaves the ring around it alone, which is the whole of a
bulkhead inside a vessel: a void in a hull is structure, and nothing has to say
so. On a map it is not. Ground reads as wall only where the cell names which of
its faces are outside, so an unauthored ring falls through to the blank fill
meant for a cell buried inside a wall mass — a building with no wall on any
face, standing as an invisible obstruction with its furniture apparently in the
open. A host that packs rooms onto a map authors the wall its packing implies,
and gives that wall ground to stand on: a floor laid flush to the edge of its
envelope puts the ring outside it, on ground the host does not own and may not
close. The same goes for what a wall carries: an ordinary building shell opens
firing windows along its facades, and a packed one that does not is a building
its garrison can only shoot out of through the door. A window is a shared-edge
barrier on a standable cell rather than a see-through wall cell, which is what
lets one feature describe itself identically to movement, sight, fire and cover.

A stage that replaces what an earlier stage built owns everything the earlier one
recorded, not only what it drew: decorative placements, points of interest,
tactical nodes, and authored shared-edge identities all belong to the ground being
replaced. Because an edge carries exactly one authored identity, a leftover
window is not merely scenery whose building is gone — it is an edge the next
stage cannot author on. The obligation runs the other way as well: a stage that
clears ground to make room for its own must be told what it may not clear.
Keeping the fortress wall's route out of the ward is only half of not destroying
it, since the same stage demolishes whole buildings that stray into its sweep,
and a garrison shed is indistinguishable from a tenement once both are only
joined-up interior floor.

Station recipes compose a chosen layout, partitioning, room carving, corridors,
spawn placement, station-topology analysis, tactical linking, and finalization.
The concentric, diamond, and partitioned interior layouts are alternative
spatial premises, not separate game modes. Stations must preserve a legible
entry-to-objective structure while allowing deliberately chosen loops and
hardpoints.

Ship recipes are a third interior premise. Where a station organizes space
around a core — radial depth, mirrored geometry, converging ports — a ship deck
organizes it around a longitudinal axis: an elongated, deliberately asymmetric
hull whose beam varies along its length, a fore-aft spine, ordered transverse
bulkheads, and an assault gradient that runs along the axis from a breach point
rather than inward from a perimeter. The layout-neutral topology tier is shared
with stations; the ring, core, and port annotations are not. `ship-interiors-nouns.md`
owns that family's model, its facility compartments, and its boundaries.

## Defense-post layouts

A **defense-post layout** is a bounded, anchor-relative arrangement of occupied
cells. Barrier cells own their appearance and outward facing; pad cells may host
a referenced turret structure or the distinct drone-hub occupant. Layouts own
initial geometry only. Defense-post tiers continue to own garrison size,
tactical priority, patrol and budget policy, while turret catalogs own the
behavior and durability of every referenced structure.

The Conquest fortress's open-backed forward bunkers use the same separation of
place from position: a blocked center turret remains the bunker anchor, while
two attacker-facing shared-edge windows bound the garrison's authored member
positions. Each exterior neighbor remains walkable and a one-cell front-and-
flank halo proves a legal route around the intact pane. The road reservation,
halo, and reachable rear approach are placement requirements, so a bunker is
omitted rather than publishing an isolated fighting cell.

The layout catalog loads after turret structures and rejects unknown structure
ids, duplicate or out-of-bounds cells, turrets placed off ordinary pads, and
invalid tier composition. Generation selects among the layouts authored for a
tier through its request-owned random stream. A tier with one layout consumes
no selection draw; a tier with several variants consumes exactly one, preserving
determinism as fixed stamps become data. Connectivity checks and tactical-node
bounds consume the selected layout's actual occupied cells and declared bounds,
not a parallel hard-coded silhouette.

## Target profile and economic identity

A **target profile** carries campaign-resolved facts into generation without
bringing campaign types into the generator. Its economic-function mix is a
stable vocabulary for choosing district character; an empty/neutral mix remains
the historical neutral output. Structural geography still owns its bands: an
economy signal may sharpen compatible city content but cannot erase the purpose
of a harbor, perimeter, or fortress.

An **economic district** earns its identity through navigable geometry, cover,
destruction, and sightlines. Spaceport aprons exemplify this: they are open
tarmac with sparse, destructible hard cover and a reachable control hardpoint,
not a visual theme. New district types must state their tactical promise and
prove that the whole result remains connected and deployable.

Generator-published tactical facts need a named consumer. Do not widen
`MapResult` with preview-only or speculative analysis artifacts; an in-pipeline
consumer reads the context, while an external consumer justifies the result
contract it needs.

Target faction identity may select an authored treatment for a facility only
through a named content consumer. Such a treatment can express materials,
footprint composition, cover, and approach character, but it may not change the
facility's owning battle semantics or bypass connectivity/deployment validation.
`target-faction-facility-treatment.md` owns the first bounded consumer over the
hard-installation family.

## Boundaries

Generation authors the initial world. The desktop emplacement editor stages and
validates layout data, but does not decide runtime placement or become a second
geometry authority. Map-editor or battle services may mutate
topology later, but they must not be smuggled into recipe logic. Content-specific
fillers own their geometry; broad generator facades own recipe selection and
result assembly. Current active work is indexed in `stories.md`; completed
historical slices are recorded in `shipped.md`.
