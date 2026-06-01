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
| `TurretDemolitionSystem` | flip dead turret cell to rubble; mark `demolished` | **reaction** — same-tick, reads `cellX/Y` |
| `HubDemolitionSystem` | cascade-kill a dead hub's drones; flip to rubble | **reaction** — same-tick, reads `homeHub` backlink |

Collective field needs (the corpse-record spec): `type`, `faction`,
`cellX/cellY`, `renderX/renderY`, `deathPoseIdx`; drone crash adds
`body.x/y/facing`; demolition adds the `demolished` flag + cell.

### Bucket C — other (~10 sites) — UI/debug/flyby
`FlybyOverlay` (4 target/AoE passes, all `isAlive`-gated), debug dumpers,
profiling counts, squad panels. All live-filtered → Bucket-A treatment, or
left reading the registry. `SquadStateDumper` is the one that wants dead
members for diagnostics — give it the corpse mechanism or drop dead-member
dumping.

## The corpse-home design (the crux — decide before building)

The 4 readers split into **static** (render a frozen corpse) vs **lifecycle/
reaction** (run post-death behavior). The recommended model handles both
without keeping dead `Unit`s in a list:

**On death (`DamageResolver.resolve`), emit a death event carrying a corpse
snapshot, then release from the registry immediately.** Consumers react:

- **Infantry/static** → stamp a render-only **corpse decal**
  `{type, faction, renderX, renderY, deathPoseIdx}` into a `List<CorpseDecal>`.
  `sweepDeadSprites` iterates decals, not units. No entity, no tick.
- **Drone crash** → spawn a **crash FX** (animation) seeded with
  `body.x/y/facing` at death; the crash becomes an FX lifecycle, not a `Unit`
  lifecycle. (`DroneCrashSystem` → an FX emitter.)
- **Turret/hub demolition** → a **death-event handler** flips rubble / cascades
  immediately on the event, instead of scanning the list for `!isAlive()`.

Alternative considered — **deferred release** (keep the dying entity registered
with a `DYING` role until its animation completes, then release): less code for
the crash/demolition cases (they stay in dense-iter), but muddies the
"registry == live only" invariant and leaves a dead-ish unit observable. The
event+decal+FX model keeps the invariant clean and is the ECS-aligned target;
it's also the natural seam for a future **revive** mechanic (a death event can
resolve to "downed, not dead" and re-allocate instead of stamping a decal).

## Sequencing

1. **Corpse home (enabler).** Build the death-event + corpse-decal (+ crash-FX,
   demolition-handler) mechanism; migrate the 4 Bucket-B readers off the list.
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
