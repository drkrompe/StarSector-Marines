# Story: drop the BattleSimulation facade delegators (terminal migration story)

> **SHIPPED (substantive scope).** Both tiers that reach the sim through a contract now
> depend on `BattleView` (read) / `BattleControl` (mutate), not raw `BattleSimulation`:
> the GOAP spine (terminal flip `61e322a`) and the command tier (`cb91e87`), ~55 files
> narrowed total. The leftover upcasts (render/UI facade reads, the `decision/` per-unit
> dispatch, a few GOAP callers) are **out-of-scope by design** — see the DECISION block
> below; they are dispatch plumbing, not coupling-reduction wins.

## Context

`BattleSimulation` is no longer a god class — it's a thin orchestrator
that owns the tick loop and a constellation of constructor-injected
Services. But it still carries ~40 **facade delegators**: one-line
methods that forward into a service so a consumer can reach the service
through the sim instead of holding it directly. Two flavors:

- **Mutating behavior delegates** — `advanceMovement`, `fireShot` /
  `fireSecondary` / `fireMechWeapon` / `fireShotFrom`, `applyDamage`,
  `postShot`, `queueProjectile`, `setPath` / `clearPath`, `mintSquad`,
  `resolveUnit`, `targetOf`, `detonateNow`, `addUnit` / `queueSpawn`.
- **Service getters** — `getTacticalScoring`, `getHitResponseService`,
  `getReinforcementService`, `getCompoundService`, `getBattleResources`,
  `getUnitRegistry`, `getUnitIndex`, `getDestIndex`, `getVision`,
  `getDoodadCoverAt*`, `getVantagePointsFor`, `getAttackersOf`.

