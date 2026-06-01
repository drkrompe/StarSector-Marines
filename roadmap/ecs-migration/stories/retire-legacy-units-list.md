# Story: retire the legacy units list (the migration spine)

Promoted to the **spine** of the component-model phase on 2026-06-01 after the
Slice-1 corpse NPE (`getBurstRemaining` on a released unit) surfaced the root
cause: the battle tier keeps **two parallel unit collections that disagree
about death**.

- `UnitRegistry` (dense SoA) — **live units only**; `release()` swap-and-pops
  the dead out.
- `UnitRosterService.units` (`List<Unit>`) — **live + dead corpses**; dead
  entries are retained for the whole battle (no cleanup path).

A corpse is therefore a `Unit` with `registry == null` still sitting in a list
that dozens of systems iterate. Any accessor that assumes registration trips
over it (the Slice-1 NPE). The null-safe accessor fix (`2e03ade`) papers over
the whole bug *class*; **deleting the list removes the class** — and lets the
mid-combat accessors go back to unconditional/fail-loud (the overview's
endgame), because a live unit would then *always* be registered.

This story subsumes the old Phase-A Slice 3 (corpse handling) — the corpse
home is the enabler — and is the concrete form of the "event-driven death
emit" the [`UnitRegistry`](../../../src/main/java/com/dillon/starsectormarines/battle/unit/UnitRegistry.java)
javadoc flagged as future work.

## Goal

Delete `UnitRosterService.units`. Live iteration reads the dense registry;
post-death needs read a dedicated corpse mechanism. End state: no `Unit` ever
has `registry == null` while observable, so the Group-N accessors revert to
unconditional registry reads (fail-loud on misuse).

## Consumer classification (from the 2026-06-01 audit)

Three buckets across the `getUnits()` / injected-`List<Unit>` consumers:

### Bucket A — live-iteration (~20 sites) — migrate to dense registry
All gate on `isAlive()` (or only expect live units) and skip the dead, so they
can iterate `registry` over `[0, liveCount())` — the corpse never appears.
Examples: `GoapInfantryBehavior`, all live passes in `UnitRenderService`
(footprints, turret/hub/infantry sprite sweeps, HP bars), `DroneRenderSystem`
live pass, `WorldPicker`, `AttackerIndexService`, `FleeBehavior`,
`TacticalScoring`, the objective/recapture counts. Mechanical; fan-out-able.

### Bucket B — corpse-readers (4 sites) — need a corpse home
| Site | Post-death job | Shape |
|---|---|---|
| ~~`UnitRenderService.sweepDeadSprites`~~ | draw the frozen death pose for the rest of the battle | **static** — render-only — **MIGRATED** to a `DeadBody` **component**. A body is recorded on every `DeathEvent` (`DeadBodySystem`), and the sweep iterates that store paired with the surviving render-position component — no units-list scan, no `Unit` handle. Same render gates (pose ≥ 0 / `hasDeathPose` / cache). **All four Bucket-B readers now done → corpse home complete.** |
| ~~`DroneCrashSystem`~~ | multi-tick fall → impact → wreck animation | **lifecycle** — **MIGRATED** to a `Crashing` **component** + presence-driven system (slice 2b). The crash is composition now: a dead drone *has* a `Crashing` component, the system processes the component-set (no units-list scan), FX is the side effect. `DroneRenderSystem` reads the same store. The hub cascade publishes per-drone `DeathEvent`s so the seam handles cascade kills too. |
| ~~`TurretDemolitionSystem`~~ | flip dead turret cell to rubble; mark `demolished` | **reaction** — same-tick, reads `cellX/Y` — **MIGRATED** to `onDeath(DeathEvent)` (foundation slice). Its guardpost-all-dead scan still reads the list; migrates with the rest of this bucket. |
| ~~`HubDemolitionSystem`~~ | cascade-kill a dead hub's drones; flip to rubble | **reaction** — same-tick, reads `homeHub` backlink — **MIGRATED** to `onDeath(DeathEvent)` (slice 2a). Its drone cascade still scans the list, and the drones it hp=0s bypass `DamageResolver` so they don't publish their own `DeathEvent` — fine while the crash system list-scans for dead drones; revisit when it migrates. |

Collective field needs (the corpse-record spec): `type`, `faction`,
`cellX/cellY`, `renderX/renderY`, `deathPoseIdx`; drone crash adds
`body.x/y/facing`; demolition adds the `demolished` flag + cell.

### Bucket C — other (~10 sites) — UI/debug/flyby
`FlybyOverlay` (4 target/AoE passes, all `isAlive`-gated), debug dumpers,
profiling counts, squad panels. All live-filtered → Bucket-A treatment, or
left reading the registry. `SquadStateDumper` is the one that wants dead
members for diagnostics — give it the corpse mechanism or drop dead-member
dumping.

