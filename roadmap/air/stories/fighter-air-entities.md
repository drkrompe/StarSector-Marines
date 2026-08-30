# Story — Fold fighters into the air entity model

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-30 — the overlay class and the profile's dead tuning are gone;
only the package rename remains.

Read `air-nouns.md` before changing this story, in particular **Air cover, and
where a sortie is from**.

## What shipped

`FlybyOverlay` was 1,583 lines and is gone. Its flight
integration, map-edge entry, cycling re-entry, cluster scan, bank-back/run state
machine, tracer and missile fire, dogfight aggro, fighter vision push, GL
renderer and particle pool were all deleted as duplicates of `battle.air`, which
owns every one of them properly.

`FighterProfile` is a roster again: a hull, a size, structure to shoot at, a
mount count and an identity colour. The tracer, burst and projectile tuning
blocks were the overlay's own fire resolution and went with it; `AirOrdnance`
presets carry the equivalent facts. The six weapon-sound constants and their
`sounds.json` entries went too — the gun-run cues are keyed on the delivery
(`OrdnanceFx`), so nothing had played one since the overlay was cut. `WeaponClass`
went with them: `ordnance()` names a preset per hull, so the tag selected
nothing.

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

**Rename the package.** `battle.flyby` holds `FighterProfile`, `FighterWing`,
`FlybyRoster`, `PlayerFleetWings` and `DebugAirRoster` — the fighter roster, and
nothing that flies. It is the last thing in the codebase calling a fighter a
"flyby". `FlybyRoster` and `FighterWing` want to move under `air/` and be named
for what they are; that touches roughly thirty files (fixtures, ops, detachment,
briefing UI) and is a mechanical rename best done when no sibling session is
mid-flight in the air package.

**A profile's identity colour is not wired to anything.** `tracerColor` is kept
as the one authored per-fighter visual fact worth having, but the gun-run
presentation keys on the delivery rather than on the carrier, so no effect reads
it. Either give a run some per-hull tint that does not undo that keying, or drop
the field.

## Out of scope

- Wing composition from `wing_data.csv`, formation, and air-to-air.
- Recallable air cover, or any live player control over a committed wing.
  Commitment is the control; see `command-powers-nouns.md`.
- Dense storage optimization; it follows measured fighter-swarm pressure.