These are the residue of the old `*SimContext` interface pattern: every
subsystem reached into the sim for read/write access. The migration's
stated direction is to **drop that pattern in favor of explicit per-class
deps** ([`overview.md`](../overview.md) — "Decompose into Services …
Drop the context-interface pattern").

**The delegators were kept on purpose.** Every SoA-promotion story so far
(`target-id-primitive`, `ai-timer-primitives`, …) deliberately kept the
facade delegates as thin one-liners to hold per-promotion consumer churn
at **zero** — see the "Keep thin sim delegates" sections in those stories
and in [`path-mutation-to-navigation`](path-mutation-to-navigation.md).
That was a **stepping stone**, never the endpoint. This story is the
endpoint: consumers depend on the specific services they need, and the
facade methods come off the sim.

## Why this is the big one

The `sim` handle is welded into the GOAP contract. `Action` (and `Goal`)
thread `BattleSimulation sim` through `cost(state, squad, sim)`,
`execute(member, squad, sim)`, `roles(squad, sim)`,
`highlightCells(squad, sim)` ([`Action.java`](../../../src/main/java/com/dillon/starsectormarines/battle/ai/goap/Action.java)).
`BattleSimulation` is referenced in **~141 battle files**. So removing
the delegators is not a mechanical rename — it forces a decision about
*how a consumer acquires the services it needs*, and that decision has to
flow through the action/goal/behavior interface signatures.

## Scope

### In scope (the delegators come off)

The mutating behavior delegates and the service getters listed above.
After [`path-mutation-to-navigation`](path-mutation-to-navigation.md)
lands, `setPath` / `clearPath` are already pure `NavigationService`
delegates, so they fold in here cleanly.

### Out of scope (stays on the sim)

- **The sim's genuine public API** — `advance(float)`, `isComplete`,
  `getWinner`, `getGrid` / `getTopology` / `getZoneGraph`, `damageCell`.
  This is the battle's top-level handle, not a service forward.
- **Render / UI facade reads** consumed by `BattleScreen`, `FlybyOverlay`,
  and the HUD/debug panels — `getDecals`, `getActiveShots`,
  `getSmokingWrecks`, `getShuttles`, `getActiveProjectiles`, etc. The
  render layer legitimately treats the sim as the battle facade; pushing
  the renderer onto per-service refs is a separate question with no ECS
  payoff. Revisit only if it falls out naturally.
- **Test-only seams** already minimized (`releaseFromRegistry` for
  `TestUnits.kill`).

## Approach — the real design fork (decide before starting)

The constraint that shapes everything: GOAP actions are **stateless
singletons** ([`Action.java`](../../../src/main/java/com/dillon/starsectormarines/battle/ai/goap/Action.java)
class doc) — the planner holds one shared instance per action type and
runs search in parallel. So an action **cannot** hold per-battle service
references as fields. Whatever replaces `sim` must be *passed in* and must
honor the parallel-replan read-only contract (`cost` / `roles` /
`preconditions` run concurrently; `execute` runs serial).

Three candidate shapes, roughly in increasing ambition:

1. **Narrowing context interface(s).** Replace the `BattleSimulation sim`
   parameter with one or more role-scoped read interfaces
   (e.g. `MovementContext`, `CombatContext`, `WorldQuery`) that
   `BattleSimulation` implements. *Pro:* incremental, keeps a single
   passed handle, drops nothing from call sites but narrows the type.
   *Con:* the sim still implements everything — it's a smaller seam, not
   no seam. A genuine waypoint, possibly the right *first* slice.
2. **Services bundle / context record.** Pass an immutable
   `BattleServices` record (the constructor-injected services grouped)
   into `execute` / `cost` instead of the sim. Consumers pull
   `services.navigation()`, `services.weapons()`, etc. *Pro:* the sim
   stops being the access path; services are named explicitly. *Con:*
   touches every action/goal signature once.
3. **Direct per-consumer injection where the consumer is itself a
   stateful holder.** Behaviors that already hold state
   (`InfantryWeapons`, `TacticalScoring`, the `*System` classes) are
   constructor-injected today — extend that so they never read through
   the sim. Stateless actions still need option 1 or 2 for their slice.

Don't prescribe here. The likely path is **(1) as the first slice**
(narrow the type, zero call-site churn) then **(2) or (3)** to actually
sever the dependency. Pin the choice in this doc before writing code.

## DECISION (pinned 2026-05-28)

**Sequencing: command-tier getters first, defer the GOAP `sim`-param spine.**
A chunk of the in-scope service getters are *not* reached through the GOAP
`sim` parameter at all — they're consumed by **stateful, already-injectable**
classes (commands, reinforcement triggers/means, the reinforcement service
itself). Sever those by **option (3) direct injection**, one getter per
green slice, *without* touching the `Action`/`Goal` interface. Only once
that surface is gone do we decide option (1) vs (2) for the GOAP spine —
with a smaller, clearer problem in front of us.

### Getter difficulty audit (2026-05-28)

GOAP-free → injectable now (do these first):

- **`getBattleResources`** — 1 consumer (`ReinforcementService.dispatch`),
  no GOAP, no tests. **SHIPPED `53d5e7d`** (constructor-injected
  `BattleResources` into `ReinforcementService`).
- **`getReinforcementService`** — 2 consumers: `BattleSetup.installReinforcementLayer`
  (setup-time wiring, takes `sim`) and a `BattleScreen` debug hotkey
  (UI-facade, out of scope). No GOAP. Note: removing it means the setup
  method needs the service handed in another way (BattleSetup builds the
  layer *onto* the sim it just created — revisit whether this getter is
  even worth dropping vs. accepting it as a setup-only seam).

GOAP-bound → blocked on the spine decision (do these last):

- **`getCompoundService`** — ~8 main consumers incl. GOAP `HoldZone` +
  `SecureCompoundGoal`, plus UI panels and a wall of tests. Hardest;
  needs the `sim`-param mechanism settled first.
- **`getTacticalScoring` / `getHitResponseService` / `getUnitRegistry` /
  `getAttackersOf` / `getVantagePointsFor`** — heavily consumed by GOAP
  actions/postures via `sim`. Spine-gated.

### Command-tier reassessment (2026-05-28)

After reading consumer *bodies* (not just the call list), the command-tier
getters past `getBattleResources` turned out **not** one-at-a-time
severable: `ConvoyMeans` / `ShuttleMeans` / `WalkInMeans` /
`GarrisonDepletedTrigger` / `ConquestCommand` all receive `sim` as a
**method parameter** (`canFulfill`/`dispatch`/`check`/`tick`) and use it
for *several* reads each (grid, topology, squads, zone graph, vehicles),
not just compounds. Injecting `CompoundService` removes one of N sim uses
and doesn't let the getter drop (GOAP + UI + tests still call it) — net
churn, no severance. `getBattleResources` was severable only because its
sole consumer (`ReinforcementService`) is a constructor-injected stateful
*service*, not a sim-by-param method. **Conclusion:** the command-tier
getters need the same interface-narrowing decision as GOAP. We went
straight to the spine.

### GOAP spine mechanism — DECIDED: read/mutate interface split

Chosen over the services-bundle record because it **encodes the
thread-safety contract in the type system**. The GOAP-consumer `sim`
surface (~24 methods) splits cleanly along the parallel/serial line:

- **`BattleView`** (read-only) → the parallel-replan methods
  (`cost`/`roles`/`relevance`/`desiredState`/`highlightCells` + query
  helpers). A param narrowed to `BattleView` *cannot compile* a
  `setPath`/`fireShot`/`advanceMovement` — the contract was Javadoc-only
  before.
- **`BattleControl extends BattleView`** (adds mutators) → the serial
  `execute`.
- `BattleSimulation implements BattleControl` (every method already
  existed; zero new code).

**Incremental strategy (the key unlock):** because the sim *implements*
the interfaces, any consumer narrows its own param type **independently**,
and callers still passing `sim` upcast automatically. So:

1. ✅ Define interfaces + `implements` + migrate one read-only consumer
   (`ZoneQueries`) — **proving slice SHIPPED `9c6267e`**.
2. Narrow leaf consumers / helpers one slice at a time (read consumers →
   `BattleView`, mutating behaviors → `BattleControl`). Callers unaffected.
   - ✅ **Read sweep slice 1 SHIPPED `5f1bd7a`**: `WorldStateBuilder.build`
     + every `PredicateEvaluator` → `BattleView`. Grew `BattleView` with
     `getUnitIndex` + `snapshotActiveShots`. Plus `57304e0`:
     `InfantryCohesion.cohesionOverride` → `BattleView` (the postures'
     shared read helper; their `cost`/`relevance` are interface overrides,
     so they only flip in step 3).
   - ✅ **Interface growth `0c91af4`**: audited every `sim.*` call in the
     GOAP packages and grew `BattleView`/`BattleControl` to the full
     surface, so the helper sweep + flip never touch the interface files
     (conflict-free for parallel fan-out).
   - ✅ **Helper sweep `62ed71f`**: every private/static GOAP helper
     narrowed by what it does with the sim (read → `BattleView`, mutate →
     `BattleControl`). Fanned out to 3 Sonnet agents (infantry / mech /
     drone+goap-action); central compile + commit on the main thread.
     Callers still pass `BattleSimulation` → upcast, stays green.
3. ✅ **Interface flip SHIPPED `61e322a`** — the big-bang. `Action`'s
   `cost`/`roles`/`highlightCells` → `BattleView`, `execute` →
   `BattleControl`; `Goal`'s `relevance`/`desiredState`/`customPlan`/
   `pickMostRelevant` → `BattleView`. All ~38 implementors `@Override`d
   at once (3-agent fan-out, param-type only, name kept `sim`). Rode
   along `getSimTickIndex()` on `BattleView` (was a raw public-field read
   in `DroneSwarmAction.tickEngage`). Test doubles in `PlannerTest`/
   `GoalTest` updated. Build + full suite green. The parallel-replan
   read-only contract is now compile-enforced, not Javadoc-only.

## Command tier — SHIPPED (`a734122` grow, `cb91e87` flip)

The second and final substantive tier. The four command-tier interfaces
now take scoped views:

- `Objective.tick` → `BattleView` (objectives advance their own
  completion state by reading the sim; zero sim mutators in any impl).
- `MissionCommand.tick` → `BattleView` (commands orchestrate by writing
  `Squad.assignedObjective` — a Squad-object mutation, not a sim one).
- `ReinforcementMeans.canFulfill` → `BattleView` (cheap feasibility
  probe); `ReinforcementMeans.dispatch` → `BattleControl` (spawns:
  addUnit/addShuttle/addConvoyVehicle/mintSquad).
- `ReinforcementTrigger.check` → `BattleView` (reads sim, posts requests
  to a Consumer).

All ~15 impls + the concrete services (`ReinforcementService`,
`RecaptureTargetService`, `CompoundCaptureSystem`, `CompoundGarrisonSystem`)
narrowed by what each method does with the sim (2-agent fan-out, central
compile). Only the reinforcement spawn paths land on `BattleControl`;
commands, objectives, triggers, and capture/recapture tracking are
read-only `BattleView`. `getBattleResources` was dropped earlier
(`53d5e7d`), and `Planner.plan` narrowed to `BattleView` (`65ed79a`).

## What's left (out of scope / cosmetic)

The story's two substantive tiers — GOAP and command — both depend on the
contract now. What still takes a raw `BattleSimulation` is deliberately
left:

- **Render/UI facade reads** (`BattleScreen`, `FlybyOverlay`, HUD/debug
  panels) — the render layer legitimately treats the sim as the battle
  facade; no ECS payoff (per Scope § above).
- **`decision/` per-unit dispatch** — `UnitBehavior.update(Unit,
  BattleSimulation)` and `FallbackBehavior`/`FleeBehavior`/
  `CombatantBehavior`. These read AND mutate (they fire / move / tick the
  unit), so they'd be `BattleControl`, but `update` is the dispatch entry
  point, not a service-forwarding facade delegator. Narrowing it is
  uniformity, not coupling reduction.
- **Cosmetic GOAP-caller leftovers** — `GoapXBehavior.replanIfNeeded`,
  `SquadReplanSystem.tick`, `DroneSpawner.tryLaunch`. They pass the sim
  into now-narrowed params and upcast fine; narrowing them buys nothing.

Pick these up only if a future pass wants the across-the-board uniformity;
none is a coupling-reduction win.

**Sweep convention:** keep the parameter *name* `sim` (just change its
type) so each slice is a pure signature-level change, bodies untouched —
ideal for a Sonnet fan-out. The interface surface is grown per slice;
adding a method `BattleSimulation` already has is zero-risk. `BattleView`
currently exposes the high-frequency reads (grid, zoneGraph, units,
occupancyMap, targetOf, getSquad(s), tacticalScoring); `BattleControl`
the core mutators (setPath/clearPath/advanceMovement/fire*). Grow as
consumers need (`resolveUnit`, `getUnitIndex`, `getDoodadCoverAt*`,
`getCompoundService`, `getTacticalMap`, `fireShotFrom`, `queueSpawn`,
`mintSquad`, `snapshot*`).

## Sequencing

- **After** [`path-mutation-to-navigation`](path-mutation-to-navigation.md)
  (so `setPath`/`clearPath` are already clean nav delegates) and after the
  remaining low-payoff SoA promotions are either done or explicitly
  deferred — this story churns interface signatures, so it wants a quiet
  base.
- **Incrementally, one service at a time.** Migrate every consumer of one
  getter (say `getTacticalScoring()`), delete that getter, commit green;
  repeat. Same per-slice discipline the SoA promotions ran under. A
  single mechanical sweep per service is a good Sonnet-subagent fan-out
  (cf. the `targetId` sweep), with the interface-shape design kept on the
  main thread.

## Acceptance

- The in-scope facade delegators are gone from `BattleSimulation`;
  consumers acquire services via the chosen mechanism, not through the sim.
- `BattleSimulation`'s public surface is tick-loop + lifecycle +
  render-facade reads only.
- GOAP `Action` / `Goal` no longer take a raw `BattleSimulation` (or take
  a deliberately narrowed type per the chosen approach).
- `gradlew.bat compileJava` clean; full suite green.

## Priority

The migration's **terminal** story — the destination the whole arc was a
stepping stone toward. Lower urgency than any perf-bearing SoA work (this
is coupling reduction, not a hot-loop win), but it's what "BattleSimulation
is just the tick loop" actually requires. Large; budget it as its own
multi-slice arc, not a single commit.