## The corpse-home design (decided 2026-06-01)

A **death-event mailbox/distributor** + a **lightweight body entity** —
see [[battle_death_dispatcher_design]]. Chosen over both a render-locked decal
and deferred-release.

**On death (`DamageResolver.resolve`): publish a `DeathEvent`, then clean the
unit up / remove it from the live `UnitRegistry`.** A `DeathDispatcher`
(mailbox) fans the event to subscribed handlers, each deciding how to represent
the death — the reaction is decoupled from the death site, so new post-death
behavior attaches as a handler, not an edit to the death method.

The corpse is **not** a render-locked decal — that freezes it into "just a
sprite" and blocks later interaction. It is a **lightweight body entity**
(identity + location + the render-needed fields, minimal state), removed from
the *live* registry but still an entity, so future mechanics — **medics, a
revive / "downed-not-dead" state** — can find and act on it. Revive = a handler
intercepts the death event (or re-allocates the body into the live registry)
instead of finalizing the corpse.

Handlers (initial set, mapping the 4 Bucket-B readers):
- **Render** → draws bodies from the corpse/body store (replaces
  `sweepDeadSprites` scanning the units list).
- **Drone crash** → drives the fall→impact→wreck lifecycle off the body /
  a crash FX seeded with `body.x/y/facing`.
- **Turret demolition** / **hub demolition** → flip rubble / cascade-kill on
  the event, instead of scanning the list for `!isAlive()`.
- *(future)* **medic** → query downed bodies, revive.

