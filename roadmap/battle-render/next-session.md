# Battle render reorg — next session

> ✅ **STRUCTURAL REORG COMPLETE.** All stories A–J + Final shipped & verified.
> `BattleScreen` is the loop; `renderWorld` is two loops (collect-all → drain-all);
> every world pass is a `worldSystems` producer.
>
> ✅ **`QuadBatch.flush` perf spike — SHIPPED & VERIFIED** (client-side vertex
> arrays; −75% combined flush CPU, in-game render correct — see below).
>
> 🎯 **Active next: Phase 2 — FX command-model migration.** Dissolve the residual
> `Custom` escape hatches into the command model, SHOTS/FX first, using the
> Story-J flyweight + capability-tag + stateless-sweep pattern as the template.
> Design + slice plan: [`stories/fx-shots-command-model.md`](stories/fx-shots-command-model.md).
> **F1–F4 shipped** (`LINE` command + `LineBatch`; `ShotFx` composition + test;
> `ShotRenderService` + path-keyed sprite cache; contrail state/render split via a
> `RIBBON` command + `ContrailFxService`). SHOTS is now fully command-driven — no
> `Custom` left in the layer. **F5** is the close-out tail (prune orphaned getters,
> dedupe tracer constants, move the doc to `complete/`).
> Optional perf follow-up still on the shelf: the ground-FBO work-reduction spike.

## State of play

Concept doc written ([`overview.md`](overview.md)). **Stories A–D shipped.**
- A — `BattleSprites` asset registry ([`complete/story-a-extract-assets.md`](complete/story-a-extract-assets.md)).
- B — `BattleRenderer` + `RenderContext`; pipeline severed from the loop
  ([`complete/story-b-battlerenderer.md`](complete/story-b-battlerenderer.md)).
- C — `RenderLayer` + `DrawList` + `DrawCommand` + `drainLayer`; the `SHOTS` pass
  converted ([`complete/story-c-drawlist-model.md`](complete/story-c-drawlist-model.md)).
- D — first sheet-batched pass + `RenderSystem`: `DOODADS` migrated to
  `DoodadRenderSystem` emitting `SheetQuad`s; drain gained the per-sheet
  `QuadBatch` grouping path ([`complete/story-d-doodads-system.md`](complete/story-d-doodads-system.md)).

Command vocabulary now: `SheetQuad` (batched sheet sub-rect),
`Sprite` (whole-texture `renderAtCenter`), `Custom` (own-GL escape hatch). Two
passes (`SHOTS`, `DOODADS`) route through the draw list; ~15 still draw inline.
`renderWorld` hand-wires `doodadSystem.collect` + `drainLayer`.

**Engine/game package split shipped** (structural, zero behavior change) — see
[`complete/engine-game-package-split.md`](complete/engine-game-package-split.md).
The render *mechanism* now lives in `render2d` (engine): `BattleCamera`,
`DrawCommand`, and the extracted `DrawListRenderer` drain joined the batch
primitives; `render2d` imports nothing from `ops.battleview` (verified). The
concrete passes, `RenderLayer`, `DrawList`, `RenderContext`, `RenderSystem`, and
`BattleRenderer` stay game-side in `ops.battleview`. `drainLayer` is a one-line
delegator to `DrawListRenderer.drain`. Both packages now carry charters. This is
the clean seam the remaining pass migrations land on.

## Next-up

**Story E is shipped, verified, and the fallback is deleted.** In-game check
passed (and fixed a latent beach-doodad bug); the old `@Deprecated`
`renderGrid`/`renderTiledFloorsAndWalls`/`draw*Tile`/predicate block + its
orphaned constants were removed from `BattleRenderer` (−426 lines). The live
`renderZoneOverlay`/`renderDecals` (interleaved in that span) were preserved.

**Story F (VEHICLES → `VehicleRenderSystem`) shipped & verified** (fallback
deleted) — parked map vehicles now emit one batched `SHEET_QUAD` each. This added the
**sprite-sheet batch registration** seam: `buildTileBatches` builds+registers a
`QuadBatch` per vehicle sheet (sheets are loaded by `ensureVehicleSheets()`
before that runs). UNITS/DRONES reuse the same seam. Inline `renderVehicles`
retained `@Deprecated`+uncalled pending the live check. See
[`complete/story-f-vehicles-system.md`](complete/story-f-vehicles-system.md).

