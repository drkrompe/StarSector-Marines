# Moddable Weapons

Status: ACTIVE — handheld, special-item and turret weapon data is owned; mech migration remains

Written: 2026-08-23

Updated: 2026-08-25 — made discovery sandbox-safe and carried contributed special equipment through player/faction issue and typed battle execution.

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
- A **delivery mechanism** describes how the definition reaches its payload:
  direct chemical or electromagnetic kinetic fire, flechette sub-munitions,
  pulse/laser energy, rocket or arcing grenade, or a close-contact implement.
  It constrains applicable trajectory, payload, and presentation fields. It is
  not the weapon's tactical role, grade, provenance, or faction availability.
- A **projectile release** is the set of independently resolved traveling
  bodies emitted at one burst instant. `projectilesPerShot` authors a
  simultaneous cloud; `burstCount` and `burstSpacing` author temporal cadence.
  Neither is inferred from display name, mechanism, carrier, or faction.
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
- The **weapon registry** owns parsed definitions from enabled catalog providers and resolves ids.
  It is an asset store, not a combat system. `WeaponRegistry` is the present
  boundary.
- A **catalog provider** is an enabled mod whose fixed marine-catalog manifest
  explicitly lists the resources it contributes. Provider identity and resource
  path are definition provenance used for diagnostics; they are not faction
  availability or equipment provenance inside the fiction.
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
- A **catalog preview** is another consumer of authoritative definitions, not
  a parallel recipe. It shares pure pose and seeded effect composition with
  runtime while owning its storyboard projection as retained panels and
  backend-neutral canvas producers. The shared retained paint targets own live
  and headless rendering; the preview does not own a private raster painter.
- A **turret authoring document** is an editor transaction over the linked
  weapon, mount, structure, and defense-post layout catalogs. It presents one
  resolved turret without merging those authorities: saves return every value
  to the catalog that owns it, validate all cross-references, and cannot create
  an editor-only behavior path.
- A **defense-post layout** is map-generation-owned bounded cell geometry that
  references turret structure ids. It may place several structures and barrier
  or pad cells, but it does not own their weapon stats, durability, garrison,
  placement budget, or tactical priority.

## Authority flow

At application load, the fixed marine-catalog manifest is discovered in each
enabled mod in game load order. Its explicit paths are loaded from that exact
provider, then weapon catalogs are parsed into the registry
before catalog-walking presentation consumers initialize. A loadout or legacy
handle supplies an id; firing, UI, audio, and rendering resolve the same
definition and use only the portion they own. The currently shipped
marine-primary handle delegates to that registry, so gameplay and catalog
presentation do not retain a duplicate Java stat table.

Manifest discovery and catalog reads go through Starsector's provider-scoped
resource API. An absent fixed manifest means that enabled mod is not a catalog
provider; a present but unreadable or malformed manifest remains a load error.
Shipped mod code never probes provider directories through Java filesystem or
reflection APIs, which the game's script classloader rejects.

Turret catalogs load after weapons and resolve structure → mount → weapon
references immediately. A static emplacement takes durability, collision
geometry and force value from its structure; carriers such as shuttles and
vehicles keep their own durability and geometry. Mount capacity, traverse and
optional layered appearance remain mount policy, while the shared weapon owns
ballistics, contact and area payloads, audio and composed effects. The retained
`TurretKind` is only a stable-id compatibility handle over those definitions.
Runtime and the deterministic six-state catalog preview consume the same pose
and seeded effect commands. The preview mounts those commands into the retained
document canvas seam, so its sprite layers, atlas frames, tint, and blend intent
are rendered by the same live/headless target boundary as other authored UI.
Its storyboard derives launch count, boost curve, arc, scatter character, and
contrail from the resolved weapon definition, so an artillery rocket battery
cannot silently degrade into a generic straight-flying turret round.

Defense-post layouts load after turret structures so each placement resolves
at ingestion. Map generation chooses an eligible layout through the seeded run
stream and interprets its cells; singleton tiers consume no layout-selection
draw, while a tier with several authored variants consumes one. The desktop
authoring surface resolves the same chain in memory and feeds current edits to
the same catalog preview before a validated multi-file save.

Registry loading is deliberately fail-loud: a missing registry, unknown id,
duplicate id, malformed required value, unknown mount class, or invalid
impact-profile name stops loading instead of producing a harmless-looking but
unwinnable weapon. Turret cross-catalog references and sprite assets are also
validated before presentation consumers see them. Optional presentation
values have defined neutral defaults. Cross-mod contributions are additive:
enabled-mod order controls deterministic ingestion and iteration, never override
priority. A duplicate id stops load and reports both provider mod ids and paths.
The public manifest and authoring examples live in `submod-catalog-contract.md`.

