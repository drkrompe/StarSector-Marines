# Story J — UNITS pass → component/tag render service — 🚧 IN PROGRESS (slice 1 shipped)

The heavy pass, and the last inline world pass before the endgame. `renderUnits`
is not one pass — it's **five strata painted in a fixed order into the `UNITS`
layer**, each a sweep over the unit list. This story migrates it into the
command model, but unlike C–I it also fixes the *internal shape* of the system to
the ECS-native target the rest of the battle tier is converging on
(`[[battle_services_systems]]`, `[[feedback_entity_for_loop_endgame]]`):
**type-flyweight appearance + capability tags, consumed by a stateless service
that sweeps per render-task-kind and emits the tick's draw tasks.**

## The pass today (paint order = submission order under the strict-painter drain)

| # | Stratum | Entities | Command kind | Notes |
|---|---------|----------|--------------|-------|
| 1 | `renderTurrets` | `MapTurret` | footprint `SOLID_RECT` (ROAD_FILL) → barrel-recoil `SPRITE` → base `SPRITE` | footprints batched first, then sprites; recoil offset `sin/-cos · pushPx` |
| 2 | `renderDroneHubs` | `DroneHubUnit` | footprint `SOLID_RECT` → hub `SPRITE` | **lazy `ensureDroneHubSprite()` inside the pass** (GL upload) |
| 3 | `renderDeadUnits` | dead w/ `deathPoseIdx≥0` | `SHEET_QUAD` (dead sheet, frame by pose) | no flip, no HP bar |
| 4 | live-sprite loop | live non-turret/hub/drone | `SHEET_QUAD` (unit/aim sheet, frame by facing) | **flipY** for SOUTH-weapon-up; colored-quad fallback |
| 5 | HP-bar loop | combatant non-drone | two `SOLID_RECT` (`HP_BG`+`HP_FG`) | iterates **after every sprite** → bars on top of the whole layer; `barY` varies turret/hub/normal |

