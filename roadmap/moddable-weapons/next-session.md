# Moddable weapons — next session

## State of play

**W1 is shipped.** Marine primaries live in
`mod/data/marines/marine-weapons.weapon.json` and are served by
`WeaponRegistry`; `MarineWeapon` is an id handle whose accessors delegate to
the registry. Nothing else changed — `WeaponRegistryParityTest` pins every
value field-for-field, the full suite passes with no expectation edited, and
the TTK report is unchanged inside its standard error.

The other three catalogs — `MarineSecondary`, `MechWeapon`, `TurretKind` —
are still enums with `public final` fields.

## Recommended pickup

**W2 — layered effect definitions** (`w2-layered-fx.md`). It is the half of
this track that the user actually asked for: not just "stats in JSON", but a
weapon composing its own effects, with tracer particles independent of
impact particles.

The groundwork is better than it looks. `ImpactFx`'s four recipes are
already compositions of a handful of primitives that each fill the same
generic `Particle` struct — `sparkFlash(radius, lifetime, color)`,
`dust(radius, lifetime)`, `smokePuff`, `fireBurst`, an explosion frame, an
expanding ring. A layer list maps onto those almost one-to-one, so this is a
re-expression rather than a rendering rewrite.

Two things to carry in:

- **Parity is testable here too, and should be.** Drive the old recipe and
  the new layer list through a seeded RNG and assert the emitted `Particle`
  values match field-for-field. That is what lets a *visual* change be
  reviewed without eyes, and it is the same trick that made W1 and the
  progression S1 tuning reviewable.
- **The open question worth answering first:** do layer lists get their own
  ids so weapons share one by reference (`"impact": "fx.rifle-strike"`), or
  does each weapon inline its own? The four current profiles are shared by
  many weapons across all four catalogs, so inlining means copying the same
  block 19 times. Leaning shared-by-id, which makes it a small second store
  rather than a field on `WeaponDef`.

**Then W3**, which is now genuinely mechanical: marine secondaries and mech
mounts only. `MechWeaponComponent` already draws the mount/weapon boundary
`WeaponDef` wants, so mech mounts migrate by pointing at a weapon id.

## Decisions already locked

Do not relitigate these in a story; change them in `overview.md`.

- One schema across all four catalogs; `MountClass` distinguishes them.
- The registry is the single source of truth — no dual authoring.
- FX are data-defined and **layered**, not a fixed profile enum.
- Parity pinning before any consumer migrates.
- Ids are namespaced (`weapon.pulse-rifle`) and become save-persisted API at
  W4.
- The weapon registry fails loud when absent, unlike the tile registry.

## Cross-track coordination

- The `moddable-tilesets` track owns the registry pattern this copies.
  **W5 and its Phase 3 are the same problem** — discovery, load order, id
  override, validation. Whichever track meets a real submod first should
  build the shared mechanism; the other adopts it.
- The `progression` track is the consumer. S2 (catalog expansion) and S6
  Slice 3 (factional equipment) both get much cheaper after W3, and S1's
  `TtkHarness` measures any data-authored weapon for free.
- Load order: `WeaponRegistry.loadBuiltins()` must precede any consumer that
  walks the catalog at load time. `BattleSprites` is one today.

## Commit chain

- `91d832f3` — W1: `battle.weapon` package, marine-primary catalog as
  data, `MarineWeapon` reduced to an id handle, parity test.
- *(this session)* — design only: split `TurretKind` out of W3 into W6 and
  lock the platform / mount / weapon commitment.

## Resolved this session

**Turrets are not weapons, and the code already knew it.** `TurretKind.maxHp`
was flagged during W1 as a wart. Reading its consumers showed something
sharper: the same `TurretKind` is mounted on **three** platforms — static
`MapTurret`, shuttle `MountedTurret`, and a `GroundSystem` vehicle turret —
and only the static one uses `maxHp`. The javadoc already documents the
conflict without resolving it (`startingAmmo`: "Static `MapTurret`s ignore
this"; `role`: "Static `MapTurret`s default to A2G; mounted shuttle turrets
honor the role").

The resolution is the model mechs already use — platform → mount → weapon —
and it is now design commitment 7 in `overview.md`, with the test for which
layer a field belongs to: **if two carriers of the same weapon disagree about
a field's value, it is not a weapon field.**

`TurretKind` therefore moved out of W3 and into its own story,
`w6-emplacements-and-structures.md`. Two things make it cheaper than it
looks: the renderer already composites a recoil barrel over a base sprite
(the layering exists, only the keying is wrong), and mount slots already
exist twice (`TurretMount` for shuttles, `MechMountSlot` for mechs). **No new
art is required** — vanilla turret sprites bundle the gun with its pedestal,
so the base/barrel pair belongs to the mount, exactly as
`MechWeaponComponent.appearanceSelector` does.

The payoff is the same gun on different platforms — a Vulcan as a hardened
emplacement, a shuttle hardpoint with a magazine, and a convoy vehicle mount
— which today would need three `TurretKind` entries duplicating every
ballistic field.
