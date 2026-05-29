# Story — Slice 1: `GenContext` (signature collapse)

> First slice of the [composable generation pipeline](../composable-pipeline.md).
> Introduce the `GenContext` blackboard and collapse the sprawling
> per-pass argument lists onto it. **Behavior-preserving** — no sequence
> change, no recipe, byte-identical output.

**Status:** not started. Foundation slice; Slices 2–3 (stages, recipe)
build on it.

## Goal

Replace the threaded argument soup —
`{grid, topology, roadCells, roadReservation, pois, doodads, tactical,
defensePosts, rng, biomeMap, axis}` — with a single `GenContext` passed
to every filler, compound filler, partition strategy, and stamper. The
orchestrator (`BspCityGenerator.generate()`) stays imperative; it just
builds a `ctx` once and hands it down instead of long parameter lists.

## What lands

### New types (in `battle/world/gen`)

- **`GenKey<T>`** — identity-keyed, value-typed token. `name` field is
  debug/preview only; identity is the static field itself.
- **`GenContext`** — universal spine as direct final fields
  (`grid`, `topology`, `rng`, `pois`, `doodads`, `tactical`) + a typed
  blackboard (`put` / `get` / `has`) for optional overlays.
- **`ConquestKeys`** — `BIOME_MAP`, `ROAD_GRAPH`, `ROAD_RESERVATION`,
  `COMPOUNDS`, `DEFENSE_POSTS`. (Legacy/district mode keys —
  `DISTRICT_MAP`, `AXIS` — land here too, or a sibling `UrbanKeys`,
  whichever reads cleaner once the call sites are in front of us.)

### Signature changes (the mechanical sweep)

- `BlockFiller.fill(leaf, grid, topology, pois, doodads, rng)`
  → `fill(BlockLeaf leaf, GenContext ctx)`.
- `CompoundFiller.fill(compound, grid, topology, roadCells,
  roadReservation, pois, doodads, tactical, rng)`
  → `fill(Compound compound, GenContext ctx)` — the nine-arg signature
  collapses to two; `roadCells` / `roadReservation` / `compounds` read
  from keys.
- `PartitionStrategy.partition(grid, topology, bl, bt, br, bb, rng,
  interiorGround)` → `partition(GenContext ctx, int bl, int bt, int br,
  int bb, GroundKind interiorGround)`. (The bbox + interiorGround stay
  explicit args — they're per-call geometry, not shared state.)
- Stamper static methods (`FortressWallStamper.stamp`,
  `DefensePostStamper.stamp`, `CompoundPerimeterDefenderStamper.stamp`,
  `KeepEntryChamberStamper.stamp`) → `stamp(GenContext ctx)`, reading
  `biomeMap` / `roadReservation` / `axis` / accumulators from spine+keys.

### Orchestrator

`generate()` builds the `ctx` after the grid/topology init (Step 0),
`put`s each overlay as it's computed (`BIOME_MAP` at Step 1c,
`ROAD_GRAPH` + `ROAD_RESERVATION` at Step 2c, `COMPOUNDS` at Step 2b),
and threads `ctx` into every downstream call. The `if (biomeMap != null)`
guards become `if (ctx.has(ConquestKeys.BIOME_MAP))` — same control flow,
read off the context. **No stage extraction, no recipe** — that's Slice 2.

## Out of scope (deferred to later slices)

- `GenStage` / `GenRecipe` — Slice 2 / 3.
- Scope-narrowing (`LeafScope` clamped read/write view for fillers) — the
  fat `ctx` hands fillers the whole grid exactly as the current `grid`
  param does, so no regression and no new safety. Future nicety.
- Any change to *what* the passes do or the order they run in.

## Verification

Behavior-preserving, so the bar is **byte-identical generation**:

- Lock against `BspMapPreviewTest` — same seeds must produce the same
  grid / topology / preview PNGs. A pixel diff is a regression.
- `mcp__intellij__build_project` clean (catches missed call sites the
  Gradle incremental compile can skip — see [[intellij_mcp_refactor_tools]]).
- `gradlew.bat test` green.

## Execution note

The signature propagation across ~17 fillers + 4 stampers + the
orchestrator is a wide mechanical sweep — delegate to Sonnet subagents
per [[feedback_delegate_mechanical_sonnet]]; the `GenContext` /
`GenKey` design + the spine-vs-key decisions + the verification stay on
the main thread. Watch for absolute-path edits if any agent runs in a
worktree ([[subagent_worktree_absolute_paths]]).

## Cross-refs

- [`../composable-pipeline.md`](../composable-pipeline.md) — the design
  doc; § "The three pieces" has the `GenContext` / `GenKey` sketch.
- [[battle_services_systems]] — the state-owner/consumer split this
  mirrors (`GenContext` = the Service).
