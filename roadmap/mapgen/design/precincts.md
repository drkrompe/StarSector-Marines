# Precincts

Status: ACTIVE — adopted and wired. A `PrecinctPlan` handed to
`MapGenerator.generate` builds a whole map this way: places seeded from a world
or authored, grown, welded onto one road network, claimed, allowed ground,
filled — zoned from a character or packed from a program — walled and gated.
Conquest, Assault and Raid against a real market generate their maps this way,
Conquest at 560x336. What remains is the shape work below.

Written: 2026-09-01

Updated: 2026-09-01 — Conquest generates as places at 560x336; the front is a
depth from the objective rather than a biome; a mission states its sprawl and
how far from the objective its force lands.

## The shift

A map is currently a **recipe of map-wide stages**: one stage lays the road
skeleton for the whole map, another zones it, another fills leaves, and the
fortress is a special conquest-only stage that finds a biome band and packs a
program into it. Every distinct kind of place is therefore a stage, and a second
one of anything is a second stage or a loop somebody remembers to write.

The proposal is that a map is instead **a set of places, each grown**. A
fortress is not a kind of stage; it is a place that happens to owe a garrison
program and to be walled. A town is a place that owes nothing and is open. Two
fortresses is two places. So is a fortress, a mining camp, and three hamlets.

This is the same inversion `compound-programs.md` already argues for one level
down — the program sizes the thing, not the other way round — applied to the
map rather than to a compound.

## The noun

A **precinct** is a seeded, grown, filled, optionally-walled area of a map:

| Part | What it decides |
|---|---|
| **Seed** | where it starts growing from |
| **Growth** | how its road skeleton spreads — junction budget, arm lengths, class ladder |
| **Program** | what it owes, with counts: buildings and lots, any of them zero or many. Absent for an ordinary settlement |
| **Character** | what a zoned place is on the inside — the mix of district themes its parcels are built from, and whether it has a centre. Absent for a programmed place, whose program already says |
| **Boundary** | open, or walled — and if walled, where its gates are |
| **Claim** | how much ground it may take, and what happens where two precincts meet |

`Precinct` rather than `District` only because `DistrictMap` and
`MapDistrictTheme` already hold that word for the zoning-theme overlay, which is
a different thing — a 20x20 grid of themes, not a place. Renaming that is the
alternative and is a bigger, more mechanical change.

## Growth is shared, and the borders are where arms meet

All precinct seeds go into **one frontier**, not one graph each. `walkArm`
already stops one cell past first contact with an existing band, so an arm from
one precinct that reaches another's simply joins it. Nothing needs to arbitrate.

Two things fall out of that rather than being built:

- **The border between two precincts is where their growth met**, which is
  irregular and looks deliberate, instead of a partition line.
- **The artery between them is the arm that joined them.** A road from the
  fortress to the town is not routed after the fact; it is the arm that grew
  from one and touched the other, and it is drivable because it was a road
  class, not a corridor reserved afterwards.

A cell belongs to the precinct whose arms are nearest to it. That gives every
precinct an outline without anybody choosing one.

## A programmed place claims differently from a settled one, and first

This was found by asserting rather than assuming, and it is the one place the
model does not collapse to a single rule.

Growing a claim outward from a precinct's **roads** gives every arm a collar a
couple of cells deep. For a town that is exactly right — buildings line streets,
and the ground that matters is the ground you can front onto one. For a fortress
it is useless. Measured on a grown garrison claim: buildable ground came out as
a ribbon whose largest inscribed square was **5 cells** and whose best rectangle
was **107x5**, against a program owing a **31x16** vehicle shed. Nothing was
placed at all.

Growing the same allowance outward from the precinct's **seed** pools it into a
blob instead: largest square **40**, best rectangle **61x30**. The shed fits.
It is still grown and still irregular — it stops where it meets a neighbour, so
its outline is a fact about what is around it — and the precinct's own arms run
through it as circulation, which is the shipped ward's arrangement arrived at
from the other direction.

So `PrecinctClaim` has three policies and the choice is a property of the kind
of place: `nearest()` and `budgeted()` spread along streets, `compact()` pools
around a seed. A programmed precinct needs `compact()` and cannot use the
others.

**Need goes before frontage.** The two kinds used to claim in one frontier —
a settlement from every one of its road cells at cost zero, a garrison from its
single seed — which is fair while the places are far apart and is not on a
production map, where the settlement's arms span the whole map and it enters
with thousands of sources against the garrison's one. Measured for a size-6,
rating-5 world: at 144x80 the garrison was allowed 4592 cells and claimed
2832 with seven buildings unplaced, the keep among them; at 112x64 it was
allowed 3031 and claimed 647. A programmed precinct's allowance is the ground
its program has to stand on, and short of it the place is not a smaller
version of itself but an installation missing its keep; a zoned precinct's
allowance is a measure of how far its streets reach, which yields gracefully.
So the programmed places pool first, all of them together in one frontier so
two garrisons still contest ground by nearness rather than by list position,
and the zoned places then flood together into what is left. The same world at
144x80 now claims its whole allowance and builds everything but the vehicle
bay; at 112x64 the keep is built and three items are short. At 560x336 nothing
moved, so the measurements below still hold.

## Fill is a policy, not a stage

Inside its own claim, a precinct is filled one of two ways:

- **Programmed** — pack its authored footprints into its buildable mask. This
  already works on an arbitrary shape: `FortressInterior.pack` takes a buildable
  mask and a circulation mask, and `Bounds.of` reads the extent off the mask.
  The rectangle a fortress has today enters only through `wardRect`.
- **Zoned** — hand its parcels to the existing labelling and fillers, themed
  from the precinct's own character rather than from the map.

That is the whole difference between a fortress and a town. Everything else —
how the roads grew, how the ground was dressed, how the border landed — is the
same code.

