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
b8b7b9d  mapgen: Slice D — ternary keep wiring + multi-chamber stamper emission  ← latest mapgen work
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

1. **[`gen-context`](stories/gen-context.md)** — Slice 1. Introduce
   `GenContext` (typed blackboard via `GenKey<T>`) + `ConquestKeys`;
   collapse the 9-arg `fill` / `stamp` / `partition` signatures onto it.
   Behavior-preserving; lock against `BspMapPreviewTest` seeds. The
   signature sweep is delegate-to-Sonnet; design + verify on main.
2. **Slice 2 (to author)** — `GenStage` interface; extract each numbered
   `generate()` step into a stage object. `generate()` becomes
   "build ctx, run an ordered `List<GenStage>`."
3. **Slice 3 (to author)** — `GenRecipe`; `ConquestCityRecipe` /
   `LegacyUrbanRecipe`; `BattleSetup` selects by mission.

Station-tier fills (extension, post-pipeline) are parked in
[`stories/`](stories/): **[`corridors-first-class`](stories/corridors-first-class.md)**
(the real blocker — corridors as first-class connective structure) and
**[`station-interior-fills`](stories/station-interior-fills.md)** (rides
on corridors + the recipe machinery).

## Sanity check before resuming

- `gradlew.bat compileJava` clean.
- `gradlew.bat test` — map-gen label/partition tests green.
- `git log --oneline -1 b8b7b9d` confirms Slice D is in history.
