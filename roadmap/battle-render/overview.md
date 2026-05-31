# Battle render reorg

## Why

`ops/BattleScreen.java` is a ~4,360-line god class. It conflates three
unrelated jobs:

1. **Asset lifecycle** — ~900 lines of `ensureXSheet()` + a dozen `EnumMap`
   sprite caches. Pure incidental coupling; none of it is render
   orchestration.
2. **Frame loop / input / audio / camera** — `attach`, `advance`, pan-drag,
   the positional audio bed.
3. **The render pipeline** — ~20 painter passes (`render()`, then
   `renderTiledFloorsAndWalls`, `renderUnits`, `renderShots`, …).

And #3 is itself two tangled things: the **ordered list of passes**
(orchestration) and **each pass's pull-from-sim + geometry/sprite logic**.

This is the render-side mirror of [`battle-reorg/`](../battle-reorg/): the same
move toward services + stateless systems, with the north star that
`BattleScreen` becomes *just the loop* and `render()` becomes *just the layer
drain*.

## Target shape

```
BattleScreen      → loop / input / audio / camera. Owns the scissor bracket.
BattleRenderer    → drains the per-frame DrawList layer-by-layer, batches, flushes GL.
RenderSystem(s)   → stateless; read services + camera + vision, append DrawCommands.
render2d/*        → the primitive layer (QuadBatch, SolidQuadBatch, RibbonBatch, accumulators). Unchanged.
BattleSprites     → asset/sprite-cache lifecycle, extracted from the screen.
```

`BattleScreen.render()` collapses to roughly:

```java
for (RenderSystem s : systems) s.collect(ctx, drawList);
renderer.flush(drawList);   // inside the existing scissor bracket
```

## Key constraints (these shape the whole design)

- **Pass order is semantic, not a depth sort.** `render()` is a carefully
  reasoned stack — roofs over units, drones over roofs, fog between,
  lightmap-multiply dead last, all inside one scissor bracket. The inline
  comments there are load-bearing. A naive "global Renderable + sort by Y"
  throws this away. → Encode order as an explicit ordered `RenderLayer`.
- **Not everything is a textured quad.** `DecalAccumulator` / `LightAccumulator`
  are FBO blits; the lightmap pass is a multiply-blend; contrails are ribbon
  strips; HP bars / capture arcs / crosswalk stripes are solid geometry. → The
  command model needs a `Custom`/callback escape hatch, not just `SpriteQuad`.
- **GL state is hostile.** Starsector hands UI hooks a polluted GL state
  (see memory: GL state gotchas). All batched flushes stay wrapped in
  `GlStateBracket`; the renderer owns the bracketing so systems never touch GL.
- **Render is per-frame, not per-tick.** Render runs faster than the 30Hz sim
  and interpolates (`renderX/renderY`); camera/zoom, visibility fade, and
  weapon-up/recoil timers all vary per frame. → **Pull** model: systems compute
  draw commands fresh each frame. Cache tick-stable sub-parts only if profiling
  flags a hot system (ship-then-optimize).

## Vocabulary

- **RenderLayer** — an ordered enum that *is* today's pass list. Ordinal =
  paint order. Makes the load-bearing ordering visible data instead of an
  implicit method-call sequence.
- **DrawList** — per-frame collector the renderer owns. Holds `DrawCommand`s
  tagged with a `RenderLayer`.
- **DrawCommand** — `SpriteQuad` (sheet ref, srcRect, dstCenter, size,
  rotation, rgba), `SolidRect`, `Ribbon`, `Custom` (a callback that does its
  own GL bracket — the FBO/lightmap escape hatch).
- **RenderSystem** — stateless consumer. `collect(ctx, drawList)` reads
  services/camera/vision and appends commands into the right layer. The
  render-side analog of a sim System. (Producer lives in the system, **not** on
  `Unit` — `Unit` stays a pure data/sim object and never imports `SpriteAPI`.)
