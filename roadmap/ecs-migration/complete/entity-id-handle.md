# Story: entity-id handle — held refs → id, then delete `Unit.registry`

> **SHIPPED.** Every held-ref→id slice landed (mech salvo targets `6f4e42b`,
> `Squad.leader`→`leaderId` `5a3ffb3`, `Squad.droneHub`→`droneHubId` `38d25c8`,
> pending-mutation POJOs `4c19bb7`); `localHp` was removed (`isAlive` redefined to a
> registry-null short-circuit) and `Unit.registry`/`denseIdx` deleted (`335cce8`). The
> dangling-self-route NPE class is structurally gone — a held `Entity` ref can no longer
> reach mutable state. Continued in [`world-facade`](world-facade.md) (registry dissolution).

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
- `localHp` removal (DONE, Slice 5): the duality collapse finished not by
  converting every held ref to an id first (the original plan), but by
  redefining `isAlive()` to short-circuit on the `registry == null` release
  marker — so held-ref *liveness* is safe without a corpse-window hp shadow.
  `getHp`/`setHp` went fail-loud; `release` snapshots nothing. The persistent
  dangling refs (mech salvo, Squad leader/hub, pending-mutation queue) were
  still worth converting to ids (Slices 1–4) — they *use* the resolved unit, not
  just its liveness — but the transient/within-pass held refs that only ask
  "alive?" stay object refs, made safe by the new `isAlive()`.
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
4. **Pending-mutation POJOs — SHIPPED (2026-06-02).**
   `PendingTargetMutation.target` → `targetId`, `PendingOccupancyDelta.u` →
   `unitId` (both `long`, `0L` = none). `DamageService` takes a
   `LongFunction<Unit> resolver` (`registry::getOrNull`) used only by the two
   flush drains. **`PendingTargetMutation` was a real post-release reader, not
   just a consistency item:** its drain (`flushPendingTargetMutations`) runs
   *after* `flushPendingDamage`, which calls `DamageResolver.resolve` →
   `releaseFromRegistry` **inline** — so a target killed this tick is already
   released when the mutation drain reaches it, and the old `target.isAlive()`
   was a `localHp`-dependent deref of a released `Unit`. Resolving `targetId`
   and skipping a null is the exact `isAlive()` replacement (death+release are
   atomic, so `getOrNull(id) != null` ⟺ alive). `PendingOccupancyDelta` drains
   in APPLY_OCCUPANCY (before any death this tick) so it never dangled — the
   id-resolve there is the consistency move. Full suite green at 681.
5. **Remove `localHp` — SHIPPED (2026-06-02). Phase A duality collapse
   complete.** `localHp` field deleted; replaced by a write-only `seedHp` seed
   field (mirrors `seedMaxHp`) for the pre-allocate window. `allocate` seeds
   `hp[]` from `seedHp`; `release` snapshots nothing back. `getHp`/`setHp` are
   now fail-loud (registry-only), like `getMaxHp`. Subclass ctors (`MapTurret`,
   `DroneHubUnit`, `Drone`) + the base ctor write `seedHp` directly instead of
   `setHp`.

   **Key simplification vs the plan:** this did NOT require converting the
   remaining held-ref readers (the SoA damage queue's `dmgTarget[]`, the flyby
   projectile target, turret/vehicle aim targets, the mech continuation-pass
   scratch list). They all learn liveness through **`isAlive()`**, which was
   redefined to `registry != null && registry.getHp(denseIdx) > 0f` — it
   short-circuits on the `registry == null` release marker (release already
   nulls it) *before* touching the dense slot. So a held ref to a released unit
   reads as dead without a corpse-window hp shadow. The only thing made
   fail-loud is a **direct** `getHp()`/`setHp()` on a released ref — audited:
   every direct caller is on a live, dense-iterated, or `getOrNull`-resolved
   unit (renderers, `SquadDetailPanel`/`SquadStateDumper` over the live
   registry, `DamageResolver` post-`wasAlive`-guard, `HubDemolitionSystem` over
   freshly-gathered live drones). The [[deferred_flush_released_target_guard]]
   early-out (`if (!wasAlive) return`) still holds — its `isAlive()` is now
   registry-null-safe rather than `localHp`-dependent. Full suite green at 689.

**Terminal phase (separate, large): now its own story —
[`world-facade`](world-facade.md).** Two-faced `World` access (hot primitive
`world.hp(id)` over the dense SoA + opt-in cold `world.id(id).getOrNull(Cmp.class)`
projection), then relocate self-accessors → delete `Unit.registry` → `Unit` →
`Entity` rename. Pervasive `u.getX()` churn (~516 sites / 72 files); staged per
accessor group, fan-out-able to Sonnet.

## Guardrails

- The resolve-by-id pattern is settled — don't reinvent it; mirror `targetId`.
- Run the **full** suite each slice — held-ref NPEs hide in tests that don't
  `advance()` between a kill and a read ([[battle_failloud_accessor_stale_readers]]).
- A writer stores `target.entityId` only while the target is live (it always is
  at trigger time); a reader never assumes the id still resolves.
- SoA design rules (overview.md) still apply to any new registry column.
