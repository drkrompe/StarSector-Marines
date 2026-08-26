# W4 — Retire the enums

> The id is the handle.

Status: IN PROGRESS

Written: 2026-08-22

Updated: 2026-08-26 — handheld primary and special-equipment enum handles are removed across persistence, campaign, battle, rendering, and tests; mech and turret compatibility handles remain.

Read `moddable-weapons-nouns.md` before implementing this story.

## Scope

Delete `MarineWeapon`, `MarineSecondary`, `MechWeapon` and `TurretKind` as
stat carriers. Weapon consumers hold a `WeaponDef` or its id string; a marine's
optional billet item holds the special-equipment id established by
`progression-nouns.md`, which may in turn reference a weapon id.

The load-bearing part is **persistence**. `MarineSoldier.primary` is an
xstream-serialized enum, and `FireTeamBillet` stores weapons inside persisted
armory fire-team templates too, so every existing save carries enum names. The migration is the established
`readResolve` legacy-repair pattern: map historical primary and secondary enum
names to their registry ids, and fail closed onto the starter primary or an
empty special slot when an id no longer resolves — a submod the player
uninstalled must not corrupt a roster.

Also in scope: the identity references that survive. Callers name specific
constants across `InfantryLoadoutRolls` roll tables, `MarineArmory`
unlock ladder, `DebugPersonnelPreset` fixtures, `Drone`'s built-in mount.
Those become id lookups, and the roll tables themselves are a candidate for
data in a later story.

## Acceptance

- No Java type enumerates weapons.
- A save written before this story loads with every marine's primary and
  equipped rocket intact.
- A save referencing an unresolved primary loads with the starter weapon; an
  unresolved special loads with an empty special slot. Both repairs log a
  warning rather than throwing.
