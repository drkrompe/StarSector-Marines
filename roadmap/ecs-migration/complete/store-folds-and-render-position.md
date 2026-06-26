# Step 4 part A — fold the sparse stores into the EntityWorld

**Shipped 2026-06-25** — `dafaacaf` (Crashing), `8f8a0d76` (MechLoadout + World
cold face), `1cbf5b03` (RenderPosition). Suite green at 771.

The first half of `ecs-migration` step 4: get **every battle-unit component into
the one archetype `EntityWorld`**, retiring the parallel sparse-`ComponentStore`
mechanism for units. With this done, the registry's dissolution (part B) is purely
the id-mint + dense `Entity[]` relocation.

## What folded

| Capability | Was | Now | Commit |
|---|---|---|---|
| `Crashing` (drone fall) | `ComponentStore<CrashingComponent>` | `CRASHING` (id 10, OBJECT) | `dafaacaf` |
| `MechLoadout` (chassis) | `ComponentStore<MechLoadoutComponent>` | `MECH_LOADOUT` (id 11, OBJECT) | `8f8a0d76` |
| Render position | `RenderPositionService` (own `ComponentStore`) | universal `RENDER_POSITION` (id 2, was corpse-only) | `1cbf5b03` |

Each holds the existing component object in an OBJECT column (Crashing/MechLoadout)
or is a primitive float pair (RenderPosition). Walked via shared queries
(`crashing`, `mechLoadouts` excl. `CORPSE`) or read by id.

## The load-bearing trick: survive the death transmute

`Crashing`, `MechLoadout`, and `RENDER_POSITION` are all read **after** the entity
leaves the live registry — the drone crash animates post-death, `MechWreckSystem`
reads the loadout to drop a wreck, the corpse draws at its death spot. The
`ComponentStore`s gave this for free (keyed by id, untouched by release). The world
equivalent: **keep them OFF `DeadBodySystem.corpseRemove`**, so they ride the
corpse archetype through the death transmute (`copySharedFrom` copies shared
columns). `MechWreckSystem` then detaches `MECH_LOADOUT` when the wreck spawns;
`DroneCrashSystem` detaches `CRASHING` on impact; `RENDER_POSITION` stays for the
life of the corpse. Death-order across the `DeathDispatcher` subscribers is
irrelevant — the components survive the transmute regardless of who runs first.

## Access shape

- **Typed accessors** replace the generic cold face. `world.mechLoadout(id)` /
  `hasMechLoadout(id)` / `attachMechLoadout`; `world.renderX/Y(id)` /
  `setRenderPos(id,…)` (tolerant 0 reads — render must not fail-loud on a released
  ref). Registry adapters back them (`mechLoadoutOf`, `renderXById`, …).
- **The `World` cold face is deleted.** With both optional capabilities folded, the
  generic `component(id, Class)` / `hasComponent` / `id(id).getOrNull` projection +
  the `Map<Class, ComponentStore>` had no callers — gone. ~8 mech behaviors moved
  from `world.component(id, MechLoadoutComponent.class)` to `world.mechLoadout(id)`.
- **Hot passes walk queries:** `HeavyWeapons` over `mechLoadouts`, `DroneCrashSystem`
  / `DroneRenderSystem` over `crashing` — snapshot-then-apply kept for the
  fire/impact mutations.

## Render fold specifics (the big one)

`Entity.getRenderX/getRenderY/setRenderPos` + the `Entity.renderPositions` service
ref are **deleted** — an `Entity` now holds no render state. ~28 caller files swept
to by-id reads (the path-ref-fold-in playbook: `sim.world()` in behaviors,
`registry` in combat/render hot paths; 3 Sonnet agents on disjoint buckets,
compiler backstop). `ShotEndpoint.resolve` took the target's render x/y as floats
(a static helper with no handle). `RenderPositionService` +
`RenderPositionComponent` + `RenderPositionServiceTest` deleted.

## `ComponentStore<T>` survives — for air FX only

`ComponentStore<T>` is **not** deleted: it still backs `ThrusterFx` and
`AirTurrets`, which key the **air-craft** entity space (`Shuttle` +
`AirSystem.nextAirId`), disjoint from the battle `EntityWorld`. Unifying air into
the one world is a separate, captured epic
([`../../air/air-entities-into-world.md`](../../air/air-entities-into-world.md));
`ComponentStore<T>` dies there. No battle-unit component uses it any more.

## Next: part B — dissolve `UnitRegistry`

Relocate id-mint + the dense `Entity[]` + `indexById` + the owned
`EntityWorld`/`BattleComponents` from `UnitRegistry` up to the sim, and delete the
class — the finale.
