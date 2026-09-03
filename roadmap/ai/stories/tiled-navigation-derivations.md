# Tiled navigation derivations

Status: IN PROGRESS

Written: 2026-09-03

Updated: 2026-09-03 — all five steps built; the tiled mesh awaits its
determinism and Conquest evidence before the story folds.

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
2. **DONE.** A changed-cell log on `NavigationGrid` with the `CellTopology`
   contract: `changeCount()`, `changedCellAt(seq)`, `changeLogCapacity()`.
   Every write that bumps the revision records its cell; `clear()` records
   "more than the log holds" rather than 188k entries.
3. **DONE.** Local recompute for the clearance mask.
   `VehicleClearanceCache.clearance` catches up from the log — one
   `VehicleClearance.copyOf` clone plus a `refreshAround` per logged cell —
   and falls back to a full `erode` only when `NavigationGrid.hasCaughtUpFrom`
   says the reader has fallen behind the log. The cost field
   (`ConvoyMeans.costField`, from `TerrainCostField`) was investigated and
   left alone: it derives from `CellTopology`'s `GroundKind`, which this log
   does not carry, and its own staleness is pre-existing and documented as
   deliberate.
4. **DONE.** Tile-local component labels with a boundary union-find.
   `ClearanceComponents` labels each 32x32 tile on its own and unites the
   local labels across every adjacency that crosses a tile seam; a catch-up
   relabels only the tiles holding a cell within the chassis radius of a
   change and re-runs the seam union. The whole-map flood survives as the
   tests' oracle (`floodOf`).
5. **DONE.** Tile the greedy navigation mesh. `GreedyNavigationMesh` keeps a
   cover per 32x32 tile (regions never cross a tile edge) and the transitions
   along each seam between two tiles; `rebuild()` re-covers only the tiles
   holding a logged cell, re-derives the seams touching them, and assembles
   the snapshot from every tile's retained cover. Adjacency and transition
   indexes are counted into flat arrays rather than boxed lists, which was
   most of the assembly.

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

### AFTER step 2 (`NavigationGrid` changed-cell log added, nothing consumes it yet)

Re-run to re-establish the baseline after the earlier transcript was lost; same
probe, same fixture. Timings move tick to tick with JIT and OS noise (compare
`navFlush`/`zone` to the table above, which nothing here touched), but the
shape is identical: nothing yet reads the log, so `clearanceCatchUp` is still a
full `erode` + full `ClearanceComponents.of` every time.

| tick | rev delta | cell changes | zone | navFlush | clearance catch-up |
|---|---|---|---|---|---|
| 201 | 1 | 3 | 0.737 | 11.370 | 9.305 |
| 289 | 8 | 12 | 0.653 | 4.398 | 3.683 |
| 398 | 2 | 3 | 0.575 | 3.309 | 3.254 |
| 501 | 1 | 5 | 0.550 | 2.748 | 3.286 |
| 802 | 8 | 12 | 0.571 | 2.556 | 3.248 |
| 901 | 1 | 3 | 0.576 | 2.569 | 3.240 |
| 914 | 2 | 3 | 0.564 | 2.538 | 3.308 |
| 1401 | 1 | 3 | 0.581 | 2.539 | 3.233 |
| 2001 | 1 | 3 | 0.622 | 2.686 | 3.308 |
| **total** | | | **5.429** | **34.712** | **35.866** |

`clearanceBuilds 10 componentBuilds 10` — cold plus a full rebuild on every one
of the 9 topology-moving ticks, exactly the pre-existing behaviour.

### AFTER step 3 (`VehicleClearanceCache` catches up the mask from the log)

`VehicleClearance.erode`'s 3x3-per-cell footprint sweep is gone from the steady
state: `clearanceBuilds` across the whole 2,500-tick run is **1** (the cold
build only) against the 10 it was before, and a wreck's own neighbourhood test
(`VehicleClearanceCacheCatchUpTest`) confirms the catch-up re-evaluates exactly
the `(2r+1)^2` cells a change could have flipped — 9 for this radius-1 chassis
— not the 188,160 an erosion touches.

