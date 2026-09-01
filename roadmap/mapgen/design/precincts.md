# Precincts

Status: ACTIVE — adopted. The noun, multi-seed growth, three claim policies,
the derived allowance and programmed fill are built; the boundary and seeding
are not.

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
crosses its boundary exactly once, so a gate is a discovered fact rather than a
placed object with a road found for it afterwards.

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

## Still open

1. **Whether `Compound` collapses into this.** A compound is already a claimed
   group of leaves with a purpose; it may be a small precinct, or a distinct
   thing that lives *inside* one.
2. **What `BiomeKind` becomes.** It is read as front-line progression ordering
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