## The boundary is drawn last

A walled precinct has its wall stamped around **the outline of what grew**,
after filling. `compound-programs.md` establishes why that order and not the
other: a wall stamped first can only ever enclose whatever the fill happened to
leave, however good the wall is.

Its **gates are where arms cross the outline**. An arm leaving the precinct
crosses its boundary, so a gate is a discovered fact rather than a placed object
with a road found for it afterwards. The shipped wall stamper does the opposite
— it rolls one to three gate positions along a rectangle's south side and then
separately tries to align one of them with the vehicle corridor, which is two
decisions about the same thing that have to be kept in agreement.

**Whose arm does not matter.** The network is one, the fill already counts
every road inside a claim as that place's circulation whichever precinct grew
it, and a road between two places is in both of them. The gate rule was the
one place still asking whose road it was, and once a garrison claimed first it
mattered: its pooled claim runs far past the ends of its own short arms, so its
outline is crossed by the settlement's arms in many places and by its own in
none. Measured on the Raid fixture at 144x80, that was a wall with no opening
and twenty-three points of interest on the defender's side that nothing could
reach. A road leaving the map through the outline is still not a gate; a hole
onto the world outside is not a way through for anyone in the battle.

**A gate's cells are grouped diagonally.** A claim grown by orthogonal steps has
a diamond-ish boundary, so a road crosses it at an angle and its cells come out
as a staircase. Grouped orthogonally, one seven-cell crossing reads as seven
one-cell gates: measured, a garrison reported eighteen gates none of which was
wide enough to drive through, when it had three crossings of three, seven and
seven. The wall is one cell thick, so the hole a staircase leaves is as passable
as the hole a row leaves.

**Growth does not always leave a way out, and a walled precinct owes one.** A
claim large enough relative to how far its arms reach swallows its own road
network entirely — measured, one seed in three produced a garrison whose road
was 931 cells inside its claim and none outside it, so nothing crossed and the
wall had no opening at all. `PrecinctArtery` carves one when growth did not, and
reports that it had to, so the rescue is visible rather than silent. Firing often
means the growth profile is wrong — arms too short for the ground the place
claims — and the fix belongs there.

Three things about that carve were learned by measuring rather than reasoning.
It runs as a **ray from the claim's centroid**, not as an L to a target: an L's
legs run along the axes, so a target nearer the centre than the boundary leaves
the whole path inside the claim, and the carve never crossed its own outline. It
does not head for a neighbour's road: the rule that an artery may not overwrite
another precinct's road is right, but it means a ray aimed at that road cannot
paint the cells it needs, and the carve stops one cell outside its own boundary.
And it does not head for the **nearest map edge** either, which was the second
answer: a claim that already reaches that edge sends the ray off the map still
inside itself, and the guarantee fails in silence, which is the one way it must
not fail. It heads for the **nearest ground that is not its own** — the first
cell outside the claim along whichever axis reaches one soonest — because that
is what a way out is. A neighbour whose network lies across the route is met on
the way rather than aimed at, and only a claim that spans the whole map in every
direction is left to the edge.

## What this collapses

| Today | Becomes |
|---|---|
| `FortressWardStage` — conquest-only, biome-band-placed, rectangle-enveloped | a precinct with the garrison program and a walled boundary |
| The map-wide grown settlement | a precinct with no program and an open boundary |
| A second fortress | a second seed |
| An airfield-only forward base | a precinct whose program owes airfields and little else |

## Why now, measured

One seed with a fixed junction budget cannot fill a bigger map. Generating at
three sizes with the same profile, stock against grown:

| size | cells | stock POIs | grown POIs |
|---|---|---|---|
| 280x168 | 47k | 138 | 109 |
| 396x238 | 94k | 317 | 127 |
| 560x336 | 188k | 658 | 239 |

Stock scales with area because its subdivision is proportional. Grown does not,
because `Profile.of` gives a junction count that is a constant while only arm
length scales with the short dimension. At the target size the grown map carries
a third of the content.

The fix is not a bigger budget for one settlement — that produces one enormous
town and no variety. It is several precincts, each with its own budget, placed
where the map wants them.

## Settled

**The name is `Precinct`.** Taking `District` from the zoning overlay was
costed: about five hundred identifier occurrences, and a meaningful share of
them — `SpaceportDistrictPlanStage`, `renderFortressDistrict`,
`civilianDistrict` — already use the word in the place sense this model wants,
so it needs sense-by-sense judgement rather than a rename. Not worth an hour of
zero-behaviour change against concurrent work for a word.

**Seeds come from both.** The campaign target profile derives a default set and
a mission may state its own, which wins. Neither is built yet.

**The claim is a policy, and both are implemented** — `PrecinctClaim.nearest()`
and `PrecinctClaim.budgeted()` — because which produces better maps is a
question about how maps should feel, and may not have the same answer for a
fortress and a hamlet. Measured at 560x336 with three places:

| policy | precinct A | B | C | left over |
|---|---|---|---|---|
| nearest, seed 1 | 100763 | 59487 | 27910 | 0 |
| nearest, seed 42 | 92689 | 81921 | 13550 | 0 |
| nearest, seed 777 | 131037 | 40334 | 16789 | 0 |

Nearest partitions the whole map and the sizes are a seed lottery — the largest
place swings between 92k and 131k cells across three seeds. Budgeted holds its
number exactly, and that is the trap the measurement found: **a flat budget is
spent almost entirely on the arms**. Roads alone are around twenty thousand
cells, so budgets of 26000/14000/6000 produced precincts that were their own
street plan and almost no ground. A precinct's allowance has to be derived from
what it holds, the way `compound-programs.md` sizes a fortress envelope from its
program, rather than picked as a free number.

