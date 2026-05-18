# Battle AI Roadmap — GOAP Migration

> Long-form companion to the task list. Open this when picking up the GOAP work cold.

## What we're doing

Migrating infantry squad combat in the battle sim from hand-authored
behavior classes (`InfantryCombatantBehavior`, `PlanterBehavior`,
`KitRetrieverBehavior`, `OBJECTIVE_CAMPER`, eventually `PatrolBehavior` and
`GarrisonBehavior`'s engaged path) to a **squad-level GOAP planner**
(Goal-Oriented Action Planning, F.E.A.R.-style).

## Why GOAP

The mod's mission system is growing — Conquest landed recently, and the
backlog implies many more types (assault, sabotage, raid, escort, defend,
exfil, intel-recovery, etc.). Authoring a bespoke behavior tree per mission
type does not scale. A planner that **recombines** a shared action library
to satisfy whichever goal the current mission injects scales better.
We accept higher up-front infrastructure cost for that flexibility.

## Architecture

**Hybrid granularity (F.E.A.R.-style).** Squad-level planning, unit-level
execution.

- The planner runs per-squad. It chooses a goal, then a plan (ordered actions)
  whose chained effects satisfy that goal's desired state.
- Each plan step is assigned to one or more squad members.
- The per-unit AI tick is "execute one tick of my current action" — it does
  not plan.

**Core types** (`battle.ai.goap`):
- `Predicate` — enum of all facts the planner reasons over.
- `WorldState` — `EnumMap<Predicate, Boolean>` snapshot. Cheap to copy for
  planner search. Will swap to bitmask later when we feel the cost.
- `Action` — preconditions, effects, cost, execute-per-tick.
- `Goal` — relevance score + desired-state builder.
- `Planner` — backward-chaining A* over plan-space.
- `SquadPlan` — ordered (Action, assignedMembers) steps + current index.
- `WorldStateBuilder` — sim+squad → WorldState snapshot.
- `Scorer<T>` / `RoleAssigner` (`battle.ai.goap.scoring`) — generic suitability
  scoring for picking which members fill which action slots.

**Existing pieces we keep:**
- `TacticalScoring` — primitives for target picking, firing position, cover.
  Actions call these; they don't get duplicated.
- `GridPathfinder`, `Squad`, `SquadAlertLevel` — unchanged.
- `MechCombatantBehavior` — single-unit ad-hoc combat, planner overkill;
  stays as-is.
- `FleeBehavior`, `TurretBehavior`, `FallbackBehavior` — bespoke, stay.

**Replan triggers (not every tick):**
- Plan invalidated (a queued action's precondition stopped holding)
- Goal achieved (plan ran to completion)
- Squad member died
- `SquadAlertLevel` transitioned
- Periodic ~2 sim-seconds fallback

## Persistence

`BattleSimulation` is transient — battles don't survive saves, only the
campaign-level marine roster does. So plans, WorldStates, and per-unit
action assignments do **not** need to be `Serializable`. They live in
memory only.

## Parallelism & data-oriented design

The expensive phase of GOAP is **planning** (WorldState build + backward
A* search). Action **execution** is one tick of cheap work. The natural
parallelism split mirrors this:

| Phase | Threading |
| --- | --- |
| Sim tick — events, damage, deaths | Serial (current behavior) |
| Per-squad replan (WorldState build + Planner.plan + RoleAssigner.assign) | **Parallel across squads** |
| Per-unit Action.execute | Serial (writes back to sim) |

Constraints this places on the architecture:
- **`WorldState` is a value type** — two-long bitmask (see [01](01-world-state.md)),
  no allocations on read, lockless thread-safe by value.
- **`Action`/`Goal` implementations are stateless singletons.** Per-step state
  lives on the `SquadPlan` and the assigned `Unit`, never on the action itself.
- **Sim reads during the planning phase are thread-safe.** Achieved by phase
  ordering: no other sim mutation happens during the parallel replan window.
- **Pathfinder allocations** during planning (cost estimation) must be either
  thread-local scratch or fully reentrant. Audit `GridPathfinder` when actions
  start scoring against it inside `cost(...)`.

Stage 1 lands the architecture (immutable WorldState, stateless actions). The
actual parallel `replan-across-squads` execution gets wired in
[08-behavior-wiring](08-behavior-wiring.md), gated on the same `USE_GOAP_INFANTRY`
flag — flip serial-vs-parallel at one site for A/B.

## Staging

### Stage 1 — Infrastructure + parity (this roadmap)

Build the planner, world-state, action/goal interfaces, and a minimal set
of actions/goals that reproduce current infantry behavior. Run behind a
`USE_GOAP` flag in `BattleSimulation`, A/B against `InfantryCombatantBehavior`,
bake at parity. **The deliberate "no new behavior" stage that earns the rest.**

Tasks (see numbered sub-docs):
1. [WorldState + Predicate](01-world-state.md)
2. [Action / Goal / SquadPlan interfaces](02-interfaces.md)
3. [Backward-chaining A* planner](03-planner.md)
4. [WorldStateBuilder](04-world-state-builder.md)
5. [Generic SuitabilityScorer system](05-suitability-scorer.md) — subagent
6. [Parity actions](06-parity-actions.md)
7. [EliminateEnemies goal](07-parity-goal.md)
8. [GoapInfantryBehavior + dispatch flag](08-behavior-wiring.md)
9. [Parity validation](09-parity-validation.md)

### Stage 2 — Real tactical actions (future)

Add `Suppress`, `MoveToFlank`, `TakeCover`, `AdvanceUnderCover`, `Regroup`,
`Withdraw`. Add goals `SurviveContact` (HP-threshold gated) and
`SecurePosition`. This is where the visible "interesting combat" payoff lands.

Subagent fanout candidates already on the radar:
- Spatial grid for fast "enemies/allies within radius" queries
- Cover data on doodads (current cover is per-cell from the grid; richer
  cover model unlocks better firing-position scoring)
- Suppression effect for units / areas (per-unit pinned state)

### Stage 3 — Mission-specific goals (future)

`CompleteObjective` (with sub-actions `PlantCharge`, `Hack`, `Hold`),
`EscortVIPToExfil`, `RetrieveKit`. `PlanterBehavior` / `KitRetrieverBehavior` /
`OBJECTIVE_CAMPER` retire — their work becomes goals + actions.

## Conventions

- Package root: `com.dillon.starsectormarines.battle.ai.goap`. Sub-packages
  for `actions`, `goals`, `scoring`, `world`.
- Predicates are **bucketed** when expressing numeric thresholds
  (`SQUAD_HP_BELOW_HALF` not `SQUAD_HP_FRAC`). Keeps the planner pure-boolean
  and the action effects honest.
- Actions are stateless singletons (like the current `UnitBehavior`s). Per-step
  state lives on the `SquadPlan` step + the assigned `Unit`'s fields.
- Don't bypass `TacticalScoring` — extend it if a new primitive is needed.

## Cross-references

- Task list: see TaskList in-session.
- Current behavior we're preserving in Stage 1: `InfantryCombatantBehavior.java`.
- Memory: `[[architecture_decisions]]`, `[[ship_then_optimize]]`.
