# The Heart — maps with intent

Status: ACTIVE. Direction for the map work of `heart-mission-depth.md`. The owner picked the ridge layout for both forms (2026-10-23); the cordon ridge (no ridge in a city, its line on the city's edge) and The Burn's brook valley are built, the hollow's rim is held and partly raised; the cordon's works, the beats' set pieces and the three densities are laid.

Written: 2026-10-23

`heart-nouns.md` is the model and `heart-story-beats.md` holds the beats this
ground stages. The heart's maps today are generated country with a creep
blanket over it. Nothing on them says that an army has been facing this bloom
for weeks, or where its commander chose to stand.

## The law: the mission places its structures

**Structure placement is the mission's, not map generation's** (owner,
2026-10-23: "try to control structure placement on the map and not lean
totally on map-gen"). Generation grows the country: woods, fields, the creep,
the dark settlement's streets. The heart then lays its **authored layout**
over that country: every structure a story beat or the military's presence
needs, at a place the mission chose.

- **A layout is data.** Each form has one layout. It places structures by
  depth and across, measured against the form's own geometry (start line,
  lanes, rides, the brook, the bores). That way one layout reads right on
  every pooled seed and biome.
- **A pooled seed may override a placement** with absolute cells when its
  ground needs it (`story-map-pools.md`, the patch). That needs a new op,
  `place`, next to `pin` and `ruin`. The pool doc currently rules out adding
  buildings, so this amends it and is agreed with mapgen's owner first.
- **Placed, then proven.** Structures go down before the navigation grid is
  built. `storyMapPools` checks every layout on every pooled seed: each
  placement on walkable ground, reachability from the start line unbroken.
- **Existing stampers first.** The defence post, compound perimeter and
  building stamps already exist. The layout composes them; a new structure
  kind is added only where none fits.

## The ground

Terrain is authored too, through the elevation planner
(`ElevationPlan`, `PlateauPlanner`, `ElevationBrush`), not left flat:

- **The start line on a rise.** The cordon dug in on the last high ground
  before the bloom: a low ridge along the farmland's far edge, looking down
  into the overgrowth. The guns (The Burn) and the vehicle park sit behind it.
- **The bloom in the low ground.** The country falls away into the woods. The
  workings and the reservoir sit in a hollow at the far edge, so the heart is
  something the attack goes down into.
- **The brook in its valley** (The Burn). Banks a step down to the water, and
  each ford a gap in the banks: that is why the fords are the crossings.
- **Broad Front's town on its own knoll**, the reason it was built there, so
  the lost column's holdout has height over the lanes around it.

## Density: rural, town, city

A heart grows wherever the bloom rooted, so the country between the cordon
and the heart may be almost empty or built up. **Density** is a map axis next
to biome (owner, 2026-10-23: "Consider rural vs medium populated vs worst case
urban"):

- **Rural.** Farms, woods, a village, the reservoir workings: today's maps.
  Open enough for armour to drive the lanes.
- **Town.** A market town or two across the approach. Streets and a square the
  lanes must pass through, built-up ground beside open fields.
- **City (worst case).** The heart rooted inside the colony's suburbs: blocks
  and streets most of the way to the workings, the creep over roofs and roads.

**Density decides the force mix.** Vehicles struggle to path through built-up
ground, so the denser the map, the more the allied force (step 3) leans on
infantry and mechs: "Urban versions might lean more heavily on infantry/mechs
as vehicles will likely struggle with pathing" (owner). A rural Broad Front is
columns of armour up the lanes. A city one is militia companies and mech
lances working block by block, with armour held to the main roads. Close air
support and the barrage matter more where the ground hides the swarm.

Built (`HeartCountry.Density`, chosen from the world's market size as the
Cordon's is): a town world adds a second market town across the approach and
grows the settlement bigger; a city grows the settlement as a city and lays
suburbs across the middle of the approach. A city has no cordon ridge, as an
urban Cordon has none (`cordon-nouns.md`): the colony's blocks run from the
farmland's far edge across the whole band, so no stretch of a ridge stood on any
pooled city map. Its forward line is dug a few rows short of the first built-up
row instead, along the farmland's far edge, broken at the lanes as everywhere,
and the lost column's holdout stands in the streets past it. Each density has its
own pool, three seeds per biome like the open-country one (`story-map-pools.md`,
Density), and a campaign heart takes its pool from the world's market size.

Each pooled map should carry its density, so the pool can offer all three on
each biome. The layout's placements are measured against density's ground: a
logistics base on a rural farm road, in a town's goods yard, or in a city
car park.

### What generation leaves room for

Authored ground is raised in the landscape pass, after the settlements, roads,
fields and landing are laid, and elevation never touches any of them. That is
fine for the cordon ridge, which lies in open country, and fails for ground
that has to stand among built places: on every pooled seed the workings stood
among their own roads, fields and paved yards, and a rim round them found no
stretch long enough.

So the hollow's rim is **held ground** (`elevation-nouns.md`, Held ground): its
ground is kept clear before the places grow, and the landscape pass raises it
there. The rim wraps the workings on the attack's side, with a pass straight
toward the start and wherever a road or route crosses it. Measured on the
pooled seeds it now stands on 11 of 12 Burn maps and every Broad Front map,
but as one to three arcs rather than a ring: the lane routes, the access
routes the landscape keeps open and, on Broad Front, the market town growing
up against the workings still break most of it. A rim that reads as a hollow
needs those routed round it, or the workings placed clear of the town, and is
open.

The knoll under Broad Front's town is not built. Held ground keeps places off
it; a knoll wants the place on top of it, growing on the plateau with only the
faces and ramps kept clear, which is a different reservation.

## The cordon's works (both forms)

What a field commander who has held this line for weeks would have built,
laid by the heart's mission layout (`mission-layout.md`, `HeartLayout`) as
named pieces, every one optional, none of them ground held before growth, so
the pooled maps keep the ground they were vetted on:

- **The cordon ridge** (`cordon-ridge`): a layout ridge, raised before the
  works with the hollow's rim and the valley sides in one stamp.
- **The forward line** (`forward-line-N`): trench and wire across the whole
  front along the cordon ridge's crest, facing the bloom, dug two rows deep
  with nests along it. It is broken at every lane (Broad Front's four and the
  company's road; The Burn's battlegroup sectors and the company's) so the
  columns drive through the ridge's passes, and wherever a road crosses it.
- **Observation posts** (`op-1`, `op-2`): a sandbagged post forward of the
  line in each half of the front.
- **The logistics base** on the farmland behind the line, beside the
  company's road: two ammunition dumps (`ammo-dump-1`, `ammo-dump-2`) and the
  vehicle park between them (`vehicle-park`), open ground where the armour
  waves and convoys form up (`F6`, `B5`). It is works and ground, not yet a
  fenced base that spawns machines: that is step 3's.
- **The supply road** (`supply-road`): a drivable road from the landing's
  farmland up to the settlement's near gate.

## The beats' set pieces

| Beat | Placed piece | Laid |
| --- | --- | --- |
| F1 The lost column | `holdout`: a sandbag ring at the market town's near edge, its way in on the start side, with `holdout-wreck`, the carrier, in its yard. | yes |
| F5 Linkup | `reservoir-yard`: open ground on the workings' start side that the lanes' last stretches come out onto. | yes |
| F6 The supply road | `supply-road` and the vehicle park; the satellite of the band nearest the road's far end stands within surge reach of the road (`HeartBores.ROAD_REACH`), past the settlement's near gate, falling back to its band where no such ground seats. | yes |
| B1 Ranging / B3 the guns | `gun-flank-1`, `gun-flank-2`: trench and wire along each open side of the battery, facing out. | yes |
| B2 The village | `village-holdout`: a sandbag ring at the village's near edge. | yes |
| B4 The ford | `company-ford`: the ford nearest the company's sector held open, its banks either side. | yes |

The heart's runtime holds what was laid (`HeartSequence.places`), so a beat
asks "is this here" by name, and review frames box and name every laid piece.

## Open

- Whether this direction is right: the owner's pick (`heart-map-intent`).
- The `place` op's shape, agreed with the story-map-pools owner.
- Which elevation tools can do a ridge and a hollow without art work
  (cliff art exists; gentle slopes may not).