Load-bearing invariant: **stratum 5 (HP bars) paints after strata 1–4** — bars
are a layer-wide top sub-stratum across *all* entity kinds. (Drones differ: they
live in the `DRONES` layer above roofs and emit hull-then-bar *per drone*, a
pre-existing minor inconsistency we don't propagate here.)

## Target shape — render = `UnitRegistry` Phase 2 applied to the render tier

`UnitRegistry` is already a dense entity registry with SoA component columns
(`hp`, `cellX/Y`, `renderX/Y`, the timers…) living in parallel with the legacy
`List<Unit>`, with a documented "Phase 2 flips hot iteration to read from the
registry." This story is **Phase 2 for rendering**: a stateless consumer sweeps
the dense set by component/tag and emits the tick's render tasks.

Render data is **two-tier**, which is why the flyweight model is correct (not a
compromise):

- **Static appearance is type-flyweight.** Every marine shares one sheet, frame
  layout, `renderScale`, footprint color, `visualCells`, HP-bar policy. A
  `RenderAppearance` descriptor keyed by `UnitType` / turret-kind; entities point
  at it. *You do not store a sheet ref per entity.*
- **Dynamic inputs already live in SoA / on the subclass.** `hp`/`maxHp`/
  `renderX`/`renderY` in the registry; `recoilTimer` (MapTurret), `crashTimer`
  (Drone); facing derived from `targetId`/path.

So the "component/tag" is a **capability set + a flyweight appearance**, and the
service does **one sweep per render-task-kind**:

```java
RenderAppearance app = appearanceOf(u.type);   // flyweight, render-side table
if (app.drawsFootprint) ...
if (app.spriteKind == SHEET) ...
if (app.drawsHpBar) ...

// UnitRenderService.collect: one sweep per stratum, order = paint order
sweepFootprints(units, out);   // turret + hub pads
sweepSprites(units, out);      // dead, then live (+ recoil/base for turrets, hub)
sweepHpBars(units, out);       // last → on top, layer-wide
```

### This *dissolves* the paint-order tension (it's not a workaround)

Per-stratum sweeps = one query per task-kind. "HP bars on top across all entity
kinds" is simply **the bar sweep runs last**. The per-entity decorator ordering
trap (a later entity's sprite painting over an earlier entity's bar) cannot
arise, because no entity emits its own bar inline — the bar sweep is a separate,
last pass. This is also exactly what makes "compose HP bars onto shuttles later"
correct: `ShuttleRenderSystem` just runs its own bar sweep last *within the
`SHUTTLES` layer*. The constraint is only ever *within a layer*, and each system
owns its layer's sweep order.

### Capability tags replace the `instanceof` ladder

`instanceof MapTurret` / `u.type.combatant` / `deathPoseIdx≥0` **are** the tags
today, hardcoded. The migration declares them as `RenderAppearance` capabilities
(`spriteKind`, `drawsFootprint`, `drawsHpBar`, `hasDeathPose`, `frameLayout`)
resolved once at descriptor-build time from the sim type.

### Boundary — appearance is render-side, keyed by sim type

Per the overview's hard rule (*"`Unit` stays a pure data/sim object and never
imports `SpriteAPI`"*), `RenderAppearance` + the tag set live **game-render-side**
(`ops/battleview`), in a table keyed by `UnitType` (and the special sim
subclasses). The "component attached to the entity" is a *type → descriptor
lookup*, not a `SpriteAPI` field on `Unit`. The sim tier gains nothing. This is
the same flyweight relationship `BattleSprites` already has with `UnitType`,
just promoted to carry capability tags + geometry policy instead of only a sheet
cache.

## Second output stream — decal-addition tasks (forward-looking, parallel)

The service shape is "emit render *tasks*," not "emit draw commands," because the
same consumer eventually emits two streams per tick: `DrawCommand`s into the
`DrawList` (transient) **and** writes into the `DecalAccumulator` (persistent
FBO). **Units don't drop decals** (bullet impacts do), so UNITS exercises only
the draw stream — but the abstraction is named for both, and this lines up with
the still-deferred FBO-accumulator migration (decals/lightmap). Not in this
story's scope; noted so the service contract is shaped right.

## Engine gap to close (one real addition)

Live units use **flipY** (negative `setTexHeight`) for the SOUTH-weapon-up
vertical mirror. The batched `SHEET_QUAD` path (`QuadBatch.append`) has a fixed
UV orientation and no flip. UNITS needs a **vertical-flip option on `SHEET_QUAD`**
(flip flag on the command, or a `QuadBatch.appendFlippedV` that swaps `v0/v1`).
Isolated to the live-infantry slice — turret/hub/dead all use whole-`SPRITE` or
unflipped sheet quads.

## GL-free-collect chores (same discipline as DRONES)

- Hoist `ensureDroneHubSprite()` to `BattleScreen.attach` (it's a `loadTexture`
  inside the pass today) so `collect` stays GL-free.
- Register the unit / aim / dead sheets in `buildTileBatches`
  (`registerSpriteSheetBatches`) so live + dead infantry batch via `SHEET_QUAD`.
  This is also a perf win: today each unit is a separate immediate-mode
  `renderAtCenter`; batched, same-type units coalesce (many marines, one sheet).
- The tint-tracking `Set<UnitSpriteCache>` + `setColor(WHITE)` reset loops
  **disappear** — per-quad color is explicit in the command.

## Reusable task-emit behaviors (the composable units)

Game-side emit helpers the sweeps call (and other systems reuse):

- **`HpBarDecor.emit(out, layer, cx, topY, width, hpFrac, alpha)`** — the two
  `SOLID_RECT`s, layer-agnostic. `DroneRenderSystem.addBar` collapses into this;
  shuttles can adopt it later by running a bar sweep last in `SHUTTLES`.
- **`GroundFootprint.emit(out, layer, cellX, cellY, cellPx, color, alpha)`** —
  the ROAD_FILL pad under turrets/hubs.
- **frame-sheet sprite emit** — frame sub-rect → `SHEET_QUAD` (+ flip), shared by
  live + dead; carries the frame-selection helpers (`computeFacing`/`pickFrame`/
  `eightWay`) game-side.

## Slice chain (each independently shippable + in-game verified)

1. ~~**`HpBarDecor` + retrofit `DroneRenderSystem`** to use it.~~ **SHIPPED.**
   `HpBarDecor.emit(out, layer, cx, baseY, width, hpFrac, alpha)` is the canonical
   two-`SOLID_RECT` bar and now owns the bar's intrinsic style (`HP_BG`/`HP_FG`/
   `HP_BAR_H` moved off `BattleRenderer`). `DroneRenderSystem.addBar` collapsed
   into it; the inline UNITS pass re-points its `fillRect` calls at
   `HpBarDecor.*` (non-behavioral, awaiting its slice-6 migration). `HP_BAR_GAP`
   stays on `BattleRenderer` (placement, caller-owned). Behavior-identical (same
   geometry/colors); compile + suite green.
