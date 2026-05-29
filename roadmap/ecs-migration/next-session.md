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
61e322a  battle: flip Action/Goal GOAP contract to BattleView/BattleControl (TERMINAL)  ← 2026-05-29
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
- **Full suite green** at `9c6267e`. The prior `RecaptureTargetRegistryTest`
  / `BspMapPreviewTest` failures were sibling-session WIP and are no longer
  failing — never in any ECS-migration changeset.

## Active stories (priority order)

> **TL;DR for a cold start:** stories 1–4 + story 5 Slice 1 are shipped.
> **Story 6** ([`drop-sim-facade-delegators`](stories/drop-sim-facade-delegators.md)) —
> the GOAP spine flip is **DONE** (`61e322a`): `Action`/`Goal` now take
> `BattleView` (read) / `BattleControl` (mutate), the thread-safety contract
> is compile-enforced, all ~38 implementors + helpers narrowed, suite green.
> **Resume on the command tier** — the remaining in-scope surface: the
> command/reinforcement/objective classes (`ConvoyMeans`, `ConquestCommand`,
> `GarrisonDepletedTrigger`, the `Objective`/`ReinforcementMeans`/
> `ReinforcementTrigger` interfaces, etc.) still take raw `BattleSimulation`
> as a method param. Same playbook: grow the interfaces if needed → narrow
> helpers → flip the command/objective/reinforcement interface signatures.
> Lower stakes (no parallel-replan thread-safety angle) but the same
> coupling-reduction win. Render/UI facade reads stay on the sim (out of
> scope). Story 5 Slice 2 (map generation) is an optional stretch.

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
   **Terminal** migration story. **GOAP spine flip DONE; command tier
   remains ← CURRENT FOCUS.** Goal: consumers depend on a scoped contract
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
   - **REMAINING (command tier):** `command/`, `command/reinforcement/`,
     `command/objective/`, `command/compound/` classes still take raw
     `BattleSimulation` as a method param (`ConvoyMeans`/`ShuttleMeans`/
     `WalkInMeans`.canFulfill/dispatch, `*Command`.tick, `GarrisonDepleted
     Trigger`.check, the `Objective`/`ReinforcementMeans`/`Reinforcement
     Trigger` interfaces). Same playbook — grow interfaces as needed →
     narrow helpers → flip the command/objective/reinforcement interface
     signatures. No parallel-replan thread-safety angle, so lower stakes;
     pure coupling reduction.
   - **Out of scope (stays on sim):** render/UI facade reads
     (`BattleScreen`, `FlybyOverlay`, HUD/debug panels), the sim's genuine
     public API (`advance`/`isComplete`/`getGrid`/`getTopology`/`damageCell`),
     and the cosmetic leftover callers (`replanIfNeeded` trio, `Planner.plan`,
     `SquadReplanSystem.tick`, `DroneSpawner.tryLaunch` — they upcast fine).
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
