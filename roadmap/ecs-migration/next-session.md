# Next session — picking up the migration

Read [`overview.md`](overview.md) first for design rules.
Shipped work is in [`complete/`](complete/).

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
```

(Sibling tracks interleaved on HEAD, not ECS-migration: `9084ed4` battle-render
Story B, `31d8b17` goap shared zone-entry rule, plus ongoing battle-render +
campaign work.)

## State of play

- **Primitives promoted:** hp/maxHp, cellX/cellY, cooldownTimer,
  moveProgress, renderX/renderY, attackDamage, attackRange, accuracy,
  secondaryCooldownTimer, secondaryActionTimer, secondaryAimTargetId,
  burstRemaining, burstTimer, burstTargetId, targetId, repositionCooldown,
  fallbackTimer, fallbackCellX/fallbackCellY, wanderDwellTimer.
  Three `long[]` (`secondaryAimTargetId`, `burstTargetId`, `targetId`).
- **Five consumers** on dense-iter + SoA array reads. (The burst pass in
  `InfantryWeapons.tick`, `targetId`'s ~17 sites, and the fall-back group's
  break-contact consumers route through accessors, not dense-iter yet.)
- **Duality collapse (Phase A) underway.** Of the 22 promoted columns, the
  14 Group-N (mid-combat-only) `local*` twins are now gone (`c50e50d`); their
  accessors read the registry unconditionally. The remaining `local*` are the
  4 Group-S seed-only stats + the 5 Group-C corpse-read fields (Slices 2–3).
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
  phase."** ([[feedback_build_composition_now]].) Remaining Bucket-B reader:
  `UnitRenderService.sweepDeadSprites` (infantry dead pose) — the one that pulls
  in `UnitRegistry` → per-component-service decomposition (a corpse = an entity
  with position+render components, no health/AI).

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
> 2c. **NEXT — `UnitRenderService.sweepDeadSprites` (infantry dead pose).** The
>    last Bucket-B reader, and the one that genuinely needs a corpse to survive
>    release. **This pulls in the `UnitRegistry`-decomposition the user called
>    for:** split the single kitchen-sink table into per-component services
>    (`CellPositionService`, `HealthService`, `RenderPositionService`, …) with
>    `UnitRegistry` as a facade, so a corpse is just an entity present in the
>    position+render stores and absent from health/AI — not a redefined field
>    bag, not a parallel `CorpseService`. Build composition, don't defer it
>    ([[feedback_build_composition_now]]). The `Crashing` slice is the template.
> 3. Bucket-A sweep (`getUnits()` → dense registry; fan out to Sonnet).
> 4. Bucket-C cleanup; delete `UnitRosterService.units`.
> 5. Revert Group-N accessors to unconditional (fail-loud); drop the
>    `midCombatAccessorsReturnDefaultsWhenUnregistered` regression test.
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
>   NEXT. These are written pre-allocate (ctor + deboard loadout) but never
>   read post-release; collapsing them needs the seed to reach `allocate`
>   without a `local*` twin (reorder deboard so `allocate` precedes the loadout
>   setters, or a seed-spec). Removes the pre-allocate window.
> - **Slice 3 (Group C — corpse: hp, cellX/Y, renderX/Y)** — the only fields
>   with post-release readers (isAlive, demolition cell, dead-sprite, death
>   audio). Target: an explicit **death-snapshot** instead of permanent
>   `local*` shadows — possibly realized as a stamped corpse *decal* or a
>   minimal "body" entity (renderable + location), which also leaves room for
>   a future revive mechanic. Decide the shape when we reach it.
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
- `git log --oneline -5` should show `9c6267e` or your own recent work
  at the top.
