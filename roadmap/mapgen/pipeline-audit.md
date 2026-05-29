# Battle map-gen pipeline audit

> Captured 2026-05-22 from an Explore-agent survey of `mapgen.bsp.*`
> as the audit step before the room-purpose refactor. The file:line
> citations point at the production state on commit `82c76a9` (just
> before Slice A landed); the refactor will move some seams but the
> coupling points + pipeline shape remain accurate.

## Pipeline shape

Orchestrator: `BspCityGenerator.java:139-376`. Four-step sequence:
partition → label → fill → finalize.

1. **Partition** (`BspCityGenerator.java:162-187`) — `Bsp.partition()`
   carves the grid recursively with axis-aligned cuts. Each cut
   reserves a road strip (width 3-5 cells, 1-cell perimeter). Road
   cells written to shared `roadCells[][]` mask; leaves accumulate in
   a list. Trunks (major arterials) painted first (`TrunkPlan`), then
   BSP runs in sub-rects between trunks, sharing the same road mask.
2. **Label** (`BspCityGenerator.java:215-434`) — each leaf gets a
   `BlockKind` rolled from a zoning overlay (`BiomeMap` in conquest,
   `DistrictMap` legacy). Post-roll constraints filter failing leaves
   (e.g. `LANDING_ZONE` requires min dimension 5×5). Purely
   read/write on `leaf.kind`; no topology / grid changes.
3. **Fill** (`BspCityGenerator.java:248-336`) — two dispatch paths:
   - Per-leaf: `BlockFiller.fill(leaf, grid, topology, ...)` for
     non-`COMPOUND_MEMBER` leaves.
   - Compound: `CompoundFiller.fill(compound, grid, topology, ...)`
     for multi-leaf clusters. Runs ONCE per compound; absorbed leaves
     are skipped by the per-leaf dispatcher.
4. **Stampers** (`BspCityGenerator.java:276-336`) — post-fill passes
   that read what fillers built and emit tactical anchors or overlay
   structures:
   - Pedestrian frames (line 276)
   - Biome overrides (lines 283-284)
   - Beach shoreline (lines 293-295)
   - Fortress wall (lines 304-305)
   - Defense posts (lines 315-316)
   - Compound perimeter defenders (line 325)
   - **Keep entry chamber** (line 336) — the slice-6 stamper
5. **Finalize** (`BspCityGenerator.java:346-375`) — wall HP seeding,
   cover baking, wall flagging, building flood-fill, spawn-anchor
   selection.

## Fill catalog (per-leaf)

Defined at `BspCityGenerator.java:87-107`:

| Filler | BlockKind | What it carves |
|---|---|---|
| `BuildingResidentialFiller` | BUILDING_RESIDENTIAL | hollow shell via `BuildingShellCore`, INDOOR floor, HOME layout doodads |
| `BuildingCommercialFiller` | BUILDING_COMMERCIAL | hollow shell, TILE floor, SHOP layout |
| `BuildingIndustrialFiller` | BUILDING_INDUSTRIAL | hollow shell, STRIPED floor, WAREHOUSE layout |
| `FortifiedPostFiller` | FORTIFIED_POST | clamped 3×5 hardened post |
| `PlazaFiller` | PLAZA | walkable BRICK ground + STONE border + sparse seating |
| `LandingZoneFiller` | LANDING_ZONE | striped floor, directional arrows, LZ marker |
| `ParkFiller` | PARK | grass blob, stone paths, sparse benches |
| `IndustrialYardFiller` | INDUSTRIAL_YARD | dirt floor, crate clusters |
| `WastelandRubbleFiller` | WASTELAND_RUBBLE | damaged floor + walls, rubble |
| `WaterfrontFiller` | WATERFRONT | non-walkable water + walkable shore |
| `DenseBlockFiller` | DENSE_BLOCK | 2×2 sub-buildings + 1-cell alleys |
| `NatureZoneFiller` | NATURE_GRASSLAND/WETLAND/BEACH | grass/dirt + water pools + overlays |

## Fill catalog (compound)

Defined at `BspCityGenerator.java:105-107`:

