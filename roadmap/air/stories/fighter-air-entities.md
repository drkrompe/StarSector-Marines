# Story — Fold fighters into the air entity model

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-30 — the overlay is deleted and wings fly as air entities; what
remains is the `FighterProfile` clean-up the fold could not reach.

Read `air-nouns.md` before changing this story, in particular **Air cover, and
where a sortie is from**.

## What shipped

`FlybyOverlay` was 1,583 lines and is now six sound-id constants. Its flight
integration, map-edge entry, cycling re-entry, cluster scan, bank-back/run state
machine, tracer and missile fire, dogfight aggro, fighter vision push, GL
renderer and particle pool were all deleted as duplicates of `battle.air`, which
owns every one of them properly.

Committed fighter wings now fly as real air entities. `AirCorridor` is the
explicit off-map origin — a named source and two points that are outside the map
by construction — and `AirCoverSystem` reads the same `FlybyRoster` the overlay
read and dispatches each wing's schedule as an off-map strike sortie:
`AirBody` motion, `AirOrdnance` released through the detonation pipeline (so
splash, cover, armour, wall damage and roof interception all apply), AA
vulnerability, shoot-down with crash FX, fog contribution, sprite, and engine
audio — all for free, because those are properties of being an air entity.
`EnemyConcentration` is the shared target choice; `AirStrikeSystem` and
`AirCoverSystem` ask it the same question from their two origins.

The wall-collapse dust drain the overlay happened to own moved to
`ImpactFx.spawnWallCollapse`.

## What remains

**The `FighterProfile` clean-up.** It could not be touched during the fold
(another session owned it). Three things are waiting on it:

1. Move `SFX_GUN_HEAVY`, `SFX_GUN_LIGHT`, `SFX_GUN_ENERGY`, `SFX_IMPACT`,
   `SFX_MISSILE_LAUNCH`, `SFX_MISSILE_IMPACT` onto `FighterProfile` (or a small
   `FighterAudio`), then **delete `FlybyOverlay`**. Those six constants are the
   only reason the class still exists.
2. Delete the dead tuning blocks. `tracerPxLen`, `tracerPxThick`,
   `tracerLifetime`, `burstSize`, `burstInterval`, `burstSpreadDeg`,
   `perTracerDamage`, `wallDamage`, `runFireInterval`, `projectileSpeed`,
   `projectileTurnRateDegPerSec`, `projectileFuseSec`, `projectileAoeRadiusCells`,
   `projectileAoeDamage` and `projectileSpritePath` were read only by the
   overlay's own fire resolution. `AirOrdnance` presets carry all of it now, and
   `WeaponClass` is what picks the preset. `tracerColor` is still worth keeping
   as the profile's identity colour if the gun-run FX wants one.
3. Rename the package. `battle.flyby` holds `FighterProfile`, `FighterWing`,
   `FlybyRoster`, `PlayerFleetWings`, `DebugAirRoster` and `WeaponClass` — the
   fighter roster, and nothing that flies. It is the last thing in the codebase
   calling a fighter a "flyby". `FlybyRoster` and `FighterWing` want to move
   under `air/` and be named for what they are; that touches roughly thirty
   files (fixtures, ops, detachment, briefing UI) and is a mechanical rename
   best done when no sibling session is mid-flight in the air package.

**Presentation the fold traded away.** The overlay drew tracers, muzzle flashes
and a missile body for its fighter fire. Air-model gun runs deliver through
`releaseOrdnance`, so those visuals belong to the gun-run FX work on that seam
rather than to a second renderer. Until that lands, a fighter pass is a sprite
crossing the map with detonations under it.

## Out of scope

- Wing composition from `wing_data.csv`, formation, and air-to-air.
- Recallable air cover, or any live player control over a committed wing.
  Commitment is the control; see `command-powers-nouns.md`.
- Dense storage optimization; it follows measured fighter-swarm pressure.
