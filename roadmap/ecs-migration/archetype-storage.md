# Archetype-table storage (ECS migration — committed target)

> **Status: BUILT + LIVE (retrofit complete 2026-06-27).** Locked 2026-06-03 over the
> sparse-set path under the build-it-right-up-front mandate
> ([[feedback_storage_foundation_build_right]]); the engine and the full game retrofit
> have since shipped — the battle sim runs entirely on this `EntityWorld`, and
> `UnitRegistry` + the transitional `ComponentStore<T>` are deleted. The Migration
> section below records each shipped step. **This supersedes
> [`component-model.md`](component-model.md)'s "keep the single-archetype dense
> table" target** and the transitional `LinkedHashMap`-backed
> `ComponentStore<T>`. Read [`overview.md`](overview.md) for the SoA design rules
> that still apply, and [`components-by-capability.md`](complete/components-by-capability.md)
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
- **Safe structural change during iteration.** Destroy / add-component /
  remove-component all swap-pop rows in the table being walked, so a system must
  **not** apply them mid-query. The engine provides a `CommandBuffer`
  (`world.cmd().destroy(e)` / `.add(e, ct)` / `.remove(e, ct)`) that records ops
  into primitive parallel arrays during the walk; `world.flush()` applies them in
  FIFO order at a tick barrier. This is the gather-then-apply idiom made a first-class
  engine primitive. **Creates are not buffered** — `createEntity` is walk-safe (a new
  row only ever lands at/past the iterator's captured `rowCount`, and a grow reallocs
  away from the captured alias), so spawn child/FX entities inline and set their
  fields immediately. Apply-time guards: add/remove on an entity destroyed earlier in
  the same flush is skipped; double-destroy is a no-op.
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
| `Combat` | `float attackDamage, range, accuracy, cooldownTimer` + burst scalars (`int burstRemaining; float burstTimer; long burstTargetId`); `long targetId` | combatants (universal; SHIPPED 3c) |
| `SecondaryWeapon` | `MarineSecondary spec; int ammo; float cooldownTimer, actionTimer; long aimTargetId; int fired` | units with a secondary (optional presence; SHIPPED 3d) |
| `Movement` | `moveProgress`, path ref | path-executing entities (kinematic) |
| `AiState` | fallback cell + timer, reposition cooldown, wander dwell | thinking entities (AI decision cadence) |
| `MechLoadout` | (existing `MechLoadoutComponent`) | mechs |
| `Crashing` | (existing `CrashingComponent`, holds `AirBody`) | crashing air |
| `Corpse` | *(zero-data tag — pure presence)* | corpses (the dead-archetype marker) |

Resulting real archetypes: live-infantry `{Identity,Position,RenderPosition,Health,Combat,Movement,AiState}`,
live-mech (+`MechLoadout`), turret `{…,Combat}` (no Movement/AiState), drone, drone-hub,
**corpse `{Identity,Position,RenderPosition,Corpse}`**, crashing-drone. ~8–12 total.

Open granularity questions for you: ~~(a) is one fat `Combat` right, or split
primary/secondary/burst?~~ **DECIDED (3c, `a390b79`):** split by
capability-optionality, not by weapon-subsystem — `Combat` is the universal
primary capability *with burst folded in* (burst is never independent of
primary), and the optional secondary weapon is its own `SecondaryWeapon`
presence component. (b) does `Identity.type` stay (pragmatic flyweight key for
`RenderAppearance` + lots of existing branches) or do we push toward "the
component set *is* the type" and shrink `type`'s role over time? (c)
`RenderPosition` stays its own component vs. folding into `Position` (I'd keep
separate: int-logical vs float-interpolated, different update cadence).

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
decals aren't quads — they stay specialized render passes (battle-render's
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

1. ~~**Build the core**~~ — **SHIPPED** (`88d5511` core + tests, `955b6e5`
   deferred CommandBuffer, `0faa8bd` move to `engine.ecs`). Structural-move +
   swap-and-pop location fixup, query-cache invalidation, array lifecycle, and
   safe structural change during iteration, all engine-tested synthetically.
2. ~~**Prove on the corpse path first**~~ — **SHIPPED** (`b98c706`, see
   [`complete/corpse-archetype-retrofit.md`](complete/corpse-archetype-retrofit.md)).
   Corpse archetype `{Identity, Position, RenderPosition, Sprite, Corpse}` in the
   per-battle world; `DeadBodySystem` spawns it per DeathEvent with the pose
   authored into `SPRITE.index`; `sweepDeadSprites` + the MissionResolver tally
   walk columns; `DeadBodyComponent` deleted. (Death = row-move arrives with
   step 3 — live units aren't in the world yet, so death is a corpse-create
   for now.)
