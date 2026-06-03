# Corpse path onto the archetype EntityWorld (retrofit step 2)

**Shipped 2026-06-03** — `b98c706` (after engine core `88d5511`, CommandBuffer
`955b6e5`, and the `battle.ecs` → `engine.ecs` package move `0faa8bd`).

First game consumer of the archetype-table engine
([`archetype-storage.md`](../archetype-storage.md) migration step 2): the corpse
home moved off `ComponentStore<DeadBodyComponent>` + the shared
`RenderPositionService` entry onto a **corpse entity** in the battle
`EntityWorld`.

## What landed

- **`battle.component.BattleComponents`** — the game-side component-type
  registry for the per-battle world: `IDENTITY {UnitType, Faction}` (OBJECT
  columns), `POSITION {cellX, cellY}`, `RENDER_POSITION {x, y}`,
  `SPRITE {sheet, index, flipV}`, `CORPSE` tag — plus named field-index
  constants and the shared `corpses` query (per-world lifecycle, so the cached
  matched-table list dies with the battle).
- **`DeadBodySystem`** rewired: on each `DeathEvent` it `createEntity`s the
  corpse archetype — identity from the dead unit, logical cell from the event
  snapshot, draw position **frozen at death** (copied from the surviving
  render-position entry; the corpse never reads the shared store again), and
  the death pose **authored into `SPRITE.index`** (the appearance-as-authored-
  data pattern; `-1` = died without a pose roll, render skips it). Creates are
  walk-safe, so no command-buffer deferral needed.
- **`UnitRenderService.sweepDeadSprites`** and **`MissionResolver`'s casualty
  tally** are now pure column walks over `world.matched(c.corpses)` — no
  entity-id map lookups, no released-`Entity` handles.
- **`BattleSimulation`** owns `entityWorld` + `battleComponents`
  (`getEntityWorld()` / `getBattleComponents()`), and calls
  `entityWorld.flush()` at the end of each tick — the command-buffer barrier,
  established now so the first deferred consumer doesn't have to plumb it.
- **`DeadBodyComponent` deleted**; `WorldTest` re-exampled on
  `RenderPositionComponent`; `DeadBodySystemTest` rewritten against the world
  (spawn-on-drain, identity/cell/pose columns, frozen render position,
  no-deaths → empty world).

## Planned vs. landed

As planned in the migration section, with one note: "death = row-move" is
deferred to step 3 by construction — live units aren't in the world yet, so
death is currently a corpse-spawn (create), and becomes the
`remove(HEALTH)+add(CORPSE)` row-move proven in `CommandBufferTest` once the
live archetypes migrate in.

## Follow-ups

- `RenderPositionService` entries still survive registry release, but the
  corpse render no longer needs that property (it snapshots at death). When the
  remaining released-ref readers are audited away, entries can be dropped on
  release. (Javadoc updated to reflect the new contract.)
- `SPRITE.sheet` stays `0` until the
  [unified sprite registry](../../battle-render/stories/unified-sprite-registry.md)
  mints sheet handles; the render resolves the corpse sheet from
  `IDENTITY.type` until then.