**Story G (CONVOY → `ConvoyRenderSystem`) shipped & verified** (fallback deleted)
— convoy trucks + turrets now emit **rotated** batched sheet-quads. This added the
engine extension **rotation on `SHEET_QUAD`** (`DrawCommand`/`DrawList`
`addSheetQuad(..., angleDeg, ...)` overload; drain routes `angleDeg != 0` →
`QuadBatch.appendRotated`, keeps the cheap axis-aligned path for dense tile
layers). Reused by UNITS/DRONES. ⚠️ **Parity risk to verify**: rotation moved
from `SpriteAPI.setAngle` to `appendRotated` (manual CCW corners) — confirm
convoy chassis/turrets aren't mirrored or sign-flipped. Debug overlays (docking
paths, selected-vehicle) stay inline after the drain. The sprite-sheet batch
registration is now a shared `registerSpriteSheetBatches` helper. See
[`complete/story-g-convoy-system.md`](complete/story-g-convoy-system.md).

**Story H (SHUTTLES → `ShuttleRenderSystem`) shipped & verified** (fallback
deleted) — aircraft hull + turrets as `SPRITE`s, engine FX as a `CUSTOM` (own-GL
escape hatch); first system to combine both. All whole-texture sprites → no batch
registration. **No rotation-parity risk** (uses `SPRITE`/`setAngle`, identical to
inline — unlike CONVOY). `drawTurretLayer` + `RECOIL_*` constants stay (still
shared by the inline map-turret/drone passes). See
[`complete/story-h-shuttles-system.md`](complete/story-h-shuttles-system.md).

**Story I (DRONES → `DroneRenderSystem`) shipped & verified** (fallback deleted) —
drones above the roof layer: hull `SPRITE` + HP-bar `SOLID_RECT`s, same crash/
vision gating + fade as the inline pass. First system added straight into the
registry (list entry + dropped inline drain slot). Drone sprite load hoisted to
`BattleScreen.attach` so collect stays GL-free; `HP_*` constants made
package-visible (shared with the inline UNITS HP bars). No rotation-parity risk
(`SPRITE`/`setAngle`). See
[`complete/story-i-drones-system.md`](complete/story-i-drones-system.md).

