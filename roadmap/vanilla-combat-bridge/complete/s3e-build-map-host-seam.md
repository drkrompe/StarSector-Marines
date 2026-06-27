# S3e — "Build a BattleSimulation, then choose a host"

> ⚠️ **Superseded detail (2026-06):** this doc records S3e setting the bridge to
> `AirProvider.EXTERNAL`. The later live-battle slice + the S3d drop-ship invasion **reverted the
> bridge to `INTERNAL`** — the sim owns its own shuttles (dropships spawn via `addShuttle`), and the
> vanilla carriers' air-to-ground is additive. `EXTERNAL` + `deliverSquad` remain valid for a future
> direct-injection bridge but are *not* the current path. Anything below that says "bridge → EXTERNAL"
> is the original S3e plan, not today's reality.

> **SHIPPED** — `d152441` (AirProvider) → `e171d00` (buildMap) → `03dd62f` (bridge). Test
> suite green throughout. The standalone factories and the combat-bridge host now build
> their sim through one shared `BattleSetup.buildMap`, and air ownership is an explicit
> `AirProvider` capability on the sim. Landed as planned; the external marine-delivery
> entry point (`deliverSquad`) is correctly left to S3d (only the EXTERNAL strafe path,
> `applyExternalDamage`, exists today).

> Enabling refactor under the S3 phase. Splits ground-battle setup into a **host-agnostic
> map build** (shared) and **host-chosen provisioning** (scenario + air), so the standalone
> `BattleScreen` and the combat-bridge overlay stand up the *same* sim through one code path.
> Prerequisite for S3d (the shuttle handoff) and the long-term convergence of the two entrypoints.

## The shape

Every `BattleSetup.createX` factory and the bridge's `setupSimCoupled` decompose into three layers:

1. **Map** — generate → stamp vehicles → resolve defense posts → `new BattleSimulation` →
   `setTacticalMap`/`setBuildings`/`setDefensePosts` → add vehicles/doodads →
   `spawnDefensePostTurrets`. *Pure planet geometry + structures. Identical across all
   factories and the bridge.*
2. **Scenario** — objectives, defenders, civilians, commander, reinforcement. *Live-battle
   population. Standalone-only today; the bridge skips it (it's the battle, not the map).*
3. **Air** — marine shuttles (`addShuttle` + deboard state machine) and flyby (`setFlybyRoster`).
   *Who owns the air is host-selectable.*

Target call shape:

```
MapResult map = generate(...)                 // host-agnostic
BattleSimulation sim = BattleSetup.buildMap(map, rng)   // layer 1; returns sim + structure units
   ↓ choose host + provisioning
A standalone → populate scenario (defenders/objectives/commander/reinforcement)
              + internal air: sim stays AirProvider.INTERNAL; addShuttle / setFlybyRoster
B bridge     → sim.setAirProvider(EXTERNAL); wire proxies to the structure units;
              air comes from real vanilla ships above (strafe = applyExternalDamage today,
              marine delivery = S3d deliverSquad)
```

## Air ownership — `AirProvider` (decided: explicit, on the sim)

`enum AirProvider { INTERNAL, EXTERNAL }` on `BattleSimulation`, default `INTERNAL`.

- **`INTERNAL`** (standalone): `airSystem.tick()` runs; `addShuttle`/`attachAirTurrets`/
  `setFlybyRoster` install internal air as today.
- **`EXTERNAL`** (bridge): `airSystem.tick()` is skipped — the host's real vanilla ships own
  the air. `addShuttle`/`attachAirTurrets`/`setFlybyRoster` **fail loud** (contract violation:
  you declared the host owns the air, don't also install internal air). External strafe already
  works via `applyExternalDamage`. The inverse — external marine delivery — is a future
  `deliverSquad(cellX, cellY, MarineLoadout[])` that asserts `EXTERNAL`; **built in S3d, not here.**

Why an explicit flag rather than "by omission": it declares the invariant (who owns the air) at
the seam, makes the bridge's "no internal shuttles" intentional, and the EXTERNAL guards bite
immediately if violated. It keeps the sim "just the tick loop" — the flag gates one tick call,
it doesn't sprinkle branches through the sim.

## Implementation gotchas

- **Turret-after-vehicle ordering.** `buildMap` must keep `stampVehicles` before
  `spawnDefensePostTurrets` — the turret cover-bake (`recomputeCoverAt`) reads vehicle-occupied
  neighbors. Reordering silently changes the cover values the standalone path bakes.
- **Conquest vs non-conquest defense posts.** Conquest posts come pre-stamped by the generator
  (`map.defensePosts`); non-conquest posts are stamped in setup (`DefensePostStamper.stampNonConquest`).
  Both flip grid walkability *before* sim construction (the zone graph reads final walkability).
  So `buildMap` consumes an already-resolved post list + vehicle list; the caller stamps them
  pre-construction and hands them in.

## Slices

- **S3e-1 — `AirProvider` on the sim.** Enum + setter/getter, default INTERNAL, EXTERNAL gate on
  `airSystem.tick()` + fail-loud guards. No caller changes yet (all stay INTERNAL).
- **S3e-2 — `buildMap` extraction.** Pull layer 1 into one factory; the three `createX` methods
  call it. Returns the spawned structure units. Pure dedup; test suite stays green.
- **S3e-3 — bridge through `buildMap`.** `setupSimCoupled` calls `buildMap` + `setAirProvider(EXTERNAL)`;
  drops its inline map setup; mirrors the returned structure units as proxies.

## Acceptance

Both entrypoints build their sim through `buildMap`; standalone unchanged (INTERNAL, full scene);
bridge declares EXTERNAL and the air-install guards are live. Test suite green. S3d's `deliverSquad`
has a clear home (EXTERNAL-asserting entry point).
