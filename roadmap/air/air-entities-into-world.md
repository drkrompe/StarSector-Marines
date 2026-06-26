# Air entities into the EntityWorld — one world, not two

> **Design decision (2026-06-25): there should not be two separate entity
> worlds.** Air craft and battle units belong in the *same* archetype
> `EntityWorld`; "air vs ground" is a difference in *components*, not a reason
> for a separate id space + storage. This doc captures that decision and the
> epic that realizes it. **Gated on `ecs-migration` step 4** (the sim must own
> the one world before air can be adopted into it).

## The decision

Today the battle tier has **two** entity spaces:

- **Battle units** — `Entity` (+ `Drone`/`MapTurret`/`DroneHubUnit`/…), id-minted
  by `UnitRegistry`, stored in the archetype `EntityWorld` (after the
  `ecs-migration`). Grid-positioned (`POSITION` = `int cellX/cellY`), with
  `HEALTH`/`COMBAT`/`MOVEMENT`/`AI_STATE`/etc.
- **Air craft** — `Shuttle` (non-`Entity`), id-minted by a *private*
  `AirSystem.nextAirId`, stored in `AirSystem.List<Shuttle>`, with its own
  components (`ThrusterFx`, `AirTurrets`) in standalone `ComponentStore`s keyed by
  the air id. Continuous-positioned (`AirBody` = `float x/y` + facing + velocity).

This split is an **incremental-development artifact, not a principled boundary** —
three facts say so:

1. **Drones already straddle.** `Drone extends Entity` (it lives in the battle
   world, is targetable, has `HEALTH`/`COMBAT`) *and* carries an `AirBody`
   (continuous, free-rotating). The grid cell is *synced from the body each
   tick* ([[air_unit_render_sync]]) for targeting/vision. So the battle world
   **already hosts a flier** — the grid/air line isn't the entity boundary.
2. **The real seam is the position model, not entity-ness.** Battle `POSITION`
   is discrete `int` cells; air is continuous `float` `AirBody`. And `AirBody`
   is a **POJO field**, not an ECS component, on both `Drone` and `Shuttle`.
   That — *how is position represented, and is kinematics a component yet* — is
   the actual difference.
3. **The code already states the intent.** `Shuttle`'s own doc: *"The first step
   of the air tier's air-entity-composition migration — air craft as real
   entities,"* and the separateness is rationalized only as *"shuttles fly in
   fractional world space … and don't pathfind or fight on the grid."* That's a
   *component* distinction (no `GRID_POSITION`, no combat/squad components), not a
   separate-world one.

In the ECS end-state (the Artemis model the migration follows), a shuttle is just
**an entity with `{Identity, Kinematics, Sprite, ShuttleMission, AirTurrets?,
ThrusterFx?}`** and *no* grid/combat components. One world; the component set is
the type. `nextAirId` and the parallel `ComponentStore`s go away.

## Why it's gated on ecs-migration step 4

Air-unification needs a **single, sim-owned `EntityWorld`** to adopt air entities
into. Until `ecs-migration` step 4 dissolves `UnitRegistry` (hoisting world
ownership from the registry up to the sim), there is no neutral one-world home —
the world is owned by `UnitRegistry` "for the transition." So **step 4 is the
enabling step**, and this epic is its natural successor, not a competitor.

(The air `ComponentStore`s — `ThrusterFx`, `AirTurrets` — are therefore the one
legitimate reason `ComponentStore<T>` survives the end of `ecs-migration`: they
store *air* components, and air isn't in the world yet. They die in this epic.)

## Epic shape (the work, roughly)

1. **Position-model components.** Decide the representation: a continuous
   `KINEMATICS`/`AIR_BODY` component (float pos + facing + velocity) distinct from
   the grid `POSITION` (int cell). Grid-dwellers have `POSITION`; fliers have
   `KINEMATICS`; a drone has **both** (its cell derived from the body, as today).
   `AirBody` stops being a POJO field and becomes the component payload (or is
   decomposed into float columns).
2. **Adopt air craft as world entities.** `Shuttle` (and planned fighters) get a
   real world `entityId` via `createEntity`; drop `AirSystem.nextAirId` and the
   disjoint namespace. The `List<Shuttle>` iteration becomes a world `Query`
   (`{KINEMATICS, ShuttleMission}` or similar). Shuttle's POJO fields that are
   really per-entity state migrate to components as motivated (the
   `secondary`/`mech` precedent).
3. **Re-key the air FX onto world components.** `ThrusterFx` and `AirTurrets`
   become world components (OBJECT columns, like `CRASHING`/`MECH_LOADOUT` got),
   keyed by the unified id. `ThrusterFxSystem` walks a query. The two air
   `ComponentStore`s — and then `ComponentStore<T>` itself — are deleted.
4. **Reconcile the shared systems.** Render, death, and FX can treat air + ground
   uniformly where it makes sense (one `Sprite`/`RenderPosition` story); the
   grid-only systems (pathfinding, occupancy, squad/GOAP) simply don't match the
   air archetypes (no `GRID_POSITION`/`MOVEMENT`/`AI_STATE`), so they skip air for
   free — the membership-narrowing pattern already established.

This composes with the `air/` track's **hull-extraction** work
([`hull-extraction.md`](hull-extraction.md)): that decides the *kinematics +
geometry data model* (vanilla hull → `AirBody`); this decides *where the entity
lives* (the one `EntityWorld`) and *how its capabilities are stored* (components,
not a side `ComponentStore`). They meet at "air craft as real entities."

## Cross-references

- [`overview.md`](overview.md) — the air track; this is its storage/entity-world
  dimension.
- `roadmap/ecs-migration/` — the foundation (the archetype `EntityWorld`, the
  capability-as-component pattern, the membership-narrowing). Step 4 (dissolve
  `UnitRegistry`) is the prerequisite.
- [`hull-extraction.md`](hull-extraction.md) — the kinematics/geometry data model
  that the adopted air entities carry.
- Memory: [[air_vehicle_kinematics]], [[air_unit_render_sync]],
  [[user_artemis_ecs_framing]], [[feedback_components_by_capability_not_store]].
