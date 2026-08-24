# ECS nouns

Status: ACTIVE — `EntityWorld` is the battle composition substrate; capability membership, lifecycle transitions, narrow service/view boundaries, determinism, and profile-gated column work are standing laws.

Written: 2026-08-23

Updated: 2026-08-24 — kept locomotion legible beneath moving primary-weapon actions.

The battle simulation has one composition substrate: an `EntityWorld`.  This
document names the durable model, ownership, and safety laws of that substrate.
`BattleComponents` owns the battle component vocabulary.

## The vocabulary

| Noun | Meaning | Authority |
| --- | --- | --- |
| **Entity** | A positive, bare `long` identity. `0` is the no-entity sentinel. An id is monotonic for the world lifetime and is never recycled. | `EntityWorld` supplies the generic mint/adoption mechanism; `UnitRosterService` owns the battle-wide id sequence across ground, air, and convoy families. |
| **Component type** | A code-registered capability schema: a stable mask bit plus zero or more typed fields. A zero-field type is a presence tag. | `EntityWorld` provides the engine mechanism; `BattleComponents` is the battle-tier registration and shared-query authority. |
| **Archetype** | The exact set of component types an entity has. Each archetype owns one dense structure-of-arrays table. | `EntityWorld`. |
| **Row location** | The internal entity-id → `(table,row)` index. It is storage bookkeeping, not gameplay identity or an ordering contract. | `EntityWorld` only. |
| **Query** | A cached match over tables that contain every required capability and none of the excluded capabilities. | The owner that defines the capability vocabulary, normally `BattleComponents`. |
| **Service** | The narrow by-id owner for a game component family. It is the convenient boundary for irregular logic, not a second store. | Battle simulation services. |
| **System** | A stateless consumer that advances behavior or authors derived state from components. | The system's tick phase; the simulation orders phases. |

## Storage and composition laws

1. **The id is the entity.** No battle-tier object is an entity handle or an
   alternative per-entity store. References between entities use ids and must
   tolerate a target that has died before resolution.
2. **Capability, not carrier, determines membership.** A component says what an
   entity can do or what state it carries. Presence is the capability gate;
   `UnitType`, `role`, faction, or a nullable object are not substitutes for an
   archetype-membership question.
3. **Component schemas are code-driven and bounded.** The engine knows only
   `INT`, `LONG`, `FLOAT`, and `OBJECT` fields and uses no reflection. Battle
   schemas and field indices belong in `BattleComponents`, not in callers or
   roadmap prose. The current bitmask supports 64 component types (ids 0–63).
4. **Rows are dense and unstable.** Removal and transitions use swap-and-pop;
   no row, dense-list position, or natural query walk order has semantic
   identity. An order-sensitive result must establish its own deterministic
   ordering by id or another explicit key.
5. **Structural changes move rows.** Adding or removing a capability creates a
   combined target archetype as necessary, copies shared fields, and moves the
   entity once. Use `transmute` when a lifecycle change has several additions
   and removals; do not model every small changing datum as membership churn.
6. **Direct fields are for random access; columns are for homogeneous hot
   work.** Systems that can operate on a table's raw arrays should retain a
   query and walk its matched rows. Rich, branchy control flow may use its
   owning service's by-id API. Convert only when behavior and profiling justify
   it.
7. **Object fields are not a failure of ECS.** They preserve a genuinely shared,
   small-population mutable payload (for example, an air or ground body) when
   scalar decomposition has no demonstrated ownership or locality benefit.
   Primitive fields are preferred for dense, independently consumed state.

## Lifecycle, mutation, and iteration

The normal lifecycle is creation into a capability-appropriate live archetype,
ordinary field mutation while the entity remains in that archetype, then a
single lifecycle transition or destruction. Ground combat death is a transmute:
durable corpse data such as identity, continuous `POSITION`, sprite, and
telemetry may ride the row move while live-only combat and behavior capability
is removed. `POSITION` is the single ground-location authority; its cell
projection is owned by `continuous-positions-nouns.md`. Air and convoy entities
use their own lifecycle components rather than pretending to be grid units.

Structural mutation is the one iteration hazard. A direct `destroy`,
`addComponent`, `removeComponent`, or `transmute` swap-pops a row and is safe
at a serial phase boundary. A system walking the affected query must instead
queue the operation through `world.cmd()` and let the simulation call
`world.flush()` at its tick barrier. Creates are deliberately immediate: they
append beyond the current walk's captured range. Command application is FIFO;
double destruction is harmless and later commands for an already-destroyed id
are skipped.

Queries retain their matched-table cache across ticks. The world rebuilds that
cache only when a previously unseen archetype is created; a changing row count
does not invalidate it. A system must still capture each table's row count for
its own walk and must not assume a cached table list means stable row order.

## Battle-world shape

`BattleComponents` defines one world vocabulary, not one universal entity
shape. Current families include:

