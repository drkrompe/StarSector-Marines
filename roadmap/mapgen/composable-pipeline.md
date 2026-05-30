# Composable generation pipeline

> Decompose the monolithic `BspCityGenerator.generate()` into a
> **context + stages + recipe** structure so map *types* (ground-city
> conquest, station interior, ship interior) become *compositions of
> stages* rather than forks of one method. The current generator is the
> legacy reference; this is the rewrite of the **orchestrator only** —
> the filler / stamper SPI is ported, not redesigned.

## Why

Today the generator is two-level, at opposite ends of composability:

- **Micro (leaf work) — already pluggable.** `BlockFiller`,
  `CompoundFiller`, `PartitionStrategy` are clean SPIs, registered by
  `BlockKind` in an `EnumMap`, swappable without touching the
  orchestrator. Domain-agnostic. Keep as-is.
- **Macro (the pipeline) — a monolith.** `BspCityGenerator.generate()`
  (`battle/world/gen/bsp/BspCityGenerator.java:139-389`) is one hardcoded
  call-sequence with `if (biomeMap != null)` forking conquest vs. legacy.
  The stampers aren't registered anywhere — they're literal static calls
  (`FortressWallStamper.stamp(...)`, `DefensePostStamper.stamp(...)`, …)
  at fixed line numbers. Adding a map *type* means forking this method or
  writing a parallel one.

Two tells the decomposition is already latent:

