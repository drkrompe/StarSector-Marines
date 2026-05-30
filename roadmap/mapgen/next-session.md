# Map generation — Next Session

Read [`overview.md`](overview.md) first for concept + north star, and
[`pipeline-audit.md`](pipeline-audit.md) for the load-bearing pipeline
survey (its file:line citations are the map for any new work). Shipped
work is sealed in [`complete/`](complete/).

## Commit chain so far

```
82c76a9  mapgen: Slice A — room-purpose labels replace zone-graph chamber inference
d3f659d  mapgen: Slice A polish — distance-indexed label contract + critique NITs
042d084  mapgen: Slice B — PartitionStrategy interface + BinaryPartitionStrategy
ee55eb0  mapgen: Slice C — TernaryPartitionStrategy + N-axis PartitionLayout
b8b7b9d  mapgen: Slice D — ternary keep wiring + multi-chamber stamper emission
5e5ae91  mapgen: Slice 1 — GenContext blackboard; collapse fill SPI signatures
09d2590  mapgen: deterministic leaf-adjacency neighbor order
53fe951  mapgen: defense-post partition guard checks real footprint, not bbox
65c5686  mapgen: Slice 2a — GenStage pipeline (extract generate() into stages)  ← latest mapgen work
```

Full per-slice mapping (what landed vs. planned, Slice A critique
findings) in
[`complete/room-purpose-refactor.md`](complete/room-purpose-refactor.md).

## State of play

- **Room-purpose refactor (Slices A–D): shipped.** Carve-time
  `RoomPurpose` labels on `CellTopology` replace post-hoc zone-graph
  chamber inference. `PartitionStrategy` (binary + ternary) +
  distance-indexed `chamberPurposesByAnchorDistance` generalize past the
  keep toward N-chamber buildings.
- **Player-visible result:** the central keep now has up to two interior
  garrisons (KEEP_INNER priority 65 / garrison 4, KEEP_ENTRY priority 60
  / garrison 3) instead of one. Slices A–C were behaviour-preserving seam
  work.
- `KeepEntryChamberStamper` no longer builds a transient `ZoneGraph`;
  it reads `topology.getRoomPurpose(x, y)` directly.
- Build green; `BuildingShellCoreLabelTest` + ternary 50-seed coverage
  pass. Diagnostic preview at
  `build/zone-previews/ternary-partition-labels.png`.

## Next up (priority order)

The current track is the **composable generation pipeline** — decompose
`BspCityGenerator.generate()` into context + stages + recipe so map types
compose instead of forking. Design agreed (typed-blackboard `GenContext`,
incremental rollout). Design doc:
[`composable-pipeline.md`](composable-pipeline.md).

- **Slice 1 — `GenContext` + fill-SPI collapse: ✅ shipped (`5e5ae91`).**
  `GenContext` / `GenKey<T>` / `BspKeys` landed; `BlockFiller` (6-arg) and
  `CompoundFiller` (9-arg) now take `(…, ctx)`. See
  [`complete/gen-context.md`](complete/gen-context.md).
- **Slice 2a — `GenStage` pipeline: ✅ shipped.** Each numbered `generate()`
  step is now a `GenStage` class under `bsp/stage/`; `generate()` builds `ctx`,
  runs an ordered `List<GenStage>`, assembles `MapResult`. Behavior-equivalent.
  The four stampers run as `GenStage` **lambdas** for now — folding them into
  `run(ctx)` classes is 2b. Verification surfaced + fixed two pre-existing gen
  bugs (non-deterministic adjacency `09d2590`; defense-post stranding `53fe951`).
  See [`complete/gen-stages.md`](complete/gen-stages.md).

1. **Slice 2b (next)** — fold the four stampers (`FortressWallStamper`,
   `DefensePostStamper`, `CompoundPerimeterDefenderStamper`,
   `KeepEntryChamberStamper`) into `run(ctx)` `GenStage` classes; drop the
   static `stamp(...)` signatures; convert `KeepEntryChamberStamperTest` +
   `CompoundPerimeterDefenderStamperTest` to drive via `ctx`. Replace the
   lambdas in `BspCityGenerator.buildStages()` with the stamper instances and
   move `buildCompoundExclusion` into `FortressWallStamper`.
2. **Slice 3 (to author)** — `GenRecipe`; `ConquestCityRecipe` /
   `LegacyUrbanRecipe`; `BattleSetup` selects by mission. The conquest/legacy
   `ctx.has(AXIS)` gates inside the biome stages collapse into recipe membership.

Station-tier fills (extension, post-pipeline) are parked in
[`stories/`](stories/): **[`corridors-first-class`](stories/corridors-first-class.md)**
(the real blocker — corridors as first-class connective structure) and
**[`station-interior-fills`](stories/station-interior-fills.md)** (rides
on corridors + the recipe machinery).

### Slice 1 critique follow-ups (carry into Slice 2b)

Background critique of `5e5ae91` confirmed behavior-preservation (no
blocker); these minor items are still open — Slice 2a extracted the
orchestrator into stages but didn't rework the fillers, so fold them in
when Slice 2b touches the same code:

- **Test gap.** `BuildingZonePreviewTest` builds a `GenContext` but never
  binds `ROAD_CELLS` / `ROAD_RESERVATION`, so the compound-filler
  overlay-read path is unguarded there (it rests on `BspMapPreviewTest`).
  When stampers/compound fills become stages, add overlay-bound coverage.
- **Dead alias locals.** The mechanical sweep copied unused params forward
  as unused locals (`StubBlockFiller`; the `pois` alias in the
  POI-less per-leaf fillers — IndustrialYard / LandingZone / Waterfront /
  Nature / Park / Plaza / WastelandRubble). Harmless; drop when Slice 2
  reworks each filler.
- **`ctx.get` null seam.** Unbound-key reads return `null` silently — the
  "always non-null" contract the old param list carried is gone. Fine
  while the orchestrator binds everything pre-dispatch; if Slice 2/3 stage
  reordering makes overlay availability non-obvious, add the
  `stage.requires(KEY)` build-time assert sketched in
  [`composable-pipeline.md`](composable-pipeline.md) § Decisions.
- **Unused imports** from the sweep: already cleaned (`b8d992f`).

## Sanity check before resuming

- `gradlew.bat compileJava` clean.
- `gradlew.bat test` — map-gen label/partition tests green.
- `git log --oneline -1 b8b7b9d` confirms Slice D is in history.
