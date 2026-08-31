# Moddable Weapons

Status: SHIPPED — weapon identity, behavior, and presentation are data-owned

Written: 2026-08-23

Updated: 2026-08-30 — projectile-bound tracer tails became an optional authored
presentation for small traveling rounds.

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
- A **weapon id** is the durable persistence and cross-catalog reference to a
  definition. Java types do not enumerate weapon identities.
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
  important for emplacements, whose structure, mount, and weapon catalogs keep
  all three authorities distinct. That split holds for an emplacement a marine
  carried in and set down as firmly as for one bolted to a compound: its
  durability and geometry come from the structure, its magazine and traverse
  from the mount, its reach and rate of fire from the weapon. A carried item
  that placed an emplacement owns only the placement — how long setting it up
  takes and how long the result runs — and never restates the gun's numbers,
  because one emplacement must not have two answers to the same question.
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
  weapons merely because they occupy the same billet slot. The retired
  `MarineSecondary` enum conflated these concepts; this split is authoritative.
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
before catalog-walking presentation consumers initialize. A loadout or
save-migration input supplies an id; firing, UI, audio, and rendering resolve
the same definition and use only the portion they own. Mech loadout components
likewise carry stable weapon ids; simulation and presentation resolve the same
mech-mount definition while the component retains rack, ammunition, geometry,
and appearance policy.

Manifest discovery and catalog reads go through Starsector's provider-scoped
resource API. An absent fixed manifest means that enabled mod is not a catalog
provider; a present but unreadable or malformed manifest remains a load error.
Shipped mod code never probes provider directories through Java filesystem or
reflection APIs, which the game's script classloader rejects.

After weapons, special equipment, armor, and collectible templates resolve,
progression loads faction-equipment source contributions. Those files may add
a unique template/channel claim to an existing campaign faction or define a new
faction pool. They consume weapon identity through the derived template id and
cannot change a weapon definition, combat behavior, or vanilla production
knowledge.

Turret catalogs load after weapons and resolve structure → mount → weapon
references immediately. A static emplacement takes durability, collision
geometry and force value from its structure; carriers such as shuttles and
vehicles keep their own durability and geometry. Mount capacity, traverse and
optional layered appearance remain mount policy, while the shared weapon owns
ballistics, contact and area payloads, audio and composed effects. Static-turret
ECS state, shuttle mounts, vehicles, map generation, and sprite caches carry
stable structure ids; runtime resolves the installed `StructureDef` graph.
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
duplicate id, malformed required value, unknown mount class, or missing
required effect slot stops loading instead of producing a harmless-looking
but unwinnable weapon. Turret cross-catalog references and sprite assets are also
validated before presentation consumers see them. Optional presentation
values have defined neutral defaults. Cross-mod contributions are additive:
enabled-mod order controls deterministic ingestion and iteration, never override
priority. A duplicate id stops load and reports both provider mod ids and paths.
The public manifest and authoring examples live in `submod-catalog-contract.md`.

## Standing laws

- One weapon behavior has one authoritative authored value. Tests assert the
  installed definitions directly rather than maintaining a second catalogue.
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
- **Vulnerability to point defence is declared by the round, not by whatever
  shoots it down.** A weapon definition says whether it is a legal target for a
  defensive mount, and that declaration is the only authority: no emplacement,
  behavior, or system holds a list of engageable weapon ids. The alternative —
  an interceptor naming the four things it knows about — silently excludes every
  warhead authored afterwards, which is exactly the failure mode the catalogs
  exist to prevent. Being engageable is a separate question from being modelled
  as an in-flight body: the second is a prerequisite (a round with no travelling
  entity cannot be shot down) but not a synonym, since ordinary bullets are also
  bodies in flight and are not ordnance. Only a warhead may declare itself
  engageable.
- Contact privilege comes from physical interception. An explosive direct-fire
  shot does not grant its contact payload to a selected target after a wall stop
  or miss, and it does not stack contact and area payloads on one actor.
- Body penetration is authored weapon behavior, not an id exception. It is a
  non-negative count available only to direct resolved mech rounds with a
  contact payload. Every contacted actor receives that contact payload at its
  own flight time; all such actors are excluded from the shot's single area
  payload even when the lane continues through them.
- An id is stable across authored catalogs and later persistence. A missing
  persisted id must be repaired to a safe starter weapon with a warning, not
  break a roster.
- Simulation fields and presentation fields may travel together in a
  definition, but presentation never changes simulation outcomes.
