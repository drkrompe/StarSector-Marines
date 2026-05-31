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

## Decomposition into stories

Design-stage; sequence not yet committed.

- **S1 — Kinematic profile + loader.** A `KinematicProfile` record from
  `ShipHullSpecAPI.getEngineSpec()` keyed on hull id, `SCALE` applied at
  construction. Resolve the separate-vs-enriched-record question. Unit-test
  against known hulls (Talon, Trident) to lock the scale factor.
- **S2 — `FighterType` / `AirHandling` adapter.** Adapt `KinematicProfile` into
  an `AirHandling` so an `AirBody` flies a fighter. Layer the atmosphere knobs.
  Mirrors `ShuttleType`'s relationship to `AirHandling`.
- **S3 — Wing composition.** Read `num` / `formation` / `range` / `role` from
  `wing_data.csv` to drive wing size and formation behavior.
- **S4 — Flyby fighters become real air entities** (the backlog item). Rebuild
  `flyby/` fighters on `AirBody`: spawn from map edges, take fire, get shot
  down, optionally land at bases. Fold `flyby/` into `air/`. Blocked on S1–S2.
