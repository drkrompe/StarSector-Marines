# Staffed buildings

Status: IN PROGRESS

Written: 2026-10-25

`ai-nouns.md` is the model (job site, shift, work motion, how a civilian
yields); `mapgen-nouns.md` owns the buildings themselves. Nothing here adds a
noun to either: a staffed building is an ordinary generated building whose
work is read back at battle setup, plus the few people who do it.

## Scope

The owner (2026-10-25): reusable, organic pieces for populated maps — a larger
warehouse with civilian workers inside carrying boxes about, then the same for
a factory and a grocery store — good passive set dressing for a colony, with a
possible objective later where the company guards workers loading precious
cargo into a shuttle or truck that leaves the map.

**A staffed building is a building that already exists, worked.** Every
populated map already generates warehouses (`BuildingLayouts.LayoutRecipe`
`WAREHOUSE`), factories (`INDUSTRIAL_FACILITY`) and shops (`SHOP`,
`DENSE_MARKET`), stocked with complete work groups: rack bays, pallet slots, a
tally desk, machines, shelves. Nothing new is placed and no prop is invented
for the workers; the building is the one generation made, and its stock is
what they handle. That keeps the owner's rule that a place is made from
ordinary buildings rather than scattered story props.

**Its work is recovered, not published.** A job site on a surface map is read
off the map at setup (`ai-nouns.md`, Job site), and a staffed building's work
is read the same way: each stock fixture in the building affords stores work
from a free floor cell beside it, the desk a tally, a machine its tending.
Affordance stays a property of the placement — it is the building that makes a
pallet stock rather than spoil — so only a building chosen to be staffed has
work in it, and the same crate in a yard is still scenery. What kind of
building it is comes from the same reading: whatever most of its stock says.

| Building | Recovered from | Work |
|---|---|---|
| **Warehouse** (mostly racks and pallets) | pallets and rack bays; tally or packing desk | crates carried rack to pallet and back; the tally kept at the desk |
| **Factory** (machines) | machine tools and tanks; feed pallets; control consoles | the machines tended, feed stock carried to the line, the board read |
| **Grocery** (mostly shelves) | shelf runs; the counter | shelves restocked from the stockroom; the till kept |

## The crew

A few civilians (`Faction.CIVILIAN`, colonist outfits) per building, never more
than its stock can keep busy. Each walks one loop round the building's own
work, started at a different point so a room is not worked in step. A walk
between two stores stops carries a crate (`WorkMotions.between`), so the
warehouse reads as boxes moving rather than people standing at shelves. Every
stop is claimed as its own point, the way a ship's local work round is, so two
workers never stand on one place and nobody walks back to the shelf they left.

**Where.** Any populated map may staff some of its buildings; the caller says
how many. The first consumer is the procedural operation map (the precinct
towns of Conquest, Assault, Raid and Extraction), which staffs up to two of
its civilian warehouses, its largest factory and two of its shops: a building with a garrison's compound over it is the
garrison's, never a civilian workplace. Cordon towns, story maps and the colony
are natural next consumers and belong to their own sessions.

## Combat: they yield to a fight, not to a uniform

`ai-nouns.md` has a civilian yield to anybody armed. On a garrisoned town that
empties every workplace the first time the town's own patrol walks past, so a
staffed building's crew yields to a **fight**: an armed actor near them shooting
at somebody also near them, or a round that finds one of them. A garrison going
about its rounds is part of the town, and the company guarding a loading,
firing at what comes up the street, lets the crew keep carrying; a firefight
at the door is neither. A worker who yields
drops the work and flees as any colonist does (`UnitRole.FLEE`: away from the
fight toward the far edge), and goes back to the building once nothing near
them, and nothing near the work they would go back to, has been fighting for a
few seconds. Without the second half a worker who has outrun the fight turns
straight back into it and flees again. A worker killed is gone: nobody holds
a civilian warehouse, so nobody sends a replacement (`ai-nouns.md`, A posting
outlives the people filling it — a worked room with no compound over it is
nobody's).

Sheltering inside the building, rather than running, is the better picture for
a crew whose building is not itself being fought through, and waits on a
shelter behaviour that does not yet exist.

## The objective hook: secure the loading

**Secure the loading.** A mission may name one staffed warehouse as a
**loading** (`Loading`, `battle.staffed`): the settlement's own truck (an allied
`CARGO_CARRIER`) stands in the yard, and the crew's loop changes from
rack-to-pallet to rack-to-tailgate, each crate set down behind the truck counted
aboard. The company guards the yard; the crew keep loading while the fight is
outside their reach and yield, as above, when it reaches them. When the count is
met the crew go back to the building's ordinary work and the truck drives a
road it was proved before it parked off the map. The truck lost, or the whole
crew dead, before then fails it; a battle that ends mid-loading lapses it.

