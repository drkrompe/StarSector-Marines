# Story: entity-id handle — held refs → id, then delete `Unit.registry`

Phase B identity work for the [component model](../component-model.md). The
direct sequel to [`collapse-unit-handle`](collapse-unit-handle.md): that story
collapsed the `local*` duality down to the pinned `localHp`; this one removes the
reason `localHp` has to exist and drives `Unit` toward a bare id.

## Why (user, 2026-06-02)

> "I want to delete `unitRegistry` from `Unit` and stop the backward pattern of
> referencing unit but instead reference the `entityId` for callers. We've had a
> number of NPEs due to `Unit` having POJO references that are nullable based on
> 'unit' being alive."

Two problems, one root cause — **callers hold `Unit` object references that
dangle when the unit dies.**

1. **Held `Unit`-ref fields** (`MechLoadoutState.lrmSalvoTarget`,
   `TurretAim.State.target`, `Squad.leader`, …). When the referent is killed and
   swap-and-popped from the registry, the holder still has the object; the only
   thing keeping `x.isAlive()` from NPE-ing is the `localHp` shadow (≤0 at death).
   This is the [[battle_failloud_accessor_stale_readers]] class.
2. **`Unit.registry` back-pointer** makes `Unit` a smart handle so `u.getHp()`
   can self-route. It's what lets a *released* `Unit` still be dereffed at all.

The ECS fix is the same for both: **the entity is its `long entityId`; mutable
state lives in stores keyed by that id; you resolve to data on demand, you don't
hold the object.** A released id resolves cleanly to `null`
(`registry.getOrNull(id)`) — no dangling ref, no liveness-gated nullable field.

## Target shape

- Held references become `long *Id` fields (0L = none). At point of use, resolve
  `Unit t = registry.getOrNull(id)` (or `sim.resolveUnit(id)`); `null` means
  released-or-never — the holder reacts (re-acquire / clear) without an
  `isAlive()` on a stale object. This is **exactly** the pattern the registry's
  SoA `targetId`/`burstTargetId`/`secondaryAimTargetId` columns already use; the
  held off-registry fields are the stragglers.
- Once no held ref dangles, `localHp` loses its last reader →
  `getHp` goes fail-loud, `release` stops snapshotting hp (the duality collapse
  finishes).
- **Endgame:** with callers id-based, relocate `Unit`'s self-accessors
  (`getHp/getCellX/…`) to a registry/`World` API addressed by id (or dense idx
  for hot dense walks), then **delete `Unit.registry`** (and `denseIdx`). `Unit`
  becomes id + immutable archetype (`id` label, `faction`, `type`, `rng`) +
  capability components. That is what finally earns the `Unit` → `Entity` rename
  (overview.md's north star).

## Slices

Held-ref → id, smallest-blast-radius first; each independently shippable + green.

1. **Mech salvo/burst targets — SHIPPED (2026-06-02).**
   `MechLoadoutState.{chaingunBurstTarget, srmSalvoTarget, lrmSalvoTarget}` →
   `long *TargetId` (`0L` = none, the project-wide "no entity" id sentinel —
   ids start at 1, `getOrNull(0L)` fast-paths to null; `-1` stays reserved for
   *cell* sentinels). Writers (`MechCombatantBehavior`) store `target.entityId`;
   the `HeavyWeapons` continuation pass resolves `registry.getOrNull(id)` — a
   target killed mid-salvo resolves to null and drops the lock, no `isAlive()`
   on a dangling ref. Established the pattern. Full suite green at 677.
2. **`Squad.leader` → `leaderId` — SHIPPED (2026-06-02).** The highest-churn
   held ref (~13 sites / 12 files). Identity compares → `member.entityId ==
   squad.leaderId`; `isAlive()`/cell reads → `sim.resolveUnit(leaderId)` (null =
   dead/none, which *replaces* the isAlive check). `mintSquad` null-guards the
   leader (`0L` sentinel). **`isMechSquad()` denormalized** — it probed
   `leader.mech`, which both needed a leader deref and silently flipped a mech
   squad to "infantry" once leaderless; now a `mechSquad` flag is set once at
   mint from the first member (squads are homogeneous), so it survives leader
   death. Full suite green at 677.
3. **`Squad.droneHub` → `droneHubId` — SHIPPED (2026-06-02).** The other
   persistent-held ref on `Squad`. `isDroneSquad()` reads id presence
   (`droneHubId != 0L`) — stable for the squad's life; `DefendHubGoal.relevance`
   resolves `sim.resolveUnit(droneHubId)` to gate on the hub still being alive
   (null = destroyed). Writer in `DroneSpawner` stores `hub.entityId`. Green at 677.
4. **Turret aim (cosmetic, deferred)** — `TurretAim.State.{target,
   excludeFromCrowding}` are **transient**: the State is rebuilt fresh each tick
   and `target` is `getOrNull`-resolved from the caller's canonical id field
   (`v.turretTargetId` / `mt.targetId`), so they never dangle across ticks. Not
   an NPE source; convert only as a consistency pass if/when the static
   `TurretAim.tick` gets a resolver.
4. **Pending-mutation POJOs** — `PendingTargetMutation.target`,
   `PendingOccupancyDelta.u`. Drained same-tick, so lower NPE risk; convert for
   consistency (or document why they stay object refs).
5. **Remove `localHp`** — with every held ref id-based, `getHp` has no
   post-release reader. Drop the shadow + fallback; `getHp` fail-loud; `release`
   stops snapshotting hp. **Phase A duality collapse complete.**

**Terminal phase (separate, large):** relocate self-accessors → delete
`Unit.registry` → `Unit` → `Entity` rename. Pervasive `u.getX()` call churn;
staged per accessor group, fan-out-able to Sonnet. Not in this story's first pass.

## Guardrails

- The resolve-by-id pattern is settled — don't reinvent it; mirror `targetId`.
- Run the **full** suite each slice — held-ref NPEs hide in tests that don't
  `advance()` between a kill and a read ([[battle_failloud_accessor_stale_readers]]).
- A writer stores `target.entityId` only while the target is live (it always is
  at trigger time); a reader never assumes the id still resolves.
- SoA design rules (overview.md) still apply to any new registry column.
