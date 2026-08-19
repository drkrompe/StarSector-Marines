# Coherent commercial compound — shipped

**Code:** `7bb9b4f2`
**Status:** second ground tactical-structure slice complete.

## Player-visible result

`DENSE_QUARTER` now reads as one commercial site instead of unrelated buildings
that happen to share a road frame:

- the largest lot becomes the anchor store, reusing the tactical
  `SHOP_FLOOR` + `STOCKROOM` planner;
- COMMAND and BARRACKS members become smaller storefronts, while ARMORY and
  VEHICLE_BAY members become warehouse/service units;
- every member chooses the facade with the strongest exposure to the shared
  paved circulation area and places its public door there;
- an opposed rear door gives each building an outward service entrance;
- tactical stores align the room split with that frontage, keeping the sales
  floor public and the stockroom at the rear;
- public frontages receive sparse bench cover, while service frontages receive
  loading crates.

The shared road frame is now split semantically: unreserved edge cells become
BRICK pedestrian concourse, while `ROAD_RESERVATION` centerline cells retain
their STREET paving and stay clear for the vehicle graph.

## Agent-size and lot-size decision

The compound does not invent a smaller tactical-store layout. It keeps the
shipped 10x9 activation threshold and two-cell aisle policy derived from the
marine's 0.3-cell radius. Smaller members remain useful as non-blocking
storefronts or service units. The largest claimed member is selected as the
anchor, which improves the odds that the compound's primary store qualifies
without raising `CompoundClaim` minima so far that commercial compounds vanish
from ordinary 80x80 city batches.

## Structural seams

- `BuildingPlacement` carries per-carve frontage and opposed-door hints that
  do not belong on a reusable `BuildingConfig`.
- `PartitionStrategy` has a placement-aware default overload, so existing
  binary and ternary planners retain their behavior.
- `CommercialPartitionStrategy` consumes frontage to choose its split axis and
  rear stockroom end when both orientations are legal.
- `BuildingShellCore` uses the same placement for the perimeter doors.
- `DenseQuarterFiller` derives frontage only from the bridged concourse mask;
  it does not suppress or rewrite road-graph edges.

## Verification

- `DenseQuarterFillerTest` covers a deterministic three-building L-shaped
  compound, horizontal and vertical concourses, public/rear doorway pairs,
  sales/stock orientation, sparse frontage cover, reserved-road preservation,
  determinism, representative full-city prevalence, and a visual preview at
  `build/zone-previews/dense-quarter-commercial-compound.png`.
- `CommercialFloorPlanTest` covers all four frontage directions on a store that
  supports either partition axis.
- The full `gradlew.bat :test` suite passes, including every map preview and
  `MapValidationScanTest` connectivity/deployability scan.

## Deliberate next boundary

The reusable next slice is broader multi-room building plans: offices,
warehouses, and larger civic structures should consume the same
placement/room-purpose seam. A truly fused multi-lot structure still needs an
earlier footprint-plan stage that can suppress or replan internal road-graph
edges before `RoadGraphStage`; this slice deliberately leaves those cells
intact.
