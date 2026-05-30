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
65c5686  mapgen: Slice 2a — GenStage pipeline (extract generate() into stages)
8666b8f  mapgen: Slice 2b — fold stampers into GenStage classes
bf0cf22  mapgen: hoist FortressWallStamper ctx reads to top of run()
7016b8e  mapgen: Slice 3 — GenRecipe; conquest/legacy recipes selected by axis  ← latest mapgen work
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
- **Slice 2b — stampers as `GenStage` classes: ✅ shipped (`8666b8f`).** The
  four post-fill stampers implement `GenStage` directly; `run(ctx)` replaced the
  static `stamp(...)` signatures, `buildCompoundExclusion` moved into
  `FortressWallStamper`, `buildStages()` lists the stamper instances, and the
  two stamper tests drive through a `GenContext`. Behavior-equivalent; full
  suite green. `stampNonConquest` / `stampPost` / `blockedFootprint` stay static
  (called outside the pipeline). See [`complete/gen-stages.md`](complete/gen-stages.md).

- **Slice 3 — `GenRecipe` + conquest/legacy recipes: ✅ shipped (`7016b8e`).**
  `GenRecipe` is a named, ordered `List<GenStage>`; `BspCityGenerator` builds a
  `ConquestCity` recipe (full list) + a `LegacyUrban` recipe (full minus the six
  conquest-only stages) and `generate(…, axis)` selects by axis. The
  conquest/legacy fork is now recipe membership. Byte-identical output (all six
  dropped stages verified RNG/mutation-inert in legacy mode); full suite green.
  See [`complete/gen-recipe.md`](complete/gen-recipe.md).

**The composable-pipeline core (context + stages + recipes) is complete.**
Adding a map type is now additive — author a recipe + its domain stages; the
generic stages (`FillDispatchStage`, `TacticalLinkStage`, `FinalizeStage`) are
reused verbatim. Candidate next tracks (priority order):

- **Self-gate cleanup: ✅ done.** The four conquest-*geometry* stages
  (`BiomeGroundOverride`, `BeachShoreline`, `FortressWall`, `DefensePost`) now
  **fail-fast** — `if (biomeMap == null) throw` instead of the dead silent
  no-op — so mis-composing them into a non-conquest recipe surfaces loudly. The
  two compound stages kept their graceful handling on purpose: `CompoundSeed`
  delegates null-tolerance to its seeder, and `CompoundPerimeter`'s axis-skip is
  a tested, intentional "no attacker side → nothing to place" behavior — left
  flexible pending how compounds compose into future recipes.

1. **Slice 1 filler-level critique nits** (see section below) — fold in when a
   filler-rework pass next touches that code.
2. **Station-tier track** — [`stories/corridors-first-class.md`](stories/corridors-first-class.md)
   (the real blocker — corridors as first-class connective structure) then
   [`stories/station-interior-fills.md`](stories/station-interior-fills.md)
   (rides on corridors + the recipe machinery); both plug in as new recipes +
   domain stages on the now-complete pipeline.

### Slice 1 critique follow-ups (still open — carry into Slice 3 / filler rework)

Background critique of `5e5ae91` confirmed behavior-preservation (no
blocker); these minor items are still open. Slices 2a/2b reworked the
orchestrator + stampers but not the fillers, so these filler-level items
stayed untouched — fold them in when Slice 3 (or a filler-rework pass)
next edits the same code. (The Slice 2a critique's own nits are resolved:
the `PedestrianFrameStage` `~50%` comment was fixed in `8666b8f`; the
`GenStage` "no instance fields" Javadoc vs `FillDispatchStage`'s config
registries is a documented, intentional tension, not a defect.)

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
