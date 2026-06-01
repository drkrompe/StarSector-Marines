# Story — Fighters as air entities (rewrite the movement handler)

> Slice 4 of [`air-entity-composition`](air-entity-composition.md) and the
> fighter-specific expansion of [`fighters/overview.md`](../fighters/overview.md).
> The shared air-entity core that the old fighters overview *planned for* now
> **exists** (slices 1–3): `entityId` + `AirBody` (pure data) + `AirSteeringSystem`
> + component stores (`ThrusterFx`, `AirTurrets`) + the `releaseAirEntity` death
> seam. This slice makes fighters compose that core, and **rewrites their
> movement** off the scripted cosmetic handler onto real kinematics.

## The problem — `flyby/` fighters fake their motion

`FlybyOverlay` flies fighters on a hand-scripted heading state machine
(`tickFighter` → `pickTargetHeading` / `tickCruise` / `tickBankBack` / `tickRun`
over a private `Fighter` struct):

```
f.headingDeg  += clamp(targetHeading - heading, ±TURN_RATE·dt)   // steer
f.worldX/Y    += cos/sin(headingDeg) · f.speed · dt              // constant-speed glide
```

No acceleration, no momentum, no lateral drift — a fighter pivots in place at a
fixed turn rate and slides at a fixed speed, with a sinusoid "weave" painted on
to fake life. There's no kinematic difference between a twitchy interceptor and a
sluggish bomber; the role contrast the fighters overview wants can't emerge from
this. It also doesn't fly on `AirBody`, so none of the slices-1–3 work (engine
plumes, the death seam, the shared steering feel) reaches it.

## The fix — compose the core, drive with `AirSteeringSystem`

A fighter becomes an air entity, exactly like a shuttle:

- **`entityId`** — minted by the air registry (`AirSystem.add`-equivalent), so it
  keys the shared component stores and releases through `releaseAirEntity`.
- **`AirBody`** — the kinematic data; driven each tick by
  `AirSteeringSystem.steer(body, goalX, goalY, mode, type, dt)`. The boat-feel
  (interceptor carves, bomber pendulums) **falls out of the handling profile** —
  the same kinematic-limited steering that gives shuttles their arc, no scripted
  weave needed.
- **`FighterType` implements `AirHandling`** — mirrors `ShuttleType`. Per-tier
  tunables (maxSpeed / accel / brakingAccel / maxTurnRate / lateral+station
  damping) for INTERCEPTOR / FIGHTER / BOMBER / DRONE. Seed from vanilla engine
  ratios ([[vanilla_ship_spec_scraping]],
  [`vanilla-kinematics-reference.md`](../vanilla-kinematics-reference.md)) + the
  atmosphere knobs; hand-tuned tiers are a fine first cut ([[ship-then-optimize]]).
- **`ThrusterFx`** — engine plumes for free: the slot resolver keys on the
  fighter's `renderHullId`, the smoothing system already advances any air entity.
- **`FighterMission`** — the strafing-run state machine, ported off headings onto
  *goals*. States map to a (waypoint, `SteeringMode`):
  - INBOUND/CRUISE → fly the entry→exit line at `CRUISE` (the weave becomes
    emergent drift, or a small facing-only wobble if we want extra life).
  - ATTACK_RUN: BANK_BACK → steer to the run-in waypoint; RUN → steer to the
    run-out waypoint, spraying tracers. The cluster scan + waypoint planning
    (`tryPlanStrafingRun`) is preserved — only the *execution* swaps from
    heading-lerp to `AirSteeringSystem`.
  - EXIT → off-map waypoint at `CRUISE`; on arrival → GONE → `releaseAirEntity`.
- **`FighterProfile` is kept** — sprite, weapon class, tracer/burst/projectile
  tuning, audio, faction pool. It's the loadout/FX half; the new kinematic half
  is `FighterType`. Joined on `hullId` (separate records, per the overview's S1
  call).

## What stays vs what dies