- **BattleRenderer** — for each layer in order: drain commands, group
  `SpriteQuad`s by sheet, flush each `QuadBatch` under one `GlStateBracket`,
  run solids/ribbons/customs in submission order. Generalizes the manual
  batching already in `renderTiledFloorsAndWalls`.

## Proposed RenderLayer order (from today's `render()`)

`GROUND → DECALS → VEHICLES → DOODADS → HIGHLIGHTS → FOG → UNITS → ROOFS →
DRONES → OBJECTIVES → COMPOUND → CONVOY → SHUTTLES → SHOTS → IMPACT_FX →
FLYBY → LIGHTING`

(Lift verbatim from the call sequence + comments in `BattleScreen.render()` so
no ordering reasoning is lost in translation.)

## Stories (incremental, each verifies in-game)

Deliberately **not** a big-bang. Each slice is independently shippable.

- **A — Extract assets.** Move `ensureXSheet()` + caches into a `BattleSprites`
  / sprite registry. ~900 lines out, zero behavior change. Lowest risk, biggest
  immediate readability win. Good first Sonnet-delegated mechanical sweep.
- **B — Extract `BattleRenderer` + `RenderContext`.** Move the existing
  `render*` methods *verbatim* into a renderer class holding
  camera/layout/batches/sheet refs. Still a call sequence, but severed from the
  screen/loop/input. `BattleScreen` shrinks to the loop.
- **C — Prove the model on one layer.** Introduce `RenderLayer` + `DrawList` +
  the drain. Convert one pass (`SHOTS` or `UNITS`) to emit commands. End-to-end
  validation of the command/batch/flush path on a single layer, including the
  `Custom` escape hatch on at least one accumulator pass.
- **D…N — One pass per slice** into `RenderSystem`s. Tiles, units, vehicles,
  doodads, shuttles, drones, objectives, fog, impact-fx, flyby, lighting.
- **Final — Collapse `render()`** to the systems-loop + renderer-flush + scissor
  bracket. Delete the per-pass methods as their systems land.

## Future: camera view-projection + camera-Z

> Beyond this reorg's scope (the decomposition above is purely structural). A
> forward direction, captured so the air/altitude work has something to point at.

Today's `BattleCamera` is a 2D fit-to-viewport projection: `MIN_ZOOM = 1.0`
means the whole map already fits at rest, and you only zoom *in* from there.
Two pressures want more:

- **Larger maps.** A planned **512+** cell dimension makes "whole map fits at
  zoom 1.0" pixel-starved; you want to pull the camera back/up past full-map and
  navigate a slice.
- **Airborne altitude.** The `air/` category (fighters, overhead ships) wants
  altitude to be a real coordinate, not a render-small fake. A camera with a
  **Z** gives both the camera and the craft a shared height axis.

Direction: replace the fitted-ortho zoom with a **proper view-projection** and a
**camera-Z** (height above the battlefield) driving zoom/altitude transitions.
Orthographic-with-Z vs. perspective is open. This is a `render2d` engine change
(the camera mechanism already lives there post engine/game split); the concrete
passes and `RenderSystem`s shouldn't care, since they consume the camera
projection abstractly. Cross-ref: [`../air/ships/`](../air/ships/overview.md) §
"Scale & altitude", [`../air/hull-extraction.md`](../air/hull-extraction.md).

## Considered alternatives

### A proper ECS dependency (artemis-odb) — rejected

Tempting given the desired end-feel (components keyed by `long`, mutated through
systems), but rejected for this codebase:

1. **Reflection vs the sandbox — the hard blocker.** Starsector's script
   sandbox forbids reflection (the source tree has *zero* `java.lang.reflect`
   usage, by necessity). artemis-odb's `World`/`ComponentManager` instantiates
   components and injects `ComponentMapper`s through libgdx `ClassReflection`
   (→ `java.lang.reflect`) at World construction. That's core, not opt-in.
   Verdict: this is the part that kills it.
