# Precinct interior coherence

Status: PLANNED

Written: 2026-09-01

Read `precincts.md` in full before implementing this story; it owns the model,
the vocabulary and the measurements this one builds on. `mapgen-nouns.md` owns
zoning themes and fillers, which is the machinery this story reaches into.

## Goal

A precinct is a place on the outside and not yet a place on the inside. Make a
zoned precinct's kind reach what is built in it, so that a town, a hamlet and an
outlying district are different places rather than three samples of one
map-wide scatter.

## Where we are

The precinct model shipped from the outside in, and that half is done. Growth,
allowance, claim, artery, highway pruning, interconnection, boundary, gates and
emplacements are all per-precinct, all measured, and all recorded in
`precincts.md`. The dials a mission turns — `Fortification`'s emplacements,
gates and wall hit points, and `PrecinctPlan.Sprawl` — are settled and are not
what this story is about.

**A programmed precinct is coherent end to end, and that was checked rather than
assumed.** Program sizes envelope sizes allowance sizes claim; the packer fills
the claimed shape; its rooms become tactical nodes; the emplacements are seeded
against its own gates and its own depth; the wall is stamped around what grew.
Measured on two derived `BALANCED` maps at 560x336, the number of civilian
buildings standing within thirty cells of the installation seed is **0 and 1**.
Nothing civilian is leaking inside the wall. The chain from an authored program
to a defended installation holds.

**What does not hold is everything with no program.** A zoned precinct's
identity stops at its outline, and three separate measurements say so.

*The interior is themed by where it is on the map, not by what it is.*
`ZoningOverlayStage` builds one `DistrictMap` for the whole map and
`LabelLeavesStage` asks it `themeAt(x, y)` — an absolute position lookup on a
fixed grid of roughly twenty-cell blocks. A precinct's parcels are correctly cut
from its own claim, and are then themed by a scatter that has never heard of it.
The one nudge the scatter has, a civic bias at the trunk crossing, is a single
point on a map that now has four places, so at most one of them can have a
centre and the rest take what the scatter gave them.

*Precinct identity reaches three stages, none of which fills anything.*
`BspKeys.PRECINCTS`, `PRECINCT_CLAIM` and `PRECINCT_ROAD` are read by
`PrecinctWardStage`, `PrecinctDefenceStage` and `SpawnAnchorStage`. The whole
fill chain — zoning overlay, leaf labelling, spaceport planning, compound
claiming, fill dispatch — reads none of them. This is not an oversight in any
one stage; the precinct stages were slotted into the legacy recipe around a fill
sequence that predates the model.

*So the places come out the same.* The same two maps, points of interest
attributed to their nearest seed:

| seed | place | POIs | residential | depot | comms |
|---|---|---|---|---|---|
| 42 | settlement | 75 | 59 | 14 | 2 |
| 42 | outlying-1 | 95 | 83 | 6 | 6 |
| 42 | outlying-2 | 115 | 93 | 14 | 8 |
| 7 | settlement | 100 | 84 | 11 | 5 |
| 7 | outlying-1 | 66 | 47 | 15 | 4 |
| 7 | outlying-2 | 225 | 191 | 20 | 12 |

Residential runs 71–85% of every place on both seeds. A town profile and a
hamlet profile produce the same mix, and the size is not a statement either: at
seed 7 an *outlying* place came out with 225 points of interest against the main
settlement's 100. What separates two zoned precincts today is how much ground
their claims happened to win, which is the claim lottery `precincts.md` already
records as open item 2 seen from the other end.

(Attribution is by nearest seed rather than by claim, which is an approximation
and is why the installation's own row is left out of the table above — the
region nearer that seed than any other is far larger than its claim, so its
count is mostly its neighbours' houses. The thirty-cell reading is what actually
answers the question about the installation, and it answers it clean.)

## What this story does

Give a zoned precinct a **character** — the thing a programmed precinct's
`FortressProgram` already is for the packed case — and let it decide the themes
its own parcels get, instead of sampling a map-wide grid.

The shape to reach for is the one the model already uses twice: the place states
what it is, and the generator derives the rest. A character is not a second
zoning system; it is what a `DistrictMap` should be asked *per precinct* rather
than once per map.

Three things fall out that are worth stating as constraints rather than steps:

- **Hinterland is not a precinct and must not acquire a character.** Open
  country is dressed, not built, and the moment it has a theme it becomes a
  fourth kind of settlement.
- **A precinct's centre is its own, not the map's.** Whatever civic nudge exists
  belongs at each place's seed. Four places on a map should be able to have four
  centres, or none.
- **The mix is a statement, not a roll.** "A depot town" and "a dormitory
  suburb" should be different documents, in the same way a garrison and a depot
  are different programs — which means the same authored-or-derived pair
  `PrecinctPlan` already has.

## Not in scope

- `Fortification` and the wall/emplacement dials. They are settled and measured;
  nothing here changes them.
- Deriving `Fortification` from the campaign — `precincts.md` open item 5. It is
  a separate decision, blocked on which of defence rating or detachment strength
  drives it.
- Whether `Compound` collapses into the precinct model — open item 3. Compounds
  are claimed on the precinct path today and this story should not disturb them;
  a character that decides compound eligibility is the follow-on, not this.
- The claim lottery — open item 1 and 2. A place whose size is a statement is
  the sibling problem to a place whose contents are, and doing both at once
  would leave neither measurable.

## Acceptance

- A zoned precinct states or derives a character, in the same authored-wins
  shape as `PrecinctPlan`.
- Leaf themes inside a claim come from that precinct's character, and the
  map-wide `DistrictMap` remains the answer only where no precinct claims the
  ground.
- Two precincts with different characters on one map produce measurably
  different POI mixes at the same seed; the same character produces the same
  mix. Both directions asserted, because a change that merely adds noise would
  pass the first alone.
- The hinterland is unchanged and holds no themed content.
- The conquest recipe is byte-identical: it does not run these stages through a
  precinct plan, and this story must not move the ground under the mission the
  campaign is balanced on.

## Known gaps this story does not close

Two stages on the conquest recipe have no counterpart on the precinct path, and
both are deliberate today rather than accidental:

- `VehicleCorridorStage` is conquest-only, so a walled precinct's guaranteed
  drivable gate opens onto no reserved corridor. Whether it needs one is a real
  question once armour uses these maps.
- `OverwatchTowerStage` is a taxonomy consumer parked behind
  `overwatch-tower-adoption.md`, and `PrecinctDefence` now covers the same
  ground from a stated dial instead. The parked story should be re-read against
  the emplacement loadout before it is picked up, because the dial may have
  answered it.
