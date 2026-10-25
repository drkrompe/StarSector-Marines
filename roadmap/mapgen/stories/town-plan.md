# Town plan

Status: IN PROGRESS — frontage rows, town blocks and the planned zoning (a civic core, a high-street ring, an
industrial edge) are built; the owner's look review of the planned zoning remains.

Written: 2026-10-25

Read `mapgen-nouns.md` (partition, labels, zoning, compound claims, the road reservation), `precincts.md`
(a precinct's character and centre), `set-piece-districts.md` and `cross-leaf-footprint-planning.md` first.

## Scope

The owner: the project could always use better lots and district planning than the old city planner gives.
The look review of main street showed why: a lined high street came out as one or two large buildings a side
behind wide pavement, because every lot became one building. Three faults, in the order they show:

1. **Sparse frontage.** A lot is one building, so a street is a building, a side street, a building.
2. **Small, scattered pockets.** The zoning is a 20-cell grid of rolled themes, smoothed once. Main street
   stood on 5 of 24 stock cities because few cities zone a civic or mixed pocket big enough; the owner has
   since let it stand in the residential quarter too, which lifts cities to 15 of 24 without planning a
   pocket.
3. **No town plan.** The stock city has no centre, no high street, no industrial edge, no residential rings;
   a precinct has a character and a centre, the stock city a single civic nudge at the trunk crossing.

This story covers the stock and grown city recipes and the precinct path's zoned settlements. Conquest keeps
its biome bands and its buildings: its lots fill exactly as before.

## What the planner made (audit)

Measured over 24 stock medium cities and 24 Colony Breach colonies (`generatedSpaceEvidence --tests
*TownPlanEvidence`, `build/reports/town-plan/town-plan.md`). "Street edge" is a lot's edge cells that face a
street; "built" means a building within three cells of it.

| family | lot kind | street edge built | buildings per 20 street-edge cells | buildings per lot |
|---|---|---|---|---|
| city | shops | 87% | 0.59 | 1.00 |
| city | homes | 85% | 0.57 | 1.00 |
| city | main street members | 45% | 0.50 | 0.82 |
| colony | shops | 86% | 0.68 | 1.00 |
| colony | homes | 85% | 0.67 | 1.00 |
| colony | main street members | 42% | 0.40 | 0.70 |

The lots themselves are the reason. Shop and home lots are mostly five to seven cells deep and eight to twelve
long (a city's short sides: 136 lots of 5, 84 of 6, 78 of 7, falling away to a handful past 12), so a lot is
already about one building, and every lot is ringed by a three-to-five-cell street frame that renders as
pavement. Main street's members are the same lots, seven to sixteen cells along the spine with a narrowed
cross street between each.

Zoning: a stock city has 28 blocks, of which 6.3 are civic or mixed on average, the largest connected group
of them 3.9 blocks. A colony has 240 blocks, 76.7 civic or mixed, largest group 18.7: the precinct path's
characters already plan their places, which is why colonies carry main street on 14 of 24 maps and cities 5.

## The nouns

- A **frontage row** is a lot cut along its street into narrow buildings side by side, neighbours sharing one
  **party wall**, each building's doors and windows on its street front and on its back, never on the flanks.
  The row covers the whole frontage, corner to corner. A lot too short for two plots, or too shallow for one,
  keeps the building it had. Behind the row is its **back yards** (gardens for homes, a paved service yard for
  shops); a lot deep enough with a street behind it too takes **two rows back to back** with an **alley**
  between, each facing its own street.
- A district's **character** says how it builds its frontage: plot widths along the street, a front garden,
  the building depth, the least yard worth leaving. Shops in town are six to nine cells wide straight onto the
  street; terraced homes five to seven behind a one-cell front garden where the lot has room; a mixed town's
  homes eight to eleven, semi-detached and villas behind a deeper garden; main street's shopfronts five to
  seven behind the district's own pavement. The outskirts, coast and industrial belt build none: their lots
  stand alone as before. A character's widths must tile every frontage from two plots up (three of the
  narrowest fit where two of the widest end), or some lengths would hold no row.
- A **town block** is two ordinary shop or home lots in the residential, civic or mixed town and the minor
  street between them, joined once compounds, districts and the market lot have claimed and before the road
  graph is built. They join only where they face each other across nothing but one street along the whole of
  their shared side, so the street removed runs from one cross street to the next and no dead end is left; the
  street ring round the block stays complete and no trunk is crossed (the market lot's rules). Blocks grow to
  36 by 20. Joining this late leaves the labels and every planned claim exactly where they were.

## Built

Frontage rows on ordinary shop and home lots in town, on main street's shopfronts (the hall stays one
building), and town blocks. A shop row alternates its two styles so each front reads as its own shop, roof
and all. After (same seeds and measure):

| family | lot kind | street edge built | buildings per 20 street-edge cells | buildings per lot |
|---|---|---|---|---|
| city | shops | 81% | 1.01 | 1.99 |
| city | homes | 72% | 0.92 | 1.78 |
| city | main street members | 43% | 0.68 | 1.11 |
| colony | shops | 82% | 0.97 | 1.49 |
| colony | homes | 73% | 1.05 | 1.61 |
| colony | main street members | 38% | 0.56 | 0.98 |

The street edge is a little less built because a row leaves back yards and front gardens where one building
filled the lot; what changes is the grain, close to twice the buildings along every street. Main street stands
on the same maps as without rows (15 of 24 cities with the residential quarter weighed, 14 of 24 colonies).
Every story map pool still passes.

**Tried and dropped: joining before the labels.** Joining town lots straight after zoning, before they were
labelled, made blocks of everything and reshaped what the compound, district and set-piece planners read:
main street fell from 5 to 2 of 24 cities and from 14 to 11 colonies, because its spine wants two or three
members a side. Joining after the claims keeps them all where they were.

Look: `createSnapshots -Psnapshot=town-plan-look` (cities 12, 13, 19; colonies 1, 4, 8; play zoom, and close
with roofs off, centred on main street).

## Built: the planned zoning

The stock city's theme grid was noise with one civic nudge: its civic and mixed blocks a few scattered cells,
its industrial lots wherever the roll put them. For a map that is one settlement (no precincts) the zoning is
now planned over the roll (`TownZoning`), the way a precinct's character already plans its own place:

- **Civic core**: the block holding the trunk crossing and its neighbours toward the crossing's nearer block
  corner, two by two.
- **High street ring**: every block beside the core is mixed town, where main street and the market square
  find their pocket.
- **Industrial edge**: the map edge farthest from the core is the industrial belt, two blocks deep where the
  world's economy is industry. The coast keeps its waterfront everywhere.
- **Elsewhere** the roll stands, but a town has one centre and one industrial edge: a civic block away from
  the core becomes homes, and an industrial block becomes homes unless it touches the belt, which it then
  extends.

The plan draws no random number, so the roll and every stage after it see the same stream. Measured on 24
stock medium cities (precinct maps, colonies among them, are unchanged):

| measure | rolled | planned |
|---|---|---|
| civic or mixed blocks per map | 6.3 | 13.0 |
| largest connected civic or mixed group | 3.9 | 12.7 |
| industrial blocks per map | 5.0 | 3.9 |
| largest connected industrial group | 3.1 | 3.5 |
| main street stands | 15 of 24 | 16 of 24 |

Industry is a little less of the map but one place rather than three or four scattered blocks. It moves every
stock and grown city's labels and pockets (the `Request.pocket()` contract is unchanged); every story map pool
still passes. Look: the same suite adds a whole-town still for each city.

## Acceptance

- Unit tests for the row rule (spans tile the frontage, party walls shared, depth and yards, back-to-back
  rows only with a street behind) and the block rule (whole shared side, complete ring, no trunk, size cap),
  each under a second: `FrontageRowTest`, `TownBlockStageTest`.
- Before and after look stills on the same seeds at 1920x1080 from the look preset.
- `storyMapPools` passes, swapped seeds named.
- Measured: the tables above for rows, blocks and the planned zoning.
- Unit tests for the zoning plan (core, ring, belt; deep belt; one centre and industry only on the belt over
  rolled maps): `TownZoningTest`.
