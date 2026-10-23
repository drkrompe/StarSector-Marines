# The Heart — maps with intent

Status: ACTIVE. Direction for step 2 of `heart-mission-depth.md`. The owner picked the ridge layout for both forms (2026-10-23); the cordon ridge is built, the rest is not.

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

Each pooled map carries its density, so the pool can offer all three on each
biome. The layout's placements are measured against density's ground: a
logistics base on a rural farm road, in a town's goods yard, or in a city
car park.

## The cordon's works (both forms)

What a field commander who has held this line for weeks would have built
behind and along the start line:

- **The forward line:** a trench with wire in front, along the start line's
  ridge, broken by the roads and lanes it lets out through.
- **Observation posts:** a couple of sandbagged posts on the ridge's high
  points, looking down each lane or along the barrage's first lines.
- **The logistics base:** a fenced yard on the farm road behind the line.
  Vehicle park, fuel and ammunition stacks, the aid station and a landing pad.
  This is where armour waves and convoys come from (`F6`, `B5`), and where step
  3's tools live.
- **The supply road** from the logistics base through the line to the lanes or
  the gun line.

## The beats' set pieces

| Beat | Placed structure |
| --- | --- |
| F1 The lost column | A walled compound on the market town's knoll: sandbagged gaps, a wrecked APC in the yard, a radio mast. |
| F5 Linkup | The reservoir yard: the workings' sheds and tanks around an open yard that every lane's last stretch comes out onto. |
| F6 The supply road | The metalled country road from the logistics base north, with a satellite placed within surge reach of it. |
| B1 Ranging / B3 the guns | The gun line: Locust pits behind the ridge, with a trench and wire on its open flank where a breakout would come. |
| B2 The village | One holdout house in the village, shuttered and sandbagged, on the company's side of line 3. |
| B4 The ford | One stony ford in the company's sector, with the valley's banks either side of it. |

## Open

- Whether this direction is right: the owner's pick (`heart-map-intent`).
- The `place` op's shape, agreed with the story-map-pools owner.
- Which elevation tools can do a ridge and a hollow without art work
  (cliff art exists; gentle slopes may not).