- **Grid actors** use identity, position, health, combat, vision, and optional
  movement, decision, equipment, squad, task, or specialized capability.
  Static emplacements deliberately lack movement and AI-state membership.
- **Corpses** are lifecycle variants, not detached records: they retain only
  what their corpse consumers need and can additionally carry a crash
  capability while a drone falls.
- **Air craft** are disjoint world entities with air identity, kinematics,
  mission, authored appearance, and optional engine or turret capability. They
  carry no grid position, so grid queries exclude them naturally.
- **Convoy vehicles** are disjoint world entities with ground identity,
  kinematics, mission, optional turret, and vehicle-control state. Their
  mission state owns vehicle liveness; health is not implied.
- **Presentation and reporting** are component data too. `SPRITE` is an
  authored, tier-neutral draw instruction for both live sheet actors and
  corpses; layered-animation, locomotion, and telemetry are separate
  capabilities where their consumers differ. Rendering resolves graphics
  resources and never becomes the authoritative entity store.

These families may share the same `EntityWorld` without being forced through a
false common component. A new component must have a stable capability/lifecycle
reason; a new query should name a real consumer set, not a speculative generic
API.

### Authored layered motion

Layered unit motion splits driver authority from pose authority. Simulation
systems author tier-neutral facing, state flags, and normalized phases into the
layered-animation components. Locomotion phase advances from distance traveled;
action phase advances from the active weapon or equipment use. The simulation
therefore still owns whether an actor is moving or acting and how quickly that
progress changes.

The unit-layer layout document owns what each normalized phase looks like. Every
clip declares a time, locomotion-phase, or action-phase driver; its keyframe
durations weight segments within normalized progress rather than dictating
simulation speed. The live renderer samples those clips read-only and applies
their offsets, independent scale, pivot, angle, and visibility to compatible
runtime-selected armor and equipment sprites. Fixed ordering for dynamic
equipment remains compositor-owned. Mech thigh stretch is ordinary authored
layer scale, not a separate animation system. Actors or clips without a usable
layered definition retain their existing procedural or sheet fallback.

Secondary equipment definitions own the selected item's sprite identity, ordinary
carry state, occlusion fallback, and references to its using/firing clips. The
referenced equipment-specific unit-layer variant owns the combined action pose
while that item is in use, including an optional `special` layer transform. Combat
timing and AI policy do not move into the clip; they continue to supply the
normalized action phase that the clip samples.

An equipment action that begins mid-stride retains that last locomotion sample for
a bounded entry interval. The renderer blends only matching body layers from the
sampled stride into the destination action pose; equipment-specific destination
layers remain action-owned. Returning to carry or idle is authored as the action
clip's final recovery frame. This seam needs no renderer history and does not delay
or reinterpret the gameplay action.

Primary aim and fire remain upper-body actions while a marine translates. Their
authored body, head, and weapon transforms therefore keep action authority, while
both feet continue sampling the live distance-driven walking clip. A primary shot
cannot visually plant a marine that the simulation is still moving.

The standalone layer workbench consumes the same document and duration-weighted
sampling law. Its driver scrubber is therefore acceptance evidence for the live
pose at a given simulation phase, not an editor-only approximation.

## Authority and tier boundaries

- `engine.ecs` is game-agnostic storage and composition. It owns tables,
  locations, masks, queries, and the structural-change protocol, but knows no
  battle types or rendering objects.
- The battle tier owns component registration, spawn archetypes, lifecycle
  transforms, service APIs, and tick ordering. `BattleSimulation` is the
  coordinator, not a second component facade.
- `BattleView` is the read-only contract for the parallel replan window;
  `BattleControl` adds the serial-update mutators. A consumer receives the
  narrowest of these contracts that its phase permits rather than the whole
  simulation.
- A component service owns domain operations and invariants around its fields;
  callers do not bypass it merely because a by-id field accessor exists.
- Presentation systems may author presentation components after game state is
  settled. Game decisions must not accidentally read presentation-only facing,
  sheets, or graphics handles back as simulation truth.
- `MapEditor` is the stateless coordinator for runtime topology mutation. It
  sequences topology, navigation, zone-graph, and collapse-effect updates but
  owns none of their component or map data; generation remains a separate
  concern.

## Determinism and performance

The world is serial simulation infrastructure. It promises deterministic table
matching order (by archetype mask), but swap-and-pop deliberately does not
promise deterministic row order. Explicitly sort any order-sensitive candidate
set, and seed all battle randomness through the simulation's battle-owned
stream rather than ad-hoc random sources.

Column walking is an available tool, not a rewrite mandate. Use it only for a
real homogeneous consumer with a current profile and a behavior-preserving
scope. `NavigationService`'s occupancy rebuild is an existing example, not a
mandate to convert branchy domain logic.

## Extension boundaries

Presentation, firing, and vehicle follow-ons do not reopen the storage model.
New components and systems must preserve the same ownership, lifecycle,
structural-mutation, and phase contracts. Related feature behavior belongs in
its own noun package, including air craft, vehicle control, rendering, and
combat.