2. ~~**`RenderAppearance` table + capability tags**~~ **SHIPPED.**
   `RenderAppearance` (render-side, `ops/battleview`) is an `EnumMap<UnitType,…>`
   flyweight built once at class-load via `derive(UnitType)` — the inline
   `instanceof`/`combatant`/`deathPoseIdx` ladder stated once. Tags:
   `spriteKind` (`SHEET`/`WHOLE_SPRITE`/`NONE`), `drawsFootprint`, `drawsHpBar`,
   `hasDeathPose`, `frameLayout`, `renderScale`. `of(UnitType)` is the lookup.
   Per-*kind* turret geometry (`visualCells`, sprite) and footprint color stay
   resolved at sweep time (not type-flyweight) — see the class scope note. No
   pass change; `renderUnits` still reads the inline ladder (consumers land J3–J6).
   `RenderAppearanceTest` pins the derivation against `UnitType`. The
   `UnitType`↔subclass invariant (`TURRET`/`DRONE_HUB_STRUCTURE`/`DRONE` ⟺ the
   three subclasses) was verified before keying on type.
3. **Dead units → `SHEET_QUAD`** via the frame-sheet emit (simplest sprite case:
   no flip, no bar). Warm-up for batched infantry.
4. **MapTurret + DroneHub** → `GroundFootprint` + whole-`SPRITE` + (bar deferred
   to slice 6). Absorbs `drawTurretLayer` + `renderDroneHubs`; hoists the hub
   lazy-load.
5. **Live infantry** → `SHEET_QUAD` + the flip extension + frame-selection
   helpers. The core slice.
6. **HP-bar sweep** for turret/hub/unit via `HpBarDecor`, run last → confirm
   layer-wide bars-on-top holds.
7. Delete the `renderUnits` fallback after a live verify.

Slices 1–3 de-risk the pattern before the heavy infantry slice. Slice 2 is the
one that commits the codebase to the flyweight+tags shape.

## Verification

- `mcp__intellij__build_project` clean + `gradlew test` green at each slice.
- Live battle after each render-affecting slice (turrets/hubs render with
  footprints + recoil; dead poses correct; live infantry facings + weapon-up
  flip; HP bars on top across all kinds; fallbacks for missing sheets).
- Critique agent per non-trivial slice (`[[feedback_critique_pass]]`).

## Cross-refs

- `ops/battleview/BattleRenderer.java` — `renderUnits` + sub-passes being migrated.
- `battle/unit/UnitRegistry.java` — the Phase-1 SoA registry this extends to render.
- `ops/battleview/DroneRenderSystem.java` — the bar-emit retrofit target (slice 1).
- Memory: `[[battle_services_systems]]`, `[[feedback_entity_for_loop_endgame]]`,
  `[[render2d_batching]]`.