| tick | rev delta | cell changes | zone | navFlush | clearance catch-up |
|---|---|---|---|---|---|
| 201 | 1 | 3 | 0.995 | 15.349 | 9.284 |
| 501 | 1 | 5 | 0.573 | 5.010 | 3.284 |
| 802 | 8 | 12 | 0.645 | 2.843 | 2.591 |
| 824 | 9 | 13 | 0.633 | 3.178 | 2.708 |
| 901 | 1 | 3 | 0.668 | 7.304 | 11.439 |
| 913 | 2 | 3 | 0.621 | 3.056 | 2.775 |
| 1401 | 1 | 3 | 0.607 | 4.058 | 3.566 |
| 2001 | 1 | 3 | 0.643 | 2.820 | 2.627 |
| **total** | | | **5.385** | **43.617** | **38.274** |

`clearanceBuilds 1 componentBuilds 9`.

**The `clearance catch-up` column has not dropped yet, and that is the correct
reading, not a null result.** `VehicleClearanceCache.components` still calls
`ClearanceComponents.of(grid, mask)` — a whole-map flood — every time the mask
moves, per this story's own step split; step 3 was scoped to the mask alone.
The probe measures `cache.components(...)`, so its wall-clock is dominated by
that still-full labelling pass exactly as before, even though the mask
underneath it is now nearly free. `clearanceBuilds` collapsing from 10 to 1
(confirmed independently by `VehicleClearanceCacheCatchUpTest`'s evaluation
count) is the honest measure of what step 3 shipped; step 4 is what should
move this column.

`navFlush` and `zone` are unrelated to this step (nothing here touches
`GreedyNavigationMesh` or the zone graph) and their tick-to-tick movement
against the step-2 table is measurement noise — JIT warmup and scheduler
variance on ticks that land at different points in the fixture's own event
timing, not a code effect.

Two design decisions worth recording here rather than only in code comments:

- **The catch-up clones the mask before patching it
  (`VehicleClearance.copyOf`) rather than mutating the cached instance in
  place.** `ConvoyMeans.dispatch` stores the proved `RoutePlan`'s clearance
  into `VehicleMission.routeClearance` for the recovery ladder to re-route
  against for the whole of a vehicle's drive — many ticks past the proof job
  that built it — so a later catch-up must not retroactively rewrite a mask
  another live object is already holding as "what this route was proved
  against". A `RouteProofJob` itself does not need this protection: both
  `ConvoyMeans.advance` and `ConvoyMeans.dispatch` already discard a job the
  moment `job.gridRevision() != revision` (`ConvoyMeans.java:286,308`) rather
  than replaying it against a moved grid, so no in-flight job ever observes a
  post-catch-up mutation of the mask it started with. The clone protects the
  longer-lived `VehicleMission` holder, not the proof job.
- **`ConvoyMeans`'s cached `TerrainCostField` is left exactly as it was —
  built once, never invalidated — and deliberately does not catch up from this
  log.** `TerrainCostField.from` reads `CellTopology`'s `GroundKind` array;
  `NavigationGrid`'s changed-cell log (this story's subject) names cell flags
  and edges only and carries nothing about ground kind. There is nothing here
  for it to catch up from. `CellTopology` keeps a change log of its own
  (`CHANGE_LOG_CAPACITY`, same contract) if that staleness is ever worth
  closing — see the Javadoc on `ConvoyMeans.costField`.

### AFTER step 4 (`ClearanceComponents` labelled in tiles, seams united)

The whole-map flood is gone from the steady state. Each 32x32 tile is labelled
on its own; a union-find over the adjacencies that cross tile seams joins the
local labels into the map's components; a catch-up relabels only the tiles
holding a cell within the chassis radius of a change and re-runs the seam
union. On the 560x336 map: **198 tiles, 25,356 seam adjacencies, 392
components.**

