# Fighters / drones

> Fighter-class member of the [`air/`](../overview.md) category. The shared
> vanilla-hull → sim-entity pipeline lives in
> [`hull-extraction.md`](../hull-extraction.md); this doc covers what's
> fighter-specific. **Design stage.**

## What this is

Small, fast, swarm-in-wings craft — interceptors, fighters, bombers, drones —
recaptured from vanilla/modded hulls and re-flavored for ground-scale
atmospheric combat. The goal is the role contrast players know: interceptors are
twitchy, bombers are sluggish, drones are cheap and nimble. We borrow the
vanilla *ratios* (see [`vanilla-kinematics-reference.md`](../vanilla-kinematics-reference.md))
and apply the shared `SCALE` + atmosphere knobs.

## The flyby fighters are dead weight

Today's `battle/flyby/` fighters are a **scripted cosmetic overlay** —
heading-based weave + strafing runs, a single sim coupling via
`BattleSimulation#applyExternalDamage`. They do **not** fly on `AirBody` and
carry no kinematic profile. They are ripe for wholesale replacement: this track
rebuilds them as real `AirBody` entities, and when that lands, `flyby/` folds
into `air/` (the package move was deliberately deferred until the data model
exists — see `roadmap/backlog.md` § "Flyby fighters as real air entities").

What's worth keeping from `flyby/` is the **loadout data**, not the movement:
`FighterProfile` holds sprite, weapon class, tracer/burst/projectile tuning,
audio, and the faction→profile pool. That's the cosmetic/armament half; the
kinematic half is new.

## Loadout vs kinematics

`FighterProfile` (loadout) and the new kinematic profile share the `hullId` key.
Two shapes — decide in S1:

- **Separate records** — `FighterProfile` (loadout) + `KinematicProfile`
  (movement), joined on hull id. Clean separation; the eventual `flyby/`→`air/`
  fold doesn't drag the loadout schema into the kinematic model.
- **One enriched record** — fold a kinematic block into `FighterProfile`. Fewer
  types, but couples cosmetic + sim.

Lean toward **separate records**: the kinematic profile is sim-tier, the loadout
is render/flyby-tier.

## Wing composition

`wing_data.csv` (via the runtime spec registry) drives the swarm:

- **num** — fighters per wing (talon 4, broadsword 3, thunder 2, wasp 6…).
- **formation** — `V` / `CLAW` / `BOX` (drones); the arrangement around the
  leader.
- **range** / **attackRunRange** — leash from carrier; how close the wing closes
  on an attack run (bombers run long, interceptors stay in close).
- **role** — `INTERCEPTOR` / `FIGHTER` / `BOMBER` / `SUPPORT`.

## Collision

Fighters are small and numerous; full hull-polygon collision (the approach for
[`ships`](../ships/overview.md)) is overkill. `collisionRadius` (or an OBB) is plenty for
"is the AA hitting the gunship." Revisit only if a specific interaction rewards
sub-fighter precision.

## The shared core now exists

The `air-entity-composition` migration (slices 1–3, shipped) built the substrate
this track was waiting on: `entityId` + `AirBody` (pure kinematic data) +
`AirSteeringSystem` + component stores (`ThrusterFx` plumes, `AirTurrets`) + a
single `releaseAirEntity` death seam, all proven on shuttles. Fighters now
**compose that core** rather than inventing a parallel one — and the
movement-handler rewrite is the concrete next step.

## Decomposition into stories

The active plan lives in
[`../stories/fighter-air-entities.md`](../stories/fighter-air-entities.md) (slice
4 of air-entity-composition — fighters compose the core + rewrite the scripted
movement onto `AirBody`). Its sub-slices:

- **4a — `FighterType` : `AirHandling`** — kinematic tiers (interceptor / fighter
  / bomber / drone) + engine-slot resolution. Folds in the old S1 (kinematic
  profile from vanilla engine ratios, scale-locked by a hull unit-test) and S2
  (the `AirHandling` adapter, mirroring `ShuttleType`).
- **4b — `FighterMission` + system** — real `AirBody` driven by
  `AirSteeringSystem`; the strafe state machine ported off headings onto
  goals/modes. **The movement-handler rewrite.**
- **4c — repoint FX/fire/render to the body; delete the scripted motion.**
- **4d — fold `flyby/` → `air/`** (the backlog item; deferred package move, now
  that it shares `AirBody`).
- **Later — wing composition** (old S3: `wing_data.csv` num/formation/role) and
  **fighters take fire / get shot down** (4e; gated on AA).