2. **Serialization.** Battle state is XStream-serialized `Serializable` POJOs
   (incl. `BattleSimulation`). An Artemis `World` keeps state in its own packed
   arrays; round-tripping it through XStream means a flatten/rehydrate adapter
   fighting the grain of Starsector persistence.
3. **Render is the wrong first user anyway.** ECS pays off for *persistent
   component composition + aspect subscription* on the **sim** side. Render
   data is derived/transient per-frame; render systems would either iterate the
   existing registry as plain classes (Artemis adds nothing) or spawn render
   components every frame (pure churn).

If an ECS dependency is ever reconsidered, it's a **sim** decision, gated by a
30-minute spike: construct a throwaway 2-component `World` + one system in
`onApplicationLoad`, call `world.process()`. If it throws under the sandbox →
done. Bundling/relocation is *not* a blocker (shadowJar already shades fastutil).

### Borrow the concepts, not the jar

We keep building ECS-lite by hand (the established pattern: SoA `UnitRegistry`,
monotonic non-recycled IDs, spatial index for filtering). Aspect-subscription
sugar — entities auto-matched to a component signature — is reproducible as a
lightweight cached *view* over the registry (filter once, invalidate on
add/remove) without importing a World lifecycle the sim isn't built around.

### vs. Artemis — what's bridged, what's deferred (and why)

The hand-rolled shape sits *right up against* the artemis-odb API surface
(`[[user_artemis_ecs_framing]]`). Reading the map by Artemis concept:

| Artemis | Here | Status |
|---------|------|--------|
| Entity (`int` id) | monotonic `entityId` + dense `denseIdx`, swap-and-pop, no gen bits | **bridged** |
| Component store / `ComponentMapper<T>` | `UnitRegistry` SoA columns (`hp[]`, `cellX[]`, timers) behind hand-written typed accessors | **bridged, not abstracted** — no generic mapper; per-column accessors |
| Flyweight/type-shared appearance | `RenderAppearance` keyed by `UnitType` (Story J) | ours; Artemis has no first-class flyweight — it'd be a tag + side table |
| Aspect (all/one/exclude signature) | capability tags (`drawsHpBar`, `spriteKind`, …) | **the gap** — the tags *are* a signature, but matched by an **eager per-frame sweep**, not a maintained subscription |
| `World` / injection / `process()` | explicit service wiring | **deliberately absent** — reflection-driven; the sandbox forbids `java.lang.reflect` (the hard blocker above) |

The one abstraction that would complete the Artemis silhouette is the
**maintained aspect-view** (filter once, invalidate on entity churn) replacing the
eager sweep. Two reasons it's deferred, not forgotten:

1. **It's a sim decision, not a render one.** Render is a per-frame *pull* — it
   re-derives facing/fade/recoil/interpolated position every frame regardless, so
   a maintained subscription buys render almost nothing. The payoff is on the sim
   side, for expensive filters that stay stable across ticks. Formalizing it *for
   render* would be premature (`[[feedback_ship_then_optimize]]`).
2. **It lands naturally at `UnitRegistry` Phase 2.** When the sim's hot loops flip
   to iterate the registry (the registry's own documented next phase), the cached
   view is the right tool to introduce *there*.

So Story J intentionally takes the component + tag + system *shape* (flyweight
appearance, capability tags, stateless per-stratum sweep service) **without**
reaching for the aspect-view — the shape is the reusable win; the subscription
machinery is sim-tier work gated behind Phase 2.

## Cross-refs

- `ops/BattleScreen.java` — the god class being decomposed.
- `render2d/` — the primitive batch/accumulator layer (stays put).
- [`battle-reorg/`](../battle-reorg/) — the sim-side sibling; same
  services/systems north star.
- [`air/`](../air/overview.md) — depends on the camera-Z direction above for
  airborne-craft altitude and true zoom-out.
- Memory: "Battle services + systems", "Default to ECS shape", "Script
  sandbox", "GL state gotchas", "render2d batching".
