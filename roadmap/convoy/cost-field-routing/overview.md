# Cost-Field Vehicle Routing

> Replace the road-**graph** convoy router with cost-field grid search +
> string-pulling. Roads stop being a topology vehicles are confined to and
> become a *bias* over the free grid. Substrate for the route layer that feeds
> [`../navigation-rework/`](../navigation-rework/overview.md) — this produces
> the advisory corridor; that tracks it.

## The problem

Convoy routing plans over a `RoadGraph` (BFS between road-centerline nodes →
`expandToWaypoints` → coarse cell polyline). That hamstrings vehicles:

- They can only travel the extracted centerline graph. Open ground, plazas,
  courtyards, parking aprons, breaches — none of it is routable, even when it's
  the obvious shortcut a driver would take.
- The graph imposes a **topology a porous city doesn't have**. We already
  concluded this for tactics (memory `[[mapgen_texture_not_topology]]`: "porous
  city has no real chokepoints; city reads space *texture*, not connectivity
  graphs; graph framing is station-only"). Routing inherited the wrong frame.
- Route quality is hostage to graph-build quality (centerline extraction,
  trunk-exit filtering — `[[road_graph_design]]`). Gaps in the graph =
  un-routable destinations.

## The reframe

**Roads are cheap cells, not a graph.** Every walkable cell has a traversal
cost; roads cost least, so search *prefers* them, but a vehicle can cross any
walkable terrain when it genuinely pays. Plan with a cost-weighted grid search,
then **string-pull** the jagged cell path into a clean advisory polyline.

This swaps only the **top box** of the navigation stack. The route layer feeds
a polyline corridor; everything below it —
`ReferenceCorridor → LocalTrajectoryPlanner → VehicleController` — already
consumes a polyline and is **unchanged**. We just stopped depending on that
corridor for kinematics in the navigation-rework, so this is a clean, isolated
replacement of its *source*.

```
GroundKind per cell ─► TerrainCostField   STREET≈1.0 baseline, BRICK/STONE≈1.5,
        │                                 GRASS/DIRT/SAND≈3, RUBBLE≈5  (multiplicative)
        │              VehicleClearance    walkable eroded by footprint radius —
        │                                 cells where the truck actually fits
        ▼
cost-weighted grid A*    entry→dest (and dest→exit) over the clearance mask,
        │                weighted by the cost field; hugs roads, cuts across
        │  jagged cells   open ground only when it saves real distance
        ▼
string-pull / funnel     clearance-aware LOS simplification → sparse polyline
        │
        ▼
ReferenceCorridor → LocalTrajectoryPlanner → VehicleController   (UNCHANGED)
```

## Why this is mostly wiring (the infra already exists)

- **`GridPathfinder`** (`battle/nav`) is a zero-alloc 8-dir A* on the cell grid
  with a ThreadLocal flat-array workspace and an indexed binary heap. It
  **already has a per-cell extra-cost hook** — the `occupancy` `byte[]` adds
  `OCCUPANCY_PENALTY × count` to each step. A terrain-cost field slots into the
  same seam (generalize "occupancy penalty" → "per-cell step multiplier").
- **`CellTopology.GroundKind`** (`battle/world/model`) already classifies every
  cell: `STREET, SIDEWALK, COURTYARD, GRASS, DIRT, STONE, SAND, SNOW, WATER,
  TILE, BRICK, STRIPED, LZ_MARKER, RUBBLE`. The cost field is a lookup table
  over this enum — no new per-cell tagging.
- Water is already non-walkable on the nav grid, so it's excluded for free.

## Decisions (locked)

- **Clearance: erode a per-vehicle-class mask.** Precompute a "vehicle-walkable"
  mask — walkable cells eroded by ~the footprint radius — and run the cost-A\*
  and string-pull LOS on *that*, so a route can never thread a gap the truck
  can't fit. This is the `clearance map` `NavigationGrid` originally dropped (see
  its header). Correct-by-construction beats routing into a pinch and leaning on
  recovery (which isn't built until navigation-rework slice 3). Erosion model:
  start with footprint **half-width** (the truck can align its length down a
  corridor); a cell is vehicle-passable iff all cells within that radius are
  walkable. Tune in the feel slice.
- **Multiplicative cost, baseline 1.0.** Off-road kinds cost `> 1.0`; roads are
  the `1.0` baseline (not `< 1.0`), so the octile heuristic stays admissible and
  A\* stays optimal. (A sub-1.0 road cost would need the heuristic scaled by the
  min cell cost.)
- **RoadGraph: bypass now, retire after audit.** Cost-field becomes the router
  immediately. RoadGraph + `RoadGraphBuilder` + `ConvoyPlanner.planPath` /
  `expandToWaypoints` / `pickExitNode` stay in place until a consumer audit
  (rendering? debug overlays? other systems?) confirms nothing else needs them;
  then the dead routing code is deleted in its own slice. Roads-as-cells (the
  `GroundKind.STREET` cost bias) is all routing needs going forward.

## Component inventory

New (all in `battle/vehicle`, alongside `ConvoyPlanner`):

- **`TerrainCostField`** — per-cell `float` (or scaled `byte`) traversal cost,
  baked from `CellTopology.GroundKind`. Pure; built once per battle.
- **`VehicleClearance`** — per-vehicle-radius eroded walkable mask + the erosion
  build. Pure; one mask per distinct footprint radius (cache by radius).
- **`VehicleRoutePlanner`** — `route(startCell, goalCell, costField, clearance,
  grid)` → cell path. Cost-weighted A\* (reuse `GridPathfinder`'s machinery via a
  cost-array overload, or a dedicated planner if the seam is awkward). Includes
  the string-pull → polyline step (or delegates to a `PathSimplifier`).
- **`PathSimplifier`** (maybe folded into the planner) — clearance-aware LOS
  string-pull: keep a vertex only where a straight segment to the next would
  clip a non-clearance cell.

Reworked / removed:

- **`GridPathfinder`** — gains a per-cell cost-array overload (generalizes the
  occupancy hook). Infantry callers keep the existing signatures.
- **`ConvoyPlanner.planPath` / `expandToWaypoints` / `pickExitNode`** — bypassed
  at the spawn sites, retired after the audit slice. `RoadGraph` /
  `RoadGraphBuilder` likewise.
- **Entry/exit selection** — graph-node picking (`pickExitNode`) becomes
  cell-based: dest = LZ/rally cell, exit = farthest reachable perimeter cell
  (flood the clearance mask), entry = the existing perimeter cell. Off-map
  staging/exit prepend/append is unchanged.

Unchanged: the entire tracking stack (`ReferenceCorridor`,
`LocalTrajectoryPlanner`, `VehicleController`, `BicycleBody`, `PurePursuit`,
`ReedsShepp`, `VehicleFootprint`).

## Story decomposition

Each slice leaves the game working.

| # | Story | What it lands |
| --- | --- | --- |
| 0 | [`slice-0-cost-and-clearance`](stories/slice-0-cost-and-clearance.md) | `TerrainCostField` (GroundKind→cost) + `VehicleClearance` (eroded mask). Pure, unit-tested against hand-built fixtures. Not wired. |
| 1 | [`slice-1-route-planner`](stories/slice-1-route-planner.md) | `VehicleRoutePlanner`: cost-weighted grid A\* over the clearance mask + clearance-aware string-pull → polyline. `GridPathfinder` cost-array overload. Unit-tested standalone. Not wired. |
| 2 | [`slice-2-wire-spawn`](stories/slice-2-wire-spawn.md) | Swap `ConvoyMeans` + `BattleSetup` route construction to the cost router; cell-based entry/exit. Corridor stack untouched. **Vehicles route off-road here.** Playtest. |
| 3 | [`slice-3-retire-roadgraph`](stories/slice-3-retire-roadgraph.md) | Audit RoadGraph consumers; delete the now-dead graph routing (`planPath`/`expandToWaypoints`/`pickExitNode`, `RoadGraphBuilder`, `RoadGraph`) if unused. |
| 4 | [`slice-4-tune`](stories/slice-4-tune.md) | Cost weights per `GroundKind`, erosion radius, string-pull aggressiveness, optional cost-aware pull. Playtest pass; may fold into navigation-rework slice 4. |

### Why this order

0 builds the two pure inputs where they're testable in isolation. 1 builds the
planner on top, still pure. 2 is the payoff — the spawn sites switch routers and
vehicles can finally cross open ground; it's the only behavioral slice. 3 is
cleanup gated on an audit. 4 is feel.

## Open questions

- **Cost-aware string-pull?** Plain geometric LOS pull straightens across open
  ground, partly undoing the road bias at the micro scale (a truck may cut a
  lawn corner at an intersection). Probably fine and natural for V1 — the bias
  shapes *which streets* (macro); corner-cutting reads as real driving. Add a
  cost-increase threshold to the pull only if trucks visibly slum across terrain.
  Decide in slice 4.
- **Cost field rebake on terrain change?** Walls→rubble (`NavigationGrid.damageCell`)
  flips a cell walkable; the clearance mask and (optionally) cost field around it
  go stale. The macro route is computed once at spawn, so a mid-route breach
  isn't picked up by the macro layer — but the rolling `LocalTrajectoryPlanner`
  already replans against the live grid every tick, so it adapts locally. Full
  macro re-route on breach is a recovery-ladder concern (navigation-rework
  slice 3), not this track. Note and defer.
- **Per-cell cost storage.** `float[]` (simple) vs scaled `byte[]` (cache-friendly,
  matches the occupancy hook). Lean `byte[]` for the hot A\* loop; decide in
  slice 1.
- **Diagonal cost across kind boundaries.** A diagonal step between two kinds —
  average the two cell costs, or take the destination's? Destination-cost is
  simplest and matches the occupancy model. Slice 1.

## Cross-references

- [`../navigation-rework/overview.md`](../navigation-rework/overview.md) — the
  tracking stack this feeds; its `ReferenceCorridor` is this track's output sink.
- [`../overview.md`](../overview.md) — the convoy feature both sit beneath.
- Memory: `[[mapgen_texture_not_topology]]` (the reframe's basis),
  `[[road_graph_design]]` (what we're retiring), `[[spatial_unit_index]]`,
  `[[zone_graph_ignores_edges]]` (don't use ZoneGraph for reachability —
  flood the clearance mask instead).

## How this directory is laid out

- **`overview.md`** (this file) — concept, decisions, decomposition.
- **`stories/`** — active/queued slice docs.
- **`complete/`** — sealed shipped slices (commit hash + what landed).
- **`next-session.md`** — handoff state for picking up cold.