## A claim must not come out geometric

A four-neighbour flood expands by Manhattan distance, so a claim grown from one
seed is a **perfect diamond** — which is what the first rendered garrison was,
and it reads as a generated shape rather than as a place. Eight-neighbour trades
the diamond for a square and is no better.

The expansion is therefore cost-ordered with the cost perturbed by a
`PatchField`, the same coherent-noise field the wild ground uses. Coherent
rather than per-cell random for the same reason it is there: per-cell noise
gives a fringe on a diamond, where a coherent field bends the outline into lobes
and bays. Each precinct gets its own field so two places do not bulge alike.

## Connectedness is solved for, not hoped for

Growth joins two places only by accident: an arm stops when it runs into an
existing band, so precincts whose networks run near each other without touching
stay separate road systems. Measured over four derived maps, three came out
already whole and one came out **in three pieces** — an intermittent structural
fault, invisible in a picture and severe for anything that drives.

`PrecinctInterconnect` labels the components, floods the ground between them
from all of them at once, and takes the cheapest meeting point between each pair
in increasing order until one network remains. That is a minimum spanning tree
over the components, so a three-piece map gains two links rather than three, and
a whole one is not cut at all. Each side of a link is walked back along the
flood's own parent pointers, so it bends around what the flood bent around; a
straight link would be quicker to write and would drive through buildings.

## The partition must be handed one place's parcels, not the map's

`GrownTrunkPlan.grow` returns sub-rects decomposed from frontage across the
whole map. Handed to `BspPartitionStage` as-is, every cell near any road becomes
a parcel and the fill builds city over everything — measured, four places
rendered as one continuous conurbation with a ragged edge, 616 points of
interest and no visible boundary between a town, a garrison and two hamlets. The
claims had been computed and then ignored.

The skeleton stage now decomposes each zoned precinct's own claimed ground and
hands the partition only that; unclaimed ground becomes hinterland. A programmed
precinct is left out entirely, because its interior is packed from authored
footprints rather than subdivided, and handing it to BSP fills it with ordinary
city before the packer ever sees it. The same map then renders as four places
with country between them, at 339 points of interest.

## A district is sized from its program, and says what it could not build

Ordering more of something widens the place. Measured at 560x336 with a
garrison beside a town, the whole chain tracks — floor area to envelope to
allowance to ground actually granted:

| program | floor | envelope | allowance | granted | built | unbuilt |
|---|---|---|---|---|---|---|
| garrison | 1482 | 5293 | 9165 | 9165 | 18 | 0 |
| barracks x12 | 2346 | 7366 | 11238 | 11238 | 27 | 0 |
| airfields x4 | 1482 | 10501 | 14373 | 14373 | 18 | 0 |
| all raised | 3239 | 12982 | 16854 | 16854 | 29 | 0 |

**Granted ground is not usable ground.** The same programs on a 200x140 map
were still granted every cell they asked for and did not all fit: a garrison
owing six barrack blocks built three. A claim is one shape and a program is a
set of footprints, so area tracking is necessary and not sufficient — and the
shortfall is not even monotonic, because twelve blocks fitted where six did
not, the larger allowance having produced a better-shaped claim.

So the packer's unplaced list is bound under `BspKeys.UNPLACED_PROGRAM` rather
than dropped, empty when nothing was short so that "built everything" and
"nobody asked" stay different answers.

## An ordered airfield is built, not merely paid for

The count reached the sizing before it reached anything else: ordering four
widened a garrison by five thousand cells of apron and put nothing on it,
because this path placed no lot at all. Ground bought and unused is worse than
ground not bought — the place comes out the right size for an air arm it does
not have.

Lots are now reserved **before** the buildings are packed and from the
precinct's far end, which is the shipped ward's reasoning: claimed after
packing, a lot gets whatever shape the leftovers had, and taken from the middle
it severs the spine everything else crosses. Each is authored through the same
`AirbaseLot` the fortress uses, and its ground is closed to the four stampers
that run afterwards — told nothing, they put guns on the runway.

**The size ladder absorbs pressure rather than refusing.** Measured on a
200x140 map, berths by airfields ordered run 3, 6, 8, 11, 13, 16: growth all the
way and sub-linear, because a place that cannot seat another station seats a
field or a pad. What is not true, and was asserted here before it was measured,
is that a cramped map gets a smaller *first* field — a station is 62x28 with its
clearance and fits comfortably in either, so only later fields ladder down.

A field with nowhere to go at any size is counted under
`BspKeys.UNPLACED_AIRFIELDS` rather than dropped, for the same reason unbuilt
buildings are.

## How much of the map is settled is a knob

The same world can be an installation in wilderness or a city with an
installation in it, and which one it is belongs to the battle rather than to the
planet. `PrecinctPlan.Sprawl` says how many places there are and how far they
reach; per-place density already existed and is not the same question.

Measured through the generator at 560x336 on one seed:

| preset | places | POIs | built | wild |
|---|---|---|---|---|
| `REMOTE` | 1 | 0 | 1.2% | 93.6% |
| `BALANCED` | 4 | 339 | 8.6% | 58.2% |
| `DENSE` | 11 | 746 | 18.8% | 15.4% |

`BALANCED` is the shipped default and reproduces what the model produced before
the knob existed.

Two things that make the extremes work are worth stating because neither is a
tuning value. `REMOTE` **drops the settlement entirely** when there is a
garrison — the installation is then the somewhere the battle happens, and adding
a town is the one thing that stops the map being what it is called. `DENSE`
takes the main settlement to full density, which is not "more streets": it is
the point at which `Profile.of` stops giving frontage a depth at all, so the
place claims out to whatever its neighbours and the map edge allow. It also
seeds many places rather than one, because a single settlement claiming evenly
comes out a disc, and a city is districts meeting each other.