Where the colony has a freight yard (`set-piece-blocks.md`), its warehouse is the
one staffed and the truck stands in the yard's bay, nose to the gate. Elsewhere,
where the truck stands is decided by whether it can leave. A stand counts only
on truck-sized ground connected to the map's edge band, where the hull can set
out facing any way (`TurnAwareCorridor.canSetOutFrom`). Of the stands nearest the
warehouse door, the first with a drivable road out to an edge wins. The nearest
truck-sized cell is often tucked against the building's corner, and no turning
circle gets a truck out of there. A warehouse with no such stand has no loading,
and the mission offers no objective.

`LoadingObjective` makes it an optional objective (`optional-objectives.md`);
the mission brings the loading and what it is worth. Its first host is Colony
Breach (`ColonyBreachObjectives`): the colony's largest warehouse, a side
objective beside the rescue, never instead of it. Whether it should instead be
an alternative main objective for the first alien mission is the owner's call.
Played unguarded on eight HIGH-risk colony seeds, three finished the loading and
drove out, three lost the truck to the swarm once the crew had run, and two
colonies had no stand a truck could leave from.

A shuttle that lifts off instead of a truck is the same loading with a
different vehicle and is not built.

## Constraints

- No new art and no new props: the workers are colonists, the crates the
  existing work-prop crate, the stock what the building generated.
- No generator change: recovery reads the finished map, so seeds and story
  pools do not move.
- Presentation only for the building; the crew are ordinary roster units, can
  be seen, shot and harvested, and never count toward a mission's result.

## Acceptance

- A look scene per kind (`staffed-warehouse-look`, `staffed-factory-look`,
  `staffed-grocery-look`, one `SnapshotSuite` each) of a generated building at
  work: 1920x1080 stills at play zoom and close, and a run of close frames.
- One unit test on the recovery rule: a stock fixture yields one point beside
  it on free floor, never on a doorway or another point.
- Procedural operation maps carry staffed warehouses without changing their
  generated ground.

## Plan

Done: steps 1 to 5. `BuildingWork` recovers a building's work and kind,
`StaffedCrew` gives each hand a loop round it, `AmbientThreatPolicy.FIGHT_NEARBY`
is the yield, and `StaffedBuildings` staffs the two largest civilian warehouses, the
largest factory and the two largest shops of every procedural operation map
(`staffed-warehouse-look`, `staffed-factory-look`, `staffed-grocery-look`). Measured on one
Assault map: armed patrols passed within two cells of the crew and nobody
stopped work; with the nearby units engaged, the hands in reach stood down and
were back at work within seconds of it ending. Town warehouses are modest (the
largest about fourteen by eight inside, a pallet, shelf runs and a tally desk);
a genuinely large warehouse is a fitted freight program, which only the heart's
freight origin builds today.

1. Recover stores work from a building's stock (`BuildingWork`).
2. The crew and its loop; the fight-near-them yield.
3. Staff procedural operation maps; the look scene.
4. Factory and grocery from the same recovery.
5. The loading objective, hosted in Colony Breach (`colony-loading-look`).
6. Later: shelter instead of flight; a shuttle loading; the larger freight
   warehouse as a placeable piece for Cordon towns and the colony.
