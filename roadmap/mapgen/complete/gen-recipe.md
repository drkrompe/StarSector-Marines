# Slice 3 — `GenRecipe`; conquest/legacy recipes — ✅ SHIPPED

Third slice of the [composable generation pipeline](../composable-pipeline.md).
Reifies the conquest/legacy fork as **recipe membership** instead of an
`if (biomeMap != null)` / `ctx.has(AXIS)` running through one shared stage list.
`BspCityGenerator.generate(…, axis)` now picks a `GenRecipe` by axis presence
and runs it. **Behavior-equivalent** to the pre-recipe single-list path — both
conquest and legacy maps are byte-identical.

## What landed

### New type

- **`GenRecipe`** (`battle/world/gen`) — a named, immutable, ordered
  `List<GenStage>` with a `run(GenContext)` that fires each stage in order. The
  Game / specific-behavior analogue to `GenContext` (Service) and `GenStage`
  (System). Stateless; one instance replays against many contexts.

### Two recipes in `BspCityGenerator`

- **`ConquestCity`** — the full 19-stage sequence (the exact list Slice 2 built),
  unchanged.
- **`LegacyUrban`** — the conquest recipe minus the six **conquest-only** stages:
  `CompoundSeedStage`, `BiomeGroundOverrideStage`, `BeachShorelineStage`,
  `FortressWallStamper`, `DefensePostStamper`, `CompoundPerimeterDefenderStamper`.
  Kept stages keep their relative order.

Both recipes are built once in the constructor (`buildConquestRecipe()` /
`buildLegacyRecipe()`), replacing the single `buildStages()` + `stages` field.
`generate(…, axis)` selects `axis != null ? conquestRecipe : legacyRecipe`.

### Why dropping the six stages is byte-equivalent

The legacy path previously ran the full list with those six stages no-op'ing on
the unbound biome/axis. Removing them from the list is identical **iff** each
drew zero RNG and mutated nothing in legacy mode — verified by reading each:

| Dropped stage | Legacy behavior |
| --- | --- |
| `CompoundSeedStage` | takes no `rng`; `BiomeCompoundSeeder.seed` returns `0` on null biome before touching any leaf |
| `BiomeGroundOverrideStage` | `if (biomeMap == null) return;` before any work; no `rng` |
| `BeachShorelineStage` | biome-null gate before `noisyShoreDepth` (the only `rng` draw) |
| `FortressWallStamper` | biome-null gate before the wall RNG |
| `DefensePostStamper` | biome-null gate before the per-biome count rolls |
| `CompoundPerimeterDefenderStamper` | axis-null early-out; no `rng` |

So the kept stages see an identical `rng` stream and identical grid state with
or without them. The shared stages that DO run in both modes
(`ZoningOverlayStage` → biome vs district, `LabelLeavesStage`,
`CompoundClaimStage` → default vs biome role set, `SpawnAnchorStage` → biome vs
half-map spawn pick) fork internally and stay in both recipes.

### Self-gates kept (belt-and-suspenders)

The conquest-only stages keep their internal biome/axis guards. They're
redundant now that membership decides participation, but harmless, and they keep
each stage safe if a future recipe lists it without binding the overlay. The
doc's "self-gates collapse into recipe membership" is realized at the
orchestration level; stripping the now-dead guards from each stage body is an
optional follow-up cleanup, not required for the abstraction.

## Verification

- Full suite green. `BspMapPreviewTest` renders **both** conquest (`conquest-seed-*`)
  and legacy (`seed-*`) seeds with strict per-seed single-component connectivity
  assertions — the guard that a shifted RNG stream or stranded cell would trip.
  Both families pass.
- `BattleSetup` is unchanged: it already chooses the axis per mission, so recipe
  selection follows transitively. The `MapGenerator` API is untouched.

## Next

The composable-pipeline track's core is complete: context + stages + recipes.
Adding a map type (station / ship interior) is now additive — author a recipe +
its domain stages; the generic stages (`FillDispatchStage`, `TacticalLinkStage`,
`FinalizeStage`) are reused verbatim. See the parked station stories
([`../stories/corridors-first-class.md`](../stories/corridors-first-class.md),
[`../stories/station-interior-fills.md`](../stories/station-interior-fills.md))
and the still-open Slice 1 filler-level critique nits (carry into the next
filler-rework pass; see [`../next-session.md`](../next-session.md)).