## Standing laws

- One weapon behavior has one authoritative authored value. Transitional
  parity evidence may compare the old enum values with data, but it is not a
  permanent second catalogue.
- Mechanism does not justify a clone. A chemical slug rifle and a gauss carbine
  become separate definitions only when their engagement behavior, payload, or
  readable shot treatment creates a real choice; a renamed tracer is not a new
  family.
- Simultaneous release count and temporal burst count remain orthogonal. Every
  projectile receives its own trajectory, contact, telemetry, and visual body,
  while trigger discipline may withhold the release as one firing decision.
  Counts must be positive, and spacing without a multi-round burst is invalid
  authored data.
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
  recipe. Turret launch, muzzle, trail, impact and aftermath presentation already uses
  this model; the current named impact profile remains a compatibility bridge
  for unmigrated weapon families and shared decals, lights and audio.
- Launch layers may author forward/lateral offsets and velocities in the
  firing bearing's local frame. Persistent projectile ribbons and impact audio
  are weapon presentation fields consumed consistently by runtime and preview;
  neither is inferred from a turret id.
- Mount appearance is optional and carrier-overridable. Emplacements and
  shuttle mounts may composite base/barrel layers while a ground vehicle keeps
  equivalent art in its chassis sheet; absent appearance never changes weapon
  behavior.
- Multi-turret composition references structure ids rather than copying turret
  kind values into a stamp. Layout bounds and occupied cells are explicit, and
  every turret placement must sit on an ordinary pad inside those bounds.
- An authoring tool stages catalog mutations; it does not become a catalog.
  Preview, save validation, and runtime consumption resolve the same definitions,
  and undo/redo spans the linked document so cross-catalog edits cannot drift.
- Shared mod discovery and merge rules are one cross-catalog concern with
  moddable tilesets. Weapons, special equipment, armor, faction rosters, and
  collectible templates use the shipped manifest/provenance contract; tilesets
  must adopt it rather than invent an override scheme.
- A utility activation may reuse projectiles, detonations, and authored FX,
  but those shared execution primitives do not make its cloud or placement
  channel a weapon definition.
- A weapon-like close-contact tool still references a weapon definition for
  damage, penetration, audio, and effects. Its typed executor replaces the
  traveling trajectory; the special-equipment item still owns stock, resource
  mode, use policy, and carrier presentation.
- A weapon-like arcing grenade uses the same simulated-projectile and detonation
  authorities as other slow explosive ordnance. The definition owns range,
  scatter, velocity, arc height, area payload, structural damage, projectile
  presentation, and audio; the special item owns finite uses and activation.
  Its weapon id travels with the projectile so hazard and overkill consumers
  can distinguish frag footprints without inferring behavior from sprite,
  blast size, or carrier faction.

## Transition boundaries

Registry-owned handheld primary and weapon-like-secondary definitions are the
authoritative data boundary. `MarineWeapon` and `MarineSecondary` remain
id-backed compatibility handles rather than parallel stat authorities; a
weapon-like special reaches its definition through the distinct
progression-owned special-equipment identity. That identity now comes from a
separate JSON catalog and validates that every weapon reference resolves
through the marine-secondary mount class. Progression also owns actor-local
equipment composition and preview recipes; the layered-effects weapon story
remains specifically about muzzle, tracer, trail, and impact FX.

Mech weapon stat carriers remain a temporary transition boundary until their
definitions and mount rules enter the registry. `TurretKind` remains only as a
stable-id compatibility handle whose accessors resolve shipped catalog
definitions; it owns no duplicate authored values. Both families still obey
the same penetration and mutually exclusive contact-versus-area payload laws.

Generated faction and player-authored primary, armor, and special-equipment
issue consume contributed definitions directly. Player doctrine selection,
persistence, cargo-backed materialization, deployment, special AI and typed
activation, ballistics, rendering, and audio do not require `MarineWeapon`,
`MarineArmorPattern`, or `MarineSecondary` constants. Those enums remain only
at built-in compatibility APIs and legacy-save boundaries. Removing a provider
repairs player primary and armor ids to their safe starters and clears an
unresolved special slot with a warning.

Catalog expansion and mount validation, layered effects, compatibility-enum
completion, compatibility-enum retirement and persistence repair, and remaining
catalog-provider adoption belong to the work lifecycle tracked only by
`stories.md`.

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
