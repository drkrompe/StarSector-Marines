# Precincts

Status: ACTIVE — adopted and wired. `BspCityGenerator.usePrecincts` builds a
whole map this way: places seeded from a world or authored, grown, welded onto
one road network, claimed, allowed ground, filled — zoned or packed — walled and
gated. What remains is the shape work below.

Written: 2026-09-01

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

## A programmed place claims differently from a settled one

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

## Fill is a policy, not a stage

Inside its own claim, a precinct is filled one of two ways:

- **Programmed** — pack its authored footprints into its buildable mask. This
  already works on an arbitrary shape: `FortressInterior.pack` takes a buildable
  mask and a circulation mask, and `Bounds.of` reads the extent off the mask.
  The rectangle a fortress has today enters only through `wardRect`.
- **Zoned** — hand its parcels to the existing labelling and fillers, which is
  what a city does now.

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

Two things about that carve were learned by measuring rather than reasoning.
It runs as a **ray from the claim's centroid**, not as an L to a target: an L's
legs run along the axes, so a target nearer the centre than the boundary leaves
the whole path inside the claim, and the carve never crossed its own outline. And
it heads for the **nearest map edge**, not for a neighbour's road: the rule that
an artery may not overwrite another precinct's road is right, but it means a ray
aimed at that road cannot paint the cells it needs, and the carve stops one cell
outside its own boundary. An edge is always reachable and the ground on the way
is nobody's. A neighbour whose network lies across the route is met on the way
rather than aimed at.

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

Points of interest stay at zero on a remote map, and that is right rather than
outstanding: those come from settlement fills, and a remote map has no
civilians.

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
`BattleSetup`'s vehicle parking and `OverwatchTowerStage` still use the whole-map
form and carry the same latent failure.

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

## Still open


1. **A precinct takes what it asks for whether or not the map can spare it.** On
   a 200x140 map one garrison claimed 14640 of 28000 cells and its neighbour was
   simply squeezed. Nothing checks that the places asked for fit the map they
   are being put on.
2. **Settlement claims read as collars, not districts.** A zoned precinct's
   allowance spreads two or three cells either side of its arms, so it draws as
   a road network with a shoulder rather than as a place with streets in it.
   Either the frontage depth is too shallow for the map scale or the allowance
   wants a different derivation.
3. **Whether `Compound` collapses into this.** A compound is already a claimed
   group of leaves with a purpose; it may be a small precinct, or a distinct
   thing that lives *inside* one.
4. **What `BiomeKind` becomes.** It is read as front-line progression ordering
   by `CounterattackSystem`, `FrontLineReinforcementTrigger` and
   `RecaptureTargetService`. Distance from the objective precinct is the natural
   answer once places exist.
5. **Nothing derives `Fortification` from the campaign.** `PrecinctPlan.derive`
   hands every garrison `GARRISON` regardless of the world's defence rating or
   the force being sent, which is exactly the "not fun at either end" problem the
   dial exists to solve, still unsolved at the point where it would bite. It
   needs a decision about which of those two drives it.
6. **A wall does not look as strong as it is.** Wall hit points are invisible: an
   80hp picket fence and a 1200hp citadel wall draw with identical art on a
   one-cell line. The emplacements carry the reading now, which is most of what
   was wanted, but a thicker wall for a harder fortification would be nearly
   free — the outline is already computed — and would make the strength legible
   before contact as well as harder to breach.
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
