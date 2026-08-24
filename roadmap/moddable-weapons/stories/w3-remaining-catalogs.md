# W3 — Remaining catalogs

> The weapons that are only weapons.

Status: IN PROGRESS — marine-secondary schema and rocket migration shipped with
progression S2A; five mech weapons and mech mount validation remain.

Written: 2026-08-22

Updated: 2026-08-23 — marine-secondary half landed; remaining scope is the five mech weapons.

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
shipped anti-materiel rifle described in `progression-nouns.md` references this schema.
The frag grenade in `s2d-frag-grenades.md` may do the same for an arcing
explosive definition. Smoke and satchel activations remain owned by their
progression stories.

## Out of scope

- **`TurretKind`.** The shipped platform → mount → weapon split is defined in
  `moddable-weapons-nouns.md`; this story does not reopen that catalog or its
  id-only compatibility handle.
- `MechVariant` chassis stats and `MechWeaponComponent` mount geometry.
  Those describe the platform and the hardpoint; they stay where they are.
- A generic special-equipment schema or utility activation. W3 moves weapons,
  not smoke fields or placement channels.

## Acceptance

- Parity test extended to marine secondaries and mech mounts.
- Mech and rocket behavior unchanged in the full suite, and the TTK report
  unchanged inside its standard error.
