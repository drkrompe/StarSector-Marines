# Zone graph: catch up from the change log instead of rebuilding whole

Status: DRAFT

Written: 2026-09-03

## Why

The tiled navigation derivations (see the `battle.nav` package charter) took
the clearance mask, the clearance components, and the navigation mesh off the
whole-map path: a breach now costs each of them well under a millisecond. The
one whole-map derivation still standing is `ZoneGraph.rebuild()`, and it is
now the largest single number on a breach tick.

Measured on the production Conquest fixture the convoy stall was traced from
(`tick_profile_spike_2010.fixture.json`, forced serial), tick 808 spent 26 ms
in a full `ZoneGraph.rebuild()` reached through the cell-less dirty path
(`zoneForceFullRebuild`): a caller marked the graph dirty without naming a
cell, so the rebuild could not scope itself and re-detected every zone on a
560x336 map.

## Scope

- Find every caller of the cell-less dirty path and make each name the cells
  it changed, so the rebuild has something to scope to.
- Give the zone detector a tiled or region-scoped catch-up that re-detects
  only the zones a changed cell touches and re-links their edges, the way
  `ClearanceComponents.catchUp` and `GreedyNavigationMesh.rebuild` do; a
  reader that has fallen behind the log still rebuilds whole.
- Doorway cells stay zone-less, and a zone's identity across a catch-up is
  whatever the detector already promises; this story must not widen that.

## Acceptance

- A unit test that mutates a random city cell by cell and asserts the
  caught-up graph partitions the map identically to a fresh detection.
- The same fixture replayed serially shows no `ZoneGraph.rebuild()` above
  ~2 ms on a breach tick, read from the tick profile's own lap.
- `simDeterminism` byte-identical; full `:test` green.