Open sub-questions for the build (resolve as we go, don't over-design):
- Where bodies live — a dedicated `CorpseService` / body store vs a second
  registry. Keep it minimal; it only needs what the handlers read.
- Whether the multi-tick lifecycles (crash, demolition) run on the body entity
  directly or as FX seeded from the event. Pick per-handler; the mailbox
  doesn't care.

## Progress

- **Foundation slice SHIPPED (2026-06-01).** `DeathDispatcher` (buffered
  mailbox — `publish` on death, per-tick `drain()` fans out to subscribers at
  the demolition phase) + `DeathEvent(Unit)` in `battle/unit`.
  `DamageResolver.resolve` publishes in the `died` branch before
  `releaseFromRegistry`, alongside the untouched `deathSink`. The dispatcher
  dispatches serially (resolve is serial-only — inline or off the
  `flushPendingDamage` drain), so no synchronization needed. **First handler
  migrated:** `TurretDemolitionSystem` now reacts via `onDeath(DeathEvent)`
  instead of a per-tick `List<Unit>` scan; the sim subscribes it and the old
  `tick(units)` call became `deathDispatcher.drain()` at the same phase slot
  (timing preserved). Tests: `DeathDispatcherTest`, `TurretDemolitionSystemTest`.
  - **Why buffered, not synchronous:** `resolve` fires at several tick points
    (inline direct fire, the APPLY_DAMAGE queue drain, AoE detonations,
    off-tick strafing). Buffering decouples *when a death is recorded* from
    *when its reaction runs*, so handlers fire once per tick at one known serial
    phase — exactly the end-of-tick timing the batch demolition had, and by
    drain time every this-tick death is settled (so sibling-state queries like
    "all turrets on this post dead?" behave identically).
- **Slice 2a SHIPPED (2026-06-01).** `HubDemolitionSystem` migrated off its
  per-tick `List<Unit>` scan to `onDeath(DeathEvent)`, same shape as turret
  demolition — flip the dead hub's cell to rubble + smoking wreck, then
  cascade-kill the hub's launched drones. The sim subscribes it; the old
  `hubDemolition.tick(units)` call is gone (both demolitions now react in the
  one `deathDispatcher.drain()`). The two now-unified profiler phases
  `DEMOLISH_TURRETS` + `DEMOLISH_HUBS` collapsed into a single `DEMOLISH` phase.
  Test: `HubDemolitionSystemTest` (demolition + same-tick cascade→crash
  ordering + control drone untouched).
- **Slice 2b SHIPPED (2026-06-01) — first composition slice.** The drone crash
  is now a `Crashing` **component**, not a per-tick type scan. Commits:
  - `2a3abc8` — hardened `DeathDispatcher.drain()` to two swap buffers, drained
    in waves, so a `DeathEvent` published *by a handler mid-fan-out* (a cascade)
    is itself fanned out in the same drain. Prerequisite for the cascade publish.
  - `40fa668` — new `battle.component` package: `ComponentStore<T>` (presence-
    based, entity-id-keyed sparse store; a system iterates `entries()`, no
    null-check / no role branch; survives registry release) + `Crashing` (the
    falling-after-death component, composes with `AirBody`, carries its own
    timer/spin so the processor is entity-agnostic). `DroneCrashSystem` is now
    `onDeath` (attach component + opening plume) + `tick(dt)` (process the
    component-set → spin, count down, wreck on impact, detach). `Drone` lost
    `crashStarted/crashTimer/crashed` (store presence is the state).
    `HubDemolitionSystem`'s cascade publishes a `DeathEvent` per drone so the
    seam crashes cascade-kills too. `DroneRenderSystem` reads the store.
  - **Why this matters:** it's the first real **composition** in the battle
    tier — capability-as-presence + a system over the component-set, FX as a
    side effect of having the component. The pattern the rest of the migration
    follows (see [[feedback_build_composition_now]]).
- **Slice 2c-enabler SHIPPED (2026-06-01) — first column decomposed out of the
  kitchen-sink.** Render position (`renderX/renderY`) lifted from the dense
  `UnitRegistry` into a standalone `RenderPositionService` (`battle.unit`), a
  thin float API over a `ComponentStore<RenderPosition>` (new `RenderPosition`
  component in `battle.component`). Keyed by `entityId` and **not removed on
  release**, so a released corpse still resolves its death-pose location
  directly through the service — the survive-release property
  `sweepDeadSprites` needs. `Unit.getRenderX()/setRenderPos()` route through a
  per-unit `renderPositions` reference set at `allocate` and **not nulled** on
  release (unlike `registry`). The dense `renderX/renderY` columns, their
  grow/swap/snapshot, the `getRenderX(idx)`/`renderXArray()` accessors, and the
  render half of the `local*` release-snapshot are all gone; `localRenderX/Y`
  is now a pre-allocate seed only. Picked render first because the audit found
  **zero** dense-array readers of it (every reader is the per-entity accessor),
  so it's zero-perf-cost and zero-behavior-change for the living. Hot-dense
  columns (`cellX/cellY`, hp, timers) stay dense. Tests: `RenderPositionServiceTest`
  (8) + rewritten `UnitRegistryTest` render cases (route-through-service,
  survives-release, undisturbed-by-tail-swap). Full suite green.
  - **Still on the list:** `sweepDeadSprites` itself still *scans* `getUnits()`
    for dead units (reading the now-surviving `getRenderX()`); migrating that
    scan to a corpse/dead-pose iteration source is the remaining 2c work.
- **Slice 2c SHIPPED (2026-06-01) — corpse home complete; last Bucket-B reader
  migrated.** `UnitRenderService.sweepDeadSprites` no longer scans the legacy
  `getUnits()` list. New `DeadBody` component (`battle.component`) — the corpse's
  render identity (`type` + `deathPoseIdx`); a `DeadBodySystem` (`battle.unit`)
  subscribed to the death dispatcher records one on **every** `DeathEvent`,
  keyed by entity id, surviving release. The render sweep iterates the
  `getDeadBodies()` store and pairs each body with its surviving
  `RenderPositionService` entry (the prior slice) to place the sprite — a corpse
  is now literally *an entity present in the dead-body + render-position stores
  and absent from the live registry*. Render gates unchanged (`deathPoseIdx ≥ 0`
  / `hasDeathPose` / cache guard), still no vision gate. Attaches a body for
  every death (not just sprited types) so the store is a true body home for
  future medics/revive; the render filter handles "has corpse art". Verified
  faithful: both production death paths publish a `DeathEvent` (`DamageResolver`
  + the hub cascade), so every corpse-with-pose that the old list-scan drew now
  has a `DeadBody`. Test: `DeadBodySystemTest` (attach-on-drain, survives-release
  paired with render position, no-body-while-alive). **With this, all four
  Bucket-B corpse-readers are off the list — sequencing step 1 (corpse home) is
  done.**
- **Bucket A wave 2 + Bucket C SHIPPED (2026-06-01) — every production reader off
  the list.** Six commits (`f6851eb` → `78f54fe`):
  - *Dead-unit readers.* `DeadBody` gained `faction`; `MissionResolver`'s casualty
    count is now live registry + `DeadBody` store (survivors + corpses, no
    live+dead scan). `DroneRenderSystem`'s dead pass reads the `Crashing` store
    directly (the wreck tracks the component's `AirBody`, no `Unit` handle). The
    dead-mech smoking wreck moved off `HeavyWeapons.spawnMechWrecks` onto a
    `MechWreckSystem` death-event handler (the one death seam catches every kill
    path); HeavyWeapons dropped its `effects` + `units` fields.
  - *Mutate-during-iteration → snapshot-then-apply.* The combat continuation +
    AoE readers fire damage inline in serial phases (`insideParallel` is false
    outside UPDATE_UNITS), so a lethal hit runs `DamageResolver.resolve` →
    `releaseFromRegistry` and swap-and-pops the dead target out of the dense
    table mid-walk. `InfantryWeapons.tick` (gather mid-burst units),
    `HeavyWeapons.advanceMechWeapons` (gather mechs), `Detonations.detonate`
    (gather in-splash), `FlybyOverlay` ×2 (gather in-radius), and
    `HubDemolitionSystem.cascadeKillDrones` (gather the hub's drones) each gather
    the matching set first, then apply over the snapshot — reused scratch lists,
    allocation-free in steady state for the hot passes.
  - *Simple live + Bucket-C.* `EquipmentDropService` (+ its two helpers) and the
    `TurretDemolitionSystem` guardpost scan walk the dense registry (the latter
    only ever cared about live turrets — the old "needs dead turrets" comment was
    wrong). `SquadOverviewPanel`/`SquadDetailPanel`/`SquadPlanDebugPanel` →
    dense; `TickProfileDumper`/`TickProfileDebugPanel` unitCount →
    `liveUnitCount()` (live, was live+dead); `SquadStateDumper` member dump went
    live-only (corpses no longer listed — the stuck-squad diagnostic cares about
    survivors, squad-level `aliveMembers` carries attrition). Dropped the dead
    `DamageResolver.units` field.
  - **No production reader of `getUnits()`/`UnitRosterService.units` remains.**
    What's left is the accessor definitions, the sim's internal `units` alias
    (passed to `vision.tick`/`rebuildOccupancyMap`/`squadFallback.tick`), and the
    test surface — all step 4.

## Sequencing

1. ~~**Corpse home (enabler).** Build the death-event + component mechanism;
   migrate the 4 Bucket-B readers off the list.~~ **DONE.** Death-event mechanism
   + turret demolition + hub demolition + drone crash (`Crashing` component) +
   render-position decomposition (`RenderPositionService`, entity-id-keyed,
   survives release) + the dead-sprite render (`DeadBody` component +
   `DeadBodySystem`, the last Bucket-B reader). A corpse is now an entity present
   in the dead-body + render-position component stores and absent from the live
   registry's health/AI — exactly the composition target
   ([[feedback_build_composition_now]]). No Bucket-B reader scans the list.
2. ~~**Bucket A sweep.** Migrate the live-iterators `getUnits()` → dense
   registry.~~ **DONE — wave 1+1b (`9b4100a`/`008afb1`/`b2e0df2`) + wave 2
   (`f6851eb`→`78f54fe`).** Wave 1 converted 46 live-only loops across 30 files
   (6-way Sonnet fan-out + straggler pass); wave 2 handled the non-mechanical
   remainder — dead-unit readers (corpse/`Crashing` stores + a `MechWreckSystem`
   death handler), mutate-during-iteration sites (snapshot-then-apply), and the
   simple live readers. See the Progress block above.
3. ~~**Bucket C cleanup.** Point UI/debug/flyby at the registry; resolve
   `SquadStateDumper`'s dead-member dump.~~ **DONE (wave 2).** Panels + profile
   counters → dense/`liveUnitCount()`; `SquadStateDumper` member dump went
   live-only (corpses dropped, by design). `FlybyOverlay` AoE → snapshot.
4. **Delete `UnitRosterService.units`.** No production readers remain. Repoint
   the sim's internal `units` alias (vision/nav/squad-fallback) off the field,
   drop `getUnits()` from the three types, migrate the test surface, then
   `release()` stops retaining corpses.
5. **Revert Group-N accessors to unconditional** (fail-loud) — live units are
   now always registered; the null-safe branch is dead. Same for the Slice-2/3
   seed/corpse work if not already done.

Independent of this spine: **Slice 2 (Group S seed-only stats)** can land
whenever — it removes the pre-allocate window and is orthogonal to the list.

## Acceptance

- `UnitRosterService.units` is gone; nothing reads a `registry == null` `Unit`.
- Corpses render (decals) and drone-crash/demolition still work, sourced from
  the corpse mechanism, not the list.
- Group-N accessors are unconditional again; the `midCombatAccessorsReturn
  DefaultsWhenUnregistered` regression test is removed or repurposed.
- Full suite green; a play-test confirms corpses, crashes, and turret/hub
  demolition look unchanged.

## Risk / notes

- The crash + demolition systems currently **mutate** across ticks — they are
  not pure snapshot readers. The event+FX model must reproduce that lifecycle
  (crash animation timing, rubble flip) faithfully.
- Rendering draws live + dead today; after the split it draws live (registry) +
  corpse decals + crash FX. Painter order must keep corpses under live units.
- `BattleSimulation` and several systems hold the `List<Unit>` as an injected
  alias (e.g. `InfantryWeapons`, `DamageResolver`) — those wirings change to
  the registry.
</content>
</invoke>
