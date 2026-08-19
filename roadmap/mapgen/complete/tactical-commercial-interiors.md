# Tactical commercial interiors — shipped

**Code:** `d14ce6a8`  
**Status:** first ground-building tactical-floorplan slice complete.

## Player-visible result

Commercial buildings no longer all reduce to shelves lined against the outer
walls. Qualifying stores now generate as an oriented two-room plan:

- a public `SHOP_FLOOR` and rear `STOCKROOM` are labeled at carve time;
- the sales side gets a storefront entrance and the stockroom gets an opposed
  service entrance, joined by an interior doorway;
- shelves occupy real non-walkable, see-through fixture footprints in the room;
- large stores have parallel shelf runs, long firing/movement lanes, and a
  two-cell cross aisle;
- medium stores use a central display island with two-cell routes around all
  sides;
- checkout and stockroom crate cover are placed by room purpose rather than
  across the whole bounding box.

Small neighborhood stores stay on the old non-blocking perimeter-furniture
recipe. The tactical plan begins at a 10x9 footprint (either orientation); a
13x10 footprint has enough room for repeated full aisle runs. The primary
building in an eligible `DENSE_QUARTER` compound consumes the same planner, so
the slice already composes into the existing multi-leaf commercial district.

## Agent-size decision

`UnitType.MARINE.radius == 0.3` cells, so one infantry body is 0.6 cells wide.
A one-cell route is legal but intentionally single-file. Tactical-store aisles
are two cells wide: two infantry diameters total 1.2 cells, leaving separation
headroom for a pair to maneuver abreast. The heavy mech's 0.6-cell radius also
fits a two-cell lane, though the store layout is tuned primarily for infantry.

## Structural seams

- `CommercialPartitionStrategy` owns the sales/stock split and publishes plan
  metadata through `PartitionLayout`.
- `BuildingShellCore` now labels rooms before furnishing, allowing layout
  recipes to consume carve-time semantics.
- `RoomPurpose` gained append-only `SHOP_FLOOR` and `STOCKROOM` values.
- `CellTopology.Tag.FIXTURE` distinguishes prop footprints from structural
  walls. Finalization does not give fixtures wall art or destructible wall HP.
- Fixture shelves are `SEE_THROUGH`: they shape paths and supply heavy doodad
  cover without turning every aisle into an opaque wall.

## Verification

- `CommercialFloorPlanTest` covers the radius-to-aisle rule, 25 deterministic
  large-store seeds, room labels, public/service entrances, doorway clearance,
  fixture cover/visibility semantics, full reachability, two-cell primary and
  cross lanes, small-store fallback, and representative full-map prevalence.
- `BuildingZonePreviewTest` now includes a 15x11 commercial building so the
  tactical plan appears in `build/zone-previews/buildings-commercial.png`.
- `MapValidationScanTest` passes for legacy, conquest, and all station recipes.
- Full `gradlew.bat :test` passes.

## Deliberate next boundary

The next ground-building slice should make a commercial compound read as one
site: an anchor store, smaller storefronts, loading/service space, and a shared
arcade or courtyard with doors facing the shared circulation area.

Do not fuse BSP leaves into one solid building after `RoadGraphStage`: those
inter-leaf road cells may already carry vehicle routes. A true multi-leaf
building needs an earlier footprint-plan stage that suppresses/replans internal
road edges. Until then, commercial compounds should preserve those cells as a
walkable concourse or service lane.