## Palettes are cosmetic and read that way

All four surface palettes render distinctly at the same seed and produce the
same layout, which is the boundary working: what a world's ground is made of
changes what is drawn and nothing about where anything is.

`ROCK` and `ARID` read flatter than `VERDANT` because their pools are dominated
by one ground and the patch field has nothing to arrange — the measurement in
the wild-ground section says so directly. `FROZEN`'s dirt shows through around
settlement, which reads as trampled ground and is a happy accident of its
50/50 pool rather than anything authored.

Parks and verges stay green on every world, including the rock one. That is the
cultivated-ground law in `mapgen-nouns.md` made visible: lawn on an airless rock
is a statement about the colony, not about the planet.

## A place is something a battle can be about

A programmed precinct was geometry: walls, roofs, a motor pool and two runways,
and nothing to fight over — no objectives, no garrison spawns, nothing for the
commander tier to reason across. On a map with settlements around it that hides
behind their fills; on a `REMOTE` map, where the installation is the only place,
the whole map came out with none.

Its placed rooms now become tactical nodes, at 14 on a balanced map where there
were none from the garrison before. Two differences from the conquest ward are
deliberate:

**A precinct garrison keeps its own command post.** The conquest ward is packed
around a citadel compound the recipe seeded separately, and its program has the
keep taken out so the map does not end up with two. A precinct is
self-contained; nothing else is going to provide one, and two garrisons on one
map are meant to have one each.

**Nodes are emitted after the airfields are authored**, because authoring a lot
clears tactical nodes standing on its reservation and a building's node has no
business being removed by an airfield.

**It can be landed on.** A closing stage authors paired shuttle berths inside
the attacker's region — the plan's stated placement, or the corner the spawn
stage chose, sharing that choice so the beachhead and the spawn cannot disagree
— scanning inward from whichever map edge the region touches so the first legal
area is a beachhead. The stock recipe did this on its beach band and threw
without one; measured at 560x336 a precinct map seats eleven areas from the
south and six from the west, against the three a Conquest asks for.

**It has one keep.** A settlement's military base used to emit a command post
of its own, because only a biome told the filler otherwise, and a Conquest with
two command posts has no canonical keep at all. On a map whose plan has a
programmed objective the keep is the garrison's, and a settlement's base is
stores or quarters the way the port's and the city's are on the stock recipe.

**It owns its guns.** Its emplacements were manned all along; what was wrong
was one layer down, where the non-conquest factories built their post list from
nothing and then scattered random posts, so a precinct's whole fortification —
walled, gated, seeded — was generated and discarded before the battle saw it. A
map that stated its own posts keeps them, and the scatter runs only on a map
that stated none.

**Its stores and its seat of command are points of interest as well**, in the
other vocabulary a battle uses: the armoury, vehicle bay, stockroom and parts
cage as depots, the keep as administrative, the control room as comms. Found
by Raid rather than by design: once the garrison claimed its whole allowance
on a 144x80 map, every point of interest stood on the settlement's side, which
is the marines' side, and the raid had nothing on the defender's side to
strike. Barracks, mess and gate emit none; they are not prizes.

## Road in open country is a supply route or it is nothing

Growth does not stop at a claim. Arms run their full length whether or not the
place they belong to holds the ground they cross, so an installation with a
modest claim throws a street network across the wilderness around it. Measured
on a remote map, **two thirds of all road lay outside every precinct** — 3086
cells, fully connected, with no dead ends, and serving nothing. A street grid in
a field.

What open-country road is *for* is the reason to keep any of it: a remote
installation is supplied from somewhere and the road out is how. So road beyond
every claim survives where it carries a place to the map edge, and is removed
otherwise. On the same map that keeps three highways and prunes 2752 cells; the
result reads as an installation in country with a couple of roads leaving it.

Four things were learned by measuring rather than reasoning, and each is a rule
rather than a number.

**A route traced is one cell wide, because a breadth-first path is.** Kept as
traced, a five-cell road becomes a footpath nothing drives — 193 cells of
highway across five exits. The route is widened back out over the road that was
already there, so a highway is as wide as the road it is made of and never
wider.

**A contiguous run of border road is one way out, not one per cell.** A
three-cell-wide road traced from each of its cells comes back as three routes,
so a map with a single supply road reported three exits and spent its whole
budget on them.

**Only a few are kept.** Growth reaching the border five times is five roads out
of a place that needs one, and keeping them all leaves the wandering with an
excuse. The shortest survive, because a supply road takes the near way out.

**The question is whether any road reaches the border, not whether an
open-country route does.** A dense city has streets to the edge and no open
country at all; asked the narrow way, every city on the map had a supply road
cut across it.

A place with no road off the map at all is given one, straight and looking it,
on the same terms as `PrecinctArtery`: it exists so a place is supplied, not to
be a good road.

## A mission says roughly where, not exactly where

Conquest culminates in taking an installation, so a scenario has to be able to
put that installation somewhere and the attacking force somewhere else. A cell
is the wrong unit to say it in — what cell means "north-east" depends on the map
— so `MapPlacement` is a fraction of the map and a `PrecinctBrief` is a place
stated against one. The same brief lays out at 200x140 and at 900x600.

Deliberately coarse: a placement is a region to land somewhere inside, not a
position. Two missions asking for the north-east should not produce the same
map, and a placement that pinned a cell would be an authored map wearing a
generator's clothes.

**Placement wins over separation.** Two places asked for the same corner end up
close together, because a mission that asks for that means it. Spacing is the
generator's business only in a derived plan.

**The spawns come from the plan, not from an axis.** A precinct map has neither
axis nor biome bands, so `SpawnAnchorStage` was falling through to a low-X /
high-X split that is arbitrary against wherever the objective actually grew — on
a map whose garrison is in the west it put the attacker on top of it. The
attacker now arrives at the stated placement and the defender stands inside the
objective's claim.

