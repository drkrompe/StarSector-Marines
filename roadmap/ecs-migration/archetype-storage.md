# Archetype-table storage (ECS migration — committed target)

> **Status: locked direction (2026-06-03).** Chosen over the sparse-set path
> after design review, under the build-it-right-up-front mandate
> ([[feedback_storage_foundation_build_right]]). **This supersedes
> [`component-model.md`](component-model.md)'s "keep the single-archetype dense
> table" target** and the transitional `LinkedHashMap`-backed
> `ComponentStore<T>`. Read [`overview.md`](overview.md) for the SoA design rules
> that still apply, and [`components-by-capability.md`](stories/components-by-capability.md)
> for the decomposition axis.

## The model

- **Entity = bare `long` id.** Monotonic, never recycled, no generation bits
  ([[feedback_skip_generation_bits]]). No `Unit`/`Entity` object — the id is the
  whole entity. This finishes the `collapse-unit-handle` endgame and unblocks the
  `Unit → Entity` rename (overview.md naming north star) by *achieving* the
  reality the name claims.
- **Component = a typed column of data**, never a per-entity object. Two backings:
  - **Primitive columns** — `Position{int cellX,cellY}` → raw `int[]`; `Health{float hp,maxHp}` → raw `float[]`. No boxing (the chosen "primitive-specialized" answer).
  - **Object columns** — only for genuine object payloads (e.g. a mech loadout holding refs) → `T[]`/`ObjectArrayList<T>`.
- **Archetype = a set of component types**, identified by a `long` bitmask
  (component id = bit index; component count stays < 64, so one `long` is the
  whole identity — cheap equality + `(mask & query) == query` matching).
- **One table per archetype** = the SoA columns for exactly that component set,
  plus a packed `long[] rowToEntity`. Rows are dense; removal is **swap-and-pop**
  (the proven `UnitRegistry` mechanism, now per-table).
- **Structural change (add/remove component) = move the row** between tables:
  copy the shared columns, swap-and-pop the source row, append to the dest table,
  and fix the location index for *both* the moved entity and the source tail that
  filled the hole. Rare per entity (spawn → live → dead is 1–2 transitions), so
  the move cost is paid seldom and bought back as contiguous hot iteration.

### Why archetype here (not sparse-set)

The hot combat tick reads `Position + Health + Combat + AiTimers` together; an
archetype table stores those as adjacent contiguous columns, so a system query
walks them with no per-entity sparse probe — the locality `UnitRegistry` gives
today, *without* welding everything into one table. And the corpse-cell problem
that started this **disappears**: a corpse is a row in `{Position, Identity,
Dead}`; `Position` is a real column there; death is the row-move out of
`{Position, Identity, Health, Combat, AiTimers}`. No dual-backed accessor, no
snapshot-at-death, no "which store am I in" archetype proxy. Capability *is* the
table.

## Core types (design sketch — illustrative, not final API)

```java
// Stable component id + how to make its column. Registered explicitly at World
// construction — NO reflection (sandbox forbids it; [[starsector_script_sandbox]]).
interface ComponentType<C> { int id(); Column newColumn(int capacity); }

// Typed, primitive-friendly. Hot systems grab the raw backing array once.
interface Column { void swapPop(int row); void copyRowTo(Column dst, int srcRow); int size(); }
final class IntColumn   implements Column { int[]   a; /* + ensureCapacity */ }
final class FloatColumn implements Column { float[] a; }
final class LongColumn  implements Column { long[]  a; }
final class ObjColumn<T> implements Column { Object[] a; }

final class ArchetypeTable {
    final long mask;
    final Column[] columns;     // ordered by component id
    long[] rowToEntity;         // packed, parallel to columns
    int rowCount;
    // append(entityId) -> row;  swapPopRow(row) -> entityId that moved into `row`
}

final class World {
    // fastUtil, no boxing: entityId -> packed(archetypeIndex<<32 | row)
    final Long2LongOpenHashMap entityToLoc;
    final List<ArchetypeTable> tables;            // index = archetypeIndex
    final Long2IntOpenHashMap maskToTableIndex;   // archetype mask -> table
    long mintEntity();
    long createEntity(long mask);                 // allocate row in the mask's table
    void addComponent(long e, ComponentType<?> t);    // -> row move
    void removeComponent(long e, ComponentType<?> t);  // -> row move
    Query query(long required, long excluded);    // cached matched-table list
}
```

Systems iterate a `Query`'s matched tables, grab the raw column arrays per table,
and run a tight row loop — the archetype analog of today's
"capture `hpArray()` at top of tick, walk `[0, liveCount())`" (overview.md rule 6).

## Constraints baked in (not deferred)

