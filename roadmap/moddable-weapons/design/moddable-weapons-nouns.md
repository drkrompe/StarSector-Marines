# Moddable Weapons

Status: ACTIVE — the marine-primary catalog is data-owned; the remaining weapon families are planned migrations

Written: 2026-08-23

## Purpose

A weapon is a durable, data-authored description of what a shot does and how
it is presented. The catalog separates that description from the carrier that
mounts it, from the progression systems that grant it, and from the runtime
systems that fire or draw it. The goal is one dependable weapon vocabulary
without turning a JSON typo into a silent zero-damage battle.

## Vocabulary and ownership

- A **weapon definition** is the immutable, stable-id description of combat
  behavior, catalog identity, and shot presentation. It does not own a unit's
  health, hardpoint geometry, magazine policy, or progression eligibility.
- A **weapon id** is the durable reference to a definition. It is the future
  persistence and cross-catalog handle; Java enums are transitional handles,
  not a second source of weapon values.
- A **mount class** is the compatibility family for a definition: handheld
  primary or secondary, mech mount, or turret mount. It distinguishes what
  may use a definition; it is not a statement about which individual unit
  happens to carry it. A drone pulse weapon can therefore use the shared
  primary firing family without making the drone a marine.
- A **platform** owns survivability, footprint/chassis, and its available
  mounting positions. A **mount** owns the hardware that fits a platform:
  installed weapon, capacity or rack behavior, and mount appearance. The
  weapon owns projectile behavior. This three-way distinction is especially
  important for emplacements, whose current enum conflates all three.
- The **weapon registry** owns parsed built-in definitions and resolves ids.
  It is an asset store, not a combat system. `WeaponRegistry` is the present
  boundary.
- **Progression and loadout** own which weapons a marine may receive, grade,
  stock, unlocks, templates, and save repair. They consume weapon identity;
  a weapon definition must not decide whether the player owns it.
- **Effects** are presentation descriptions. A shot's simulation result never
  depends on particles, tracer art, or fire audio.

## Authority flow

At application load, bundled weapon catalogs are parsed into the registry
before catalog-walking presentation consumers initialize. A loadout or legacy
handle supplies an id; firing, UI, audio, and rendering resolve the same
definition and use only the portion they own. The currently shipped
marine-primary handle delegates to that registry, so gameplay and catalog
presentation do not retain a duplicate Java stat table.

Registry loading is deliberately fail-loud: a missing registry, unknown id,
duplicate id, malformed required value, unknown mount class, or invalid
impact-profile name stops loading instead of producing a harmless-looking but
unwinnable weapon. Optional presentation values have defined neutral
defaults. Built-in catalog discovery is currently explicit; cross-mod
discovery, ordering, overrides, and diagnostics remain deferred until a real
shared consumer exists.

## Standing laws

- One weapon behavior has one authoritative authored value. Transitional
  parity evidence may compare the old enum values with data, but it is not a
  permanent second catalogue.
- An id is stable across authored catalogs and later persistence. A missing
  persisted id must be repaired to a safe starter weapon with a warning, not
  break a roster.
- Simulation fields and presentation fields may travel together in a
  definition, but presentation never changes simulation outcomes.
- Mount classes constrain authoring once multiple families populate the
  registry. Fields that make no sense for a family are authoring errors, not
  spare switches for consumers to interpret.
- If two platforms using one gun need different health, ammunition, targeting,
  or geometry answers, that answer belongs to the platform or mount, never to
  the weapon.
- Data-authored effects compose layers rather than select a fixed global
  recipe. Until that migration ships, the current named impact profile is a
  compatibility bridge, not the final extension surface.
- Shared mod discovery and merge rules are one cross-catalog concern with
  moddable tilesets, not two independently invented override schemes.

## Current boundary and direction

W1 has moved the five marine-primary definitions into the registry, while
`MarineWeapon` remains an id-backed compatibility handle for current callers
and persistence. Marine secondaries, mech weapons, and turrets remain
enum-owned work; their migration is deliberately not implied by the shared
schema. The open work is on `stories.md`.

W2 turns effect recipes into ordered authored layers. W3 adds the remaining
portable and mech weapon families and the first meaningful mount validation.
W4 retires enum stat carriers and owns the save migration. W6 applies the
platform/mount/weapon split to emplacements and structures. W5 is deferred
direction: a real submod should establish shared weapon/tile discovery and
override semantics before either catalog claims a modding merge API.

## Boundaries

`moddable-tilesets-nouns.md` owns the sibling asset-catalog model; the two
features share only future discovery/merge machinery, not weapon semantics.
Progression owns availability and economic value, while this feature owns
what an available weapon is. Combat and rendering own execution of the
definition, not catalog parsing or progression choices.
