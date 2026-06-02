# Story: World facade — two-faced component access, then delete `Unit.registry`

The terminal phase the [`entity-id-handle`](entity-id-handle.md) story pointed
at, and the access layer for [`component-model`](../component-model.md)'s
component grouping. This is what finally dissolves `Unit.registry` and earns the
`Unit` → `Entity` rename (overview.md's north star).

## Why (user, 2026-06-02)

After Phase A (duality collapse — `local*` shadows gone, `getHp`/`setHp`
fail-loud), the entity is *almost* a bare id: `Unit` still carries the
`registry` back-pointer so `u.getHp()` can self-route. The user wants the
artemis shape — **the entity is its `long` id; you fetch state by id from
stores** — expressed as:

> "the Artemis-like approach … `world.id(999).getOrNull(HpComponent.class)`"
> "World could construct these component classes from the dense data."
> "I need to be careful … we might lose our benefits of primitives for ECS and
> cache locality with pointer generation and creation. We could do this with
> dedicated hot primitives (hp, cellX, cellY, renderX, renderY, etc.), then have
> opt-in cold-path convenience."

## The decision: a two-faced `World`

`World` is a **facade/projection over the stores that already exist** — the
dense SoA `UnitRegistry` columns and the sparse `ComponentStore<T>`s
(`Crashing`, `DeadBody`, `RenderPosition`). It exposes two access faces, and the
split is the whole point — it's what keeps the ECS cache-locality win while
adding ECS ergonomics:

### Hot face — primitive accessors (no allocation, SoA preserved)

Mandatory dense columns every live entity has — hp, cell, render pos, combat
stats, AI timers — are read by id through **primitive** accessors:

```java
float hp = world.hp(id);
int   cx = world.cellX(id);
```

- Backed directly by the dense SoA arrays (`registry.hp[idx]` &c.). **Zero
  object construction, zero pointer chasing** — identical cost to today's
  `u.getHp()`.
- Bulk per-tick systems do NOT even go through this — they keep iterating the
  dense column arrays over `[0, liveCount())` as they do now. The primitive
  accessor is the by-id random-access path (held refs, cross-system reads).
- **No component object is ever materialized in a hot loop.** This is the
  guardrail that protects the primitives-for-ECS / cache-locality benefit the
  user flagged.

### Cold face — projected components (opt-in convenience)

`world.id(id)` returns an entity handle; `.get(Cmp.class)` / `.getOrNull(Cmp.class)`
fetches a component. Two backing kinds resolve differently:

- **Sparse object component → a real store lookup, zero construction.**
  `getOrNull(MechLoadout.class)` is `mechStore.get(id)` → the existing object or
  null. This is genuine artemis `ComponentMapper.get/has`: presence *is* the
  data. Optional capabilities (`mech`, future `VehicleBody`, `SecondaryWeapon`)
  live here — composition by presence, not nullable fields on `Unit`.
- **Dense column group → a view constructed from the arrays.**
  `get(Position.class)` reads `cellX[idx]`/`cellY[idx]` and returns a `Position`.
  This is the "World constructs components from dense data" idea. It **allocates
  per call**, so it is **cold-path only**: debug/UI, one-off cross-cutting
  queries, held-ref convenience. If a projected dense component ever gets hot,
  the fix is the hot-face primitive — not a flyweight band-aid. (Valhalla value
  classes later make `Position[]` a literal flat array, so the projection
  becomes zero-cost — "a layout swap, not a rewrite", per component-model.md.)

### Semantics: `get` vs `getOrNull`

Mirror artemis: `get(Cmp.class)` for components an entity is known to have
(mandatory groups; fail-loud if absent — a programming error), `getOrNull(Cmp.class)`
for optional capabilities where absence is a normal answer. Mandatory hot columns
use the primitive face (`world.hp(id)`), never a nullable fetch — every live
entity has hp, so a null-check there would be noise.

## How this dissolves `Unit.registry`

- `Unit.getHp()` → `world.hp(id)`; `u.getCellX()` → `world.cellX(id)`; the
  optional-capability fields (`mech`, …) → `world.id(id).getOrNull(Cmp.class)`.
- With no caller routing through `u.<accessor>()`, `Unit` no longer needs the
  `registry` back-pointer or `denseIdx` self-knowledge. **Delete `Unit.registry`
  + `denseIdx`.**
- `Unit` shrinks to **id + immutable archetype** (`id` label, `faction`, `type`,
  `rng`) + the capability components (now in stores). That is the `Unit` →
  `Entity` rename.

## Staged migration (always-green, no big-bang)

~516 self-accessor call sites across 72 files (`u.getHp()`, `u.getCellX()`, …) —
this is a per-group sweep, not one commit.

1. **Introduce `World` over the existing stores; prove both faces — SHIPPED
   (2026-06-02).** `battle.sim.World` (named there, not `battle.world` — that
   package is map/terrain): hot face `world.hp(id)`/`setHp` over new
   `UnitRegistry.hpById`/`setHpById` (one map probe + array read, fail-loud on a
   dead id); cold face `world.id(id).getOrNull(Cmp.class)` via a
   `Map<Class<?>, ComponentStore<?>>`. `EntityHandle` is a tiny record (allocates
   per `.id()` — cold-path only). Wired into `BattleSimulation` (`sim.world()`)
   over the existing `Crashing` + `DeadBody` stores. Proven by `WorldTest` (3
   tests): hot face hits the same dense slot as the registry/OO accessor + is
   fail-loud on a released/unknown id; cold face is presence-by-type (instance
   when present, null when absent or no store). **No production call site migrated
   yet** — the abstraction is in place, zero behavior change, suite green at 692.
   (Cold face proven with the existing `DeadBody` store rather than `mech`, which
   is still a `Unit` field — moving `mech` field→store is a later slice.)
2. **Per-group accessor sweeps** — `Unit` accessor → `world.<col>(id)`. Fanned
   to Sonnet ([[feedback_delegate_mechanical_sonnet]]) by disjoint file bucket
   (not by group — file-disjoint avoids concurrent-edit conflicts); design/verify
   on the main thread; full suite each wave. Prereq SHIPPED: the complete by-id
   `World` surface + `UnitRegistry.requireLiveIndex` + `BattleView.world()`
   (`c69a24b`).
   - **2a — AI decision layer SHIPPED (2026-06-02, `4c3ec2f`).** ~37 files: GOAP
     actions, infantry/mech postures + behaviors, drone swarm action, command
     objectives, debug panels — all reach state via the `BattleView`/`BattleControl`
     they already receive. 5 Sonnet agents, disjoint buckets, green at 705.
     **Decision-cadence only.** Out of this wave (deliberately): hot per-frame/
     per-tick loops (renderers, combat resolvers `DamageResolver`/`HeavyWeapons`/
     `HitResponseService`/`Detonations`, bulk systems `VisionService`/
     `UnitSpatialIndex`/`InfantryWeapons`), render accessors (`getRenderX/Y`), and
     optional-capability fields (`mech`). Leftover decision sites with no `World`
     handle in scope, pending a wired field/param: `InfantryUnitPrep.tickCooldowns`,
     `TurretAim`/`TurretFireService` statics, `DroneSwarmAction.tickPursue`/
     `clampGoalToLeash`, `SquadFallbackSystem.allMembersHome`, `SquadAlertSystem`.
   - **2b — no-sim-param services SHIPPED (2026-06-02, `00f2e1d` + `3d96e5a`).**
     Key refinement vs. the original "field-wire World everywhere" plan: most of
     these services are **dense iterators that already hold the registry + loop
     index `i`**, so the right conversion is `registry.<col>(i)` — zero map probe,
     strictly better than routing through `World` (which would re-probe an index
     it doesn't have). `World`/`registry` by-id was used only where a bare id /
     `Unit` ref is in scope without the index, resolving the index once.
     - Part 1 (`00f2e1d`): `AttackerIndexService.rebuild`,
       `SquadFallbackSystem.allMembersHome`, `SquadAlertSystem.clearSquadMemberTargets`,
       `NavigationService.rebuildOccupancyMap`, `VisionService.sweepUnitVisibility`
       (dense-index); `SquadMoraleSystem` (threads cellX/cellY arrays beside its
       hp/maxHp); `InfantryUnitPrep.tickCooldowns` (now takes `World`),
       `DroneSwarmAction` tickPursue/clampGoalToLeash (now take `BattleView`),
       `VisionService.tickFogCohort`/`addContributor` + `NavigationService.setPath`
       (by-id; NavigationService gains a setter-injected `registry` field —
       built before `World`).
     - Part 2 (`3d96e5a`): turret aim/fire statics — `TurretFireService` (World
       ctor field, resolve target cell once in `fire`), `TurretAim.tick` (World
       param; safe fail-loud since callers recreate `State` each tick so the
       target is always freshly acquired/live). Callers `TurretBehavior` +
       `DroneSwarmAction` pass `sim.world()`; `AirSystem` + `GroundSystem` gain a
       `World` ctor field.
   - **TacticalScoring (53 sites) — its own slice, NOT a mechanical sweep.**
     Several sites sit inside the `for dy/for dx` candidate loops and
     dense-iteration loops, where a blanket `u.getCellX()` → `world.cellX(id)`
     would inject a HashMap probe per iteration (the cache-locality regression
     the guardrail forbids). The conversion is hoist-fixed-coords (self/target
     once before the loop) + dense-array the per-iteration reads (like
     `findBestTargetImpl` already does). It already holds `registry`, so no ctor
     wiring — just careful.
     - **Part 1 SHIPPED (2026-06-02, `e1f86cc`).** Every self/target-style ref
       (`self`, `target`, `cur`, `currentTarget`, `closerVisible`, `member`) now
       resolves its index once per method via `registry.requireLiveIndex` and
       reuses locals — removing the per-candidate `self.getCellX()` denseIdx
       reads from the firing-position + vantage loops (fewer indirections AND
       fail-loud on a released id). `findBestTargetImpl` passes candidate `ox/oy`
       through to `scoreThreatDensity`/`scoreZoneMismatch` (now `int`-coord
       signatures) so the dense loop stops re-reading `other.getCellX()`;
       `projectedRocketDamageOnTarget`'s dense-index timer reads use loop index
       `i`; `occupantsExcludingSelf` takes `selfCellX/selfCellY` (path-dest reads
       stay — plain `int[]` field, not denseIdx). Suite green at 723.
     - **Part 2 SHIPPED (2026-06-02, `ff105a9`) — gathered-list held-ref reads.**
       `findEngageableEnemyWithin` + `isHiddenFromAllEnemies` resolve each
       gathered enemy index once via `registry.requireLiveIndex` (gather skips
       dead → every entry live); both loops already do heavy per-element work (a
       full `findFiringPositionWithin` ring scan / a Bresenham), so the one probe
       per gathered enemy is negligible. The hot per-candidate path: static
       `countEnemiesWithLos` now takes pre-resolved parallel SoA columns (`int[]`
       cell + `float[]` range) instead of `List<Unit>`; `findFallbackPosition`
       projects the threat set once via a new `resolveThreatColumns` helper (one
       probe per threat, total), so the `~1089`-candidate scan reads plain arrays
       with ZERO registry probes — the guardrail honored exactly. Suite green at
       724.
     - **Still deferred to task #14** (denseIdx deletion, with perf in view):
       static `effectiveAttackRange` (only reads `shooter.getAttackRange()`, but
       it's `static` + called statically from `EngagePosture` and 3 tests, so
       conversion = make instance or take a range param — a ripple), and
       `alliesNearForSpread` pass-2 (`u.getCellX()` over a `destIndex` gather
       that happens *inside* a per-candidate call, so it needs either the dest
       index to expose cells or an accepted small per-candidate probe). Both are
       isolated, zero-probe-today via denseIdx, and naturally forced when
       `Unit.getCellX()`/`getAttackRange()` are deleted.
   - **2c — hot loops + render → dense-array / RenderPositionService**, not
     `world.<col>(id)` (the cache-locality guardrail). Renderers, combat
     resolvers, bulk systems. Careful, possibly per-file.
3. **Model the remaining optional fields as presence components** as they're
   touched (`secondaryWeapon`/`secondaryAmmo`, `assignedObjective`,
   `equipmentDropTarget`) — `getOrNull` instead of nullable-field + null-check.
4. **Delete `Unit.registry` + `denseIdx`.** Once no caller self-routes.
5. **`Unit` → `Entity` rename** (`rename_refactoring`, see
   [[intellij_mcp_refactor_tools]]).
3. **Model the remaining optional fields as presence components** as they're
   touched (`secondaryWeapon`/`secondaryAmmo`, `assignedObjective`,
   `equipmentDropTarget`) — `getOrNull` instead of nullable-field + null-check.
4. **Delete `Unit.registry` + `denseIdx`.** Once no caller self-routes.
5. **`Unit` → `Entity` rename** (`rename_refactoring`, see
   [[intellij_mcp_refactor_tools]]).

## Guardrails

- **Never materialize a component in a hot/bulk loop.** Hot per-tick work reads
  dense arrays or the primitive by-id face. The projected cold face is opt-in.
  This is the user's explicit constraint — protect the primitives/cache-locality
  win.
- **No generic `Aspect`/`World.process()` engine yet** ([`feedback_no_stopgap_dev`],
  [`feedback_ship_then_optimize`]). `World` is a hand-wired facade over the
  current stores; presence is hand-rolled per optional component. Phase C
  (generic aspect/bitset queries) stays gated on measured heterogeneity cost.
- SoA design rules (overview.md) still bind any storage change.
- Ids are monotonic, never recycled — no generation bits ([[feedback_skip_generation_bits]]).