3. **Migrate live combat** — model the live archetypes; port `UnitUpdateSystem`,
   `DamageResolver`, AI/squad systems to `Query` + column iteration, capability by
   capability, **toward the table model** (no reverting to a mega-table).
   - **3a SHIPPED (`adb4bc9`): Health.** Every unit spawns into the world as
     `{Identity, Health}` (`UnitRegistry.allocate` mints the id, adopts it via
     `createEntity(long id, …)` — engine seam `e720e98` — writes identity once,
     seeds hp); the registry's hp/maxHp columns are deleted. **Death is now the
     row-move**: `DeadBodySystem` `transmute`s the dead entity to the corpse
     archetype — same id, identity carried by the shared-column copy, `Health`
     removed. Liveness = "has `Health` && hp > 0" (one tolerant-read probe),
     registry presence dropped out of the definition. Transitional registry
     adapters (`hpById`/`setHpById`/`maxHpById`) keep call sites on their
     existing receivers; the registry owns the world for the transition
     (ownership hops to the sim when step 4 dissolves it).
   - **3b SHIPPED (`b92c8bd`): Position.** Live archetype is
     `{Identity, Position, Health}`; the registry's cellX/cellY dense arrays
     are deleted; ~85 call sites across 20 consumer files converted to the
     by-id adapters (`cellXById`/`cellYById`/`setCellPosById`) via a 4-agent
     fan-out. `Position` persists alive→dead, so **"the corpse keeps its
     cell" is now the component's own lifecycle** — the death transmute's
     row-move carries it and `DeadBodySystem` stopped re-writing it from the
     event snapshot.
   - **3c SHIPPED (`a390b79`): Combat.** Live archetype is
     `{IDENTITY, POSITION, HEALTH, COMBAT}`; the registry's 8 combat dense
     arrays (attackDamage/attackRange/accuracy + cooldownTimer + targetId +
     burst{Remaining,Timer,TargetId}) are deleted; by-id adapters
     (`attackDamageById` … `burstTargetIdById`) read the world COMBAT columns
     and the `World` facade reroutes through them (signatures unchanged). ~17
     by-index call sites across 7 files converted to by-id; the dead
     `selfIdx`/`oIdx` locals fell out (cells already went by-id in 3b). The
     death transmute removes COMBAT (a corpse neither lives nor fights), so the
     corpse archetype is unchanged. **Granularity decided with the user:** the
     fat-vs-split question (a) is answered by capability-optionality, not by
     weapon-subsystem — `Combat` is the *universal* primary-weapon capability
     **with burst folded in** (burst is never independent of primary), and the
     *optional* secondary weapon is its own `SecondaryWeapon` presence component
     (the next slice, folding in the `#13` nullable `secondaryWeapon`/`ammo`
     fields). COMBAT is universal to every allocated unit (mirrors the old
     universal dense arrays — behavior-preserving); combatant-gated membership
     is a deferred refinement.
   - **3d SHIPPED (`a5da51a`): SecondaryWeapon — the FIRST optional capability
     as archetype presence.** A unit carries `SECONDARY_WEAPON`
     `{spec(OBJECT), ammo(INT), cooldownTimer, actionTimer(FLOAT),
     aimTargetId(LONG), fired(INT 0/1)}` iff it has a secondary, so "has a
     secondary" IS the archetype membership — no nullable field. Entity's
     nullable `secondaryWeapon`/`secondaryAmmo`/`secondaryFiredThisAction` and
     the three formerly-universal registry timer arrays are deleted. **Two grant
     paths:** born-with-it seeds via `Entity.seedSecondaryWeapon`/`seedSecondaryAmmo`
     (allocate adds the component to the spawn archetype); runtime acquisition
     uses `attachSecondaryWeapon` (an `addComponent` row-move — the model seam for
     a future pick-up-a-launcher mechanic, and how tests grant it post-spawn). The
     corpse transmute removes it (no-op for units that never had it). Consumers
     **presence-gate** on `hasSecondaryWeapon` before any secondary read (the
     reads are fail-loud without the component) — three timer-first reads were
     reordered. `TacticalScoring.canRocketTarget`/`effectiveAttackRange`/
     `scoreWeaponAffinity` became instance methods (they need `registry`). This
     proves the conditional-membership path the `Crashing`/`MechLoadout` fold-in
     will follow. **Future (not built):** more secondary weapon types will join
     the `spec` flyweight; richer AI may query the equipped weapon to decide
     what the unit can do at any moment ("rocketeer" is not an archetype — "has a
     secondary" is). Suite green at 757.
   - **Next capabilities:** Movement (`moveProgress` + path ref), AiState
     (reposition/fallback/wander), then fold `Crashing`/`MechLoadout`
     ComponentStores into archetype membership.
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

Build order: (1) engine core `com.dillon.starsectormarines.engine.ecs` + tests;
(2) retrofit stories move the game onto it per the Migration section above.

## Cross-refs

- [`components-by-capability.md`](complete/components-by-capability.md) — the
  decomposition axis (rewritten to this model).
- [`component-model.md`](component-model.md) — superseded target; needs the
  rewrite noted above.
- `battle.unit.UnitRegistry` and `battle.component.ComponentStore` — **both deleted.**
  The dense mega-table and the transitional store were fully folded into the archetype
  `EntityWorld`; see [`complete/dissolve-unit-registry.md`](complete/dissolve-unit-registry.md)
  and [`complete/store-folds-and-render-position.md`](complete/store-folds-and-render-position.md).
- Memory: [[feedback_storage_foundation_build_right]],
  [[feedback_components_by_capability_not_store]], [[feedback_skip_generation_bits]],
  [[dense_registry_swap_pop_trap]], [[starsector_script_sandbox]],
  [[starsector_persistence_pattern]], [[user_artemis_ecs_framing]].
```
</content>
