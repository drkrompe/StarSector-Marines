# Story: MapService coordinator — Slice 1 (runtime map-modification cycle)

**Shipped `c49eea7`** (2026-05-28). First slice of the
[`map-service-coordinator`](map-service-coordinator.md) story:
a new `MapService` (in `battle.world`) now owns the cross-domain runtime
map-modification cycle, lifting it off `NavigationService` — which should
own navigation, not topology mutation. Resolves the cross-domain-ownership
smell surfaced when path-mutation shipped.

## What landed vs. planned

- **As planned:** `damageWall` / `peelRoofAround` / `destroyRoof` /
  `flipCellToRubble` moved off `NavigationService` onto `MapService`,
  along with the `CellCallback` `roofCollapseSink` + its setter (a
  rubble-decal FX glue — neither topology nor navigation, so the
  cross-cutting coordinator is its right home).
- **Constructor shape:** `MapService(NavigationService)`. It aliases
  `grid` + `topology` from the nav service once at construction, mutates
  `CellTopology` **directly** (sub-decision 1 resolved: CellTopology stays
  a data holder, *not* promoted to a service — the mutation set didn't
  warrant minting a one-owner service), and delegates the walkability +
  zone-graph writes back to navigation via `grid.*` +
  `navigation.markZoneGraphDirty()`.
- **Consumers repointed (3):** `Detonations`, `TurretDemolitionSystem`,
  `HubDemolitionSystem` each swapped their `NavigationService navigation`
  field for a `MapService mapService` field — clean swaps because each
  used `navigation` **only** for the moved ops (verified by grep before
  touching them).
- **Sim wiring:** `BattleSimulation` builds `mapService` right after
  `navigation`; `damageCell` stays a thin delegate (now →
  `mapService.damageWall`); the roof-collapse decal sink wiring moved from
  `navigation.setRoofCollapseSink` → `mapService.setRoofCollapseSink`.
  `FlybyOverlay` + `BattleScreen` reach the breach path through
  `sim.damageCell`, so they're **unchanged**.
- **`NavigationService.markZoneGraphDirty()` stays public** — it's now
  called by MapService rather than internally; the zone-graph dirty flag +
  `flushZoneGraphIfDirty` drain remain navigation's (they're navigation
  state).

## Package home

`MapService` lives at the **top level** of `battle.world` (the map feature
domain), not in `battle.nav`. It depends on both `nav` (NavigationService,
NavigationGrid) and `world.model` (CellTopology); placing it in `world`
keeps the dependency acyclic (`nav` and `world.model` don't import the
`world` top level). The `battle.world` package-info charter was updated:
the top level is no longer a "pure container" — it hosts the MapService
coordinator, everything else stays in subpackages.

## Not in this slice

- **Slice 2 — generation cycle** (stretch): folding the
  `UrbanMapGenerator` / `BspCityGenerator` grid+topology population into
  MapService. Larger surface, lower smell; scope as a follow-on only if the
  seam proves worth it. Still named in the
  [story](map-service-coordinator.md#slice-2-stretch--generation-cycle).

## Coverage

`gradlew.bat compileJava` clean; full suite green at `c49eea7`. No tests
referenced the moved methods (they're reached only through sim/consumer
wiring exercised by the integration paths), so no new unit test — this is a
behavior-preserving relocation, not a new primitive promotion.
