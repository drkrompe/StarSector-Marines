# W3 — Remaining catalogs

> The weapons that are only weapons.

Status: PLANNED

Written: 2026-08-22

Updated: 2026-08-23 — scoped the portable migration to the current weapon-like rocket, not future utility specials.

Read `moddable-weapons-nouns.md` before implementing this story.

## Scope

Bring the current rocket carried by `MarineSecondary` (1) and `MechWeapon` (5) onto the `WeaponDef`
schema, adding the fields marine primaries do not use:

- `aoeRadius`, `wallDamage`, `wallDamageRadius` — splash and structural bite.
- `arcHeight`, `flightSec`, `minRange` — the lobbed family.
- `aimDuration` — the marine rocket's aim window.

Add mount-class validation now that there is more than one class to get
wrong: reject a marine primary declaring `aoeRadius`, a mech mount declaring
`aimDuration`, and so on.

These two catalogs are the easy half, because **their platform/weapon split
already exists.** `MechWeaponComponent` is the mount layer — it owns rack
size, ammo capacity, appearance shell and which slot family it fits, while
`MechWeapon` owns projectile behavior. That is exactly the boundary
`WeaponDef` draws, so mech mounts migrate by pointing `MechWeaponComponent`
at a weapon id and changing nothing else.

This story migrates the one shipped weapon-like secondary; it does not declare
that every future item in the same player-facing slot is a weapon. The
anti-materiel rifle in `s2a-anti-materiel-rifle.md` may reference this schema.
Smoke and satchel activations remain owned by their progression stories.

## Out of scope

- **`TurretKind`.** It was scoped here and has been moved to
  `w6-emplacements-and-structures.md`. It is not one catalog entry with an
  awkward field — it is a platform, a mount and a gun fused into one enum,
  already mounted on three different platforms that disagree about which of
  its fields mean anything. Migrating it as "just another weapon" would bake
  that fusion into the schema.
- `MechVariant` chassis stats and `MechWeaponComponent` mount geometry.
  Those describe the platform and the hardpoint; they stay where they are.
- A generic special-equipment schema or utility activation. W3 moves weapons,
  not smoke fields or placement channels.

## Acceptance

- Parity test extended to marine secondaries and mech mounts.
- Mech and rocket behavior unchanged in the full suite, and the TTK report
  unchanged inside its standard error.
