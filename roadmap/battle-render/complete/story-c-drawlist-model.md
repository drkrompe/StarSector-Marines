# Story C — Prove the DrawList model on one layer (SHOTS) — ✅ SHIPPED

## What landed

The draw-list command model, proven end-to-end on the `SHOTS` layer. New in
`ops/battleview/`:

- **`RenderLayer`** — the ordered enum that *is* today's pass list, lifted
  verbatim from the `renderWorld` call sequence (`GROUND … LIGHTING`, 17
  layers). Ordinal = paint order; the load-bearing ordering is now visible data.
- **`DrawCommand`** — sealed interface, two variants (the ones `SHOTS` needs):
  - `SpriteQuad` — a single rotated `SpriteAPI` sprite (projectiles, and later
    shuttles/turrets/drones). Carries a whole-texture sprite + screen-space
    center/size/angle/rgba; *not* a sheet+sub-rect, because single sprites don't
    share a sheet so there's no `QuadBatch` win.
  - `Custom` — the escape hatch: a `Runnable` that owns its own GL state. Used
    for the contrail accumulator (`RibbonBatch` + bracket) and the `GL_LINES`
    tracer block.
  - `SolidRect` / `Ribbon` as first-class commands are deliberately deferred —
    they're added when a pass that needs them migrates (HP bars → `SolidRect`
    in the UNITS slice). No speculative vocabulary.
- **`DrawList`** — per-frame collector (`EnumMap<RenderLayer, List<DrawCommand>>`),
  one instance on `BattleRenderer`, `clear()`ed at the top of each `renderWorld`;
  lists retain capacity so steady-state frames allocate nothing in the list.

In `BattleRenderer`:
- **`drainLayer(RenderLayer)`** — replays a layer's commands in submission order:
  a run of consecutive `SpriteQuad`s shares one `GlStateBracket.textured2D()`;
  each `Custom` runs standalone. Generalizes the manual batch-and-flush already
  in `renderTiledFloorsAndWalls`.
- **`renderShots` → `collectShots` + `drawTracers`.** `collectShots` appends to
  the `SHOTS` layer: the contrails `Custom` **unconditionally** (its callback
  ages/decays trails every frame, even with zero live shots), then — only when
  shots are present — the tracers `Custom` and one `SpriteQuad` per projectile.
  `renderContrails` is unchanged (the `Custom` just defers its invocation).
- `renderWorld` now does `collectShots(...)` then `drainLayer(SHOTS)` at the SHOTS
  seam, between shuttles and impact-FX. All other passes still draw inline.

Drop-outs from the old `renderShots`: the four `touched*` angle-reset sets are
gone — `drawSpriteQuad` resets the sprite angle to 0 after each render, so no
cached sprite carries rotation into another pass. Removed the now-unused
`MarineSecondary` / `MarineWeapon` imports.

## Verified

`mcp__intellij__build_project` clean (no problems). **In-game render check still
required** (render-behavior change): projectile sprites, hitscan tracers, and
LOCUST contrails should look identical, and contrails must still age/decay while
the sim is paused (the unconditional contrails `Custom` covers this).

## Deviations from spec / follow-ups

- **`Custom` over a first-class `Ribbon`/`Line` for contrails+tracers.** The
  overview lists `Ribbon` as a command type; this slice routes contrails through
  `Custom` (sanctioned by the watch-out: "Custom escape hatch must cover the
  FBO/lightmap/contrail passes"). Tracers (`GL_LINES`) likewise — no `Line`
  command type was added for one caller. Promote to first-class commands only if
  a later pass shares the need.
- **`SpriteQuad` carries a whole-texture `SpriteAPI`, not sheet+sub-rect.** So
  this slice does *not* exercise the `QuadBatch` sheet-grouping path — that gets
  proven when the first sheet-based pass (tiles or UNITS) migrates in D…N. The
  drain's bracket-a-run-of-SpriteQuads structure is in place for it.
- **Story B carry-overs not yet addressed:** `MARINE_TRACER`/`DEFENDER_TRACER`/
  `bearingDeg()` duplication; fuller inter-pass comments in `renderWorld`. Still
  open.

---

# Original design / spec

Introduce `RenderLayer` (the ordered enum that *is* today's pass list) + `DrawList`
+ the drain in `BattleRenderer`. Convert ONE pass (`SHOTS` or `UNITS`) to emit
`DrawCommand`s instead of drawing inline, and validate the command/batch/flush
path end-to-end on that single layer — including the `Custom` escape hatch on at
least one accumulator pass (FBO/lightmap/contrail). See `overview.md` §Stories C.

Chosen pass: **SHOTS** — self-contained, and naturally exercises `SpriteQuad`
(projectiles) + `Custom` (tracers + the contrail accumulator) in one layer, which
is exactly the combo the watch-outs flag to pressure-test before fanning out.
