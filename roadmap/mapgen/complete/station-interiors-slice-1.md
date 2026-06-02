# Station interiors — Slice 1: rooms + corridors as a recipe — ✅ SHIPPED

Commit `aae4244`. First map type beyond the ground compound, and the first
implementation of the **corridors-as-first-class** story
([`../stories/corridors-first-class.md`](../stories/corridors-first-class.md)).
Proves the rooms-and-corridors model end to end as a `StationRecipe` over the
now-complete context/stage/recipe pipeline — additive, no new orchestrator.

## The inversion

The city is open-with-obstacles (everything walkable; walls are the exception).
The station is enclosed-with-passages: **default-solid hull, rooms + corridors
carved walkable**, and connectivity comes *only* from explicit corridors. That
inversion — not the leaf shape — is what the city idiom couldn't transfer, and
it's expressed entirely as recipe membership + a few domain stages.

## What landed

### Recipe (`BspCityGenerator.buildStationRecipe`, run via `generateStation(w,h,seed)`)

`InitSolid → StationPartition → RoomCarve → Corridor → StationSpawn →
TacticalLink → Finalize`. The last two are reused **verbatim** (no tactical
nodes yet → empty `TacticalMap`; finalize tags the un-carved hull as wall, seeds
wall HP, bakes cover, flood-fills interior buildings).

### New stages (`battle.world.gen.bsp.stage`)

| Stage | Role |
| --- | --- |
| `InitSolidStage` | every cell non-walkable `INDOOR` — inverse of `InitFloorStage` |
| `StationPartitionStage` | **reuses `Bsp.partition` verbatim**; the road-strip mask is just ignored (becomes inter-room hull wall) |
| `RoomCarveStage` | one room per BSP leaf carved walkable (leaf = room for v1; no inset / subdivision) |
| `CorridorStage` | the core — see below |
| `StationSpawnStage` | marine/defender at the room-graph **diameter** endpoints (double BFS over `StationGraph`) |

### `CorridorStage` — the connective spine

- Rooms = vertices of `LeafAdjacency.compute(...)` (reused; deterministic sorted
  order). Corridors = a carved subset of its edges:
  - **Spanning tree** via deterministic BFS from room 0 (guarantees no islands;
    adjacency-orphaned leaves get a nearest-center fallback edge).
  - **Sparse loops** — `roomCount / 10` extra non-tree edges, RNG-shuffled, for
    alternate / flanking routes (the user-chosen "tree + sparse loops" spine).
- **Carve** each chosen edge as a 2-wide Manhattan-L between room centers, but
  **only solid cells are converted** to walkable `RoomPurpose.CORRIDOR`
  (`GroundKind.STRIPED` placeholder). Cells already inside a room are left
  untouched, so the corridor materializes only in the wall gap and the doorway
  lands exactly where the path crosses a room boundary — the stage owns both
  endpoints, which dissolves the doorway-coordination problem. Straight runs are
  degenerate Ls.
- **Publishes the graph** (the story's non-negotiable): `StationGraph` (room
  nodes + corridor edges + degree) bound under `BspKeys.STATION_GRAPH` — the
  structure downstream passes will *query* instead of re-deriving geometry.

### Supporting additions

- `RoomPurpose.CORRIDOR` (additive enum value; `ordinal()+1` byte storage is
  non-breaking).
- `BspKeys.STATION_GRAPH`; `StationGraph` value object.
- `BspCityGenerator.generateStation(...)` entry; the `generate()` assembly tail
  factored into a shared `assembleResult(ctx)` (overlays a recipe didn't produce
  read back null and degrade gracefully — `RoadGraph.EMPTY`, empty compounds).

## Deviation from the written story (step 1)

The story's step 1 was "extract road carving out of `Bsp.partition()` into a
stage, keep the city byte-identical." **Not done, deliberately** — that
extraction is code-sharing elegance, not a functional prerequisite. The station
recipe simply *ignores* `Bsp.partition`'s `roadCells` mask (a `boolean[][]` it
never makes walkable), so the city path is untouched and there's no
byte-identical gate to defend. The extraction may never be needed: the city's
open-with-obstacles connectivity is genuinely different from the station's.

## Decisions locked with the user

- **Entry point:** minimal `generateStation()` now — no `MapGenerator`-interface
  churn until a *production* caller (battle setup) selects stations.
- **Spine:** tree + sparse loops (vs. pure tree) — alternate routes from day one.

## Verification (gut-check harness reused)

- `BspMapPreviewTest.renderStationBatch` → `build/map-previews/station-*.png`
  (hull black, rooms beige, corridors striped-yellow tinted cyan, spawns
  diamonds). Eyeball confirms discrete rooms, every room corridor-reached, no
  islands, visible loops, spawns in distinct rooms.
- `MapValidationScanTest.scanStationBatch` hard-asserts **one** walkable
  component (no islands) + marine→defender pathable via the real
  `GridPathfinder`. All 6 seeds (1/42/100/777/1234/9999, 80×80): 1 cell/edge
  component, ~3.5k walkable cells, defender reachable. Cell-walls + cell-corridors
  keep the edge scan the armed no-op it is for the city (edge-doors are later).
- Map-gen + station tests green. (Three unrelated GOAP/decision failures at the
  time of commit trace to a sibling session's in-flight `Unit.java` registry-null
  edit, not this work.)

## Follow-ups (next slices)

- **Station thematic room kinds** (`HANGAR / COMMAND / HABITATION / CORRIDOR`
  set) — the [`station-interior-fills`](../stories/station-interior-fills.md)
  story; sits on top of the topological role.
- **Topological roles on `StationGraph`** — depth-from-entry (indoor assault
  gradient), articulation points / bridges, on-spine vs on-loop. Degree is
  already free; the rest is the next layer placement passes will query.
- **Spawn spatial spread** — diameter endpoints are *graph*-far, occasionally
  *spatially* close. Could tie-break `StationSpawnStage`'s double BFS by
  euclidean distance, or seed it from a spatial extreme.
- **Junction-bulge widening** + the combat/transit width policy
  (degree-≥3 → 4–6-wide arena, no hold-nodes on degree-2 cells); promote the
  scan's cramped-garrison finding to a hard assert once hold nodes exist.
- **Edge-based doors** — arms `MapValidationScanTest`'s edge-connectivity scan
  (currently a no-op because walls + corridors are cell-based).
- **Hull wall toughness / tuning** — finalize currently gives hull cells the
  default 100 wall HP; stations may want tougher (or indestructible) hull.
- **Battle-setup wiring** — when a production caller selects stations, that's the
  point to weigh a `MapArchetype` selector on the `MapGenerator` interface.
