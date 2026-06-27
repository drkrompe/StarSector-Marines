# Story — Air craft as composed entities (the air-tier ECS)

> **Superseded for storage + identity (2026-06-27) by
> [`air-entities-into-world.md`](../air-entities-into-world.md)** (COMPLETE).
> Slices 1–3 below shipped as written, but their *storage decision* (sparse
> `ComponentStore<T>`, "promote to dense later") and *identity* decision (a
> disjoint air id space) were both realized differently and better by the
> into-world epic: air craft are now real entities in the single archetype
> `EntityWorld` (one id space, no `ComponentStore`, no `nextAirId`, no `Shuttle`
> handle), and the `releaseAirEntity` seam folded into `world.destroy(id)`. Read
> the into-world doc for the shipped end-state; the slices-4/5 ("fighters",
> "dense-SoA + unified id space") framing below is largely subsumed — only the
> *fighters* arm remains open (see [`fighter-air-entities.md`](fighter-air-entities.md)).

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
2. **Turrets as a presence component — SHIPPED `3050a1e`.** `MountedTurret[]`
   lifted off `Shuttle` into a `ComponentStore<AirTurrets>` keyed by entity id;
   armed craft carry it, transports don't — presence is the gate (replaced the
   `turrets.length == 0` scans). `shouldHoverLoiter`/`allTurretsDry` moved into
   `AirSystem` (query the store); `releaseAirEntity` drops it via the slice-1
   death seam. Entity flow corrected: `addShuttle` (mint id) → `equipDefaultTurrets`
   → `attachAirTurrets`. Render reads via `getAirTurretMounts`. Air-path tests green.
3. **`ShuttleMission` component — SHIPPED `a9ad43f`.** The delivery state machine
   + per-sortie lifecycle (state, LZ/entry/exit, deboard, cycles, hover, squad,
   garrison, hp) grouped into a `ShuttleMission` composed on `Shuttle`
   (`s.mission`). `Shuttle` shrank to the shared air-entity core: `entityId` +
   `AirBody` + render state + the mission (engine/turret already in stores). The
   state-machine behavior was already a system (`AirSystem`), so this was pure
   data grouping — ~100 `s.X` → `s.mission.X` accesses (compiler-as-worklist;
   `State` enum stays on `Shuttle`; ctor signature unchanged). A fighter composes
   the same core with a different mission. Full compile + air/objective tests green.
4. **Fighters compose the same core** — real `AirBody`/engine/turret components
   with a `FighterMission`; retire `FlybyOverlay`'s parallel cosmetic path
   (the `roadmap/backlog.md` "flyby fighters as real air entities" item).
5. **Dense-SoA promotion + unified id space** — gated on a fighter-swarm perf
   read; only when branch/sentinel cost beats a dense table.

## Follow-ups (from slice-1 critique)

- **Component release seam — DONE.** `AirSystem.releaseAirEntity(entityId)` is the
  single authoritative drop-all-components point, called at the one death
  transition (DEPARTING → GONE). New air component stores (slice 2 turrets, …)
  register their removal in that one method, so adding a component can't
  reintroduce an orphan leak. The future AA shoot-down path (the `Shuttle.hp` /
  `HOVER_HP_THRESHOLD` fields are wired forward for it) just calls
  `releaseAirEntity` — a one-liner, not a leak risk.
- **AEROSHUTTLE plume positioning fidelity.** AEROSHUTTLE renders the aeroshuttle
  sprite but borrows `kite`'s hull (`renderHullId`), so its engine plumes now
  *show* (fixed in `27250d7`) but sit at kite's slot geometry projected onto the
  aeroshuttle sprite — close, not pixel-true. Real fix needs an `aeroshuttle.ship`
  (none in vanilla) or hand-authored slots. Cosmetic; low priority.
- **Per-tick `float[]` alloc in `ThrusterDemand.compute`** (one per shuttle per
  tick). Fine at current counts ([[ship-then-optimize]]); revisit if fighter
  swarms make air-FX allocation measurable (folds into the slice-5 dense pass).

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
