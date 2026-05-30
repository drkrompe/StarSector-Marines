# Slice 1 — `GenContext` (fill-SPI signature collapse) — ✅ SHIPPED

**Shipped `5e5ae91`** — "mapgen: Slice 1 — GenContext blackboard; collapse
fill SPI signatures". First slice of the
[composable generation pipeline](../composable-pipeline.md). Introduce the
`GenContext` blackboard and collapse the sprawling per-pass argument lists
onto it. **Behavior-preserving** — generation output byte-identical.

## What landed

### New types (`battle/world/gen`)

- **`GenKey<T>`** — identity-keyed, value-typed token. Identity is the
  static field; `name` is debug-only.
- **`GenContext`** — universal spine as direct final fields (`grid`,
  `topology`, `rng`, `width`/`height`, and the output accumulators
  `pois` / `doodads` / `tactical` / `defensePosts`) + a typed blackboard
  (`put` / `get` / `has`) for optional overlays.
- **`BspKeys`** (`battle/world/gen/bsp`) — the BSP-city overlay keys:
  `BIOME_MAP`, `DISTRICT_MAP`, `AXIS`, `ROAD_CELLS`, `ROAD_RESERVATION`,
  `ROAD_GRAPH`, `COMPOUNDS`. Lives in `bsp` (domain-specific), not the
  generic `gen` core.

### Signature collapse (the fill SPI)

- `BlockFiller.fill(leaf, grid, topology, pois, doodads, rng)` (6 args)
  → `fill(BlockLeaf leaf, GenContext ctx)`.
- `CompoundFiller.fill(compound, grid, topology, roadCells,
  roadReservation, pois, doodads, tactical, rng)` (9 args)
  → `fill(Compound compound, GenContext ctx)`. `roadCells` /
  `roadReservation` read from `BspKeys`.
- 16 filler impls ported via signature + alias-prelude — bodies untouched
  (the alias locals keep the old names, so zero body churn). Thin
  delegators (residential / commercial / industrial) inline `ctx.field`
  into the `BuildingShellCore.carve` call.

### Orchestrator

`BspCityGenerator.generate()` builds the `ctx` after grid/topology init,
binds each overlay under `BspKeys` as it's computed (biome/district/axis
at Step 1c, compounds at Step 2b, road masks + graph at Step 2c),
dispatches fillers via `ctx`, and sources the (still-explicit) stamper
args + `MapResult` from `ctx.pois` / `ctx.doodads` / `ctx.tactical` /
`ctx.defensePosts`. The `if (biomeMap != null)` control flow is unchanged.

## Scope refinement vs. the plan

The story originally listed `stamp` and `partition` in the collapse. During
implementation the scope sharpened to **the orchestrator-facing fill SPI
only**, for two reasons that made the slice both smaller and more correct:

- **Stampers deferred to Slice 2.** Their `stamp(...)` entry points are
  coupled to dedicated unit tests (`KeepEntryChamberStamperTest`,
  `CompoundPerimeterDefenderStamperTest` — 15 call sites). Since stampers
  *become* `GenStage`s in Slice 2, their signature + test conversion
  belongs there. This slice leaves them untouched; the orchestrator feeds
  them `ctx.*` lists explicitly.
- **`PartitionStrategy` / `BuildingShellCore.carve` excluded.** These are
  internal to the building-fill subsystem (called by fillers, never by the
  orchestrator) and genuinely don't want a wide `GenContext` — `partition`
  needs only grid/topology/rng + bbox. Collapsing them onto the context
  would be *worse* design. They keep their narrow, honest signatures.

Net: the scope line is "every pass the orchestrator threads state into" —
which is exactly what becomes a `GenStage` in Slice 2.

### Judgment calls baked in

- `defensePosts` kept on the **spine** (not a key) despite being
  conquest-leaning — `generate()` always allocates it and `MapResult`
  always carries it, so it's an output accumulator like the other three.
- Spine holds only always-present state (`grid`/`topology`/`rng` + the
  four accumulators); making those keys would force every pass to
  null-check the most fundamental state.

## Verification

- `mcp__intellij__build_project` clean (zero problems).
- `gradlew test` green — `BspMapPreviewTest`, `BuildingZonePreviewTest`,
  `BuildingShellCoreLabelTest`, both stamper tests.
- Behavior-preserving **by construction**: signature + alias-prelude only,
  same `rng` instance + call order, `put()` never touches RNG → byte-
  identical generation.

## Execution note

The 15 non-exemplar filler conversions were fanned out to a Sonnet
subagent (mechanical signature + alias sweep, the exemplar
`BuildingResidentialFiller` as the pattern); the `GenContext` /
`GenKey` design, orchestrator threading, and verification stayed on the
main thread — per [[feedback_delegate_mechanical_sonnet]].

## Next

Slice 2 — `GenStage` interface; extract each numbered `generate()` step
(including the now-deferred stampers) into a stage object;
`generate()` becomes "build ctx, run an ordered `List<GenStage>`." See
[`../composable-pipeline.md`](../composable-pipeline.md).