**Dies (the movement handler):** `FlybyOverlay.tickFighter`, `pickTargetHeading`,
the heading-lerp + constant-speed integration, the `Fighter` struct's
`headingDeg/speed/vx/vy/weave*` motion fields, `TURN_RATE_DEG_PER_SEC`. Replaced
by `AirBody` + `AirSteeringSystem` + `FighterType`.

**Stays (cosmetic + the one sim coupling), repointed to read `body`:** weapon
fire (`fireBurstShot`/`fireRunShot`), tracers, projectiles, muzzle/impact/AoE FX,
audio, shadow + sprite + engine-glow draw, and the lone sim coupling
`BattleSimulation#applyExternalDamage`. These read `body.x/y/facingDegrees`
instead of the struct's `worldX/headingDeg`.

## Where it runs

`AirSystem`'s charter is "owns every airborne vehicle." Fighters join its roster
and id space (shared `ThrusterFx` store + `releaseAirEntity`). To keep `AirSystem`
from bloating, the fighter behavior is its own `FighterMissionSystem` (the
goal-provider that feeds `AirSteeringSystem`), called from `AirSystem.tick`
alongside `advanceShuttles` — same shape as the shuttle state machine, different
mission. Rendering moves to a `FighterRenderSystem` (mirrors `ShuttleRenderSystem`)
when `flyby/` folds into `air/`; until then `FlybyOverlay`'s draw is reused.

## Decomposition (committable sub-slices)

- **4a — `FighterType` : `AirHandling`.** The kinematic tiers + engine-slot
  resolution (renderHullId). Pure; unit-test the tiers against known hulls
  (Talon/Trident) to lock the scale. No behavior change yet.
- **4b — `FighterMission` + `FighterMissionSystem`.** Real `AirBody` per fighter,
  driven by `AirSteeringSystem`; port the strafe state machine to goals/modes.
  Fighters now move on real kinematics. **This is the movement-handler rewrite.**
- **4c — Repoint FX + fire + render to `body`; delete the scripted motion.** The
  `Fighter` struct loses its kinematic fields (gains an `AirBody` + `entityId` +
  `FighterMission`, or is replaced by a `Fighter` air-entity like `Shuttle`).
- **4d — Fold `flyby/` → `air/`.** git-mv the kept classes (`FighterProfile`,
  `WeaponClass`, roster), add `FighterRenderSystem`, retire the overlay shell,
  update package-info charters. The deferred package move, now that it shares
  `AirBody`.
- **(Future) 4e — Fighters take fire / get shot down.** Wire `hp` + the AA path
  through the same death seam; land at bases. Gated on the AA work.

## Out of scope

- Wing composition from `wing_data.csv` (num/formation/role) — the overview's S3;
  layer on once single fighters fly on `AirBody`. Note as a follow-up, don't
  silently drop.
- Full hull-polygon collision — `collisionRadius` is plenty for fighters
  ([`fighters/overview.md`](../fighters/overview.md) § Collision).
- Carrier/launch sourcing — fighters still spawn from map edges (`spawnFromWing`).

## Done when

- A fighter flies on `AirBody` + `AirSteeringSystem` under a `FighterType`
  handling profile; interceptor-vs-bomber feel is visibly different and emergent,
  not scripted.
- The scripted heading movement handler is gone; weapon/tracer/FX read the body.
- Fighters carry an `entityId`, get engine plumes from `ThrusterFx`, and release
  through `releaseAirEntity` on exit.
- Each sub-slice compiles + its tests pass; sub-slices land as separate commits.

## Cross-references

- [`air-entity-composition.md`](air-entity-composition.md) — slices 1–3 built the
  core this composes; slice 4 is this story.
- [`fighters/overview.md`](../fighters/overview.md) — the fighter feature design
  (loadout vs kinematics, wing composition, collision).
- `roadmap/backlog.md` § "Flyby fighters as real air entities" — closed by 4b–4d.
- Memory: [[air_vehicle_kinematics]], [[air_unit_render_sync]],
  [[vanilla_ship_spec_scraping]].
