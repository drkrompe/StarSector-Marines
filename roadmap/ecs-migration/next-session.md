# Next session — picking up the migration

Read [`overview.md`](overview.md) first for design rules.
Shipped work is in [`complete/`](complete/).

## Component class convention (locked 2026-06-03)

Before extracting any new component: data components are named `XxxComponent`
and live in a per-domain `components` subpackage (`battle.<domain>.components`);
`ComponentStore<T>` and processing systems stay put. Full rule in
[`component-model.md`](component-model.md#component-class-convention-locked-2026-06-03).
The four existing components were retrofitted to match (`CrashingComponent` →
`battle.air.components`, `RenderPositionComponent`/`DeadBodyComponent` →
`battle.unit.components`, `MechLoadoutComponent` → `battle.mech.components`).

## Commit chain so far

```
2afee3d  battle: Phase 2d — MountedTurret refs → long entity ids
e7a97fc  battle: TestUnits.kill helper + sweep raw hp=0 test writes
fffd973  battle: drop redundant isAlive() follow-ups on registry-resolve sites
1f26de4  battle: critique chaser — three more isAlive() drops in the aim path
7972009  battle: Phase 3 — hp/maxHp SoA promotion
53ee895  battle: Phase 3 critique polish — final accessors + xstream caveat
e78bd25  battle: SquadMoraleSystem — first SoA consumer
a78d417  battle: SoA cellX/cellY — Unit logical position into int[] arrays
9787bd9  battle: critique polish for cellX/cellY SoA lift
4edb1f4  battle: UnitSpatialIndex + DestIndex rebuild — second SoA consumer
d2a1cbd  battle: DamageResolver.pickPromotionCandidate — third SoA consumer
ef4d798  battle: TacticalScoring bulk loops — fourth SoA consumer
9ff4dae  battle: SquadAlertSystem — fifth SoA consumer migration
a4df09b  battle: SoA cooldownTimer — third primitive promotion
489b1db  battle: SoA moveProgress + renderX/renderY — fourth promotion
c929087  battle: SoA attackRange/attackDamage/accuracy — fifth promotion  ← 2026-05-27
01fe905  battle: SoA secondary{Cooldown,Action}Timer/secondaryAimTargetId — sixth  ← 2026-05-28
024344f  battle: SoA burstRemaining/burstTimer/burstTargetId — seventh  ← 2026-05-28
7ae84e6  battle: SoA targetId — eighth (keystone cross-reference)  ← 2026-05-28
b620e77  battle: SoA repositionCooldown — ninth (C3 Slice A)  ← 2026-05-28
9104c85  battle: SoA fallback group + wanderDwellTimer — tenth (C3 Slice B)  ← 2026-05-28
2f48c36  battle: relocate setPath/clearPath into NavigationService + trim sim surface  ← 2026-05-28
c49eea7  battle: MapService — runtime map-modification coordinator (Slice 1)  ← 2026-05-28
53d5e7d  battle: drop sim.getBattleResources facade getter (drop-facade Slice 1)  ← 2026-05-28
9c6267e  battle: BattleView/BattleControl interfaces — GOAP spine, proving slice  ← 2026-05-28
5f1bd7a  battle: narrow WorldStateBuilder + PredicateEvaluator to BattleView  ← 2026-05-29
57304e0  battle: narrow InfantryCohesion.cohesionOverride to BattleView  ← 2026-05-29
0c91af4  battle: grow BattleView/BattleControl to full GOAP surface (pre-flip)  ← 2026-05-29
62ed71f  battle: narrow GOAP helper methods to BattleView/BattleControl (pre-flip sweep)  ← 2026-05-29
61e322a  battle: flip Action/Goal GOAP contract to BattleView/BattleControl  ← 2026-05-29
65ed79a  battle: narrow Planner.plan to BattleView (critique follow-up)  ← 2026-05-29
a734122  battle: grow BattleView/BattleControl with command-tier surface  ← 2026-05-29
cb91e87  battle: flip command tier to BattleView/BattleControl  ← 2026-05-29
c50e50d  battle: collapse Group N local* duality (Phase A Slice 1)  ← 2026-06-01
2e03ade  battle: fix Group N accessor NPE on unregistered units (corpse iter)  ← 2026-06-01
1201585  battle: DeathDispatcher mailbox + migrate turret demolition (spine foundation)  ← 2026-06-01
7661571  battle: migrate hub demolition onto the death-event seam (spine slice 2a)  ← 2026-06-01
2a3abc8  battle: harden DeathDispatcher.drain() for re-entrant publish  ← 2026-06-01
40fa668  battle: model the drone crash as a Crashing component (first composition slice)  ← 2026-06-01
90f5fd5  battle: decompose render position out of UnitRegistry into RenderPositionService  ← 2026-06-01
aa55855  ai(ecs-migration): document the first-write invariant on RenderPositionService setters  ← 2026-06-01
0967df5  battle: migrate dead-sprite render onto a DeadBody component (last Bucket-B reader)  ← 2026-06-01
9b4100a  battle: add read-only live-iteration accessor to BattleView (Bucket-A prereq)  ← 2026-06-01
008afb1  battle: Bucket-A sweep — migrate live-iterators off getUnits() (wave 1, 26 files)  ← 2026-06-01
b2e0df2  battle: Bucket-A sweep wave 1b — convert the isAlive-not-first live-iterators  ← 2026-06-01
f6851eb  battle: wave-2 dead-unit readers — corpse store + crash store, off the units list  ← 2026-06-01
1d677e7  battle: mech wreck → death-event handler; HeavyWeapons off the units list  ← 2026-06-01
a30f0bd  battle: InfantryWeapons + Detonations off the units list (snapshot-then-apply)  ← 2026-06-01
7b66db8  battle: FlybyOverlay AoE + hub cascade off the units list (snapshot-then-apply)  ← 2026-06-01
c1fb304  battle: EquipmentDrop + turret guardpost scan dense; drop DamageResolver dead field  ← 2026-06-01
78f54fe  battle: UI/debug consumers off the units list (Bucket-C)  ← 2026-06-01
8b0e110  battle: repoint internal consumers to the dense registry (step 4 stage A)  ← 2026-06-01
879c766  test: migrate the test surface off sim.getUnits() (step 4 stage B)  ← 2026-06-01
1ed41bc  battle: delete the legacy live+dead units list (step 4 stage C)  ← 2026-06-01
58d6d5e  battle: Group-N accessors fail-loud again (step 5)  ← 2026-06-01
e038706  battle: SoA Group-S seed-only stats — collapse the local* duality (Phase A Slice 2)  ← 2026-06-01
31058bf  battle: collapse cell local* via DeathEvent snapshot (Phase A Slice 3a)  ← 2026-06-02
2a25347  battle: UnitSpatialIndex.gather skips dead units (fail-loud cell fix)  ← 2026-06-02
0296579  battle: SquadDetailPanel value-snapshot + getMaxHp re-fail-loud (Phase A Slice 3b)  ← 2026-06-02
6f4e42b  battle: mech salvo targets -> entityId (entity-id-handle Slice 1)  ← 2026-06-02
5a3ffb3  battle: Squad.leader -> leaderId (entity-id-handle Slice 2)  ← 2026-06-02
38d25c8  battle: Squad.droneHub -> droneHubId (entity-id-handle Slice 3)  ← 2026-06-02
9cd7c61  battle: delete Unit.denseIdx — slot resolved by id (task #14)  ← 2026-06-02
4aef28e  battle: registry-deletion prep — World.isAlive + Unit.idOf + overloads  ← 2026-06-02
65a61f1  battle: registry-deletion sweep — production callers off Unit accessors  ← 2026-06-02
335cce8  battle: DELETE Unit.registry — the back-pointer is gone  ← 2026-06-02
a708ce8  battle: rename Unit -> Entity — the entity is its id (task #15)  ← 2026-06-02
3718047  battle: model mech loadout as a presence component, delete Entity.mech (task #13)  ← 2026-06-02
88d5511  ecs(engine): archetype-table storage core + contract tests  ← 2026-06-03
955b6e5  ecs(engine): deferred CommandBuffer for safe structural change during iteration  ← 2026-06-03
0faa8bd  ecs(engine): move battle.ecs to engine.ecs — game-agnostic substrate gets an engine-tier home  ← 2026-06-03
b98c706  battle: corpse path onto the archetype EntityWorld (retrofit step 2)  ← 2026-06-03
e720e98  ecs(engine): id adoption, transmute, tolerant getFloat — the retrofit seams for live entities  ← 2026-06-03
adb4bc9  battle: Health onto the EntityWorld — death is a row-move (retrofit step 3a)  ← 2026-06-03
```

(Sibling tracks interleaved on HEAD, not ECS-migration: `9084ed4` battle-render
Story B, `31d8b17` goap shared zone-entry rule, plus ongoing battle-render +
campaign work.)

## NEW PHASE — archetype EntityWorld + game retrofit (2026-06-03, in flight)

The committed storage target ([`archetype-storage.md`](archetype-storage.md))
is now **built and consuming real game state**:

- **Engine layer DONE** (`com.dillon.starsectormarines.engine.ecs` — moved out
  of `battle.*`; game-agnostic): `EntityWorld` / `ArchetypeTable` / `Column` /
  `ComponentType` / `FieldKind` / `Query` (`88d5511`) + the deferred
  `CommandBuffer` (`world.cmd().destroy/add/remove` → `world.flush()` at the
  tick barrier; creates are walk-safe and NOT buffered) (`955b6e5`). 17 engine
  tests green, all synthetic components. **No system-runner abstraction built**
  — deliberate: systems are plain classes over `EntityWorld`; revisit only if
  retrofit shows a need.
- **Retrofit step 2 (corpse path) SHIPPED** (`b98c706`,
  [`complete/corpse-archetype-retrofit.md`](complete/corpse-archetype-retrofit.md)):
  `BattleSimulation` owns a per-battle `EntityWorld` + `BattleComponents`
  (the game-side type registry: `IDENTITY`/`POSITION`/`RENDER_POSITION`/
  `SPRITE`/`CORPSE`, named field indices, shared `corpses` query);
  `DeadBodySystem` spawns a corpse entity per `DeathEvent` (pose authored into
  `SPRITE.index` — appearance-as-authored-data); `sweepDeadSprites` + the
  `MissionResolver` casualty tally are pure column walks; `DeadBodyComponent`
  deleted; `entityWorld.flush()` barrier established at end-of-tick.

- **Step 3a (Health) SHIPPED** (`adb4bc9`, engine seams `e720e98`): every unit
  spawns into the world as `{IDENTITY, HEALTH}` — `UnitRegistry.allocate` is
  the spawn seam (mints the id, **adopts** it via the engine's
  `createEntity(long id, …)`, writes identity once, seeds hp); the registry's
  hp/maxHp dense columns are **deleted**. **Death is now the row-move**:
  `DeadBodySystem` `transmute`s (one move, Artemis-EntityTransmuter-style
  engine op) the dead entity to the corpse archetype — same entity id,
  IDENTITY carried by the shared-column copy, HEALTH removed. Liveness is
  purely world-side: `isAliveById` = has `HEALTH` && hp > 0 (one tolerant-read
  probe; registry presence dropped out of the definition — every release path
  zeroes hp first). Transitional registry adapters
  (`hpById`/`setHpById`/`maxHpById`) keep the ~20 call sites on their existing
  receivers and die with the registry in step 4. The registry **owns** the
  world + `BattleComponents` for the transition (the RenderPositionService
  owned-sub-store precedent); the sim aliases them.

**Next: step 3 continues** — Position (cellX/cellY; biggest consumer surface,
includes the spatial-index rebuild path), Combat group, Movement, AiState;
then fold the `Crashing`/`MechLoadout` ComponentStores into archetype
membership; then step 4 dissolves `UnitRegistry` (id mint + dense Entity[]
hop to the world / sim).

## NEW PHASE — entity-id handle (2026-06-02, in flight)

User directive: **delete `Unit.registry` and stop holding `Unit` object refs —
reference `entityId` instead** (the dangling-ref NPE class). New story:
[`entity-id-handle`](stories/entity-id-handle.md). Pattern = the settled
`getOrNull(id)` resolve (mirror `targetId`); `0L` = no-entity sentinel.

**Shipped:** held-ref → id for the persistent dangling sources:
- Slice 1 `6f4e42b` — `MechLoadoutState` salvo/burst targets.
- Slice 2 `5a3ffb3` — `Squad.leader` → `leaderId` (+ `isMechSquad` denormalized
  to a `mechSquad` flag set at mint, survives leader death).
- Slice 3 `38d25c8` — `Squad.droneHub` → `droneHubId`.
- Slice 4 `4c19bb7` — pending-mutation POJOs: `PendingTargetMutation.target` →
  `targetId`, `PendingOccupancyDelta.u` → `unitId`; `DamageService` takes a
  `LongFunction<Unit> resolver` (`registry::getOrNull`) for the two drains.
  **`PendingTargetMutation` was a real post-release reader** — its drain runs
  after `flushPendingDamage`, which releases-inline via `DamageResolver.resolve`,
  so the old `target.isAlive()` was a `localHp`-dependent deref of a released
  unit. Now skips a null resolve. Full suite green at 681.
- Slice 5 — **`localHp` removed; `getHp`/`setHp` fail-loud. PHASE A DUALITY
  COLLAPSE COMPLETE.** Field deleted, replaced by write-only `seedHp` (mirrors
  `seedMaxHp`) for the pre-allocate window. **The collapse finished via a design
  pivot, not the full held-ref→id conversion the plan assumed:** `isAlive()` was
  redefined to `registry != null && registry.getHp(denseIdx) > 0f`, short-circuiting
  on the `registry == null` release marker. That makes every held-ref *liveness*
  check safe (damage queue `dmgTarget[]`, flyby projectile target, turret/vehicle
  aim, mech continuation scratch) without converting them — they only ask
  "alive?". Only a **direct** `getHp()`/`setHp()` on a released ref is now
  fail-loud; audited all such callers to be on live/dense/resolved units. Full
  suite green at 689.

**Next (in priority order):**
1. **Endgame: [`world-facade`](stories/world-facade.md) — delete `Unit.registry`.**
   - Slice 1 SHIPPED (`1e30bcf`): `battle.sim.World` introduced + wired, both faces
     proven by `WorldTest`.
   - Sweep prereq SHIPPED (`c69a24b`): complete by-id `World` surface +
     `UnitRegistry.requireLiveIndex` + `BattleView.world()`.
   - **Slice 2a SHIPPED (`4c3ec2f`): AI decision-layer sweep** — ~37 files (GOAP
     actions, infantry/mech postures+behaviors, drone swarm, command objectives,
     debug panels) → `sim.world().<col>(id)`. 5 Sonnet agents, disjoint buckets,
     green at 705.
   - **Slice 2b SHIPPED (`00f2e1d` + `3d96e5a`): no-sim-param services.** Key
     refinement vs. plan: most are **dense iterators already holding registry +
     loop index `i`** → `registry.<col>(i)` (zero map probe), strictly better than
     routing through `World` (which re-probes an index it doesn't have).
     by-id `World`/`registry` only where a bare id/`Unit` ref is in scope.
     Part 1: AttackerIndexService, SquadFallbackSystem, SquadAlertSystem,
     NavigationService (rebuildOccupancyMap dense-index + setPath via a new
     setter-injected `registry` field), VisionService (sweep dense-index;
     tickFogCohort/addContributor by-id), SquadMoraleSystem (cellX/cellY arrays),
     InfantryUnitPrep.tickCooldowns (World param), DroneSwarmAction
     tickPursue/clampGoalToLeash (BattleView param). Part 2: turret aim/fire
     statics (TurretFireService World field; TurretAim.tick World param; AirSystem
     + GroundSystem gain a World ctor field). Green at 705 both.
   - **TacticalScoring (53 sites) — its OWN slice, NOT a sweep. PART 1 SHIPPED
     (`e1f86cc`).** Every self/target-style ref (`self`/`target`/`cur`/
     `currentTarget`/`closerVisible`/`member`) now resolves its index once per
     method via `registry.requireLiveIndex` and reuses locals — removing the
     per-candidate `self.getCellX()` denseIdx reads from the firing-position
     (`for dy/for dx`) + vantage loops (fewer indirections AND fail-loud on a
     released id). `findBestTargetImpl` passes candidate `ox/oy` through to
     `scoreThreatDensity`/`scoreZoneMismatch` (now `int`-coord signatures) so the
     dense loop stops re-reading `other.getCellX()`; `projectedRocketDamageOnTarget`
     dense-index timer reads use loop `i`; `occupantsExcludingSelf` takes
     `selfCellX/selfCellY`. Suite green at 723.
     - **PART 2 SHIPPED (`ff105a9`) — gathered-list held-ref reads.**
       `findEngageableEnemyWithin` + `isHiddenFromAllEnemies` resolve each
       gathered enemy index once via `requireLiveIndex` (heavy per-element loops;
       probe negligible). The hot per-candidate path: static `countEnemiesWithLos`
       now takes pre-resolved parallel SoA columns; `findFallbackPosition`
       projects the threat set once (`resolveThreatColumns`), so the
       `~1089`-candidate scan does ZERO registry probes. Green at 724.
     - **Left for task #14** (denseIdx deletion): static `effectiveAttackRange`
       (static + tested/called statically → make instance or range-param) and
       `alliesNearForSpread` pass-2 (per-candidate `destIndex` gather). Both
       isolated, zero-probe-today, forced when `Unit.getCellX/getAttackRange` go.
   - **2d — hot loops + render. PART 1 SHIPPED (`884a4bd`) — combat resolvers.**
     Per-site rule: **bulk dense loops** → `denseArray()` + `cellX/cellY` arrays
     (hoist per iteration); **per-event/held-ref** → `requireLiveIndex` once then
     by-index; `getRenderX/getRenderY` already id-keyed via `RenderPositionService`
     (leave); `isAlive()` stays (task #14). Parallel-phase index lookups are safe
     (releases deferred to serial drains; `getOrNull` already probes concurrently).
     Done: `DamageResolver` (serial `APPLY_DAMAGE` drain), `Detonations` AoE gather
     (dense-array), `HitResponseService` (parallel), `HeavyWeapons`. Deferred to
     task #14: `FireStance.stanceFor` (static), `DamageService:248` (no registry).
     - **PART 2 SHIPPED (`5c03cc0` + `66f439d`) — infantry + renderers.**
       `InfantryWeapons` (`5c03cc0`): burst gather → dense-array + by-index
       `getBurstRemaining(i)`; continuation pass resolves `requireLiveIndex` once
       per unit, **re-resolving across `fireShot`** (a killing round swap-and-pops
       and can relocate `u`'s slot); `fireShot` dist read resolves shooter/target
       index once. **`FlybyOverlay` needed ZERO changes** — its apparent ~15 sites
       are all `getRenderX/getRenderY`, already id-keyed via `RenderPositionService`
       (the raw grep over-counted). Renderers (`66f439d`): `UnitRenderService`
       (5 sweeps) + `DroneRenderSystem` — render passes iterate `liveUnitAt(i)`
       where `i` IS the dense index (`liveUnitAt(i)=registry.get(i)`,
       `dense[i].denseIdx==i`), so cell/hp/secondaryActionTimer go by-index
       `registry.<col>(i)` zero-probe AND the vision calls drop `u.denseIdx` for
       loop `i`. Suite green at 727. Deferred to task #14: the static
       `computeFacing`/`computeEightWayFacing` helpers (nullable `sim`, also touch
       `pathCell*`) — same awkward-static class as `effectiveAttackRange`.
     - **PART 3 SHIPPED (`fab9d33` + `e092926`) — remaining systems.** Eleven
       files swept by the per-site rule. Dense iterators (`i`==denseIdx,
       zero-probe `registry.<col>(i)`): `AirSystem` hover-follow,
       `TurretDemolition` guardpost, `EquipmentDropService` pickup+nearest,
       `SquadAlertSystem` fallback gate, `VisionService.sweepUnitVisibility`
       (`u.denseIdx`→`i` on its vis/fade arrays). Per-event held-refs
       (`requireLiveIndex` once): `HubDemolition` cascade, `EquipmentDropService`
       emit (dead still registered pre-release), `DroneSpawner` tryLaunch,
       `BattleSimulation` isRoofShielded + targetOf. Narrowed-view sites
       (`sim.world().<col>(id)`, 2a idiom): `GarrisonPatrol`, `UnitUpdateSystem`,
       `SquadDetailPanel`, `DroneSpawner` isCellOccupied. Suite green at 731.
       (`WorldStateBuilder`/`PendingTargetMutation`/`SquadAlertSystem` line-299
       grep hits were javadoc/comments. Sibling deleted `SimCoupledProxyPlugin`;
       its replacement `combathybrid/GroundSimBridge` is sibling-owned in-flight
       work — left untouched.)
     - **SLICE 2d COMPLETE.**
   - **TASK #14 (delete denseIdx) — IN PROGRESS (2026-06-02).** Clearing the last
     accessor holdouts that block deleting the `Unit.registry`/`denseIdx` fields:
       - **`UnitSpatialIndex` denormalized (`06cecbd`) — keystone DONE.** Pooled
         `Bucket` of parallel arrays (`units[]`/`cellX[]`/`cellY[]`) replaces the
         `ArrayList<Unit>` buckets; `rebuild` stores the SoA cell, `add(registry,u)`
         resolves once, `gather` filters on the snapshot int — no `denseIdx`, no
         per-candidate probe, zero-alloc kept. Bucketing+distance now both
         rebuild-time (was a latent live/snapshot mismatch).
       - **Low-ripple holdouts cleared (`c884f0b`):** `FireStance.stanceFor(float)`,
         `alliesNearForSpread` pass-2, `BattleSimulation` reprio/fallback inline.
       - **Final accessor holdouts cleared (`a972bbb`).**
         `TacticalScoring.effectiveAttackRange` takes the resolved range as a param
         (findFiringPosition impls resolve selfIdx first; EngagePosture passes
         `sim.world().attackRange(id)`; 3 tests pass `rocketeer.getAttackRange()`).
         `UnitRenderService.emitLiveSprite` takes dense index `i` (cooldown + self
         cell by-index); `computeFacing/computeEightWayFacing` take `selfCellX/Y`
         and resolve target cell by id under the `sim != null` gate.
         `DamageService:248` reprio race-check moved registry-side into
         `writeReprioInline` (`ReprioApplier.apply` gains `expectedTargetId`).
         Folded in: `InfantryWeapons.fireShot` accuracy/damage by-index. Green at 733.
       - **`Unit.denseIdx` DELETED (`9cd7c61`).** The cached dense-index field is
         gone — `indexById` is the single source of truth for an entity's slot, so a
         held ref can't carry a stale index. `Unit.idx()` resolves via
         `requireLiveIndex(entityId)`; every no-arg accessor routes through it (hot
         loops still go by loop index — cache-locality untouched; only cold callers
         pay the probe). `advanceAlongPath`/`beginBurst` resolve once. `isAlive()` →
         `registry.isAliveById(entityId)` (new method). `GroundSimBridge` → `sim.world()`.
         `UnitRegistryTest`/`WorldTest` `u.denseIdx` → `r.indexOf(entityId)`. Green at 734.
       - **`Unit.registry` DELETED — the back-pointer is GONE (2026-06-02).** Three
         commits, tree green throughout (old accessors kept during the sweep; the
         final delete-commit was the compiler completeness backstop):
           - `4aef28e` prep (additive): `World.isAlive(long)`, `Unit.idOf(Unit)`
             static, and handle-taking overloads `beginBurst(World,Unit)` /
             `advanceAlongPath(UnitRegistry,float)` coexisting with the old forms.
           - `65a61f1` production caller sweep (~60 conversions, 10 Sonnet agents,
             disjoint package buckets) off Unit's self-routing accessors →
             `world.<col>(id)` / `world.isAlive(id)` / `registry.isAliveById(id)` /
             by-index. `u.setTarget(t)`→`world.setTargetId(id, Unit.idOf(t))`;
             `u.beginBurst(t)`→`u.beginBurst(world,t)`; `advanceMovement`→
             `u.advanceAlongPath(registry, TICK_DT)`. Hand-wired the 2 no-handle
             sites: `UnitSpatialIndex` stashes the registry from rebuild/add for
             `gather`'s isAliveById drop; `UnitDestinationSpatialIndex.addDestination`
             gained a registry param (mirrors `add(registry,u)`).
           - `335cce8` THE DELETION: removed the `registry` field, `idx()`,
             `isAlive()`, every registry-routed OO accessor, the null-safe target
             convenience setters, and the old `beginBurst(Unit)`/`advanceAlongPath(float)`.
             `allocate`/`release` drop the two back-pointer writes; `isAliveById` is
             the sole liveness seam. Test surface converted (19 files via 3 Sonnet
             agents; `UnitRegistryTest` rewritten to the by-index contract; 3
             straggler test files the compiler surfaced fixed). Suite green at 734.
         **A held `Unit` ref can no longer reach mutable state at all** — the entire
         dangling-self-route NPE class is structurally gone; `indexById` is the sole
         id→slot source of truth. Unit now = `entityId` + `idOf()` + immutable
         archetype + POJO fields + path helpers + render accessors
         (RenderPositionService survives release) + the two handle-taking behavior
         methods.
       - **`Unit`→`Entity` rename SHIPPED (`a708ce8`, task #15).** IntelliJ
         `rename_refactoring` on the `Unit` TYPE symbol — 1729 usages, 185 files,
         `Unit.java`→`Entity.java`. Sibling types kept their names (UnitType /
         UnitRegistry / UnitRole / UnitSpatialIndex / UnitRosterService /
         UnitBehavior / UnitUpdateSystem) — only their internal `Unit` refs became
         `Entity`. Drone/DroneHubUnit/MapTurret extend `Entity`. Build clean, suite
         green at 734. (Caveat for next time: the IDE's rename-in-comments/strings
         was on and also rewrote ~22 roadmap `.md` docs — reverted via
         `git checkout` to keep historical narrative accurate; the rename commit is
         code-only.)
   - **Task #13 — `mech` → `ComponentStore` SHIPPED (2026-06-02).** `Entity.mech`
     (the nullable `MechLoadoutState` field) is DELETED; the loadout is now a
     presence component in `ComponentStore<MechLoadoutState>` (owned by
     `BattleSimulation`, wired into `World`'s cold-face store map, exposed via
     `getMechLoadouts()`). This is the **first behavioral optional-capability**
     modeled as composition (after the corpse/render components) — meets the
     `component-grouping` acceptance ("zero nullable-field if/else for that
     capability"). Access shapes, by hot/cold split:
       - **Mech-fire bulk pass** (`HeavyWeapons.advanceMechWeapons`) iterates the
         store's `entries()` directly — only mech entities, no scan over the whole
         registry for a former `u.mech != null` (the composition win).
       - **Systems** (`DamageResolver`, `HitResponseService`, `SquadMoraleSystem`,
         `MechWreckSystem`) take the concrete store injected and use
         `store.get(id)` / `.has(id)` — zero-alloc, mirrors how `DroneCrashSystem`
         takes `crashing`.
       - **Per-tick decide code** (mech GOAP behaviors/actions/goals,
         `CombatantBehavior` dispatch) uses the new zero-alloc facade primitives
         `world.component(id, type)` / `world.hasComponent(id, type)` — NOT the
         allocating `world.id(id).getOrNull` (honours "never materialize a
         component in a per-tick bulk loop"). The static mech-fire helpers now take
         `MechLoadoutState m` as a param (resolved once by the caller).
       - **Spawn**: `BattleSetup.attachMechLoadout` adds the component AFTER
         `addUnit` (store keyed by `entityId`, assigned at allocate). `mintSquad`
         derives `mechSquad` from the new `UnitType.isMech()` (mech-ness known
         before the component is attached). **Lifecycle**: `MechWreckSystem.onDeath`
         removes the component when the wreck spawns (mirrors `Crashing`).
       Build clean, suite green at 734.
     - **Still open under #13 (optional, lower payoff):** the other nullable
       `Entity` capability fields — `secondaryWeapon`/`secondaryAmmo`,
       `assignedObjective`, `equipmentDropTarget`. Same component-as-presence
       treatment when motivated; `mech` proved the shape.
   Design LOCKED with the user (2026-06-02): a **two-faced `World`** facade over
   the existing stores. **Hot face** = primitive by-id accessors (`world.hp(id)`,
   `cellX/Y`, `renderX/Y`, combat stats) backed directly by the dense SoA — zero
   alloc, cache-locality preserved; bulk systems keep iterating dense arrays.
   **Cold face** = `world.id(id).get/getOrNull(Cmp.class)` projection, OPT-IN
   convenience only (debug/UI/held-ref/optional capabilities) — sparse object
   components are real store lookups, dense groups are views constructed from the
   arrays (allocates → never in a hot loop). **Constraint from the user: never
   materialize a component in a per-tick bulk loop** (protects the primitives /
   cache-locality win). Relocate `u.getX()` (~516 sites / 72 files) per accessor
   group, fan mechanical sweeps to Sonnet; model remaining optional fields as
   presence components; then delete `Unit.registry` + `denseIdx`; then `Unit` →
   `Entity`. First slice: introduce `World`, prove both faces on one group each
   (`hp` primitive + `mech` → `getOrNull`), leave the rest on `Unit` accessors.

## State of play

- **Legacy units-list retirement — COMPLETE (2026-06-01). THE SPINE IS DONE.**
  `UnitRosterService.units`, `getUnits()` (roster + sim + `BattleView`), and the
  sim's `units` alias are all **deleted**. The dense `UnitRegistry` is the sole
  live roster; post-death state lives entirely in the component stores
  (`DeadBody` / `Crashing` / `RenderPositionService`) keyed by entity id and
  surviving release. A corpse is literally an entity present in those stores and
  absent from the live registry. Stages: A (`8b0e110`) repointed the three
  internal consumers (vision/nav/squad-fallback) to the registry — squad-fallback
  gathers members sorted by `entityId` to preserve the spawn-priority cover order
  the insertion-ordered list gave; B (`879c766`) migrated the ~13-file test
  surface (added `TestUnits.snapshot(sim)` for the index/iterate-then-kill
  patterns the dense swap-and-pop would otherwise corrupt); C (`1ed41bc`) deleted
  the list. **Step 5 (`58d6d5e`) reverted all 14 Group-N accessor pairs to
  unconditional registry reads (fail-loud)** — the null-safe branch only existed
  for the released-corpse-on-the-list case, now impossible. Four tests that seeded
  Group-N state pre-`addUnit` were fixed to write after registration. Full suite
  green at 649 tests. **The acceptance criteria are all met; this story moves to
  `complete/`.**
- **Primitives promoted:** hp/maxHp, cellX/cellY, cooldownTimer,
  moveProgress, renderX/renderY, attackDamage, attackRange, accuracy,
  secondaryCooldownTimer, secondaryActionTimer, secondaryAimTargetId,
  burstRemaining, burstTimer, burstTargetId, targetId, repositionCooldown,
  fallbackTimer, fallbackCellX/fallbackCellY, wanderDwellTimer.
  Three `long[]` (`secondaryAimTargetId`, `burstTargetId`, `targetId`).
- **Five consumers** on dense-iter + SoA array reads. (The burst pass in
  `InfantryWeapons.tick`, `targetId`'s ~17 sites, and the fall-back group's
  break-contact consumers route through accessors, not dense-iter yet.)
- **Duality collapse (Phase A) — Slices 1, 2 & 3a DONE.** Of the 22 promoted
  columns, the 14 Group-N (mid-combat-only) twins are gone (`c50e50d`), the
  4 Group-S seed-only stats are gone (`e038706`), and **the cell pair
  (cellX/cellY) is now seed-only too (Slice 3a, 2026-06-02)**: `DeathEvent`
  became a self-contained snapshot (`DeathEvent(unit, cellX, cellY)`) so the
  three demolition/wreck death-handlers read the death cell off the event
  instead of the released unit; `localCellX/Y` → write-only `seedCellX/seedCellY`,
  `getCellX/getCellY/setCellPos` fail-loud, `release` drops the cell snapshot.
  (Hotfix `2a25347`: `UnitSpatialIndex.gather` now skips dead stale entries
  before the now-fail-loud cell read.) **Slice 3b (2026-06-02)** moved the
  `SquadDetailPanel` HUD off post-release reads — it snapshots displayed values
  (`MemberRow`) at `update()` instead of holding live refs, so `getMaxHp`
  reverted to fail-loud (the `33ba6c6` workaround is gone).
- **`localHp` is GONE (2026-06-02) — no `local*` shadows remain.** Every
  `local*` (Group-N/S, the cell pair, and finally hp) is collapsed; `Unit` carries
  only write-only `seed*` construction inputs that `allocate` copies into the SoA
  arrays. The held-`Unit`-ref `isAlive()` checks that once needed the `localHp`
  fallback (mech salvo / turret+vehicle aim / flyby / wiped-squad leader / damage
  queue) are safe via the redefined `isAlive()` (registry-null short-circuit), not
  a shadow. `localRenderX/Y` are seed-only (RenderPositionService survives release).
  See [`entity-id-handle`](stories/entity-id-handle.md) Slice 5.
- **Full suite green at 592 tests** (after the death-dispatcher foundation
  slice). The earlier sibling compile break in `ShotRenderService.java` has
  since resolved.
- **Death-event seam LANDED + COMPOSITION STARTED.** `DeathDispatcher` mailbox
  is live (wave-drained, re-entrant-safe). Turret + hub demolition migrated to
  `onDeath` handlers (unified `DEMOLISH` phase). **The drone crash is now a
  `Crashing` component** (`battle.component.ComponentStore<T>` + `Crashing`) —
  the first real composition in the battle tier: capability-as-presence, a
  system over the component-set, FX as a side effect. The hub cascade publishes
  per-drone `DeathEvent`s so the seam handles cascade kills. **This is the
  pattern everything else follows now — stop deferring composition to a "future
  phase."** ([[feedback_build_composition_now]].)
- **`UnitRegistry` decomposition + corpse home — DONE through all of Bucket-B.**
  Render position (`renderX/renderY`) is now a standalone `RenderPositionService`
  (`battle.unit`), a float API over a `ComponentStore<RenderPosition>`, keyed by
  `entityId` and **surviving registry release**. Picked first because nothing
  reads it densely (zero `renderXArray()` callers), so it's zero-perf /
  zero-behavior-change for the living and collapses the render half of the
  `local*` duality. Hot-dense columns (`cellX/cellY`, hp, timers) stay dense.
- **Dead-sprite render migrated → corpse home complete.** `sweepDeadSprites` no
  longer scans `getUnits()`: a `DeadBody` component (`type` + `deathPoseIdx`) is
  recorded on every `DeathEvent` by `DeadBodySystem`, and the sweep iterates
  `getDeadBodies()` paired with the surviving render-position entry. **A corpse
  is now literally an entity present in the dead-body + render-position stores
  and absent from the live registry's health/AI** — the composition the user
  asked for, realized. All four Bucket-B corpse-readers are off the list
  (sequencing step 1 done). **Next is Bucket-A** (the ~20 live-iterators →
  dense registry; mechanical, fan-out-able to Sonnet).

## Active stories (priority order)

> **TL;DR for a cold start:** stories 1–4 + story 5 Slice 1 are shipped.
> **Story 6** ([`drop-sim-facade-delegators`](stories/drop-sim-facade-delegators.md))
> — both substantive tiers **DONE**: the GOAP spine (`61e322a`) and the
> command tier (`cb91e87`) now depend on `BattleView` (read) / `BattleControl`
> (mutate), not raw `BattleSimulation`. ~55 files narrowed total; suite green.
> **What's left is out-of-scope or cosmetic** (see story doc): render/UI
> facade reads stay on the sim by design; the `decision/` per-unit dispatch
> (`UnitBehavior.update` + `FallbackBehavior`/`FleeBehavior`/`CombatantBehavior`)
> and the cosmetic leftover GOAP callers (`replanIfNeeded` trio,
> `SquadReplanSystem.tick`, `DroneSpawner.tryLaunch`) still take
> `BattleSimulation` and upcast — narrowing them is optional polish, not
> coupling reduction.
>
> **SPINE PIVOT (2026-06-01): retire the legacy units list.** The Slice-1
> corpse NPE (`getBurstRemaining` on a released unit, fixed `2e03ade`) exposed
> the root cause — two parallel unit collections that disagree about death
> (registry = live only; `UnitRosterService.units` = live + retained corpses).
> The null-safe accessor fix papers over the bug *class*; deleting the list
> removes it (and lets the accessors revert to fail-loud). User chose to make
> this **the spine** of the component-model phase. See
> [`retire-legacy-units-list`](stories/retire-legacy-units-list.md) — it
> subsumes the old Slice 3 (corpse home is the enabler). **Resume there.**
> Audit done: ~20 Bucket-A live-iterators (→ dense registry), **4** Bucket-B
> corpse-readers (dead-sprite render + drone-crash + turret/hub demolition),
> ~10 Bucket-C UI/debug (live-filtered).
>
> **Corpse-home shape DECIDED (2026-06-01):** death-event **mailbox/distributor**
> (`DeathDispatcher`) + a **lightweight body entity** — NOT a render-locked
> decal (would block future medics/revive). On death: publish `DeathEvent`,
> remove the unit from the live registry; subscribed handlers (render, drone-
> crash, turret/hub demolition, future medic) decide representation. See the
> story's "corpse-home design (decided)" section + [[battle_death_dispatcher_design]].
>
> **Build sequence (resume here):**
> 1. ~~**Foundation slice** — `DeathEvent` + `DeathDispatcher`; publish from
>    `DamageResolver.resolve`; migrate the first handler to prove the seam.~~
>    **SHIPPED.** `DeathDispatcher` (buffered mailbox: publish → per-tick
>    `drain()` at the demolition phase) + `DeathEvent(Unit)` in `battle/unit`.
>    `DamageResolver.resolve` publishes in the `died` branch (before
>    `releaseFromRegistry`, alongside the untouched `deathSink`).
>    **`TurretDemolitionSystem` migrated** off the per-tick `List<Unit>` scan to
>    `onDeath(DeathEvent)`, subscribed in the sim ctor; the old
>    `turretDemolition.tick(units)` call is now `deathDispatcher.drain()` at the
>    same `DEMOLISH_TURRETS` phase slot (timing preserved — by drain time every
>    this-tick death is settled, so the all-turrets-dead guardpost scan behaves
>    identically). Behavior-preserving: the guardpost scan still reads the
>    legacy list (a Bucket-B corpse-read it migrates with in step 2).
>    Tests: `DeathDispatcherTest` (4) + `TurretDemolitionSystemTest` (2). Suite
>    green at 592.
> 2. ~~**Hub demolition (slice 2a)**~~ — **SHIPPED.** `HubDemolitionSystem` →
>    `onDeath(DeathEvent)`, same shape as turret demolition; both now react in
>    the one `deathDispatcher.drain()`, and the `DEMOLISH_TURRETS`+`DEMOLISH_HUBS`
>    profiler phases collapsed to a single `DEMOLISH`. The drone cascade kept its
>    list scan; the drones it hp=0s still bypass `DamageResolver` and publish no
>    `DeathEvent` (harmless until the crash system moves onto the seam — then the
>    cascade must publish per-drone events, and the `drain()` loop must tolerate
>    re-entrant `publish` growing `pending`). Test: `HubDemolitionSystemTest`.
> 2b. ~~**`DroneCrashSystem` → component**~~ — **SHIPPED** (`2a3abc8` +
>    `40fa668`). The crash is now a `Crashing` component in a presence-based
>    `ComponentStore` (new `battle.component` package), processed by a system
>    over the component-set; FX is the side effect of the component. First real
>    composition in the battle tier — the pattern the rest follows. Drain
>    hardened for the re-entrant cascade publish. See the spine story's Progress.
> 2c. ~~**`UnitRegistry` decomposition — render position + dead-sprite render.**~~
>    **SHIPPED.** Render position (`renderX/renderY`) → a standalone
>    `RenderPositionService` (float API over a `ComponentStore<RenderPosition>`),
>    keyed by `entityId`, surviving release. Then the dead-sprite render moved off
>    the `getUnits()` scan onto a `DeadBody` component (`type` + `deathPoseIdx`,
>    recorded on every `DeathEvent` by `DeadBodySystem`), paired at draw time with
>    the surviving render-position entry. A corpse is now an entity present in the
>    dead-body + render-position stores and absent from the live registry —
>    **corpse home complete; all four Bucket-B readers off the list.** Remaining
>    dense columns (`cellX/cellY`/hp/timers) stay until a consumer pulls them
>    (B1 grouping). ([[feedback_build_composition_now]].)
>
> 3. **Bucket-A sweep — WAVE 1 + 1b DONE** (`9b4100a` prereq + `008afb1` +
>    `b2e0df2`). Added the read-only `BattleView.liveUnitCount()/liveUnitAt(int)`
>    accessor, then converted **46 live-only `getUnits()` loops across 30 files**
>    (GOAP world/action, infantry, mech/drone/air, decision/command/objectives,
>    render-live passes, picking, flyby targeting) to dense-registry iteration,
>    dropping the redundant `!isAlive()` skip. Fanned out to 6 Sonnet sweepers on
>    disjoint files; conservative rule (convert only explicit-isAlive-gate loops),
>    then a main-thread 1b pass for the isAlive-not-first stragglers. Suite green.
>    **WAVE 2 DONE** (`f6851eb`/`1d677e7`/`a30f0bd`/`7b66db8`/`c1fb304`/`78f54fe`):
>    every remaining production reader migrated off `getUnits()`/`.units`:
>    - **Dead-unit readers** — `MissionResolver` casualty count = live registry +
>      `DeadBody` store (DeadBody gained `faction`); `DroneRenderSystem` dead pass
>      reads the `Crashing` store directly (no Unit handle); the dead-mech wreck
>      became a `MechWreckSystem` death-event handler (HeavyWeapons dropped its
>      `effects`+`units` fields); `SquadStateDumper` member dump went live-only.
>    - **Mutate-during-iteration → snapshot-then-apply** (inline `resolve` releases
>      the dead target mid-walk): `InfantryWeapons.tick` (gather mid-burst units),
>      `Detonations` AoE, `HeavyWeapons.advanceMechWeapons` (gather mechs),
>      `FlybyOverlay` ×2, `HubDemolitionSystem` cascade. Each gathers the matching
>      set first, then applies over the snapshot.
>    - **Simple live + UI/debug** → dense registry: `EquipmentDropService`
>      (+helpers), `TurretDemolitionSystem` guardpost scan (only live turrets
>      matter — the old "needs dead turrets" comment was wrong), `SquadOverviewPanel`,
>      `SquadDetailPanel`, `SquadPlanDebugPanel`; `.size()` counters →
>      `liveUnitCount()` (live, was live+dead). Dropped the dead `DamageResolver.units`.
>
>    **No `getUnits()`/`.units` callers remain — the accessors are gone.**
> 4. ~~**Delete `UnitRosterService.units`.**~~ **DONE** (stages A `8b0e110` / B
>    `879c766` / C `1ed41bc`). Internal consumers repointed to the registry,
>    `getUnits()` dropped from all three types, the sim's `units` alias removed,
>    the test surface migrated (`TestUnits.snapshot` for index/iterate-then-kill).
> 5. ~~Revert Group-N accessors to unconditional (fail-loud); drop the
>    `midCombatAccessorsReturnDefaultsWhenUnregistered` regression test.~~ **DONE**
>    (`58d6d5e`). All 14 Group-N pairs unconditional; regression test removed;
>    four pre-allocate-seed tests fixed to write after `addUnit`.
>
> Death path facts for the build: `DamageResolver.resolve` (`died` branch,
> ~line 97-120) already does `deathSink.accept(target)` →
> `BattleSimulation.getDeathsThisFrame()`, `equipmentDrops.emitIfApplicable`,
> leader promo, `roster.releaseFromRegistry(entityId)`. The list is NOT touched
> on death — corpses persist there the whole battle (no cleanup path).
> **Slice 2 (Group S seed) is independent** — can land anytime.
>
> **NEXT PHASE — the component model** (seeded 2026-05-29, **Phase A in
> flight**). The SoA-peel + facade work built ECS's storage/transform half;
> the identity/composition half remains. See [`component-model.md`](component-model.md)
> (north star) + its two stories.
>
> **Phase A — [`collapse-unit-handle`](stories/collapse-unit-handle.md)** is
> sliced N → S → C after auditing the spawn + corpse seams (see the story's
> Progress block):
> - **Slice 1 (Group N) SHIPPED** (`c50e50d`): the 14 mid-combat-only columns
>   (timers, burst/secondary/target/fallback/reposition/wander) had no
>   pre-allocate seed and no post-release reader — their `local*` twins,
>   accessor fallbacks, and release snapshots were dead weight. Deleted;
>   accessors now read the registry unconditionally; `allocate` resets them on
>   slot reuse. ~250 net lines gone.
> - **Slice 2 (Group S — seed-only: maxHp, attackDamage/Range/accuracy)** —
>   **SHIPPED** (`e038706`). Collapsed via a **seed-spec** (write-only `seed*`
>   fields consumed by `allocate`) rather than the deboard reorder — turret
>   stats vary by `kind`, not `UnitType`, so `allocate` can't seed from the
>   archetype alone; a per-instance seed channel is required regardless. The 4
>   accessors are now unconditional/fail-loud; `release` drops their snapshot.
> - **Slice 3 (Group C — corpse: hp, cellX/Y, renderX/Y)** — **NEXT, and the
>   last of the duality collapse.** The only fields with post-release readers
>   (isAlive, demolition cell, dead-sprite, death audio). Target: an explicit
>   **death-snapshot** instead of permanent `local*` shadows — possibly
>   realized as a stamped corpse *decal* or a minimal "body" entity (renderable
>   + location), which also leaves room for a future revive mechanic. renderX/Y
>   already decomposed into `RenderPositionService` (survives release); hp +
>   cellX/Y are the remaining `local*` snapshot. Decide the shape when we reach
>   it (re-examine the readers with fresh eyes first).
>
> Then [`component-grouping`](stories/component-grouping.md) — named component
> structs; optional capabilities as *presence*, not nullable fields. **Forcing
> function:** the imminent vehicle HP / ground+air-body / mounted-weapon work
> — model those as components, not more nullable `Unit` fields. Story 5 Slice
> 2 (map generation) is a separate optional stretch.

Phase 3's original three (move-render, tactical, secondary-weapon) all
shipped — see [`complete/phase3-soa-promotions.md`](complete/phase3-soa-promotions.md).
The next batch was scoped 2026-05-28 after auditing the leftover `Unit`
primitives and the (now thin) `BattleSimulation` orchestrator:

1. ~~[`burst-fire-primitives`](complete/burst-fire-primitives.md)~~ —
   **SHIPPED** (`024344f`). `burstRemaining`/`burstTimer`/`burstTargetId`
   → int/float/long[]. The MapTurret shadowing question resolved clean
   (turret keeps its own fields). Next promotion candidate ↓.
2. ~~[`target-id-primitive`](complete/target-id-primitive.md)~~ —
   **SHIPPED** (`7ae84e6`). `targetId` → `long[]`, the keystone
   cross-reference. ~17 consumer sites migrated (mechanical sweep fanned
   out to a Sonnet subagent). Next promotion candidate ↓.
3. ~~[`ai-timer-primitives`](complete/ai-timer-primitives.md)~~ —
   **SHIPPED** in two slices: Slice A `b620e77` (`repositionCooldown`),
   Slice B `9104c85` (`fallbackTimer` + `fallbackCellX/Y` + the optional
   `wanderDwellTimer` ride-along). The whole AI countdown/cache cluster is
   now off the POJO. With this done, no per-unit primitive worth a hot-loop
   win remains except the deferred low-payoff set below.
4. ~~[`path-mutation-to-navigation`](complete/path-mutation-to-navigation.md)~~ —
   **SHIPPED** (`2f48c36`). `setPath`/`clearPath` bodies moved into
   NavigationService; queued occupancy-delta sink setter-injected
   (`damageService::applyOccupancyDelta`), queue stays in DamageService;
   thin sim delegates kept so the ~28 AI call sites are unchanged. Rode
   along a sim-surface trim (dead `rollFallbackOnHit` deleted, four
   `flushPending*` privatized).
5. [`map-service-coordinator`](stories/map-service-coordinator.md) —
   **Service** extraction. **Slice 1 SHIPPED** (`c49eea7`,
   [complete](complete/map-service-coordinator-slice1.md)): `MapService`
   (in `battle.world`) now owns the runtime map-modification cycle
   (`damageWall` / `destroyRoof` / `peelRoofAround` / `flipCellToRubble` +
   the roof-collapse FX sink), lifted off NavigationService. CellTopology
   stayed a data holder (sub-decision 1 resolved — no CellTopologyService).
   `sim.damageCell` repointed to `mapService.damageWall`; the 3 consumers
   (Detonations + the two demolition systems) swapped their `navigation`
   field for `mapService`. **Slice 2 (generation cycle) still open** — a
   stretch: fold the generators' grid+topology population into MapService;
   larger surface, lower smell. Pick it up only if the seam proves worth
   it, else go straight to the facade cleanup ↓.
6. [`drop-sim-facade-delegators`](stories/drop-sim-facade-delegators.md) —
   **Terminal** migration story. **GOAP spine + command tier both DONE.**
   Goal: consumers depend on a scoped contract
   (`BattleView`/`BattleControl`), not the whole orchestrator. Full
   decision history lives in the story's DECISION block.
   - **Slice 1 SHIPPED** (`53d5e7d`): `getBattleResources` dropped.
   - **GOAP spine — read/mutate interface split, SHIPPED end-to-end:**
     `BattleView` (read-only) / `BattleControl extends BattleView`
     (mutators); `BattleSimulation implements BattleControl`. Sequence:
     - Proving slice `9c6267e` (interfaces + `ZoneQueries`).
     - Read consumers `5f1bd7a` (`WorldStateBuilder`/`PredicateEvaluator`)
       + `57304e0` (`cohesionOverride`).
     - Interface growth `0c91af4` (full GOAP sim surface onto the
       interfaces so the sweep never touches the interface files).
     - Helper sweep `62ed71f` (every private/static GOAP helper →
       BattleView/BattleControl by read/mutate, fanned out to 3 agents).
     - **Terminal flip `61e322a`:** `Action`/`Goal` interface signatures
       flipped — `cost`/`roles`/`highlightCells`/`relevance`/`desiredState`/
       `customPlan`/`pickMostRelevant` → `BattleView`; `execute` →
       `BattleControl`. All ~38 implementors `@Override`d at once (3-agent
       fan-out). Thread-safety contract now **compile-enforced**. Suite green.
   - **Command tier SHIPPED** (`a734122` grow, `cb91e87` flip): the four
     command-tier interfaces now take scoped views — `Objective.tick`,
     `MissionCommand.tick`, `ReinforcementMeans.canFulfill`,
     `ReinforcementTrigger.check` → `BattleView`; `ReinforcementMeans.dispatch`
     → `BattleControl`. All ~15 impls + concrete services narrowed by
     read/mutate (2-agent fan-out). Only the reinforcement spawn paths land
     on `BattleControl`; commands/objectives/triggers/recapture are read-only.
     `Planner.plan` also narrowed to `BattleView` (`65ed79a`, critique
     follow-up). `getBattleResources` dropped earlier (`53d5e7d`).
   - **Done for this story's substantive scope.** Both tiers that reach the
     sim through a contract — GOAP + command — now depend on
     `BattleView`/`BattleControl`.
   - **Out of scope / cosmetic (stays on `BattleSimulation`):** render/UI
     facade reads (`BattleScreen`, `FlybyOverlay`, HUD/debug panels), the
     sim's genuine public API (`advance`/`isComplete`/`getGrid`/`getTopology`/
     `damageCell`), the `decision/` per-unit dispatch (`UnitBehavior.update`
     + `Fallback`/`Flee`/`CombatantBehavior` — they read+mutate, would be
     `BattleControl`, but it's dispatch plumbing, not a facade delegator),
     and the leftover GOAP callers (`replanIfNeeded` trio,
     `SquadReplanSystem.tick`, `DroneSpawner.tryLaunch` — upcast fine).
     None are coupling-reduction wins; leave unless a future pass wants the
     uniformity.
   - Sweep convention: keep param NAME `sim`, change only its TYPE.
   - NOTE: `Action.java`/`Goal.java` live in `battle/decision/goap/`
     (story's old `battle/ai/goap/` paths were stale).

Lower-priority / deferred: `attackCooldown` + `visionRange` + `moveSpeed`
(write-once stat-block completion — tidiness, not a hot-loop win);
`squadId` + `role` (read-mostly *branching* identity, not arithmetic
kernels — high churn, low SoA payoff; `role` is an enum needing an
ordinal int[]). Name them but don't lead with them.

## Sanity check before resuming

- `gradlew.bat compileJava` should be clean.
- All tests pass.
- `git log --oneline -5` should show `e038706` (Phase A Slice 2 — Group-S
  seed-only stats) or your own recent work at the top.
