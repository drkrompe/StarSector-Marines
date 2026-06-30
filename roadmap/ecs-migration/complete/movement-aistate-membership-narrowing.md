# MOVEMENT + AI_STATE membership-narrowing — static emplacements carry neither

**Shipped 2026-06-25** — `91380de4`. Suite green at 772.

Closes the membership-narrowing follow-up left open by steps 3e (Movement) and
3f (AiState), which shipped both components **universal** (every live unit) for
behavior-preservation. Now `MOVEMENT` and `AI_STATE` are added at spawn only for
the units that actually use them: presence IS the capability (the
`SECONDARY_WEAPON` 3d pattern), so "has MOVEMENT" defines a mover and "has
AI_STATE" a thinker.

## Membership

Excludes exactly the two static structure types — `UnitType.TURRET` and
`UnitType.DRONE_HUB_STRUCTURE` (new predicate `UnitType.isStatic()`). Every other
type keeps both: marines / militia / aliens / mechs (move + decide), wandering
civilians (CIVILIAN / ENGINEER / SCIENTIST — flee + wander via `AI_STATE`), and
patrolling drones. The two excluded types are combatants (targetable, damageable)
but immobile and mindless — a turret tracks + fires via `TurretBehavior`, a hub
ticks a spawn cadence via `DroneHubBehavior`; neither paths nor runs the
reposition / fall-back / wander cadence.

`MOVEMENT` and `AI_STATE` happen to share one excluded set today, so one
predicate gates both. The doc note flags the split (a future mobile-but-mindless
or static-but-thinking type) as a one-line change.

## What landed

- **`UnitType.isStatic()`** — `this == TURRET || this == DRONE_HUB_STRUCTURE`.
- **`UnitRegistry.allocate`** builds the live archetype conditionally into a
  `ComponentType[]`: always `{IDENTITY, POSITION, HEALTH, COMBAT}`, plus
  `MOVEMENT` + `AI_STATE` iff `mobile`, plus `SECONDARY_WEAPON` iff armed. The two
  non-zero seeds (`EMPTY_PATH` path, `-1/-1` fall-back cell) are gated behind
  `mobile` — a static unit has no row to seed.
- **Presence API** — `UnitRegistry.hasMovement/hasAiState` +
  `World.hasMovement/hasAiState` (one `entityWorld.has` mask test each), mirroring
  `hasSecondaryWeapon`. The adapter-block + facade docs flip from "universal, no
  presence gate" to "optional, gate first."

## The reader audit (why only four sites needed gating)

A full sweep of every `MOVEMENT`/`AI_STATE` production reader split them cleanly:

- **Per-unit behavior code** (infantry postures + zone actions, mech behaviors,
  flee / fall-back / break-contact, GOAP actions, `InfantryUnitPrep`) only ever
  runs for a dispatched mover or a squad `member`. Turrets / hubs route to
  `TurretBehavior` / `DroneHubBehavior` and are `NO_SQUAD`, so they never reach
  these — **no gate needed**.
- **Squad-member readers** (`SquadAlertSystem`, `WorldStateBuilder`,
  `SquadStateDumper`) filter on `squadId` before the read; `NO_SQUAD` turrets /
  hubs are skipped — **no gate needed**.
- **Render facing** (`UnitRenderService.computeFacing`/`computeEightWayFacing`,
  read `path`) is reached only from the SHEET-sprite pass, which excludes
  turrets / hubs / drones (whole-sprite renderers) — **no gate needed**.
- **`InfantryWeapons` burst pass** reads `moveProgress` only for units with
  `burstRemaining > 0`; turrets never call `beginBurst` (documented on
  `MapTurret`), hubs don't fire — **no gate needed**.
- **Four genuinely cross-cutting all-unit readers** DID need a gate:
  - `NavigationService.rebuildOccupancyMap` — gated on `hasMovement` after the
    current-cell occupancy bump (a static unit still occupies its cell; it just
    has no path destination to reserve). Behavior-identical: the old `EMPTY_PATH`
    yielded `destX == MIN_VALUE`, already skipped.
  - `UnitDestinationSpatialIndex.rebuild` — same `hasMovement` skip before the
    path read (an empty path was filtered by the `cells <= 0` check anyway).
  - `UnitUpdateSystem.updateUnit` — the per-tick fall-back override
    `hasAiState && fallbackTimer > 0` short-circuits before the fail-loud read;
    static roles route straight to their behavior.
  - `HitResponseSystem.rollFallbackOnHit` — gates on `hasAiState`, **replacing**
    the `instanceof MapTurret` check (kept only in `rollReprioritizeOnHit`).

## Behavior change (intended)

A damaged **drone hub** could previously roll an on-hit fall-back — the old gate
only excluded `MapTurret`, not hubs, and a hub is `NO_SQUAD` so it passed the
squad check. With no `AI_STATE` it now can't, which is correct: a static
structure has no fall-back behavior to run (the override would have dispatched it
to `FallbackBehavior`, trying to path an immobile hub). Turret behavior is
unchanged (it was already gated). This is the one observable difference; it's a
fix, not a regression.

## Corpse path

`DeadBodySystem.corpseRemove` already lists `MOVEMENT` + `AI_STATE` (from 3e/3f)
and `transmute` treats a remove of an absent component as a no-op — so a static
emplacement that dies + transmutes needs no change.

## Tests

- `UnitRegistryTest.staticEmplacementsGetNoMovementOrAiStateComponents` —
  membership parity: a mobile unit has both components (+ the `EMPTY_PATH` / `-1`
  seeds), a turret + hub have neither, and the field accessors are fail-loud on
  the component-less units.
- `StaticEmplacementMembershipTest` (new) — the runtime safety net: a turret +
  hub present through a full `BattleSimulation.advance` loop (drives the
  occupancy-map + destination-index rebuilds and the per-unit dispatch) and
  through repeated `rollFallbackOnHit`, asserting no fail-loud and that the
  emplacements never gain the mover/thinker components.

## Follow-ups

- **Step 4 — dissolve `UnitRegistry`** (the finale): fold the standalone
  `Crashing`/`MechLoadout` component stores into archetype membership, then hop
  id-mint + the dense `Entity[]` to the world / sim.
