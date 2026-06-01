# Story — Air craft as composed entities (the air-tier ECS)

> Shared-core member of the [`air/`](../overview.md) category, and the air-tier
> arm of the battle [`ecs-migration`](../../ecs-migration/component-model.md).
> Pulled into existence by the engine-FX work (thrust weighting +
> [smoothing](../complete/thruster-thrust-weighting.md)): that work needed
> per-entity, per-tick FX state, which is exactly a *component*, and exposed that
> `AirBody` is a god object.

## The smell

`AirBody` fuses three ECS concerns in one class:

| Concern | Members | ECS role |
|---|---|---|
| Transform | `x, y, facingDegrees` | component (data) |
| Motion | `vx, vy, ax, ay, angVelDegPerSec` | component (data) |
| Steering | `tickToward(goal, mode, handling, dt)` | **system** (behavior) |

And the wider air tier is a **parallel entity-world outside the ECS**: `Shuttle`
is a fat object in `AirSystem`'s `List<Shuttle>` with **no entity id** (engine
lights even key off `System.identityHashCode(s)`), while the Unit population
lives in `UnitRegistry` + `ComponentStore`. Two of the three air members above
(`ax/ay`) were *just* bolted on for the FX read — the capability-explosion the
[component model](../../ecs-migration/component-model.md) exists to kill, now
landing on the air side. Fighters (a **swarm**) and ground-targetable ships are
next; each multiplies it.

## The target — air craft are entities, behaviors are systems

An air craft becomes a **bare `long` entity id** plus composed components,
processed by systems over their sets — the same model the Unit tier is migrating
to, applied to air from the start:

- **`AirBody`** → a pure **kinematics data component** (Transform + Motion
  grouped; a finer split is a later slice if a consumer wants one without the
  other). No behavior.
- **`AirSteeringSystem`** — the extracted `tickToward` integration. Reads
  kinematics + `AirHandling` + a goal/mode; writes kinematics. Stateless.
- **`ThrusterFx`** — per-slot smoothed engine intensity (the lerp state), in a
  `ComponentStore<ThrusterFx>` keyed by entity id. **`ThrusterFxSystem`** lerps
  it toward the `ThrusterDemand` target each tick (fast attack / slow decay) —
  this is what fixes the 0↔100 plume oscillation. FX is then a *read* of a
  component, per the model's "FX as a side-effect of components" rule.
- **`Turrets`** — the `MountedTurret[]` as a presence component (armed craft have
  it, transports don't), processed by the turret-fire system over its set
  instead of a per-shuttle `if (turrets.length == 0)` scan.
- **mission / lifecycle** — the shuttle state machine (`state`, timers, cycles,
  LZ/entry/exit, capacity) is shuttle-specific; it becomes a `ShuttleMission`
  component so the *entity + kinematics + engine + turret* core is shared with
  fighters, which carry a different mission component.

### Storage decision — sparse now, dense when swarms bite

Use the existing **sparse `ComponentStore<T>`** (id-keyed map) for air
components. The air population is tiny today (a handful of shuttles), so the map
is free, and it's the project's composition primitive. When fighter **swarms**
make a hot dense iteration measurably cheaper, promote the hot air components
(kinematics) to a dense SoA table mirroring `UnitRegistry` — *ship then optimize*,
and the [component model](../../ecs-migration/component-model.md) already
prescribes "dense for hot homogeneous, sparse for optional." No generic
`Aspect`/`World` machinery — hand-rolled presence, same as the Unit side.

### Identity

Air entity ids are monotonic `long`s ([[skip-generation-bits]] — never
recycled), minted by the air registry, **disjoint** from unit ids. Slice 1 uses
the id only for air-only stores (no cross-space collision); folding air + unit
into one id space (so e.g. `LightAccumulator` keys and `targetId` are uniform) is
a later slice.

## Slices

1. **Identity + steering-system + first component — SHIPPED `0bbb25b`.**
   - `Shuttle` got a `long entityId`, minted on `AirSystem.add`.
   - `AirBody.tickToward` extracted to a stateless `AirSteeringSystem`; `AirBody`
     is now pure kinematics data (resolves the `ax/ay` placement smell — the
     system writes them as Motion data). `Shuttle` **and `Drone`** route through
     the system (drones compose `AirBody` too).
   - `ThrusterFx` component + `ComponentStore<ThrusterFx>` + `ThrusterFxSystem`
     ramp the smoothed per-slot demand each sim tick (fast attack / slow decay);
     renderer + light pass read it via `BattleSimulation.getThrusterGlow`.
     **Fixed the oscillation.** `ThrusterFxSystemTest` green.
   - **Outstanding:** in-game eyeball of the ramp feel; attack/decay rates
     (`ThrusterFxSystem.ATTACK_PER_SEC`/`DECAY_PER_SEC`) are first-cut tunables.
2. **Turrets as a presence component** — lift `MountedTurret[]` into a
   `ComponentStore<AirTurrets>`; the turret-fire loop iterates the store's set.
3. **`ShuttleMission` component** — peel the state machine off `Shuttle` toward
   an id + composition; `Shuttle` shrinks to a handle.
4. **Fighters compose the same core** — real `AirBody`/engine/turret components
   with a `FighterMission`; retire `FlybyOverlay`'s parallel cosmetic path
   (the `roadmap/backlog.md` "flyby fighters as real air entities" item).
5. **Dense-SoA promotion + unified id space** — gated on a fighter-swarm perf
   read; only when branch/sentinel cost beats a dense table.

## Out of scope

- Generic `Aspect`/`World`/bitset queries (Phase C of the migration — deferred).
- The Unit-tier migration itself — this is the air arm; they converge at the
  unified id space (slice 5), not before.
- Save/load of air entities (shuttles are transient mid-battle today).

## Done when (slice 1)

- Air craft carry an entity id; `AirBody` has no behavior; steering is a system.
- A `ComponentStore`-backed `ThrusterFx` component is advanced by a system over
  its set, and plumes ramp smoothly instead of snapping 0↔100.
- The full suite compiles + the `ThrusterDemand`/new system tests pass.

This story stays in `stories/` until slices 2–5 land; slice 1 is struck
through above with its shipped hash (`0bbb25b`).

## Cross-references

- [`thruster-thrust-weighting.md`](../complete/thruster-thrust-weighting.md) — the
  FX work that pulled this; `ThrusterDemand` is the per-tick target this smooths.
- [`ecs-migration/component-model.md`](../../ecs-migration/component-model.md) —
  the `ComponentStore`, the engine-vs-game framing, the sparse/dense rule.
- Memory: [[build-composition-now]], [[default-to-ecs-shape]],
  [[compose-effects-not-carrier]], [[battle-services-systems]],
  [[air-vehicle-kinematics]], [[skip-generation-bits]].
