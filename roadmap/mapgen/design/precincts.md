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

## Still open

0. **A walled precinct has too many gates.** Every arm crossing the outline is a
   gate, and a rendered garrison came out with eleven — which is not a fortified
   place. Through the tile renderer the wall reads as a dashed line rather than
   a wall, because it is more gap than wall. A fortress should keep the few
   crossings it wants and wall off the rest; the roads that then dead-end at the
   wall are ordinary. The rule that a gate is a discovered crossing still holds,
   but which crossings become gates is a decision the precinct has not been
   given yet. This is the most visible thing wrong with the model as it
   stands.

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
