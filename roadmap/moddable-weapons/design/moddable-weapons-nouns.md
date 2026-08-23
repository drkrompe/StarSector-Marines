# Moddable Weapons

Status: ACTIVE — handheld weapons are data-owned; mech and emplacement migrations remain

Written: 2026-08-23
Updated: 2026-08-23 — established distinct contact and area payloads for explosive direct-fire weapons while mech and turret definitions remain transitional enum authorities.

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
- A **damage payload** pairs damage against exposed structure with penetration
  against actor armor. One shot may own a **contact payload** for the actor
  physically struck and a separate **area payload** for nearby actors. The
  contacted actor receives only the contact payload; neither payload identifies
  a target category or changes its damage after armor breaks.
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
- A **special-equipment item** is a progression/loadout identity with a typed
  activation. Weapon-like specials such as rockets, anti-materiel rifles, and
  fragmentation grenades reference a weapon definition; smoke and placed charges do not become
  weapons merely because they occupy the same billet slot. The current
  `MarineSecondary` enum conflates these concepts and is transitional.
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
- Penetration replaces anti-hardened and anti-turret damage multipliers. A
  weapon never owns a list of platform types against which its damage changes.
- Contact privilege comes from physical interception. An explosive direct-fire
  shot does not grant its contact payload to a selected target after a wall stop
  or miss, and it does not stack contact and area payloads on one actor.
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
- A utility activation may reuse projectiles, detonations, and authored FX,
  but those shared execution primitives do not make its cloud or placement
  channel a weapon definition.

## Current boundary and direction

W1 moved the five marine-primary definitions into the registry. The first W3
slice moved the Annihilator rocket there too, and progression S2A added the
anti-materiel heavy round through the same marine-secondary mount class.
`MarineWeapon` and `MarineSecondary` remain id-backed compatibility handles;
the latter points through a distinct special-equipment definition so a loadout
item and the weapon it activates do not become one identity. Mech weapons and
turrets remain enum-owned work, but now author penetration with the same live
semantics until their catalog migrations ship. The Hephaestus is the current
transitional proof that one direct-fire weapon can carry mutually exclusive
contact and area payloads through those semantics. The open work is on
`stories.md`.

W2 turns effect recipes into ordered authored layers. W3 has migrated the
current rocket and added marine mount validation; its remaining slice moves
the mech weapon families and validates their mount rules. Progression's
special-equipment identity may point at those weapon ids; later smoke and
satchel stories add non-weapon activations,
while the frag story adds another weapon-like activation.
W4 retires enum stat carriers and owns the save migration. W6 applies the
platform/mount/weapon split to emplacements and structures. W5 is deferred
direction: a real submod should establish shared weapon/tile discovery and
override semantics before either catalog claims a modding merge API.

## Boundaries

`moddable-tilesets-nouns.md` owns the sibling asset-catalog model; the two
features share only future discovery/merge machinery, not weapon semantics.
Progression owns availability and economic value, while this feature owns
what an available weapon is. Combat and rendering own execution of the
definition, not catalog parsing or progression choices. Progression also owns
the special-equipment item catalog; this feature owns only any weapon
definition that such an item references.
`combat-durability-nouns.md` owns the shared calculation that combines authored
damage and penetration with a target's current armor and structure.