- **Determinism.** Swap-and-pop reorders rows on removal, and a query walks tables
  in a defined order (by ascending mask). Order-sensitive systems (damage apply,
  target tie-breaks) **sort their working set by `entityId`** — the existing
  gather-then-apply discipline ([[dense_registry_swap_pop_trap]],
  [[deferred_flush_released_target_guard]]). Document per system; never depend on
  raw intra-table row order.
- **No persistence — the world is ephemeral.** Marine-ops battles are transient:
  the game does not allow mid-battle save/load ([[battle_transient_no_save_load]]).
  So the world never serializes — the engine carries **no `Serializable` contract**,
  no relink dance, no round-trip test. Battle outcomes (casualties, loot, result)
  return to the campaign tier as plain data after the battle.
- **No reflection.** Every component type and column factory is registered in
  code; archetype matching is `long`-mask arithmetic. Nothing instantiates a
  component by class — the artemis-odb blocker (battle-render/overview.md) never
  applies to the hand-rolled core.
- **Archetype fragmentation.** Kept small by two rules:
  1. **Archetype membership = stable capability; volatile state = column value.**
     Cooldowns, timers, weapon-up, target id are *columns* in `Combat`/`AiTimers`
     (always present for combatants), **never** presence-toggled components — else
     every tick thrashes the tables. Render-derived state (facing, weapon-up
     visual) isn't a sim component at all (render resolves it per frame).
  2. **Coarse, lifecycle-aligned components** (below), so the real population is
     ~8–12 archetypes, not a combinatorial blow-up. A `Query` caches its matched
     tables and only rebuilds when a *new* archetype table is first created
     (rare), so per-tick query cost is a list walk.

## Layout & array lifecycle

**Layout: per-table column arrays** (each table owns `int[] cellX`, `float[] hp`,
… for its component set), chosen over the alternative of one global
partitioned-by-archetype array per component. Both give identical multi-component
iteration locality (contiguous runs per archetype); the partitioned variant just
adds lockstep co-sorting of every component array on each transition (the EnTT
"owning group" constraint) for no locality gain at our scale. Revisit
partitioning only if a profiled *single-component* sweep ever dominates.

- **Grow:** amortized doubling per column; all columns in a table share one
  `rowCount` and grow together.
- **Remove / transition-out:** **swap-and-pop** — last row fills the freed slot,
  fix `rowToEntity[slot]` + that entity's `entityToLoc`. Dense rows, no
  tombstones, no holes; `rowCount` is always the exact live count.
- **Transition:** copy shared columns src→dst, swap-pop src, append dst, update
  `entityToLoc` for the moved entity and the source tail.
- **Shrink:** trim capacity at a **low watermark with hysteresis** (e.g. when
  `rowCount < capacity/4`, shrink to `rowCount*2`), a cooldown so a boundary-
  hovering table doesn't thrash, and only between ticks — never mid-iteration.
- **Drained table (0 rows):** release backing arrays to a stub but **keep the
  table registered** — deletion churns `Query` matched-table caches + table
  indices, and these archetypes recur constantly as spawn/kill cycles. Cheap
  empty table beats cache churn.
- **`entityToLoc` hygiene:** `remove(id)` on *final destroy* (not transition).
  Ids are monotonic/never-recycled, so this bounds the map to live + corpses
  rather than total-ever-spawned.

**Scale note:** at N≈200 with per-battle-scoped state (tables discarded at battle
teardown; within-battle high-water bounded by peak concurrent per archetype),
bloat is minor. Build the **trim hook** into the core; keep the shrink *policy*
trivial — don't gold-plate an adaptive heuristic for a bounded, non-hot path.

## Proposed component decomposition (granularity — REVIEW THIS)

The granularity is the main open call; this is my proposal to react to. Grouped
by lifecycle-stable capability ([[feedback_components_by_capability_not_store]]):

| Component | Columns | Carried by |
|---|---|---|
| `Identity` | `UnitType type, Faction faction` | every entity; persists alive→dead |
| `Position` | `int cellX, cellY` | every spatially-present entity (incl. corpse) |
| `RenderPosition` | `float x, y` | every drawn entity (interpolation; distinct from int cell) |
| `Health` | `float hp, maxHp` | live damageable (NOT corpse) |
| `Combat` | `float attackDamage, range, accuracy, cooldownTimer, attackCooldown` + burst + secondary-weapon scalars; `long targetId` | combatants |
| `Movement` | `moveProgress`, path ref | path-executing entities (kinematic) |
| `AiState` | fallback cell + timer, reposition cooldown, wander dwell | thinking entities (AI decision cadence) |
| `MechLoadout` | (existing `MechLoadoutComponent`) | mechs |
| `Crashing` | (existing `CrashingComponent`, holds `AirBody`) | crashing air |
| `Corpse` | *(zero-data tag — pure presence)* | corpses (the dead-archetype marker) |