**UNITS — the heavy one — is ✅ SHIPPED & VERIFIED** (Story J, slices 1–7 + a
black-box regression fix), moved to
[`complete/story-j-units.md`](complete/story-j-units.md). The whole `renderUnits`
pass + sub-methods are gone — UNITS is fully command-driven via
`UnitRenderService`'s six per-stratum sweeps. In-game verified post-fix
(transparency clean; recoil/footprints/hubs/dead-poses/facings/flip/bars all
correct). With UNITS done, **every world pass is migrated** — the only remaining
battle-render work is the cross-layer **"Final"** collapse (below) and the
deferred `QuadBatch.flush` perf spike. The recap below is the original shape
rationale (kept for context); the slice log is in the complete/ doc.
`renderUnits` is five strata painted in fixed order into the `UNITS` layer
(turret footprint+sprite → hub footprint+sprite → dead sprites → live sprites →
HP bars, bars last = layer-wide on top). It migrates into the command model **and**
fixes the system's internal shape to the ECS-native target: a **type-flyweight
`RenderAppearance` descriptor + capability tags** (render-side, keyed by
`UnitType`; `Unit` stays `SpriteAPI`-free per the overview boundary), consumed by
a stateless `UnitRenderService` that **sweeps once per render-task-kind**
(footprints → sprites → bars). Per-stratum sweeps dissolve the bars-on-top
paint-order constraint (the bar sweep just runs last) — this is `UnitRegistry`
Phase 2 applied to the render tier. One engine addition: a **vertical-flip option
on `SHEET_QUAD`** for the SOUTH-weapon-up mirror. `renderDroneHubs` lazy-load +
`drawTurretLayer` get absorbed; reusable `HpBarDecor`/`GroundFootprint` emit
helpers fall out. **Slices 1–3 shipped:** J1 — `HpBarDecor` is the canonical
two-rect bar (owns `HP_BG`/`HP_FG`/`HP_BAR_H`); `DroneRenderSystem` retrofitted
onto it, inline UNITS pass re-points at it. J2 — `RenderAppearance` flyweight
`of(UnitType)` (capability tags `spriteKind`/`drawsFootprint`/`drawsHpBar`/
`hasDeathPose`/`frameLayout`/`renderScale`), `RenderAppearanceTest` pins the
derivation. J3 — `UnitRenderService` (`layer() == UNITS`) stands up the
per-stratum-sweep consumer; its **dead-sprite sweep** is the first flyweight
consumer, emitting one batched `SHEET_QUAD` per corpse. Inline `renderUnits`
dropped `renderDeadUnits` (deleted) for `drainLayer(UNITS)` at the dead slot;
dead sheets registered in `buildTileBatches`. J4 — `GroundFootprint` helper (owns
`ROAD_FILL`, camera-free one-cell pad); `UnitRenderService` gained
`sweepFootprints` → `sweepTurretBodies` → `sweepHubBodies` (then the J3 dead
sweep), absorbing `renderTurrets`/`renderDroneHubs`/`drawTurretLayer`/
`renderTurretQuadFallback` (all deleted, with `ROAD_FILL`); `renderUnits` head
collapsed to one `drainLayer(UNITS)`; `DEFENDER_COLOR` package-visible; hub
lazy-load hoisted to `BattleScreen.attach`. **Intentional**: footprints sweep
before all bodies now (was per-type interleaved). J5 — `sweepLiveSprites` (last
sprite stratum) emits live infantry as `SHEET_QUAD`s, claiming the
`SpriteKind.SHEET` types (tag-driven, replacing the inline instanceof excludes).
**Engine add**: `QuadBatch.appendFlippedV` + a `flipV` flag on the `SHEET_QUAD`
command for the SOUTH-weapon-up mirror. Frame-selection helpers + `Facing`/
`EightWayFacing` enums + `WEAPON_UP_TIME` + faction-fallback colors moved into the
service; `BattleRenderer` deleted `renderUnitSprite`/`renderUnitQuadFallback` +
those, and registers live/dead/aim sheets in `buildTileBatches`. J5 critique came
back **clean** (flip math + pooled-slot `flipV` hygiene both verified correct).
J6 — `sweepHpBars` runs last (`drawsHpBar` tag = combatant && !drone), faithful
port of the inline bar loop via `HpBarDecor`. With the last stratum migrated,
`renderUnits` was deleted outright (slice 7 folded in) and `renderWorld` calls
`drainLayer(RenderLayer.UNITS)` directly. **The whole `renderUnits` pass +
sub-methods are gone; UNITS is fully command-driven via `UnitRenderService`'s six
sweeps.** Only the in-game verify (J3+J4+J5+J6 together) remains before Story J
moves to `complete/`.

**Structural: the `List<RenderSystem>` registry shipped.** `RenderSystem` now
declares `layer()`; `BattleRenderer` holds an ordered `List<RenderSystem>`
(ground/vehicle/doodad/convoy/shuttle) instead of five named fields. `renderWorld`
split into a **collect-all phase** (one registry loop — collect is layer-tagged +
GL-free, so order among collects is immaterial) then the existing **drain
sequence**, where each migrated layer's drain is interleaved with the not-yet-
migrated inline passes at their paint-order slots. Behavior-identical (no GL/paint
change). The endgame: as each inline pass migrates it joins the list and its
bespoke drain slot folds into a drain-all loop, leaving collect-all → drain-all.
See [`complete/story-registry-consolidation.md`](complete/story-registry-consolidation.md).

**Story E shipped — what landed:**
- `DrawCommand.SolidRect` + the pooled, mutable tagged command buffer
  (`2ee5b89`): `DrawList` recycles per-layer `DrawCommand[]` slots so the dense
  GROUND pass (~38k tiles) allocates nothing steady-state.
- `DrawListRenderer.drain` is strict-painter: submission order = paint order,
  one `GlStateBracket` per layer, flush-on-sheet/kind-change. DOODADS converted
  (emits road-then-urban contiguous).
- `GroundRenderSystem` emits per-tile commands in cell order (base → nature
  overlay → doorway), then a wall pass; backing fill first.

Other passes after GROUND, rough increasing size: **VEHICLES / CONVOY / SHUTTLES /
DRONES** (single rotated sprites → existing `Sprite` command), then **UNITS**
(sprites + HP bars via `SolidRect` + turret/drone/dead sub-passes).

