# Tiled navigation derivations

Status: IN PROGRESS

Written: 2026-09-03

## Why

`NavigationGrid.topologyRevision()` says *that* the map moved. It does not say
*which* cell moved, so every derivation of the grid answers a one-cell wall
breach by sweeping all 188,160 cells of a 560x336 map again:

- `VehicleClearanceCache` re-erodes the whole clearance mask and re-floods
  `ClearanceComponents` on the first convoy dispatch after any change;
- `NavigationService.flushNavigationTopologyIfDirty` rebuilds
  `GreedyNavigationMesh` from scratch — a full row-major largest-rectangle scan
  — on every tick that had a breach or demolition, and clears the vantage-point
  cache and every retained shared-goal field wholesale.

The zone graph is the one derivation already incremental
(`ZoneGraph.applyCellsOpened`). `CellTopology` already carries the shape the
others want: a ring-buffer change log a consumer catches up on cell by cell, or
rebuilds from when it has fallen too far behind.

Most of the map does not change when one area mutates. Tile the derivations and
recompute only the tiles a change touched.

## Plan

1. **Measure, and give the flush its own lap.** `TickProfile.Phase.NAV_FLUSH`
   split out of `ZONE_GRAPH`, so the mesh rebuild stops hiding inside the
   incremental zone-graph number.
2. **A changed-cell log on `NavigationGrid`** with the `CellTopology` contract:
   `changeCount()`, `changedCellAt(seq)`, `changeLogCapacity()`. Every write
   that bumps the revision records its cell; `clear()` records "more than the
   log holds" rather than 188k entries.
3. **Local recompute for the clearance mask and the cost field.**
4. **Tile-local component labels with a boundary union-find.**
5. **Tile the greedy navigation mesh.**

## Measurements

Probe: `tick_profile_spike_2010.fixture.json` (560x336 Conquest, 188,160
cells), 2,500 ticks forced-serial, five deliberate wall breaches on top of the
natural ones. Per topology-moving tick: the `ZONE_GRAPH` lap, the `NAV_FLUSH`
lap, and the cost of the next convoy clearance rebuild (radius-1 chassis, mask
plus component labels).

### BEFORE (at `2bcdcde15`, NAV_FLUSH split out)

Cold clearance + labels: **13.28 ms**.

| tick | rev delta | cell changes | zone | navFlush | clearance catch-up |
|---|---|---|---|---|---|
| 201 | 1 | 3 | 0.737 | 10.434 | 10.755 |
| 270 | 8 | 12 | 0.629 | 17.425 | 4.116 |
| 315 | 1 | 1 | 0.628 | 2.337 | 4.014 |
| 501 | 1 | 5 | 0.626 | 2.284 | 3.922 |
| 808 | 2 | 1 | 18.980 | 2.705 | 3.805 |
| 901 | 1 | 3 | 0.912 | 4.620 | 5.077 |
| 1401 | 1 | 3 | 0.693 | 2.796 | 4.008 |
| 2001 | 1 | 3 | 0.574 | 2.540 | 3.493 |
| 2209 | 1 | 3 | 0.559 | 3.142 | 3.619 |
| **total** | | | **24.339** | **48.282** | **42.809** |

Steady state, discounting the first two ticks' JIT: **navFlush 2.3–3.1 ms** and
**clearance catch-up 3.5–4.1 ms**, for a breach that moved between one and five
cells. The tick-808 zone figure is a full `ZoneGraph.rebuild()` — the
cell-less dirty mark's path — and belongs to the zone graph rather than here.

### AFTER

Filled in per commit as the work lands.

## Acceptance

- On a tick with a wall breach on the 560 map: flush in low single-digit
  milliseconds or less; convoy clearance catch-up well under a millisecond.
- Byte-identical convoy outcome on the fixture: 1 COMMITTED, drop (263,218) at
  tick 2195.
- `simDeterminism` green; full `:test` green.
- `commanderEvidence -Pmission=conquest -PmaxTicks=6000` within one capture /
  one held of the same-machine control at `2bcdcde15` (reinforced-south 7/6/32
  marine losses/196 defender losses; full-strength-west 6/5/109/248).
