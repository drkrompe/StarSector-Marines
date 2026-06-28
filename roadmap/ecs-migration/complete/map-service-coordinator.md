# Story: MapService coordinator — own the generation + runtime-modification cycles

> **SHIPPED (Slice 1; Slice 2 deferred).** Slice 1 (`c49eea7`): `MapService` (in
> `battle.world`) owns the runtime map-modification cycle — `damageWall` / `destroyRoof`
> / `peelRoofAround` / `flipCellToRubble` + the roof-collapse FX sink — lifted off
> `NavigationService`; `CellTopology` stayed a pure data holder. Slice 2 (folding the
> generators' grid+topology population into `MapService`) was an **optional stretch, not
> pursued** (larger surface, lower smell); reopen only if that seam proves worth it.

## Context

`NavigationService` currently owns map-mutation behavior that isn't
navigation. `damageWall`, `destroyRoof`, `peelRoofAround`, and
`flipCellToRubble` ([`NavigationService.java`](../../../src/main/java/com/dillon/starsectormarines/battle/nav/NavigationService.java))
write **topology** state — wall tags, `GroundKind.RUBBLE`, `ROOF_DESTROYED`
— through `CellTopology`, which is a pure per-cell data holder (arrays +
typed getters/setters, no behavior beyond the `tagDefaultWalls` bulk
sweep — see [`CellTopology.java`](../../../src/main/java/com/dillon/starsectormarines/battle/map/CellTopology.java)).
So the topology-mutation *logic* lives one class away from the topology
*data*, on the navigation service, because the same operations also flip
grid walkability and the zone-graph dirty flag (which genuinely are nav's).

These ops are inherently **cross-domain**. A wall coming down is:

- grid walkability opens + edges open (nav — `grid.damageCell` /
  `setWalkable` / `openAllEdges` / `recomputeCoverAt`),
- the WALL tag clears, `GroundKind` → `RUBBLE`, adjacent roof cells get
  `ROOF_DESTROYED` (topology),
- the zone graph is marked dirty for end-of-tick rebuild (nav),
- a rubble decal spawns at the cave-in (FX, via the `roofCollapseSink`).

Neither service is the natural owner of the *whole* operation — which is
the smell. And generation has the same shape: `UrbanMapGenerator` /
`BspCityGenerator` carve walkability into the grid **and** write topology
(ground kinds, walls, building ids), then call `topology.tagDefaultWalls(grid)`.
The grid+topology pair is built and mutated together, by whoever happens
to hold both references.

## Proposed shape

Introduce a **`MapService`** that owns the map lifecycle and coordinates
the two domain services, delegating to each for its own slice rather than
holding cell state itself:

- **`NavigationService`** keeps its real domain: grid walkability,
  occupancy map, spatial indices, the zone graph + dirty flag,
  LOS/vantage cache, and `setPath`/`clearPath`. It exposes the walkability
  primitives MapService sequences (`grid.damageCell`, `flipCell*`
  walkability side, `markZoneGraphDirty`).
- **`CellTopology`** keeps owning per-cell topology data. Whether it stays
  a data holder that MapService mutates directly, or is promoted to a
  `CellTopologyService` that owns the topology-mutation *behavior*
  (`setRoofDestroyed` + a `peelRoof`/`wall→rubble` transition method), is
  the open sub-decision below.
- **`MapService`** owns the cross-domain orchestration:
  - **Runtime-modification cycle** — `damageWall`, `damageCell`,
    `destroyRoof`, `peelRoofAround`, `flipCellToRubble`. Each sequences
    the topology write(s) + the nav walkability/zone-graph write(s) + the
    roof-collapse FX sink. The `roofCollapseSink` (a decal effect, not
    topology and not navigation) moves here — it's the cross-cutting glue
    MapService is the right home for.
  - **Generation cycle** (see scope) — accept generator output, populate
    grid + topology, run the post-carve sweeps (`tagDefaultWalls`,
    building flood-fill).

This is the **thin-coordinator** answer to the cross-domain-ownership
fork raised when path-mutation shipped: MapService is the seam where
"a wall comes down" is one call, and the two services each do their part.

## Scope

### Slice 1 — runtime-modification cycle (do this first)

Small, well-bounded. Move `damageWall` / `damageCell` / `destroyRoof` /
`peelRoofAround` / `flipCellToRubble` orchestration + the `roofCollapseSink`
onto `MapService`; NavigationService retains only its walkability +
zone-graph primitives. Consumers to repoint (~6 sites):
`Detonations` (wall damage + roof crack), `TurretDemolitionSystem` +
`HubDemolitionSystem` (`flipCellToRubble`), `FlybyOverlay` +
`BattleScreen` (`damageCell`). `BattleSimulation.damageCell` stays a thin
delegate (now → `mapService`); the demolition systems + `Detonations`,
which hold a `navigation` ref today, get a `MapService` ref injected.

### Slice 2 (stretch) — generation cycle

Larger surface: the generators and the BSP fill stampers write grid +
topology directly across many sites. Folding generation orchestration
into MapService is real churn and lower-smell than the runtime ops, so
scope it as a follow-on only if slice 1 makes the seam obviously worth it.
Name it here so it isn't lost; don't lead with it.

## Open sub-decisions (pin before coding)

1. **`CellTopology` → `CellTopologyService`?** It's currently data
   (parallel to `NavigationGrid` / `UnitRegistry`). If MapService is the
   only behavior owner, CellTopology can stay a data holder MapService
   mutates. Promote it to a service only if topology-mutation behavior
   accumulates enough to warrant its own owner — don't mint a service
   that's a one-method passthrough.
2. **Generation scope** — slice 1 only, or commit to the full lifecycle?

## Sequencing

- Independent of the SoA-promotion tail; a Service-extraction sibling to
  the shipped [`path-mutation-to-navigation`](path-mutation-to-navigation.md).
- Land **before or alongside** [`drop-sim-facade-delegators`](drop-sim-facade-delegators.md):
  MapService is a *new* service, so `sim.damageCell` becomes one more
  facade delegate. Better to introduce it before the facade cleanup
  enumerates the surface than to add a delegate the cleanup then has to
  revisit.
- Incremental + green per slice, same discipline as the SoA promotions.

## Acceptance

- `MapService` owns the runtime map-modification ops; `NavigationService`
  no longer writes topology state; `CellTopology`'s mutation is reached
  only through its owner.
- The roof-collapse FX sink lives on MapService, not NavigationService.
- `gradlew.bat compileJava` clean; full suite green.

## Priority

Coupling-reduction, no perf win — but it resolves a real ownership smell
and is a clean prerequisite for the facade-delegator cleanup. Slice 1 is
a good standalone commit.