| tick | rev delta | cell changes | zone | navFlush | clearance catch-up |
|---|---|---|---|---|---|
| 201 | 1 | 3 | 0.676 | 6.244 | 0.858 |
| 270 | 8 | 12 | 0.700 | 4.840 | 0.659 |
| 315 | 1 | 1 | 0.568 | 2.669 | 0.754 |
| 501 | 1 | 5 | 0.734 | 2.955 | 2.448 |
| 808 | 2 | 1 | 25.957 | 3.553 | 0.529 |
| 901 | 1 | 3 | 0.589 | 2.630 | 0.532 |
| 1401 | 1 | 3 | 0.608 | 2.649 | 0.546 |
| 2001 | 1 | 3 | 0.614 | 2.607 | 0.495 |
| 2209 | 1 | 3 | 0.560 | 2.514 | 0.490 |
| **total** | | | **31.005** | **30.660** | **7.310** |

`clearanceBuilds 1 componentBuilds 1` — cold only; every breach caught up.
The catch-up column reads **0.49–0.86 ms** in the steady state against
3.2–11.4 ms before. Where it goes, best of thirty on a three-cell change with
the JIT warm: mask catch-up 0.011 ms, label catch-up 0.421 ms with one tile
relabelled, against 2.714 ms for a warm full relabel (the 8–11 ms figures in
the earlier tables were partly cold code). Cloning the map's label and mask
arrays costs 0.045 ms; nearly all of the rest is the seam walk — some 200,000
step checks to find the 25,356 adjacencies that unite.

**Follow-up, not done here:** the seam adjacency list is a property of the
mask along each seam segment and only changes where a dirty tile touches it,
so it could be cached per segment and only the dirty segments re-derived,
leaving a catch-up to run 25k unions with no grid checks — an estimated 0.4 ms
to about 0.15. Left for after the mesh, which is the larger remaining number.

**The tick-808 zone figure is the largest number in every table and is not
this story's.** It is a full `ZoneGraph.rebuild()` on the cell-less dirty
path (`zoneForceFullRebuild`); a 26 ms hitch on a breach tick belongs on the
board in its own right.

### AFTER step 5 (`GreedyNavigationMesh` covered in tiles, seams re-derived)

Same probe. The battle diverges from the earlier tables past the first breach
because the mesh's finer cover changes which corridor the hierarchical search
accepts, so the breach ticks differ; compare the columns, not the rows.

| tick | rev delta | cell changes | zone | navFlush | clearance catch-up |
|---|---|---|---|---|---|
| 201 | 1 | 3 | 0.928 | 7.602 | 1.302 |
| 270 | 8 | 12 | 0.632 | 3.465 | 1.047 |
| 377 | 2 | 3 | 0.593 | 0.984 | 0.768 |
| 501 | 1 | 5 | 0.604 | 1.004 | 0.698 |
| 802 | 8 | 12 | 0.614 | 1.049 | 0.623 |
| 901 | 1 | 3 | 0.550 | 0.851 | 0.523 |
| 1401 | 1 | 3 | 0.648 | 0.778 | 0.646 |
| 1661 | 1 | 1 | 0.586 | 0.768 | 0.577 |
| 2001 | 1 | 3 | 0.547 | 0.852 | 0.522 |
| 2428 | 1 | 4 | 0.577 | 0.612 | 0.489 |
| **total** | | | **6.277** | **17.964** | **7.195** |

`navFlush` steady state **0.61–1.0 ms** against 2.5–3.1 before the mesh was
tiled (and 2.3–3.1 at the story's start). What remains in it is the snapshot
assembly — fresh dense region ids and their adjacency over every region on
the map, a few thousand records — plus the vantage-point cache clear and the
shared-goal field invalidation, neither of which is measured on its own here.

## Acceptance

- On a tick with a wall breach on the 560 map: flush in low single-digit
  milliseconds or less; convoy clearance catch-up well under a millisecond.
- Byte-identical convoy outcome on the fixture: 1 COMMITTED, drop (263,218) at
  tick 2195.
- `simDeterminism` green; full `:test` green.
- `commanderEvidence -Pmission=conquest -PmaxTicks=6000` within one capture /
  one held of the same-machine control at `2bcdcde15` (reinforced-south 7/6/32
  marine losses/196 defender losses; full-strength-west 6/5/109/248).
