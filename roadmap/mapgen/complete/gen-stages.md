# Slice 2 — `GenStage` pipeline (extract `generate()` into stages) — ✅ SHIPPED (2a)

Second slice of the [composable generation pipeline](../composable-pipeline.md).
Reifies the monolithic `BspCityGenerator.generate()` into an ordered
`List<GenStage>`: `generate()` now builds a `GenContext`, runs the stages, and
assembles the `MapResult` from the context. **Behavior-equivalent** to the
pre-slice monolith (same `rng` draws, same order).

## What landed (2a)

### New types

- **`GenStage`** (`battle/world/gen`) — `void run(GenContext ctx)`. Functional
  interface; the System analogue of the Service/System split.
- **`battle/world/gen/bsp/stage/`** — one stage class per numbered step:
  `InitFloorStage` (0), `TrunkSkeletonStage` (1a), `BspPartitionStage` (1b),
  `ZoningOverlayStage` (1c), `LabelLeavesStage` (2), `CompoundSeedStage` (2a),
  `CompoundClaimStage` (2b), `RoadGraphStage` (2c), `FillDispatchStage` (3),
  `PedestrianFrameStage` (3a'), `BiomeGroundOverrideStage` (3b),
  `BeachShorelineStage` (3b'), `TacticalLinkStage` (3d), `FinalizeStage`
  (4 + 4b + road-graph diagnostic), `SpawnAnchorStage` (spawns). Each owns the
  private helpers + constants its step used to keep in the orchestrator.

### Context additions

- `GenContext` gains a `seed` spine field (the flood-fill seeds its own
  per-building RNG off it, not the shared stream).
- `BspKeys` gains pipeline-intermediate keys: `TRUNK_PLAN`, `PARTITION`,
  `TACTICAL_MAP`, `BUILDINGS`, `MARINE_SPAWN`, `DEFENDER_SPAWN`. The locals that
  used to thread between steps now flow through the blackboard.

### Orchestrator

`generate()` = allocate grid/topology/ctx, `put(AXIS)` when conquest, run the
fixed stage list, then read the result keys back for `MapResult` + the preview
accessors (`getLastBiomeMap`/`…Compounds`/`…TacticalMap`/`…RoadGraph`,
`getLastDistrictMap`). The conquest/legacy fork is now `ctx.has(BspKeys.AXIS)`
checks inside the biome stages (they no-op without the axis).

### Stampers (deferred to 2b)

The four post-fill stampers (`FortressWallStamper`, `DefensePostStamper`,
`CompoundPerimeterDefenderStamper`, `KeepEntryChamberStamper`) are invoked as
`GenStage` **lambdas** built in `BspCityGenerator` (steps 3c/3c'/3c''/3c'''),
each pulling its args off `ctx` and gating on `BIOME_MAP` where the legacy code
did. `buildCompoundExclusion` stays a private helper for the fortress lambda.
Folding each stamper into a `run(ctx)` `GenStage` class (and converting their
two unit tests) is **2b**, intentionally left as the next step — the lambdas are
fully functional, so the slice ships complete and behavior-equivalent.

## Verification + two pre-existing bugs surfaced

Verifying byte-equivalence against `BspMapPreviewTest` exposed that the
generator was **not reproducible from its seed** — two JVM runs of the same
seed produced maps differing in thousands of cells. Root cause and fixes
(committed separately, before this slice):

- **`09d2590` — deterministic leaf-adjacency order.** `LeafAdjacency` ordered
  neighbors via an identity-hashed `HashSet<BlockLeaf>`; `CompoundClaim` walks
  those lists, so map output rode on JVM allocation. Fixed by sorting neighbor
  lists by `(top, left)`. (My stage refactor only perturbed allocation enough to
  surface this — it didn't introduce it; HEAD was equally non-deterministic.)
- **`53fe951` — defense-post partition guard checks the real footprint.** With
  determinism restored, seed 100 reliably stranded one SAND cell: a LIGHT vent
  ring's open corner boxed in by shoreline water (carved earlier) on two sides
  and the ring arms on the other two. `wouldPartitionWalkable` modeled the
  footprint as a solid bbox and skipped the open corner. Fixed by feeding the
  guard the post's actual non-walkable cells (`DefensePostStamper.blockedFootprint`,
  pinned by `DefensePostFootprintTest`).

## Verification posture

- Full suite green (566 tests), run twice for determinism — including
  `BspMapPreviewTest`'s strict per-seed connectivity assertion, which is now
  reliable rather than flaky.
- Behavior-equivalence of the stage extraction confirmed by bisecting per-stage
  RNG draw counts against the monolith (identical through every stage) before
  the determinism fix changed the baseline layout.

## Next

**2b** — fold the four stampers into `run(ctx)` `GenStage` classes, drop their
static `stamp(...)` signatures, and convert `KeepEntryChamberStamperTest` +
`CompoundPerimeterDefenderStamperTest` to drive via `ctx`. Then **Slice 3**
(`GenRecipe` / `ConquestCityRecipe` / `LegacyUrbanRecipe`; `BattleSetup` selects
by mission) — the conquest/legacy `ctx.has(AXIS)` gates collapse into recipe
membership. See [`../composable-pipeline.md`](../composable-pipeline.md).