| Filler | BlockKind | What it does |
|---|---|---|
| `MilitaryBaseFiller` | MILITARY_BASE | perimeter wall, STONE parade ground, role-driven sub-buildings (COMMAND/BARRACKS/ARMORY/VEHICLE_BAY), gates, corner emplacements, tactical nodes |
| `GatedHousingFiller` | GATED_HOUSING | INDOOR wall ring, GRASS yard, residential sub-buildings |
| `DenseQuarterFiller` | DENSE_QUARTER | no wall, TILE alleys, large commercial sub-buildings |

## Conventions across all fills

- Every cell inside the leaf is explicitly set to a ground kind (never
  left STREET unless intentional stub fallback).
- Walkability, doorway flags, wall directions are set; doodads + POIs
  appended to shared lists.
- Fillers MUST NOT touch road frames (orchestrator's job) or other
  leaves.
- POI anchors: exterior anchor (nearest walkable outside), interior
  anchor (a walkable cell inside the footprint).
- `BuildingShellCore` is the shared carving kernel: perimeter wall,
  single optional interior wall (`MULTI_ROOM_CHANCE` 65% at ≥7 cells
  on one axis), 1-2 perimeter doorways, doodad layout strategy.

## Room representation — inferred, not labeled (pre-refactor state)

Rooms exist only as zones detected post-hoc:

- `BuildingShellCore.java:309-346` (`maybeAddInteriorWall`) carves an
  optional single interior partition wall + one interior doorway. No
  explicit "room ID" or "room purpose" — the two cells on opposite
  sides of the wall form separate zones implicitly.
- `KeepEntryChamberStamper.java:84-168` detects multi-room
  `COMMAND_POST` buildings by:
  1. Building a transient `ZoneGraph` over the final grid state.
  2. Flood-filling from the `COMMAND_POST` anchor to find its zone ID
     (the throne room).
  3. Collecting walkable non-doorway cells inside the building bbox
     that belong to OTHER zones — the antechamber.
  4. If antechamber ≥ 3 cells, emitting an `INNER_POSITION` tactical
     node.

No room-purpose metadata. The stamper doesn't know (and doesn't ask)
whether a room is "entry" or "throne" — it just finds "the other room"
via zoning.

## Coupling points (the hard constraints)

1. **Single-axis binary partition** (`BuildingShellCore.java:310-346`)
   — picks ONE axis based on which dimension ≥ `MULTI_ROOM_MIN_DIM`
   (7 cells). Split position randomized 3 cells from edges. Result:
   always 2 rooms or 0. **Blocks 3-chamber designs.**
2. **BSP leaf = bbox building** (`BlockFiller.java:52-57`) — every
   fill operates on a rectangular leaf; road frame is implicitly the
   building's perimeter. Fillers don't bridge between leaves or create
   corridors; `CompoundFiller` is the only multi-leaf fill, and it
   still treats inter-leaf roads as ABSORBED INTERIOR (parade ground),
   not connective first-class corridors.
3. **Road graph is post-fill only** (`RoadGraphBuilder.java:89-100`)
   — skeletonizes the road-cell mask AFTER all fillers run. Road cells
   are bitmask-only until graph extraction; no per-road "connector"
   metadata.
4. **Stamps assume BSP geometry** — `FortressWallStamper` sets back 12
   cells from biome edge; `CompoundPerimeterDefenderStamper` scans ±3
   cells outward. These constants encode "fortress is a large biome
   block" + "defense posts sit in the kill zone." A non-biome map or
   room-interior "fortress" would need different offsets.
5. **Compound members are whole leaves** (`Compound.java:31-53`) —
   compound owns `BlockLeaf`s (plural) via BFS-growth; each member is
   a full leaf. No sub-leaf ownership.
6. **TacticalNode emits from compound roles**
   (`MilitaryBaseFiller.java:128-165`) — each member leaf has an
   assigned `Role` enum. Role → `TacticalNode.Kind` + priority +
   garrison. No room-level tactical anchors; only per-leaf
   (per-building-in-compound) level.
7. **Doorway connectivity gates rooms**
   (`KeepEntryChamberStamper.java:132-168`) — `grid.isDoorway()` +
   `ZoneGraph` to detect multi-room buildings. Doorways are the ONLY
   connective primitive recognized by the zone detector. Interior
   walls without doorways would create unreachable zones.
8. **Interior anchor is BFS-outward from center**
   (`BuildingShellCore.java:188-213`) — `findInteriorAnchor()` BFS
   from the building center, stops at the first walkable non-doorway
   interior cell. Works for 2 rooms; order is undefined for 3+ rooms
   — BFS stops at whichever room it reaches first.