**Told nothing, the attacker still starts somewhere worth attacking from**: the
corner furthest from the objective. A force landing beside the thing it is meant
to take has no approach to fight through, which is most of what a conquest map
is for.

**A derived plan may be told roughly where its objective is.** The derivation
takes an objective placement and an attacker placement as well as the world:
the garrison is seeded inside the one and the plan carries the other, and the
settlement and outlying places fall where the map wants them. That is how
Conquest gets a fortress at the far end of its axis without authoring the rest
of the map, and the six-argument derivation is unchanged to the cell.

## And how far out its force lands

A placement says roughly where the attacker arrives; a **standoff** says how far
that is from the thing it came for. It is mission vocabulary, the shape `Sprawl`
already has: `CLOSE` at 40 cells, `STANDARD` at 80, and `FAR`, which is the
stated band unchanged and reproduces every map that existed before this to the
cell. The distance is measured along the traversal axis, between the attacker
region's objective-facing side and the objective precinct's claim boundary on
the attacker-facing side — the ground the force actually has to cross.

**Cells, not a fraction of the map.** A standoff is a walk, and a walk does not
scale with the map: doubling the map should not double the minutes before first
contact. `STANDARD` is roughly the approach the 280x168 map had — the attacker
band's far side around x=93 against an objective claim edge near x=170 — which
is the whole point of stating it in cells. The same walk on a bigger map.

**Resolved after growth, not at derivation.** The objective's claim is only
known once the places have grown, so `PrecinctPlan` carries the statement and
`ApproachRegion` turns it into cells afterwards: the stated band, at its own
depth and lateral extent, slid along the axis until its objective-facing side is
the stated distance short of the claim, never past the edge it came from and
never into the claim. The spawn anchor and the beachhead both read that one
function, because they are the same arrival and two copies of the rule would be
two answers the first time either moved. A map that cannot afford the standoff
slides as far as it can and records what it managed under
`BspKeys.APPROACH_STANDOFF`, on the same law as the unbuilt program.

**The approach is carried, not inferred.** The landing stage used to read its
approach off which map edge the region touched. A region slid inland touches
none, and the nearest-edge fallback would hand a southern approach a side it
never had, so the approach comes off the *stated* band and the berths are
scanned inward from the region's side that faces it.

Only Conquest consults one; everything else passes `FAR`. Measured on the two
canonical Conquest fixtures at 560x336, same seed and same map, only the landing
moved:

| fixture | standoff | result | ticks | first contested | first held | captures | held | marine losses |
|---|---|---|---:|---:|---:|---:|---:|---:|
| reinforced-south | FAR | MARINE | 17640 | 6990 | 7920 | 12 | 11 | 122 |
| reinforced-south | STANDARD | timeout | 18000 | 6000 | 6120 | 11 | 7 | 226 |
| reinforced-south | CLOSE | MARINE | 13680 | 5610 | 5730 | 11 | 11 | 95 |
| full-strength-west | FAR | timeout | 18000 | 14550 | never | 0 | 0 | 368 |
| full-strength-west | STANDARD | timeout | 18000 | never | never | 0 | 0 | 279 |
| full-strength-west | CLOSE | timeout | 18000 | 15570 | never | 0 | 0 | 265 |

**A shorter walk is not the same as a better battle, and the middle setting is
the worst of the three.** Landing closer does exactly what it was built to do —
first contact moves in by a thousand ticks and the first compound falls eighteen
hundred earlier — and on `STANDARD` the marines then take eleven compounds by
tick 11850 and lose four of them again to the counterattack, ending the clock
holding seven with twice the casualties. `CLOSE` lands closer still and wins
four thousand ticks earlier than `FAR` with fewer losses on both sides. The
reading is not that closer is better; it is that arriving faster than the force
can consolidate is a different battle from arriving slowly, and the setting the
default sits on is the one where that shows.

`full-strength-west` is a timeout at every setting and captures nothing, so it
separates the three only on cost: marine losses fall monotonically as the
landing moves in (368 / 279 / 265), and the retarget churn that made it stand
out — 1272 against reinforced-south's 554 — falls to 418 and 298. Eight
battle-minutes of walking was most of what that fixture was measuring.

## Which battles are made of places

`BattleSetup` derives a plan for **Conquest, Assault and Raid** against a real
market. Sabotage, Extraction, Civilian Rescue, Silent Colony and the opening
operations keep the recipe they have. The gate is the market: a battle with no
market behind it — a headless fixture, an operation against no planet — has no
size, rating or economy to derive from and takes the stock map, which is the
rule `BspCityGenerator.recipeFor` already applies to the grown recipe.

**Conquest is placed from its axis, at its own scale.** Conquest rolls a
traversal axis as it always did, because both commanders' tracks, the shuttle
entry and the reinforcement rear edge are keyed on it; the plan is then derived
with the objective in the far third of that axis and the attacker in the near
one, so the map agrees with everything that reads the axis. Its map is
`MapScale.CONQUEST`, 560x336 — mission-owned rather than tier-owned, because
Conquest is the authored siege the tier model exempts from ordinary scaling and
this model was measured at that size. The stock crossroad recipe is no longer
what Conquest plays on; it stays reachable through the axis-without-plan
generate for tests, scenes and a marketless Conquest, and retiring it is
separate work.

**How settled the map is belongs to the mission.** `Sprawl` is mission
vocabulary now: a mission may state `REMOTE`, `BALANCED` or `DENSE`, a fixture
may carry it, the debug briefing cycles it, and a mission that says nothing
takes the market's own answer from `SettlementZoning` — an outpost of size
three or less is remote, a size-eight market and up is a conurbation, and
everything between is a town with an installation and outlying places. Raid
clamps `REMOTE` to `BALANCED` until tactical nodes are targets.