When the third or fourth system lands, introduce the **`List<RenderSystem>`
registry** + start collapsing `renderWorld` toward the systems-loop + drain
endgame (the overview's "Final").

**`QuadBatch.flush` perf — ✅ SHIPPED & VERIFIED** (−75% combined flush CPU;
722→178 samples, SolidQuadBatch 193→1).
See [`complete/perf-quadbatch-flush.md`](complete/perf-quadbatch-flush.md).
The lever was **confirmed** across 3 JFR captures: the per-vertex immediate-mode
loop (`glColor4f`+`glTexCoord2f`+`glVertex2f` × 4 = 12 JNI calls/quad) is 77.9%
of render CPU / 29% of total mod CPU; `append` (packing) is <1%, so it's
submission cost, not float-packing or GL-submit-stall or flush-thrash (the drain
already coalesces). Fix landed: both `QuadBatch.flush` and `SolidQuadBatch.flush`
now use **client-side vertex arrays + `glDrawArrays`** (not a VBO — its
GPU-residency win is marginal for fill-trivial quads and adds streaming/binding
hazards), bracketed with `glPushClientAttrib`/`glPopClientAttrib`. `append`
untouched. Engine-only, below the command model — game `RenderSystem`s never see
it. **Next:** in-game visual check + re-profile vs the 6/01 baseline, then move
the story to `complete/`. Follow-up spike (bigger work-reduction lever) stubbed:
[`stories/perf-ground-fbo-cache.md`](stories/perf-ground-fbo-cache.md).
Cross-ref backlog § Performance.

Carry-over follow-ups (opportunistic): dedupe `MARINE_TRACER`/`DEFENDER_TRACER`/
`bearingDeg()`; restore fuller inter-pass comments; drop pre-existing unused
imports (`ShuttleType`, `LightKernel`).

## Slice chain

A → B → ~~C (prove model on SHOTS)~~ ✅ → ~~D (first sheet pass + RenderSystem,
DOODADS)~~ ✅ → ~~engine/game package split (structural foundation)~~ ✅ →
~~pooled command buffer + SolidRect + strict-painter drain~~ ✅ →
~~E (GROUND → GroundRenderSystem; verified, fallback deleted)~~ ✅ →
~~F (VEHICLES → VehicleRenderSystem; verified, fallback deleted)~~ ✅ →
~~G (CONVOY → ConvoyRenderSystem + rotated SHEET_QUAD; verified, fallback deleted)~~ ✅ →
~~H (SHUTTLES → ShuttleRenderSystem; SPRITE + Custom; verified, fallback deleted)~~ ✅ →
~~RenderSystem registry (ordered `List<RenderSystem>` + `layer()`; collect-all
phase split out)~~ ✅ →
~~I (DRONES → DroneRenderSystem; SPRITE + SOLID_RECT; verified, fallback deleted)~~ ✅ →
UNITS (Story J — CODE-COMPLETE, in-game verify pending: flyweight `RenderAppearance` +
capability tags + per-stratum `UnitRenderService` sweep). Sub-slices:
~~J1 `HpBarDecor` + retrofit `DroneRenderSystem`~~ ✅ →
~~J2 `RenderAppearance` table+tags (flyweight `of(UnitType)`; no pass change)~~ ✅
→ ~~J3 dead units `SHEET_QUAD` (`UnitRenderService` + dead sweep; live-verify
pending)~~ ✅ → ~~J4 turret/hub footprint+sprite (`GroundFootprint` helper;
absorbed `renderTurrets`/`renderDroneHubs`/`drawTurretLayer`; hub lazy-load
hoisted; live-verify pending)~~ ✅ → ~~J5 live infantry (`sweepLiveSprites` +
`QuadBatch.appendFlippedV`/`flipV` engine add; frame helpers moved game-side;
live-verify pending)~~ ✅ → ~~J6 HP-bar sweep (`sweepHpBars` last; `drawsHpBar`
tag)~~ ✅ → ~~delete `renderUnits` fallback (folded into J6 — `renderWorld` calls
`drainLayer(UNITS)` directly)~~ ✅ →
~~Final (collapse `render()` to systems-loop + drain — fold inline passes into
the collect-all/drain-all loop)~~ ✅ **SHIPPED & VERIFIED.**
Every formerly-inline pass joined `worldSystems` via the new
`RenderSystem.of(layer, collectFn)` adapter (emitting a `Custom`, the own-GL/FBO
escape hatch); `renderWorld` is now just `collect-all` then
`for (RenderLayer l : values()) drainLayer(l)`. Per-seam ordering rationale moved
to `RenderLayer` per-constant javadoc. Behavior-identical by construction. See
[`complete/story-final-collapse.md`](complete/story-final-collapse.md). With Final
done, the **only** remaining battle-render work is the deferred `QuadBatch.flush`
perf spike.

## Watch-outs

- Pass order in `renderWorld` is semantic — `RenderLayer` is the verbatim list.
  Emit a migrated pass into its existing layer; don't re-derive order.
- **Drain order is now `RenderLayer.values()` ordinal order — and that's correct
  (Final).** This watch-out used to say the opposite: while inline passes still sat
  *between* migrated drains, an ordinal-order drain would have floated
  CONVOY/SHUTTLES ahead of them. That hazard is **retired** — every pass is now a
  `worldSystems` producer, so ordinal order == paint order. The new constraint:
  `RenderLayer` ordinal **is** the verbatim paint order, and within a shared layer
  (GROUND, CONVOY) registry list order is submission order. Don't reorder the enum
  or the registry list without re-deriving the per-seam rationale (now in
  `RenderLayer` per-constant javadoc).
- Keep every batched flush inside a `GlStateBracket`. The drain owns this (one
  bracket per layer, spanning all batch/sprite runs); `Custom` callbacks drop out
  of the bracket and own their own GL.
- **`SPRITE` (`renderAtCenter`) pollutes blend state mid-bracket** — fixed, but
  the lesson holds. A `SPRITE` command draws via Starsector's
  `SpriteAPI.renderAtCenter`, a foreign call that mutates the blend func/colorMask
  and doesn't restore them. UNITS is the first layer to run `SPRITE` (turret/hub)
  *then* `SHEET_QUAD` (units) in one bracket, and the sheet quads inherited the
  foreign blend → transparent texels drew opaque (black boxes around units/mechs/
  bodies). Fix: the drain re-asserts `GlStateBracket.applyTextured2DState()` on the
  `SPRITE`→batch transition (`spritePolluted` flag). Any future layer mixing
  whole-`SPRITE`s with batched quads is now covered.
- **The drain is strict-painter now** (submission order = paint order), not
  first-touched. A migrated system must therefore emit in paint order, and emit
  each sheet's quads contiguously to batch them (one flush per contiguous run).
  GROUND relies on spatial coherence (street/grass regions) for long runs.
- FBO accumulators (decal/lightmap) are still inline — they'll need `Custom`
  (or a dedicated command) when their layers migrate. The UNITS service contract
  is named "emit render *tasks*" because the same sweep eventually emits a second
  stream (decal-accumulator writes) alongside `DrawCommand`s — but units drop no
  decals, so Story J exercises only the draw stream.
- **UNITS flyweight boundary**: `RenderAppearance` + capability tags are
  render-side (`ops/battleview`), keyed by `UnitType`. Do **not** add `SpriteAPI`
  or render fields to `Unit`/`UnitType` (overview's hard rule). The "component on
  the entity" is a type→descriptor lookup.
- **UNITS engine gap — CLOSED (J5)**: the SOUTH-weapon-up vertical mirror shipped
  as `QuadBatch.appendFlippedV` + a `flipV` flag on the `SHEET_QUAD` command
  (`addSheetQuadFlippedV`; drain routes rotated/flipped/plain). Axis-aligned only.
  Canonical `setSheetQuad` resets `flipV=false` so pooled slots can't leak it.
- **In-game-pending validation**: **none.** Final (collapse) verified in-game
  (zones overlay, decals/craters, fog edges, roofs over unseen interiors, objective
  pulses, compound rings, convoy debug paths, shots/contrails, impact FX, flyby,
  day/night lightmap all correct). Every story — SHOTS (C), DOODADS (D), GROUND (E),
  VEHICLES (F), CONVOY (G), SHUTTLES (H), DRONES (I), UNITS (J), Final — verified;
  fallbacks deleted. **The battle-render structural reorg is complete** — only the
  deferred `QuadBatch.flush` perf spike remains.