1. **The step-comments are a pipeline.** Step 1a trunk → 1b BSP → 1c
   zoning → 2 label → 2a seed → 2b claim → 2c road-graph → 3 fill → 3a'
   pedestrian → 3b biome-override → 3b' shoreline → 3c fortress → 3c'
   posts → 3c'' perimeter → 3c''' keep → 3d link → 4 finalize. The stage
   list is already drawn by hand in comments; it just isn't reified.
2. **The argument-list sprawl is a latent context.** Every SPI threads a
   different subset of `{grid, topology, roadCells, roadReservation,
   pois, doodads, tactical, defensePosts, rng, biomeMap, axis}`.
   `CompoundFiller.fill` takes nine params. That's a blackboard that
   hasn't been named.

## Mental-model mapping

This is the same split the rest of the battle tier runs under
([[battle_services_systems]], [[user_engine_game_framing]]):

| Generation concept | Analogue | Role |
| --- | --- | --- |
| `GenContext` | Service / engine-state | owns the shared mutable state |
| `GenStage` | System / mechanism | stateless pass, reads+mutates the context |
| `GenRecipe` | Game / specific behavior | ordered composition of stages per map type |

## The three pieces

### `GenContext` — the blackboard

A **typed blackboard** (decided over fat-context and base+subclass — see
Decisions). Universal spine as direct fields; optional / domain overlays
in an identity-keyed, type-safe store.

```java
public final class GenKey<T> {            // identity = the static field itself
    public final String name;             // debug / preview only
    public static <T> GenKey<T> of(String n) { return new GenKey<>(n); }
}

public final class GenContext {
    // universal spine — always present, never null → NOT in the store
    public final NavigationGrid grid;
    public final CellTopology   topology;
    public final Random         rng;
    public final List<PointOfInterest> pois     = new ArrayList<>();
    public final List<Doodad>          doodads  = new ArrayList<>();
    public final List<TacticalNode>    tactical = new ArrayList<>();

    // typed blackboard — optional / domain overlays
    private final Map<GenKey<?>, Object> store = new HashMap<>();
    public <T> void put(GenKey<T> k, T v) { store.put(k, v); }
    public <T> T    get(GenKey<T> k)      { return (T) store.get(k); }  // T inferred from key
    public boolean  has(GenKey<?> k)      { return store.containsKey(k); }
}

// domain keys live with their domain, not in the core
public final class ConquestKeys {
    public static final GenKey<BiomeMap>       BIOME_MAP        = GenKey.of("biomeMap");
    public static final GenKey<RoadGraph>      ROAD_GRAPH       = GenKey.of("roadGraph");
    public static final GenKey<boolean[][]>    ROAD_RESERVATION = GenKey.of("roadReservation");
    public static final GenKey<List<Compound>> COMPOUNDS        = GenKey.of("compounds");
    public static final GenKey<List<DefensePost>> DEFENSE_POSTS = GenKey.of("defensePosts");
}
```

`ctx.get(ConquestKeys.BIOME_MAP)` returns a `BiomeMap`, compile-checked —
no casts at call sites, no string lookups in stage code. The key string
is debug-only. **Open for extension:** a station stage can
`put(StationKeys.DECK_GRAPH, …)` without touching `GenContext`. The
`if (biomeMap != null)` forks become `ctx.has(BIOME_MAP)`, and once
recipes exist, simply "the stage isn't in the list."

**Spine vs. key — the judgment line.** `grid` / `topology` / `rng` and
the three accumulator lists are universal; making them keys would force
every stage to null-check the most fundamental state. `defensePosts` is
conquest-leaning, so it's a key, not spine. (It's still a direct field on
`MapResult` — only its *threading through generation* moves to a key.)

### `GenStage` — the stateless pass

```java
public interface GenStage {
    void run(GenContext ctx);
}
```

Both the filler-dispatch loop and every stamper become stages with this
one signature. A stage that needs a domain overlay reads it via key and
is responsible for ordering (its recipe must place it after whatever
`put`s the overlay).

> **Scope-narrowing is deferred.** The `BlockFiller` contract ("MUST NOT
> touch the road frame / other leaves") is enforced by convention today;
> a fat `ctx` hands a filler the whole grid, same as the current
> `grid` param does — so no regression, but no new safety either. A
> narrowed `LeafScope` (clamped-bounds read/write view, cf.
> [[battle_view_control_contract]]) is a *future* nicety, not part of
> this track. Ship-then-optimize.

### `GenRecipe` — the composition

An ordered, named list of stages.

```
ConquestCityRecipe = [trunk, bspPartition, biomeOverlay, label,
  seedCompounds, claimCompounds, roadGraph, fillDispatch,
  pedestrianFrames, biomeOverrides, beachShoreline, fortressWall,
  defensePosts, perimeterDefenders, keepChambers, tacticalLink, finalize]

LegacyUrbanRecipe  = [trunk, bspPartition, districtOverlay, label,
  claimCompounds, roadGraph, fillDispatch, pedestrianFrames,
  keepChambers, tacticalLink, finalize]
```

`BattleSetup` selects a recipe (by mission / biome / axis). Adding
`StationInterior` = author a recipe + its domain stages; the generic
stages (`fillDispatch`, `tacticalLink`, `finalize`) are reused verbatim.
This is the "different orchestrator" [`stories/station-interior-fills.md`](stories/station-interior-fills.md)
predicted; [`stories/corridors-first-class.md`](stories/corridors-first-class.md)
plugs in as station stages here.

## Decisions

- **Context model: typed blackboard via `GenKey<T>`** (not fat-context
  with nullable fields, not base+domain-subclass). Rationale: maximal
  composability — domain overlays extend the context without editing it,
  and recipes can freely mix stages across domains. The usual downside of
  a key→value store (stringly-typed) is eliminated by identity-keyed,
  value-typed `GenKey<T>`: call sites stay compile-checked.
- **Incremental rollout, context first.** Land `GenContext` as a
  behavior-preserving signature collapse before reifying stages/recipe.
  Lower risk per commit; matches the room-purpose refactor's
  seam-by-seam cadence. Composition payoff is deferred to Slices 2–3 but
  the foundation is de-risked first.
- **Orchestrator-only rewrite.** Fillers / stampers are ported to the new
  signatures, not redesigned. Legacy `generate()` stays as the reference
  ConquestCity sequence until the composed path reaches parity.

## Slice progression

| Slice | Story | What |
| --- | --- | --- |
| **1** | ✅ [`complete/gen-context.md`](complete/gen-context.md) (`5e5ae91`) | `GenContext` + `GenKey` + `BspKeys`; collapse the **fill SPI** (`BlockFiller` 6-arg, `CompoundFiller` 9-arg) onto `ctx`. `generate()` stays imperative. Byte-identical output. Stampers + `partition`/`carve` deliberately deferred (see the story's scope-refinement note). |
| **2** | _(to author)_ | `GenStage` interface; extract each numbered step — **including the stampers** — into a stage object; `generate()` becomes "build ctx, run an ordered `List<GenStage>`." Conditionals become stage-presence. Stampers' `stamp(ctx)` + their tests convert here. |
| **3** | _(to author)_ | `GenRecipe`; `ConquestCityRecipe` / `LegacyUrbanRecipe`; `BattleSetup` selects by mission. Adding a map type becomes additive. |
| **4+** | [`stories/station-interior-fills.md`](stories/station-interior-fills.md), [`stories/corridors-first-class.md`](stories/corridors-first-class.md) | Station / ship recipes + their domain stages. Out of scope here; this track is the enabler. |

> **Note on the `GenKey` holder name.** The design sketch above calls it
> `ConquestKeys`; the shipped Slice 1 named it `BspKeys` — these overlays
> serve the whole BSP city generator (conquest + legacy district modes),
> not just conquest. Station/ship would declare their own holder.

## Verification posture

- Slice 1 is behavior-preserving: lock it against the existing
  `BspMapPreviewTest` seed renders (same seeds → same grid) and
  `gradlew.bat test` green. A diff in any preview PNG is a regression.
- Slices 2–3 keep the same invariant — the recipe must reproduce the
  legacy sequence exactly until a *new* recipe deliberately diverges.

## Cross-refs

- [`overview.md`](overview.md) — the map-gen track this decomposition
  sits under.
- [`pipeline-audit.md`](pipeline-audit.md) — the coupling points (§4–5)
  this restructure untangles; note its paths predate the
  `mapgen → world/gen` package move.
- [[battle_services_systems]], [[feedback_entity_for_loop_endgame]] — the
  state-owner / stateless-consumer split this mirrors.
- [[feedback_delegate_mechanical_sonnet]] — the Slice 1 signature sweep
  is the delegate-to-Sonnet kind; design + invariants stay on main.