## The seam — where labeled rooms slot in

**Between fill and stamp, or within fill, before doorway punching.**

The cleaner design (shipped — see
[`complete/room-purpose-refactor.md`](complete/room-purpose-refactor.md)):

1. Extend `BuildingShellCore.BuildingConfig` to include a
   `RoomPurpose[] chamberPurposesByAnchorDistance` — distance-indexed
   so the same array works for any partition count.
2. Mutate `BuildingShellCore.carve()` to write `RoomPurpose` labels
   at carve time, per chamber.
3. Stampers read the label directly: `topology.getRoomPurpose(x, y)`
   is enough; no `ZoneGraph` needed.

Surgery required (mostly done as of Slice A):
- Add `RoomPurpose` enum + topology setter/getter (3 lines in
  `CellTopology`). **Done.**
- Extend `BuildingShellCore.BuildingConfig` to carry a labeling intent.
  **Done.**
- Mutate the partition logic to label the two sides before punching
  doorways. **Done for binary; ternary is Slice C.**
- `KeepEntryChamberStamper` reads the label directly. **Done.**

## Corridors — the bigger blocker for stations

BSP leaves are atomic buildings; roads are perimeter filler. Connecting
buildings as first-class corridors needs:

1. **Corridor abstraction** — a corridor is a narrow (1-2 cell width)
   connective structure between two separate buildings. Today, road
   frames fill this role implicitly but aren't marked as "corridor"
   vs. "street."
2. **Building-entry coordination** — two adjacent buildings' doorways
   could face across a road frame, creating a natural "corridor"
   between them. But `BuildingShellCore` places doorways
   independently; no check for alignment.
3. **`RoadGraph` doesn't track inter-building edges** — skeletonizes
   the road mask into nodes + edges. NO concept of "this edge
   connects building A's exit to building B's entrance."

What's needed:
- Post-fill pass: walk each compound's / fill's doorways; for each
  doorway facing an adjacent leaf, mark road cells between as
  "corridor."
- Extend `BlockFiller` / `CompoundFiller` contract: fillers optionally
  emit a `List<Corridor>` (endpoints, width, purpose).
- `RoadGraphBuilder` tags edges that fall entirely within a single
  corridor.

**Out of scope for the current refactor sequence.** Station fills will
likely need a different orchestrator entirely (corridors-as-primary,
rooms-as-vertices) rather than retrofitting BSP. Capturing here so the
abstraction designer doesn't accidentally bake the BSP-only shape into
`PartitionStrategy`.

## Three-chamber keep specifics

Current blocker: `BuildingShellCore.maybeAddInteriorWall()` only
picks ONE axis (lines 310-346). To support 3 chambers (entry / inner
/ throne):

1. **Extend partition logic**:
   - If `w >= 12 && h >= 12`, roll a 3-partition some % of the time.
   - Pick two split coordinates `wx1` and `wx2` with valid spacing.
   - Carve walls at both; punch interior doorways between each
     adjacent chamber pair.
2. **Return a richer partition tag** — `InteriorWall` is single-axis.
   Replace with a layout that carries N axes. The Slice A polish
   commit already factored `chamberIndex()` to take orientation + axis
   in a way that generalizes via a sorted-array bisect.
3. **Anchor placement question** — `findInteriorAnchor` BFS-from-center
   in a 3-chamber building lands in the MIDDLE chamber (the center
   cell lives there). Throne wants to be the deepest chamber, not the
   middle. Resolutions:
   - **Asymmetric placement** — make the middle chamber narrow so the
     center cell falls in an end chamber.
   - **Override anchor selection** — `findInteriorAnchor` takes a
     `RoomPurpose` preference and biases toward `KEEP_THRONE`-labeled
     cells if any exist (chicken-and-egg: labels are written after
     anchor today; would need reordering).
   - **Distance-from-anchor labeling** — the slice-A polish landed
     this: throne is wherever the anchor lands, entry is the farthest
     chamber, inner is the middle. Means the throne can be either end
     chamber depending on which side of the bbox center the BFS lands
     in; both ends are equally valid throne placements visually.

The distance-from-anchor labeling is the simplest answer and is
already implemented; Slice C just needs to make sure the ternary
partition produces an asymmetric chamber layout (smaller middle) so
the anchor reliably lands in an end chamber.
