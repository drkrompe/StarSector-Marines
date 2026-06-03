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
   - **2d — hot loops + render → dense-array / by-id-once**, not
     `world.<col>(id)` blind-swept (the cache-locality guardrail). Renderers,
     combat resolvers, bulk systems. Careful, per-file. Per-site rule established:
     **bulk dense loops** (iterating `[0, liveCount())`) → `denseArray()` +
     `cellX/cellY` arrays, hoist once per iteration; **per-event / held-ref**
     (resolvers, hit-response) → `requireLiveIndex` once then by-index;
     `getRenderX/getRenderY` are **already** id-keyed via `RenderPositionService`
     (not denseIdx) — leave them; `isAlive()` stays (the liveness primitive,
     reworked in task #14). Parallel-phase index lookups are safe (releases/spawns
     are deferred to serial drains; `getOrNull` already probes `indexById`
     concurrently).
     - **Part 1 SHIPPED (2026-06-02, `884a4bd`) — combat resolvers.**
       `DamageResolver` (serial `APPLY_DAMAGE` drain → resolve target index once,
       by-index cell/hp, snapshot death cell; promotion loop already dense-array;
       mech-drain by index), `Detonations.detonate` AoE gather (bulk loop →
       dense-array, `ucx/ucy` hoisted — was 4-5 `getCellX()` per iteration),
       `HitResponseService` (parallel-phase; resolve target + `expectedTarget`
       index once), `HeavyWeapons.fireMechWeapon` + LRM-salvo LoS (indices once).
       Deferred to task #14: `FireStance.stanceFor` (static) +
       `DamageService:248` `getTargetId()` (holds only the id resolver by design).
     - **Part 2 SHIPPED (2026-06-02, `5c03cc0` + `66f439d`) — infantry + renderers.**
       `InfantryWeapons` (`5c03cc0`): burst gather → dense-array + by-index
       `getBurstRemaining(i)`; continuation pass resolves `requireLiveIndex` once
       per unit and **re-resolves across `fireShot`** (a killing round swap-and-pops
       and can relocate `u`'s slot); `fireShot` dist read resolves shooter/target
       index once. **`FlybyOverlay` was a NO-OP** — its apparent "15 sites" are all
       `getRenderX/getRenderY`, already id-keyed via `RenderPositionService` (the
       raw grep over-counted; render accessors don't block denseIdx deletion).
       Renderers (`66f439d`): `UnitRenderService` (5 sweeps) + `DroneRenderSystem`
       iterate `liveUnitAt(i)` where `i` IS the dense index
       (`liveUnitAt(i)=registry.get(i)`, `dense[i].denseIdx==i`), so
       cell/hp/secondaryActionTimer go by-index `registry.<col>(i)` zero-probe and
       the vision calls drop `u.denseIdx` for loop `i`. Deferred to task #14: the
       static `computeFacing`/`computeEightWayFacing` (nullable `sim`, touch
       `pathCell*`) — same awkward-static class as `effectiveAttackRange`.
     - **Part 3 SHIPPED (2026-06-02, `fab9d33` + `e092926`) — remaining systems.**
       11 files by the per-site rule. Dense iterators (`i`==denseIdx, zero-probe):
       `AirSystem`, `TurretDemolition`, `EquipmentDropService` (pickup+nearest),
       `SquadAlertSystem`, `VisionService.sweepUnitVisibility` (`u.denseIdx`→`i`).
       Per-event held-refs (`requireLiveIndex` once): `HubDemolition` cascade,
       `EquipmentDropService` emit, `DroneSpawner` tryLaunch, `BattleSimulation`
       isRoofShielded + targetOf. Narrowed-view sites (`sim.world().<col>(id)`):
       `GarrisonPatrol`, `UnitUpdateSystem`, `SquadDetailPanel`, `DroneSpawner`
       isCellOccupied. Green at 731.
     - **SLICE 2d (and the slice-2 accessor sweep) COMPLETE.**
3. **Model the remaining optional fields as presence components** as they're
   touched (`secondaryWeapon`/`secondaryAmmo`, `assignedObjective`,
   `equipmentDropTarget`) — `getOrNull` instead of nullable-field + null-check.
4. **Delete `Unit.registry` + `denseIdx` — task #14, IN PROGRESS (2026-06-02).**
   Clearing the last accessor holdouts that block the field deletion:
   - **`UnitSpatialIndex` denormalized (`06cecbd`) — the keystone.** Replaced the
     `ArrayList<Unit>` buckets with a pooled `Bucket` of parallel arrays
     (`units[]`/`cellX[]`/`cellY[]`); `rebuild` stores the SoA cell alongside the
     ref, `add(UnitRegistry,Unit)` resolves it once, `gather` filters on the stored
     snapshot int. No `denseIdx` read, no per-candidate probe in THE proximity
     primitive, zero-alloc steady state kept. Side benefit: bucketing + distance
     now both use rebuild-time positions (was bucket=rebuild-time, distance=live —
     a latent mismatch), matching the per-tick-snapshot contract.
   - **Low-ripple holdouts cleared (`c884f0b`):** `FireStance.stanceFor(Unit)`→
     `stanceFor(float)` (caller passes `getMoveProgress`); `alliesNearForSpread`
     pass-2 (`requireLiveIndex` per gathered candidate); `BattleSimulation`
     reprio/fallback inline appliers (by-index setters).
   - **Final accessor holdouts cleared (`a972bbb`).** All three:
     `TacticalScoring.effectiveAttackRange` now takes the resolved range as a
     param (the two `findFiringPosition` impls resolve `selfIdx` first and pass
     `registry.getAttackRange(selfIdx)`; `EngagePosture` passes
     `sim.world().attackRange(id)`; 3 `TacticalScoringTest` cases pass
     `rocketeer.getAttackRange()`). `UnitRenderService.emitLiveSprite` takes the
     dense index `i` from `sweepLiveSprites` (cooldown + self cell by-index,
     zero probe); `computeFacing`/`computeEightWayFacing` take `selfCellX/Y` and
     resolve the target cell by id under the `sim != null` gate.
     `DamageService:248` reprio race-check moved registry-side into
     `writeReprioInline` (`ReprioApplier.apply` gains `expectedTargetId`), so the
     flush drain no longer reads `target.getTargetId()`. Also folded in:
     `InfantryWeapons.fireShot` reads accuracy/damage by-index. Green at 733.
   - **`Unit.denseIdx` DELETED (`9cd7c61`).** The cached dense-index field is gone;
     `indexById` (the registry's id→index map) is the single source of truth for an
     entity's slot, so a held `Unit` ref can never carry a stale index. `Unit.idx()`
     resolves the slot via `registry.requireLiveIndex(entityId)`; every no-arg OO
     accessor routes through it (hot bulk loops never do — they go by loop index, so
     the cache-locality win is untouched; only cold/per-event callers pay the
     fastutil probe). `advanceAlongPath`/`beginBurst` resolve the slot once then go
     by-index. `isAlive()` → `registry != null && registry.isAliveById(entityId)`
     (new registry method: `indexById` membership + `hp>0`). `allocate`/`release`
     drop the three `denseIdx` writes. `GroundSimBridge` (the last external OO-accessor
     caller) → `sim.world()` by-id. `UnitRegistryTest`/`WorldTest` `u.denseIdx` →
     `r.indexOf(entityId)`. Green at 734.
   - **`Unit.registry` DELETED — the back-pointer is GONE (2026-06-02).** Three
     commits, tree green throughout (old accessors kept during the sweep; the final
     delete-commit was the compiler completeness backstop):
     - `4aef28e` **prep (additive):** `World.isAlive(long)` (non-fail-loud, mirrors
       `isAliveById`), `Unit.idOf(Unit)` static (null-safe ref→id), and the
       handle-taking overloads `beginBurst(World,Unit)` /
       `advanceAlongPath(UnitRegistry,float)` coexisting with the self-routing forms.
     - `65a61f1` **production caller sweep** — ~60 conversions across ~37 files, fanned
       to 10 Sonnet agents over disjoint package buckets. `u.isAlive()`→
       `world.isAlive(id)`/`registry.isAliveById(id)`; columns→`world.<col>(id)` or
       in-scope registry by-index; `u.setTarget(t)`→`world.setTargetId(id, Unit.idOf(t))`
       (likewise the secondary-aim/burst-target setters); `u.beginBurst(t)`→
       `u.beginBurst(world,t)`; `advanceMovement`→`u.advanceAlongPath(registry, TICK_DT)`.
       Two no-handle sites hand-wired: `UnitSpatialIndex` stashes the registry from
       `rebuild`/`add` so `gather` drops released units via `isAliveById`;
       `UnitDestinationSpatialIndex.addDestination` took a registry param (mirrors its
       sister `add(registry,u)`), passed from `NavigationService`. Vanilla
       `ShipAPI.isAlive()` (combathybrid/flyby) and `MountedTurret`'s own
       setTarget/setBurstTarget are different methods — left alone.
     - `335cce8` **THE DELETION** — removed the `registry` field, `idx()`, `isAlive()`,
       every registry-routed OO accessor (hp/cell/maxHp/attack stats/cooldown/
       move-progress/target/burst/secondary/fallback/reposition/wander get+set), the
       null-safe target convenience setters, and the old self-routing
       `beginBurst(Unit)`/`advanceAlongPath(float)`. `allocate`/`release` drop the two
       back-pointer writes; `isAliveById` is the sole liveness seam. Test surface
       converted off the deleted accessors (19 files via 3 Sonnet agents;
       `UnitRegistryTest` rewritten to the by-index contract — back-pointer asserts →
       `isLive`/`isAliveById`, post-release cell-throw → `requireLiveIndex` throws IAE;
       3 straggler test files the compiler surfaced fixed). Suite green at 734.

     **A held `Unit` ref can no longer reach mutable state at all** — the entire
     dangling-self-route NPE class is structurally gone; `indexById` is the sole
     id→slot source of truth. `Unit` now = `entityId` + `idOf()` + immutable archetype
     + POJO fields + path helpers + render accessors (RenderPositionService survives
     release) + the two handle-taking behavior methods.
5. **`Unit` → `Entity` rename — SHIPPED (`a708ce8`, task #15).** IntelliJ
   `rename_refactoring` on the `Unit` TYPE symbol — 1729 usages, 185 files,
   `Unit.java`→`Entity.java`. Sibling types kept their names (UnitType /
   UnitRegistry / UnitRole / UnitSpatialIndex / …) — only their internal `Unit`
   refs became `Entity`; Drone/DroneHubUnit/MapTurret extend `Entity`. Build clean,
   suite green at 734. The IDE's rename-in-comments/strings also rewrote ~22
   roadmap `.md` docs — reverted to keep historical narrative accurate, so the
   rename commit is code-only. See [[intellij_mcp_refactor_tools]]. **`Entity` is
   now a bare id + immutable archetype + POJO fields; the world-facade endgame is
   reached.**

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