**The plan is an argument of the generate call, never state on the generator.**
`BattleSetup.MAP_GEN` is one instance shared by every factory, and a plan stored
on it would be read by the next battle. A recipe handed both a plan and a
traversal axis refuses, so Conquest cannot reach the precinct recipe by
accident. The plan's own draws come from the battle seed under a fixed salt:
deterministic in the seed, and not the generator's stream read twice.

Raid takes `Sprawl.BALANCED`, not `REMOTE`, on purpose. A raid wants a target,
and targets are points of interest, which only settlement fills emit; a remote
map is an installation in open country and has none. Making tactical nodes
eligible targets is what would unlock a remote raid. **The Raid target is on
the defender's side** — nearer the defender's spawn than the marine's, by
distance — rather than in the high-X half, because on a precinct map the sides
are wherever the objective grew. `ExtractionPayloadLayout` and
`SabotageSiteLayout` still carry the half-map rule; they are not on this recipe
and the rule is right for the map they get.

## The front is a depth, not a biome

The reinforcement layer — recapture targets, the front-line trigger, the
counterattack — needs to know how deep into the defender's territory a cell is.
It used to read that off `BiomeKind`: bucket every defender node and every live
defender by biome band, walk the bands fortress-city-port-beach, rally a
reinforcement by shifting rearward along the axis. A precinct map has no biome
map, and on one the whole layer silently failed to install.

`FrontDepth` is that answer with the biome taken out: a per-cell band index,
zero at the objective and rising toward the attacker, four bands on every map,
with a name per band and a **rearward** step that walks toward the objective's
centroid rather than along an axis. Both recipes build it in a closing stage so
they cannot disagree about what a front is — the stock recipe from its biome
map, the precinct recipe from the objective's claim, band 0 being the claim
itself and the rest three equal rings of distance out to the furthest cell.
`BiomeMap` keeps its theming and terrain role on the stock recipe; it just
stopped being the front.

On the stock recipe this is behaviour-preserving except in one honest place: a
reinforcement anchored on the map edge used to rally by stepping along the
axis and clamping against that edge; it now steps toward the objective. One
Conquest fixture's trace diverged at exactly that rally and nowhere else.

## A derived plan fits the map it is given

`MapScale` is 112x64, 144x80 and 280x168, and the model above was measured at
560x336. A derivation with a fixed 30-cell margin and 60-cell separation cannot
seat a second seed on 112x64 — the seedable span is 52x4 — so both scale with
the map: the margin is a quarter of the short side and the separation a quarter
of the long side, each capped at its large-map value so nothing changes at
560x336. A map seats what it can seat: an outlying place that finds no room is
not placed. **The garrison is the exception.** A defended world never loses its
objective to a small map; where the draw finds no room, the garrison is seeded
at the in-margin cell furthest from every seed already placed — deterministic,
no draw.

**The program is trimmed to the map before ground is asked for.**
`FortressProgram.fittedTo` takes a ground budget — a fixed share of the map,
`FIT`, a first guess to be measured rather than a tuned value — and walks a
ladder one step at a time until the envelope fits or the ladder runs out:
airfields first, because a lot is the single largest item; then barracks,
control rooms, stores, the armoury, barracks again. The keep, gatehouse,
vehicle shed and mess hall are never removed, because a garrison with none of
them is not a garrison; a program still over budget at the end of the ladder is
packed anyway and records what it could not place.

## How hard a place is to take is stated, not discovered

Two failure modes, pulling opposite ways. A wall a handful of low-tier squads
cannot breach or flank is a refusal rather than a fight; a wall a thousand
marines walk through is not a climax. Neither is fixed by tuning one number
harder, because it is the same number pointed at different forces. So
`Fortification` is stated by whoever knows what is being sent.

It carries three things, and they are different questions. **Emplacements** are
what shoots back, and are the one that decides the fight. **Gates** are about
manoeuvre: several give an attacker somewhere to feint and somewhere to commit,
one makes the approach the whole battle. **Wall hit points** are about materiel:
a wall worth less than a demolition charge is decoration, one worth more than the
force can spend is a detour. `PICKET` through `CITADEL` are named points on all
three.

**And derived when nobody states it, from two facts with different jobs.** The
question was which of the world's defence rating or the force being sent should
drive it, and the answer is neither alone. The *rating* says what is there: it
is the campaign's own weighted read of the market's batteries, station, high
command and shield, and it climbs the ladder at the same breakpoints the
overwatch line already reads at — a picket below two, a garrison from two, a
stronghold from four, a citadel from six, which takes heavy batteries and a star
fortress at least. The *operation tier* says the hardest place a mission at that
scale may be asked to take, and caps it: First Contract a picket, Established a
garrison, Veteran a stronghold, Reinforced and above a citadel. *Risk* moves the
world's answer one rung either way before the cap is applied, so a high-risk job
never lifts a place past its tier, which is the mission-tier law that a
high-risk operation stays recognisably smaller than the next tier's. The force
actually sent is not consulted, for the same reason the map and the base
defender count are not: a mission does not grow because the player brought more
lift. A demand nobody stated leaves the world's rung standing, which is what a
headless derivation gets. The translation from tier and risk lives at the
launch boundary beside the map scale's, because the generator is campaign-free
by contract and reasons only about the resolved `Fortification.Demand`.

**The wall is the least of it.** A wall is a delay and a detour — it decides
where the attack goes in and what a breach costs, and then it is over. What
turns a place into a problem is what is shooting at the ground in front of it,
so the emplacement loadout is the dial that carries the difficulty and the other
two shape the fight it produces. This was measured the hard way: rendered at map
zoom, a picket and a citadel are nearly the same picture, because the difference
between them was a handful of gaps in a one-cell line. The guns are what
separates them, and they are what a player can see.

