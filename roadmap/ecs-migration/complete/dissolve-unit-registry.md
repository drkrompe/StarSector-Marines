# Step 4 part B — dissolve `UnitRegistry` (the migration finale)

**Shipped 2026-06-27** — `751458a0` (B1 foundation), `5a79941a` (B2 the
dissolution). Suite green at 765, build clean.

The end of the battle-tier ECS migration. `UnitRegistry` is **DELETED**. The
entity is its `long` id; every per-entity datum lives in the archetype
`EntityWorld`, reached by id through one facade. No dense per-unit columns, no
back-pointer, no registry.

## What UnitRegistry was, and where its two jobs went

By this point `UnitRegistry` held only two tangled responsibilities (every dense
SoA column had already migrated to the world in steps 3a–3f + the folds):

| Job | New home |
|---|---|
| Dense live `Entity[]` roster + id-mint + `indexById` swap-and-pop + the owned `EntityWorld`/`BattleComponents` | **`UnitRosterService`** (already the sim-owned roster that *held* the registry) |
| The ~60 `*ById` / presence / typed by-id adapters | **`World`** (the by-id access facade) |

`UnitRosterService` now exposes `allocate`/`release`/`get(int)`/`getOrNull(long)`/
`liveCount()`/`denseArray()`/`isAliveById`/`indexOf`/`isLive` + `entityWorld()`/
`components()`/`world()`. `addUnit`→`allocate`, `releaseFromRegistry`→`release`
are thin wrappers it already had. It **owns** the `World` and exposes it via
`world()`.

`World` reads the `EntityWorld`'s component columns directly (no adapter
middleman): `world.hp(id)` = `entityWorld.getFloat(id, HEALTH, HP)`. Strict on
mandatory live columns (fail-loud once the corpse transmute removes them),
tolerant on `renderX/Y` (render must not fail on a maybe-released ref).

`requireLiveIndex` was dropped — it had **zero callers** (the `*ById` adapters
stopped routing through the dense index once they read the world by id).

## Sequencing — two green commits

- **B1 `751458a0`:** `World` stops delegating to `registry.*ById` and reads the
  `EntityWorld` directly; ctor becomes `World(EntityWorld, BattleComponents)`.
  Internal to `World` + one ctor call — zero caller churn. This made `World`
  independent of the registry so B2 could delete the adapters.
- **B2 `5a79941a`:** the dissolution. UnitRosterService absorbs the roster;
  `World` keeps the (now-inlined) adapters; `UnitRegistry` deleted; ~24 consumer
  files + 3 test files repointed.

## The low-churn key: `roster.world()`

`battle.unit.Entity` already imported `battle.sim.World`, so `battle.unit →
battle.sim` was an existing dependency — `UnitRosterService` (in `battle.unit`)
owning + exposing `World` (in `battle.sim`) added **no new package cycle**. That
let every service reach `World` through its existing `roster` ref
(`roster.world()`) with no new ctor params. Six services that took a bare
`UnitRegistry` in their ctor (`HitResponseService`, `Detonations`,
`HeavyWeapons`, `InfantryWeapons`, `UnitUpdateSystem`, `MechWreckSystem`) changed
to take `UnitRosterService`; everyone else already had `roster`/`sim`.

## The sweep

Compiler-backstop + 5 Sonnet agents on disjoint buckets (decision / combat /
squad+infantry / nav+unit+vision / air+vehicle+drone+turret+mech+ops). Per-site
rule: `registry.fooById(id)` → `world.foo(id)` (drop the `ById`/`Of` suffix;
hoist `World world = roster.world()` above hot loops); roster-bit
`registry.get/getOrNull/liveCount/denseArray/isAliveById` → `roster.*` (same
names). `getUnitRegistry()` → `getRoster()`; render/ops use `sim.world()` +
`sim.getRoster()`. `Entity.advanceAlongPath(UnitRegistry,…)` → `(World,…)`.
`UnitRegistryTest` → `UnitRosterServiceTest` (roster bits on `r`, adapter bits on
`r.world()`).

Behavior-preserving: pure receiver/name swaps, no logic change. Suite green at
765, build clean.

## Migration status

**Battle-tier ECS migration COMPLETE.** No `UnitRegistry`; the entity is its id;
state in the `EntityWorld`; `UnitRosterService` is the live roster; `World` is the
by-id facade. `ComponentStore<T>` survives only for the **air-FX** subsystem
(`ThrusterFx`/`AirTurrets`), a separate entity space — it dies in the captured
[air-entities-into-the-world epic](../../air/air-entities-into-world.md), now
unblocked (it adopts air craft into the sim-owned world this step leaves behind).