Resulting real archetypes: live-infantry `{Identity,Position,RenderPosition,Health,Combat,Movement,AiState}`,
live-mech (+`MechLoadout`), turret `{…,Combat}` (no Movement/AiState), drone, drone-hub,
**corpse `{Identity,Position,RenderPosition,Corpse}`**, crashing-drone. ~8–12 total.

Open granularity questions for you: (a) is one fat `Combat` right, or split
primary/secondary/burst? (b) does `Identity.type` stay (pragmatic flyweight key
for `RenderAppearance` + lots of existing branches) or do we push toward "the
component set *is* the type" and shrink `type`'s role over time? (c) `RenderPosition`
stays its own component vs. folding into `Position` (I'd keep separate: int-logical
vs float-interpolated, different update cadence).

### Appearance is authored component data

Appearance is **first-class, controllable component data** authored by systems, not
a render-derived view (this corrects an earlier "presentation is derived" detour) —
the MoonLight `Renderable`-on-component pattern. Authoring it as data is what lets a
system swap a corpse to any sprite, drive an animation, or override appearance for a
one-off; derivation can't. It splits into two components (the second optional — i.e.
a separate presence):

- **`Sprite { int sheet, int index, int flipV }`** — the authoritative "draw this,"
  and the *only* thing the render collector reads. **One `Sprite` = one drawn quad.**
  (`flipV` is an `INT` 0/1 — the engine's column kinds are INT/LONG/FLOAT/OBJECT;
  add a BOOL kind later only if it earns its keep.)
- **`Animation { int animId, int frame, float frameTimer }`** — optional; present
  only on animated entities. Not a second visual — a *driver*: an `AnimationSystem`
  over `{Sprite, Animation}` advances the cursor and **writes `Sprite.index`**.

The animation *definition* (frame-index list + per-frame durations, loop/speed) is
flyweight data in the **sprite registry** keyed by `animId`
([unified-sprite-registry](../battle-render/stories/unified-sprite-registry.md));
per-entity stores only `animId` + the cursor.

**Render reads only `Sprite`** — it knows nothing about animation, facing, or sim
state. Everything else is a *system that authors `Sprite`*:

- Directional unit (marine) → just `Sprite`; a `FacingSystem` reads movement/target
  and writes `index` (+ the SOUTH weapon-up `flipV`). No `Animation`.
- Animated FX → `Sprite + Animation`; `AnimationSystem` writes `index`.
- Animated *and* directional later (walk × 8) → `FacingSystem` picks
  `Animation.animId`, `AnimationSystem` advances it → one `Sprite`. Many drivers,
  one output.

Boundary constraints (narrow): (1) the stored value is a **tier-neutral handle**
(sheet/frame/anim id resolved by the render-tier registry), never a `SpriteAPI`;
(2) sim *logic* never reads these, so they live in the same `EntityWorld` as
render-authored components (`RenderPositionComponent` precedent). The `Corpse` tag
marks the dead archetype; a corpse system authors its `Sprite` (frozen frame or an
animation) — fully swappable.

#### Extra visuals are child entities

One `Sprite` = one quad, deliberately. A smoke plume / muzzle flash isn't a second
sprite *on* the unit — it's its **own entity**, because it has an independent
lifecycle (smoke lingers after the unit dies):

```
FX entity = { Sprite, Animation, Position, AttachedTo{parentId, offX, offY}, Lifetime{remaining} }
```

- A spawner system creates it on an event (fire → flash at the barrel; death → poof).
- `AttachedTo` glues it to the parent — resolved per-frame against the parent's
  *interpolated* position + offset, so it tracks smoothly. (Detached FX drop
  `AttachedTo` and drift on their own `Position` + velocity.)
- `Lifetime` reaps it when the one-shot anim ends.

Child entities are `createEntity`/`destroy` — cheap, no archetype thrash (born/die,
they don't transition).

**Scope.** Only *sprite-quad* FX become entities. Ribbon contrails and FBO
decals/lightmap aren't quads — they stay specialized render passes (battle-render's
`Custom` escape hatch). And two things stay **derived per-frame** by the collector,
never stored: interpolated screen position and vision-fade alpha (a damage-*flash*
tint, being authored, would be a `Sprite` field / small `Tint` component if a flash
system ever needs it — deferred).

#### Sketch (illustrative registrations + the animation system)

