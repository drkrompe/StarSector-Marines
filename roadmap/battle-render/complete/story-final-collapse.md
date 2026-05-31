# Final — collapse `renderWorld` to collect-all → drain-all — ✅ SHIPPED & VERIFIED

> Commits: `82f349a` (collapse), `391cb6d` (thread DrawList through collectShots).

The last structural step of the battle-render reorg. After every world pass had
migrated to a `RenderSystem` (Stories C–J) or stayed inline as a renderer-owned
own-GL/FBO pass, `renderWorld` was still a hand-wired interleave: a `collect-all`
loop over the seven migrated systems, then a long drain sequence that alternated
`drainLayer(X)` calls with direct inline-pass calls (`renderDecals`,
`renderRoofs`, `renderObjectiveMarkers`, the compound/highlight/fog passes, the
convoy debug overlays, shots, impact FX, flyby, the lightmap) at their exact
paint-order slots. This collapses that into the overview's endgame:

```java
public void renderWorld(RenderContext rc) {
    this.rc = rc;
    drawList.clear();
    for (RenderSystem system : worldSystems) system.collect(rc, drawList);
    for (RenderLayer layer : RenderLayer.values()) drainLayer(layer);
}
```

## What changed

- **`RenderSystem.of(layer, collectFn)`** — a static factory adapting a collect
  lambda + fixed layer into a `RenderSystem`, for renderer-owned passes that don't
  warrant a dedicated class: stateful render resources (decal/light FBO
  accumulators, contrails, impact FX, flyby) and simple own-GL overlays (fog,
  roofs, objective/compound markers, zone + convoy debug). Their state and
  `render*` bodies stay on `BattleRenderer`; they join the ordered registry by
  emitting — almost always a single `DrawList.addCustom` (the sanctioned escape
  hatch for own-GL / FBO blits, per the overview). `SHOTS` is the exception: its
  producer calls `collectShots`, which emits batchable projectile `Sprite`s plus
  `Custom` contrails/tracers.
- **`worldSystems`** — now the *complete* world-pass list in paint order: the
  seven dedicated systems (Ground/Vehicle/Doodad/Units/Drone/Convoy/Shuttle)
  interleaved with the `RenderSystem.of(...)` producers for every formerly-inline
  pass. Layers with multiple producers (GROUND = tiles then zone overlay; CONVOY =
  sprites then docking/selected-vehicle debug) list the body producer first, then
  the overlay — registry list order *is* within-layer submission/paint order.
- **`renderWorld`** — collapsed to the two loops above. The drain is now plain
  `RenderLayer.values()` ordinal order, which is correct precisely because there
  are no inline passes left to interleave (see below).
- **`RenderLayer`** — the per-seam ordering rationale that used to live as inline
  comments in `renderWorld` moved to per-constant javadoc on the enum. The enum is
  now the single source of truth for paint order.

## Behavior-identical by construction

Each pass emits into its existing layer slot, and `RenderLayer` ordinal order is
the verbatim old call sequence, so the net GL command stream is unchanged:

- **Within-layer order preserved.** GROUND drains ground tiles then the zone
  overlay Custom; CONVOY drains convoy sprites then the debug Custom — matching the
  old "drain then inline call" pairs.
- **The old "don't sort by ordinal" watch-out is now retired.** It existed only
  because unmigrated inline passes sat *between* migrated drains, so an
  ordinal-order drain would have floated CONVOY/SHUTTLES ahead of them. With every
  pass in the registry, ordinal order == paint order == correct.
- **Collect-all is GL-free and side-effect-safe.** `Custom` producers only append
  a deferred callback at collect time; the body runs at drain time exactly where it
  used to. `collectShots`' one side effect (spawning impact-FX trails) now happens
  during collect-all instead of just before the SHOTS drain — still before the
  IMPACT_FX drain, so this-frame trails still render this frame.
- **Conditionals preserved** — `debugZonesVisible`, `DEBUG_RENDER_DOCKING_PATHS`,
  and `tod.bypass` gate emission/render exactly as before.

## Verified

`gradlew build` clean; tests green. Background critique came back SAFE TO SHIP —
paint order confirmed byte-for-byte seam-by-seam, `collectShots`' trail-spawn
side-effect still lands same-frame, conditionals equivalent, no `ctx`/`this.rc`
staleness. **In-game smoke check passed** (zones overlay, decals/craters, fog
edges, roofs over unseen interiors, objective pulses, compound rings, convoy debug
paths, shots/contrails, impact FX, flyby, day/night lightmap all correct).

## What's left on battle-render

Only the **deferred `QuadBatch.flush` perf spike** (engine-only, below the command
model). The structural reorg is complete: `BattleScreen` is the loop; `renderWorld`
is two loops; every world pass is a registry producer.