- Every marine-primary definition declares its held-sprite family. Runtime combat,
  Armory portraits, and embedded shipboard scenes resolve that family directly from
  the definition; an unrecognized contributed id never silently becomes a generic rifle.
- Mount classes constrain authoring once multiple families populate the
  registry. Fields that make no sense for a family are authoring errors, not
  spare switches for consumers to interpret.
- If two platforms using one gun need different health, ammunition, targeting,
  or geometry answers, that answer belongs to the platform or mount, never to
  the weapon.
- Data-authored effects compose ordered launch, muzzle, tracer, trail, impact,
  and aftermath layers rather than select a fixed global recipe. Runtime for
  every weapon family and the catalog preview use the same seeded composition path;
  decals, lights, and audio derive supporting treatment from the authored
  primitives without restoring a named visual profile.
- A projectile sprite may carry a short authored tracer tail in world cells.
  The line follows the committed visual flight, grows out of the muzzle, and
  remains presentation only; it neither lengthens the physical round nor turns
  a bullet into a hitscan shot.
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
  collectible templates, tilesets, and tile mappings use the shipped
  manifest/provenance contract without inventing per-domain override schemes.
- A utility activation may reuse projectiles, detonations, and authored FX,
  but those shared execution primitives do not make its cloud or placement
  channel a weapon definition.
- A weapon-like close-contact tool still references a weapon definition for
  damage, penetration, wall damage, audio, and effects. Its typed executor
  replaces the traveling trajectory with an adjacency test, so the definition
  authors no velocity, flight time, arc, or projectile body and the payload is
  never carried by a phantom round. Contact privilege still comes from physical
  interception; here the interception is the reach itself. The
  special-equipment item still owns stock, resource mode, use policy, and
  carrier presentation.
- Authored wall damage on a close-contact definition is a magnitude, not a
  licence. A cutter's wall damage applies at one authored breach point and
  nowhere else, and it carries no radius: the map-edit authority decides which
  cell may open, and the weapon only says how hard the tool bites.
- A weapon-like arcing grenade uses the same simulated-projectile and detonation
  authorities as other slow explosive ordnance. The definition owns range,
  scatter, velocity, arc height, area payload, structural damage, projectile
  presentation, and audio; the special item owns finite uses and activation.
  Its weapon id travels with the projectile so hazard and overkill consumers
  can distinguish frag footprints without inferring behavior from sprite,
  blast size, or carrier faction.

## Transition boundaries

Registry-owned handheld-primary, weapon-like-secondary, mech-mount, and
turret-mount definitions are the authoritative data boundary. Historical
`MarineWeapon` and `MarineSecondary` names survive only as serialized string
input for save migration. Mech components store weapon ids, and turret
carriers store structure ids; neither family has a Java weapon enum. A
weapon-like special reaches its definition through the distinct
progression-owned special-equipment identity. That identity now comes from a
separate JSON catalog and validates that every weapon reference resolves
through the marine-secondary mount class. Progression also owns actor-local
equipment composition and preview recipes; weapon FX owns only shot
presentation slots and their composition.

Mech weapon components store stable ids and remain the authority for mount
family, rack size, ammunition, geometry, and appearance. Mech weapon behavior
and presentation resolve through the registry, including the separate
simulation decision to create an interceptable projectile and presentation
decision to compose an authored trail. Mech and turret families still obey the same penetration and
mutually exclusive contact-versus-area payload laws.

Generated faction and player-authored primary, armor, and special-equipment
issue consume contributed definitions directly. Player doctrine selection,
persistence, cargo-backed materialization, deployment, special AI and typed
activation, ballistics, rendering, and audio do not require `MarineWeapon` or
`MarineSecondary` constants. `MarineArmorPattern` remains an id-backed armor
compatibility handle while historical primary and special names are accepted
only as serialized string input. Removing a provider
repairs player primary and armor ids to their safe starters and clears an
unresolved special slot with a warning.

## Boundaries

`moddable-tilesets-nouns.md` owns the sibling asset-catalog model; the two
features share discovery/merge machinery, not weapon semantics.
Progression owns availability and economic value, while this feature owns
what an available weapon is. Combat and rendering own execution of the
definition, not catalog parsing or progression choices. Progression also owns
the special-equipment item catalog; this feature owns only any weapon
definition that such an item references.
`combat-durability-nouns.md` owns the shared calculation that combines authored
damage and penetration with a target's current armor and structure.
