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
| `UnitRenderService.sweepDeadSprites` | draw the frozen death pose for the rest of the battle | **static** — render-only |
| `DroneCrashSystem` | multi-tick fall → impact → wreck animation | **lifecycle** — ticks `crashTimer`, reads `body.x/y/facing` |
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
  - **Known follow-up (carried):** the drone cascade still scans the legacy
    list and the drones it kills (hp=0 + release) bypass `DamageResolver`, so
    they publish no `DeathEvent`. Harmless while `DroneCrashSystem` detects dead
    drones by list scan; when the crash system moves onto the event seam the
    cascade must publish per-drone `DeathEvent`s (or the crash system keeps a
    list scan). Re-entrant publish during `drain()` needs the index-based drain
    loop to tolerate `pending` growth — verify before wiring that up.

## Sequencing

1. **Corpse home (enabler).** Build the death-event + corpse-decal (+ crash-FX,
   demolition-handler) mechanism; migrate the 4 Bucket-B readers off the list.
   *(Death-event mechanism + turret demolition + hub demolition done — see
   Progress. Remaining: drone crash, dead-sprite render — the two that want the
   actual body store built.)*
2. **Bucket A sweep.** Migrate the ~20 live-iterators `getUnits()` → dense
   registry. Fan out to Sonnet; each is a local `isAlive`-gate → dense loop.
3. **Bucket C cleanup.** Point UI/debug/flyby at the registry; resolve
   `SquadStateDumper`'s dead-member dump.
4. **Delete `UnitRosterService.units`.** No readers remain; `release()` stops
   retaining corpses.
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