**Stated as a count per kind, never as a number.** Two light posts and two
rocket batteries are not the same defence at any exchange rate, so a
fortification names how many of each `DefensePostKind` it holds and the
generator places exactly that. It is the same 0-to-many shape a programmed
precinct's building list has, for the same reason: a mission designer asking for
"a garrison with no artillery but four heavy posts" should be able to say so.

**Two bands, and what distinguishes them is what the emplacement is for.** The
perimeter tiers are seeded a short way inside each open gate, widest gate first,
so the heaviest guns cover the way most of the attack will come — a gate nobody
is watching is a door. Artillery and drone hubs are seeded on the cells furthest
from the outline, because their whole point is reaching past the wall from
somewhere the attacker must get through the wall to reach; on the perimeter they
are heavy weapons with no standoff, which is the one thing they are not. This is
the same two-band shape the conquest fortress has always had — a kill zone in
front and a rear battery band behind — derived from the precinct's own claimed
outline rather than from a biome's bounding box, which is the substitution the
whole precinct model is.

**What could not be emplaced is recorded**, under `BspKeys.UNPLACED_DEFENCES`,
for the same reason the unbuilt program is. A citadel that found room for two of
its four heavy posts is a stronghold wearing a citadel's name, and an emplacement
that was never stamped leaves nothing at all on the finished map to notice.

### A placement guard asks about the stamp, not about the map

The first working version of this placed **nothing at all**, on every seed and at
every level, while every count still said it had asked for the guns. The unit
test passed the whole time, because it proved the mechanism on a clean fixture
and the fault lived in the case.

`PlacementGuards.wouldPartitionWalkable` is a whole-map check: it floods the
walkable graph from one seed and refuses the stamp if any walkable cell is left
unreached. That is the same question as "would this stamp partition the graph"
only on a map that is whole to begin with. A generated precinct map is not — this
one carried three orphan pockets of 21 cells between them, left by fills and
nothing to do with any emplacement — so the guard answered true for all 5605
candidate anchors and the defence pass placed nothing, with no exception and no
log line.

`wouldStrandGround` is the same question asked locally, of the cells that can
actually step onto the footprint, and is immune by construction. It now has a
sparse-footprint form, because an embankment is a ring rather than a rectangle
and the cells it leaves open inside itself are exactly where the interesting
failure lives. Switching the placer to it left the conquest map **anchor for
anchor identical** across three seeds — that path was never broken, because those
maps come out with one walkable component — and unblocked the precinct path
completely.

The general law: **a placement guard must answer about the stamp, not about the
map.** A guard that reports a pre-existing condition refuses everything, and a
caller that places nothing looks exactly like a caller that was never invoked.
Every stamper now asks locally: `BattleSetup`'s vehicle parking and
`OverwatchTowerStage` were switched after the emplacements were, and the same
before/after probe held their anchors identical across three seeds — except for
one 200x140 port map that had been parking **no vehicles at all** and now parks
its five. Latent is not the same as harmless; it only means nobody had looked.

**Gate count is a cap, not a count.** Growth decides where roads cross the
outline; the dial decides how many of those crossings stay open. A place whose
roads all leave by one route has one gate however many it is allowed. The widest
are kept, because a wide crossing is a main road and a narrow one is where a
track happened to touch the line — and a sealed crossing leaves its road
dead-ending at the wall, which is what a closed gate looks like from outside and
needs no special handling.

**One drivable crossing survives whatever the cap says.** A walled installation
its own armour cannot leave is a defect rather than a difficulty, and it is the
same obligation `PrecinctArtery` enforces one step earlier.

The gate *defect* — a wall reading as a dashed line because eleven crossings is
more gap than wall — turned out to have fixed itself. Pruning the wandering
open-country road removed most of the crossings with it: measured across five
seeds afterwards, the outline is 88–97% wall and carries 2–8 gates. What was
missing was not fewer gates but any control over how many.

## A zoned place says what it is inside

The model shipped from the outside in. Growth, claim, artery, boundary, gates
and emplacements were all per-precinct and all measured, and a programmed
precinct was coherent end to end — civilian buildings within thirty cells of an
installation seed measured 0 and 1. A zoned precinct was not: its parcels were
cut from its own claim and then themed by `DistrictMap.themeAt`, an absolute
position lookup on a fixed grid of twenty-cell blocks that had never heard of a
precinct. The one nudge that scatter had, a civic block at the trunk crossing,
was a single point on a map that now had four places. Measured on two derived
maps at 560x336, three zoned places came out 71–85% residential apiece, and an
*outlying* place carried 225 points of interest against the main settlement's
100 — what separated two places was how much ground their claims had won.

A zoned precinct now has a **character**, which is to a settlement what a
`FortressProgram` is to a garrison: the statement the generator derives the rest
from. A character is a *mix* — the district themes the place is built from,
weighted the way that kind of place weights them — and optionally a *centre*,
the theme forced onto the block its own seed stands on. `TOWN` is the one with a
centre; `HAMLET`, `SUBURB`, `DEPOT` and `QUARTER` have none, because a hamlet is
houses along a road. It is stated authored-wins, the shape `Fortification` has:
a mission says what a place is, and `PrecinctPlan.derive` picks for a place
nobody described — the main settlement a town leaning toward the world's
economy, and each outlying place either the plain kind or what that economy
makes of it, a depot on an industrial world and a dormitory on one that mostly
houses people.

