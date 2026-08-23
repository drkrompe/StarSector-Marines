# W3 — Remaining catalogs

> One schema, four mount classes.

**Status:** not started. Depends on `w1-weapon-registry.md`.

## Scope

Bring `MarineSecondary` (1), `MechWeapon` (5) and `TurretKind` (8) onto the
`WeaponDef` schema, adding the fields marine primaries do not use:

- `aoeRadius`, `wallDamage`, `wallDamageRadius` — splash and structural bite.
- `arcHeight`, `flightSec`, `minRange`, `indirectFire`, `noLosAccuracyMult`
  — the lobbed / artillery family.
- `startingAmmo`, `aimDuration` — secondaries and shuttle mounts.
- `turnRateDegPerSec`, `maxHp`, `recoilSprite`, `role` — emplacement-only
  properties. `maxHp` on a *weapon* is a `TurretKind` wart worth questioning
  here rather than copying: an emplacement's health belongs to the
  emplacement, not to the gun bolted onto it.

Add mount-class validation now that there is more than one class to get
wrong: reject a marine primary declaring `turnRateDegPerSec`, an
emplacement declaring `startingAmmo`, and so on.

## Out of scope

- `MechVariant` chassis stats and `MechWeaponComponent` mount geometry.
  Those describe the platform, not the weapon.

## Acceptance

- Parity test extended to all 19 weapons.
- Turret and mech behavior unchanged in the full suite, and the TTK report
  unchanged inside its standard error.
