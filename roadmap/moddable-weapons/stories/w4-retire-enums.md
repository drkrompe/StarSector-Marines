# W4 — Retire the enums

> The id is the handle.

**Status:** not started. Depends on `w3-remaining-catalogs.md`.

## Scope

Delete `MarineWeapon`, `MarineSecondary`, `MechWeapon` and `TurretKind` as
stat carriers. Consumers hold a `WeaponDef` or its id string.

The load-bearing part is **persistence**. `MarineSoldier.primary` is an
xstream-serialized enum, and `SquadEquipmentPreset` stores weapons too, so
every existing save carries enum names. The migration is the established
`readResolve` legacy-repair pattern: map the historical enum name to its
registry id, and fail closed onto the starter weapon when an id no longer
resolves — a submod the player uninstalled must not corrupt a roster.

Also in scope: the identity references that survive. Roughly 69 sites name
a specific constant — `InfantryLoadoutRolls` roll tables, `MarineArmory`
unlock ladder, `DebugPersonnelPreset` fixtures, `Drone`'s built-in mount.
Those become id lookups, and the roll tables themselves are a candidate for
data in a later story.

## Acceptance

- No Java type enumerates weapons.
- A save written before this story loads with every marine's weapon intact.
- A save referencing an id that no longer resolves loads with that marine
  on the starter weapon and a logged warning, not an exception.
