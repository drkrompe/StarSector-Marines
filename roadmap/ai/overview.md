# Battle AI Roadmap — GOAP Migration

> Long-form companion to the task list. Open this when picking up the GOAP work cold.

## What we're doing

Migrating infantry squad combat in the battle sim from hand-authored
behavior classes (`InfantryCombatantBehavior`, `PlanterBehavior`,
`KitRetrieverBehavior`, `OBJECTIVE_CAMPER`, `GarrisonBehavior`,
`PatrolBehavior`) to a **squad-level GOAP planner** (Goal-Oriented Action
Planning, F.E.A.R.-style).

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
- **`WorldState` is a value type** — two-long bitmask (see [01](complete/01-world-state.md)),
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
[08-behavior-wiring](complete/08-behavior-wiring.md), gated on the same `USE_GOAP_INFANTRY`
flag — flip serial-vs-parallel at one site for A/B.

## Staging

### Stage 1 — Infrastructure + parity ✅ COMPLETE

Built the planner, world-state, action/goal interfaces, and a minimal set
of actions/goals that reproduce current infantry behavior. Run behind a
`USE_GOAP_INFANTRY` flag in `BattleSimulation` (default `true` post-playtest).
**The deliberate "no new behavior" stage that earned the rest.**

Completed tasks (sealed in `complete/`):
1. [WorldState + Predicate](complete/01-world-state.md)
2. [Action / Goal / SquadPlan interfaces](complete/02-interfaces.md)
3. [Backward-chaining A* planner](complete/03-planner.md)
4. [WorldStateBuilder](complete/04-world-state-builder.md)
5. [Generic SuitabilityScorer system](complete/05-suitability-scorer.md) — subagent
6. [Parity actions](complete/06-parity-actions.md)
7. [EliminateEnemies goal](complete/07-parity-goal.md)
8. [GoapInfantryBehavior + dispatch flag](complete/08-behavior-wiring.md)
9. [Behavior validation](complete/09-parity-validation.md)

### Stage 2 — Real tactical actions (in progress)

**Re-imagining license.** Stage 1 infantry behavior was deliberately
parity-shaped and the playtest exposed the shape's flaws (tunnel-vision
pursuit, statue-mode firefights, no retreat). Stage 2 has full license to
replace it — no Stage 1 action / posture / target-picker is sacred.

Driven by [**10 — Stage 2 Tactical Stories**](stories/10-tactical-stories.md): a
story bank of combat moments we want the player to *see*, with primitives
derived from the stories rather than from a generic GOAP rolodex.

**Foundation complete** (sealed in `complete/`):
- [Stage 2 Foundation](complete/11-stage2-foundation.md) — Tier 0 shared
  types (predicates, per-member role slots, RoleAssigner wiring) + Tier 1
  infrastructure (cover model, ZoneQueries, goal-priority buckets,
  threat-density picker).

**Shipped stories (Slices 1–3.5):** A (garrison ambush), B (pinned and
broken), G (cover-aware reposition), I (engagement discipline), J (sabotage
cordon), K (room-clear sweep), L (choke-point ambush), M (room breach).

**Remaining stories (Slices 4–6):** C (bounding overwatch), D (patrol
intercept), E (mech-screened advance), F (objective rush under fire),
H (last-stand camper). See `stories/10-tactical-stories.md` for the full slicing.

### Squad-of-squads commander tier (active)

Strategic objective assignment above per-squad GOAP — needed for
multi-objective missions like Conquest (per-zone clears) and Assault
(sector sweeps). Per-squad GOAP plans tactical actions inside the
assigned objective; commander decides which squad goes where.

**Stage 1 spine + first two commanders shipped:** `MissionCommand`
interface, `ObjectiveAssignment` record, `ClearAssignedZoneGoal`,
`SabotageCommand`, `ConquestCommand` (lateral-strip partition). Remaining
work (AssaultCommand, defender-side commanders, richer scoring) queued
behind playtest + doc 15.

Design lives in [`12-squad-of-squads.md`](stories/12-squad-of-squads.md).

### Perception & influence (parked)

Per-squad belief about enemy positions (populated by LoS + audio
detection + commander briefings, with decay) and the commander-tier
influence map that smooths it into a tactical field. Fixes the
"squads pulled across the map by enemies they haven't observed"
class of bugs and unlocks frontline / bulge / breakthrough reasoning
for the commander tier.

Design lives in [`15-perception-and-influence.md`](stories/15-perception-and-influence.md).
**Near-term cheap wins (threat-direction cover scoring, ranged LoS
variant, threat-set gate on `HAS_LOS_TO_TARGET`) ship first as a
tactical task** — they lay the data-flow seam for the full system
without committing to it.

### Mech GOAP tree (Stage 1 complete, Stage 2 future)

Mechs promoted from `MechCombatantBehavior` (retired) to a planner-driven
role-aware tree via `GoapMechBehavior`. Stage 1 shipped two roles
(LR Support + Armored Support) with spawn-time assignment, mech morale
(HP-threshold drain + armor-gone cap + hysteresis), and
`MechSurviveContact` / `MechBreakContact`.

**Stage 1 sealed in `complete/`:**
- [Mech GOAP Stage 1](complete/14-mech-stage1.md) — MechRole, GoapMechBehavior,
  OverwatchKillZone, BackstopAssignedSquad, EngageAtCurrentBand, mech morale.

**Stage 2 (future):** Recon + Assault roles, dynamic re-assignment from
commander tier. High-level design in [`13-mech-goap.md`](stories/13-mech-goap.md).

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