```
// render-tier component registrations (ids continue the game's component space)
ComponentType SPRITE    = world.register(SPRITE_ID,    "Sprite",    INT, INT, INT);   // sheet, index, flipV
ComponentType ANIMATION = world.register(ANIMATION_ID, "Animation", INT, INT, FLOAT); // animId, frame, frameTimer
ComponentType ATTACHED  = world.register(ATTACHED_ID,  "AttachedTo", LONG, INT, INT); // parentId, offX, offY
ComponentType LIFETIME  = world.register(LIFETIME_ID,  "Lifetime",  FLOAT);           // remaining

// AnimationSystem (per tick): advance cursor, write the frame into Sprite
for (ArchetypeTable t : world.matched(animQuery /* required {SPRITE, ANIMATION} */)) {
    int[]   animId      = t.ints(ANIMATION, 0).array();
    int[]   frame       = t.ints(ANIMATION, 1).array();
    float[] timer       = t.floats(ANIMATION, 2).array();
    int[]   spriteIndex = t.ints(SPRITE, 1).array();
    for (int r = 0; r < t.rowCount(); r++) {
        AnimDef def = registry.anim(animId[r]);
        timer[r] += dt;
        if (timer[r] >= def.duration(frame[r])) { timer[r] -= def.duration(frame[r]); frame[r] = def.next(frame[r]); }
        spriteIndex[r] = def.frameIndex(frame[r]);
    }
}
// render collector then reads SPRITE only, applying per-frame transform (interp pos, fade alpha).
```

See [[feedback_appearance_authored_component]].

## Sprites stay a separate flyweight pool

Sprite sheets are **not** archetype columns — they're a shared resource pool
(many entities → one sheet) keyed by a minted handle, render-tier, behind the
[unified sprite registry](../battle-render/stories/unified-sprite-registry.md).
The sim-side entity carries at most a tier-neutral `Identity.type`; render resolves
the sheet from it (the `RenderAppearance` flyweight + the registry). Boundary holds:
the sim never stores a `SpriteAPI`.

## Migration (landable pieces, fixed target — not exploratory nudges)

1. **Build the core** (`Entity` id mint, `ComponentType` registry, `Column`
   impls, `ArchetypeTable`, `World`, `Query`) as new code with tests:
   structural-move + swap-and-pop location fixup, query-cache invalidation, and the
   array lifecycle (grow + shrink). No behavior wired.
2. **Prove on the corpse path first** — smallest, already partly componentized
   (`Dead`+`Position`+`RenderPosition`+`Identity`). Death = row-move; corpse keeps
   its cell; `UnitRenderService.sweepDeadSprites` reads columns. Validates the
   whole model end-to-end on low-risk surface.
3. **Migrate live combat** — model the live archetypes; port `UnitUpdateSystem`,
   `DamageResolver`, AI/squad systems to `Query` + column iteration, capability by
   capability, **toward the table model** (no reverting to a mega-table).
4. **Delete** `UnitRegistry`'s mega-table and the `LinkedHashMap`
   `ComponentStore<T>` once all columns/components live on the archetype `World`.
   The four already-migrated components (`Crashing`, `RenderPosition`, `DeadBody`,
   `MechLoadout`) fold into columns/archetype membership.
5. **Unified sprite registry** lands in parallel (render-tier, independent).

## Sequencing — engine layer first, then game retrofit

This is engine-layer work ([[user_engine_game_framing]]): the archetype core is
**game-agnostic** — it knows entities, component types, columns, archetypes,
queries, and array lifecycle, and nothing about `UnitType`/`Position`/combat. So
it is built and fully tested standalone with **synthetic** components, and the
**component-granularity questions above are deferred to the game-retrofit
stories** — they're game decisions, not engine ones, and don't block the core.

Other sessions are in a holding state for the related (game-layer) code, so the
earlier coordination blocker is lifted. Residual doc hygiene: rewrite
[`component-model.md`](component-model.md)'s "keep the single-archetype table"
target + Phase C ("deferred, gated on heterogeneity") to point here — the existing
`LinkedHashMap`-backed `ComponentStore<T>` is now **transitional**, to be replaced
during retrofit, not extended.

Build order: (1) engine core `com.dillon.starsectormarines.battle.ecs` + tests;
(2) retrofit stories move the game onto it per the Migration section above.

## Cross-refs

- [`components-by-capability.md`](stories/components-by-capability.md) — the
  decomposition axis (rewritten to this model).
- [`component-model.md`](component-model.md) — superseded target; needs the
  rewrite noted above.
- `battle.unit.UnitRegistry` (the mega-table being dissolved),
  `battle.component.ComponentStore` (transitional, being replaced).
- Memory: [[feedback_storage_foundation_build_right]],
  [[feedback_components_by_capability_not_store]], [[feedback_skip_generation_bits]],
  [[dense_registry_swap_pop_trap]], [[starsector_script_sandbox]],
  [[starsector_persistence_pattern]], [[user_artemis_ecs_framing]].
```
</content>