**A character is not a second zoning system.** It is what the district map is
asked per precinct instead of once per map. Each zoned precinct lays its own
themes over the blocks its claim touches, rolled from its character and
smoothed only against itself so a cluster never crosses a border, and the map
answers from that layer wherever the precinct owns the cell and from the
map-wide roll everywhere else. Three things follow and are laws rather than
steps:

- **Hinterland has no character and must not acquire one.** Open country is
  dressed rather than built, and on a precinct map the map-wide roll is the
  answer only there — where nothing is ever partitioned. A cell nobody claims
  reads exactly what it read before anything was laid over it.
- **A precinct's centre is its own, not the map's.** The trunk-crossing nudge
  runs only on maps with no precincts; four places on a map can have four
  centres, or none.
- **What a place is never moves where it is.** Character draws come after every
  seed, so the same world lays out the same map whatever it is built of, and a
  forced block — a port pocket — is forced on every place that holds it, since a
  force that landed on one layer would be invisible to a leaf another precinct
  owns.

Measured again on the same seeds, points of interest attributed to the nearest
seed as before (which is why the installation's row is left out: the region
nearer its seed than any other is far larger than its claim):

| world | seed | place | character | POIs | residential | depot |
|---|---|---|---|---|---|---|
| habitation | 42 | settlement | town | 73 | 79% | 12% |
| habitation | 42 | outlying-1 | suburb | 98 | 88% | 7% |
| habitation | 42 | outlying-2 | hamlet | 101 | 81% | 9% |
| industry | 42 | settlement | town, industrial | 74 | 66% | 26% |
| industry | 42 | outlying-1 | depot | 80 | 31% | 56% |
| industry | 42 | outlying-2 | hamlet | 101 | 61% | 26% |
| industry | 7 | settlement | town, industrial | 113 | 59% | 29% |
| industry | 7 | outlying-2 | hamlet | 191 | 57% | 32% |

A depot beside a hamlet is a different place, and an industrial world's town is
a different town from a residential world's at the same seed. Both directions
are pinned: different characters must differ and the same character must agree
with itself, because a change that merely added noise would pass the first
alone. Two things the table cannot show are worth stating. The point-of-interest
kinds are a coarse instrument — a commercial building counts as residential —
so a hamlet and a suburb on a habitation world look alike here and differ in
what is *not* a point of interest: a hamlet is mostly outskirts, which is parks,
scrub and wasteland. And the sizes still vary with the claim lottery, which is
open item 2 seen from the other end; a place whose contents are a statement now
wants a size that is one too.

The conquest recipe and the grown legacy recipe are untouched by any of this:
the layers are laid only when a map has precincts, and a map without them takes
no draw it did not take before.

## Still open


1. **A precinct takes what it asks for whether or not the map can spare it.**
   The program is now trimmed to a share of the map and the garrison claims
   first, which is what puts the keep on a 144x80 map — and also what makes
   this more visible, not less. A garrison owing forty barrack blocks on
   200x140 is allowed 23808 of 28000 cells and its town claims two thousand;
   a little larger and the town claims nothing at all, and the garrison only
   reports a shortfall once its allowance exceeds the whole map. Nothing yet
   weighs one place's need against another's existence, and a claim pooling
   freely runs to the map edge, where the packer lays its first row.
2. **Settlement claims read as collars, not districts.** A zoned precinct's
   allowance spreads two or three cells either side of its arms, so it draws as
   a road network with a shoulder rather than as a place with streets in it.
   Either the frontage depth is too shallow for the map scale or the allowance
   wants a different derivation.
3. **Whether `Compound` collapses into this.** A compound is already a claimed
   group of leaves with a purpose; it may be a small precinct, or a distinct
   thing that lives *inside* one.
4. **A wall does not look as strong as it is.** Wall hit points are invisible: an
   80hp picket fence and a 1200hp citadel wall draw with identical art on a
   one-cell line. The emplacements carry the reading now, which is most of what
   was wanted, but a thicker wall for a harder fortification would be nearly
   free — the outline is already computed — and would make the strength legible
   before contact as well as harder to breach.
5. **`OverwatchTowerStage` has no counterpart on the precinct path**,
   deliberately so far: it is parked behind `overwatch-tower-adoption.md`, and
   `PrecinctDefence` covers the same ground from a stated dial, so that story
   should be re-read against the emplacement loadout before it is picked up.
   `VehicleCorridorStage` was the other, and it closed the other way: a survey
   found the corridor has **no battle-time consumer at all** — every reference
   is inside the generator and one validation test, and convoys drive on the
   road graph — so a walled precinct's drivable gate opening onto no reserved
   corridor loses nothing the game ever collected.

(The ground allowance is now derived — see below.)

## The allowance is derived, and there are two derivations

A flat allowance does not work, so there is none. `PrecinctAllowance` asks what
kind of place it is:

- **A programmed precinct is as big as what it holds** — its road plus the
  `envelopeArea` its buildings and lots need. This is `compound-programs.md`'s
  law unchanged, one level up.
- **A zoned precinct is as big as the ground along its own streets** — its road
  plus the cells within its growth profile's `frontageDepth` of its own arms.
  There is no program to size it, and frontage is the same rule the shipped
  hinterland already uses to decide what is settled, applied per precinct rather
  than once for the whole map.

What that buys, measured over three seeds at 560x336 with a garrison, a town and
a hamlet:

| | nearest | derived |
|---|---|---|
| garrison claim | 37885 – 86044 | 8566 – 9908 |
| its program envelope | — | 5293 |

The garrison did not change between those columns. Under nearest its size swung
by 130% on nothing but how its neighbours grew; derived, it is its program plus
whatever road ran through it, and the residual variation is that road. Between
them the three places take under half the map and the rest stays open country.

Frontage is counted rather than used as a mask, deliberately: a town competing
with a neighbour for the same ground should yield it by nearness like anything
else, not carve a fixed collar out of whoever is beside it.
