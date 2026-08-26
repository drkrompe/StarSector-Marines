# Progression nouns

Status: ACTIVE — cross-tier quality, kit, career evidence, and legibility continue to evolve.

Written: 2026-08-23

Updated: 2026-08-26 — grounded shipped equipment provenance, circulation, and deliberate faction absences.

## Purpose

Progression is the cross-tier model for a mercenary company's growing combat
quality. It joins four distinct but connected concerns: the kit a marine is
issued, the innate and earned qualities of that marine, evidence of what they
did in battle, and the campaign economy and presentation that make improvement
meaningful to the player.

This domain owns troop quality. It does not own the personnel lifecycle,
contract resolution, salvage settlement, ballistic resolution, or player
command powers. Those systems supply identities, outcomes, recovery, damage
and opportunities; progression interprets them as quality, reward, and
legibility.

## Vocabulary

- **Marine** — a named, persistent rank-and-file campaign soldier. `MarineRoster`
  is the authority for that identity, status, assigned kit, and career.
- **Profile** — a marine's battle-ready individual quality: immutable aptitude
  plus earned XP, represented at the battle seam by `SoldierProfile`.
- **Aptitude** — an innate, permanent marksmanship disposition. It is not an
  upgrade track and is not rerolled by experience.
- **Experience** — earned soldier XP and the derived Green, Regular, Veteran,
  or Elite tier. It changes infantry combat performance and new-threat
  registration; it is separate from captain XP and rank.
- **Career** — a marine's persisted lifetime service evidence: deployments,
  wins, fired and landed rounds, dealt and taken damage, kills, and wounds.
  It is cumulative, not a per-mission journal.
- **Equipment family** — the weapon's tactical identity: engagement band,
  firing pattern, baseline behavior, and family presentation. A family is not
  a quality tier or merely a technology label.
- **Delivery mechanism** — how a weapon produces and delivers its payload:
  chemical slug, gauss/rail kinetic, flechette cloud, pulse/laser energy,
  missile or grenade, or close-contact tool. The weapon catalog owns this
  physical/presentation truth. Mechanism does not decide availability, grade,
  faction allegiance, or tactical role by itself.
- **Equipment grade** — the four-step manufacturing/condition quality axis:
  Surplus, Service, Milspec, Masterwork. Grade composes with family and profile;
  it does not create a separate unit type.
- **Equipment provenance** — the manufacturing tradition or factional lineage
  attached to a recipe and concrete item. Provenance governs availability,
  presentation, and at most a bounded side-grade; it is not an allegiance lock,
  a quality tier, or a set bonus.
- **Special equipment** — one optional billet item carried alongside the
  marine's primary. Rocket launchers, anti-materiel rifles, and fragmentation
  grenades activate weapon definitions; smoke and satchel charges activate
  utility behavior. “Secondary” remains a transitional code/catalog name, not
  the enduring player-facing category.
- **Assault-armor role** — the suit's battlefield weight and purpose: light
  infiltration/recon, standard line combat, or heavy mechanized battlesuit.
  Unpowered field kit remains outside those three powered roles. Role expresses
  a protection/mobility silhouette, not a vertical quality tier.
- **Armor pattern** — a concrete, player-owned infantry protection and
  appearance package. A pattern realizes one assault-armor role and may carry
  equipment provenance. Unlike weapon grade, it changes survivability and
  movement tradeoffs as its own kit choice.
- **Equipment template card** — permanent collected capability for one primary
  family-and-grade, armor pattern, or special item. It gates authoring and issue,
  is never consumed, and is distinct from a reusable squad definition.
- **Faction equipment source** — one campaign channel through which a faction
  may make a template card available: ordinary market stock, licensed access,
  patron reward, or operational recovery. A source declares eligibility and
  relative selection weight; it does not itself grant, sell, or recover a card.
- **Armory** — the persistent campaign authority for collected equipment
  templates and reusable squad definitions. Materialized marine kit persists on
  the marine; changed incoming issue is paid from ordinary fleet cargo.
- **Telemetry** — battle-local, lifecycle-stable evidence of a combatant's
  activity. It serves the debug balance artifact for all recorded combatants
  and career evidence only for campaign marines.
- **Trait** — a captain's stable named quality. Traits belong to leadership,
  not the individual marine experience ladder; some are currently functional
  while others remain planned mechanics.

## Current quality model

### Composed combat quality

For an infantry primary, `InfantryCombatStats` composes weapon family,
equipment grade, and profile rather than treating any one as the complete
answer to "how strong is this marine?" Grade affects range, damage, accuracy,
cooldown, and spread. Aptitude affects accuracy and spread. Experience affects
accuracy, cooldown, spread, trigger discipline, and first action against a
new threat. Armor is a separate defensive/loadout decision.

The standing law is **family supplies role; mechanism supplies delivery; grade
supplies quality; provenance supplies source; profile supplies person**. New
content must preserve this separation. A high-grade weapon is still its family,
a gauss label does not earn a redundant family, and a skilled soldier does not
become a bespoke carrier type.

The shipped primary catalog now covers five player-facing decisions. The Rook
is the rugged chemical-slug baseline, the Lancer is the flexible pulse burst,
and the Longbow is the deliberate gauss/rail marksman weapon. The Rattler is a
close-range flechette shredder: one trigger releases several independently
resolved projectiles with low penetration, steep falloff, and wide spread. Its
legacy `SMG` enum name and `weapon.smg` id remain only for save compatibility.
The Stalwart is the squad automatic slugthrower: one trigger begins a long
temporal burst that participates in the existing covering-fire and bounding
model without creating a suppression status or permanent gunner class.

Simultaneous projectile count and temporal burst count are separate authored
axes. Friendly-fire discipline treats one multi-projectile release as one
trigger decision, while every projectile retains its own physical trajectory,
impact, telemetry, and presentation. Both families use the same faction-neutral
engagement and maneuver rules for player and defender carriers. The automatic
is starter-reachable and appears in the built-in Fire Support template; target
faction profiles decide whether and how heavily defenders issue either family.
`equipment-lore-catalog.md` owns the models' in-universe origin and credible
circulation. Those origins are mod direction constrained by faction lore, not new
claims about vanilla canon and not a reason to create equivalent faction clones.

### Aptitude and experience

Aptitude is innate and remains fixed for a marine's life. XP is persisted on
the marine and derives the experience tier through `ExperienceTier`. Current
mission resolution still awards a flat survivor XP amount by outcome/risk;
performance-derived awards are planned work, not current behavior.

Experience already has a behavioral meaning beyond output. For infantry
training archetypes, `FiringSystem` holds the first primary shot against a
newly selected threat until the tier's reflex delay expires. Continuing on the
same threat does not restart the delay; opportunity fire registers its own
observed threat without changing the pursuit target. A legal in-range acquired
threat remains the opportunity-fire choice until another is materially closer;
near-equal hostiles cannot alternate the registration identity each tick and
starve the first shot. Visual torso facing follows this acquisition at a
bounded rate but is presentation state, never another fire gate. Sustained
cadence remains a weapon-and-handling concern. This applies to trained humanoid
infantry, not to mechs, turrets, drones, or fauna. Experience changes first
action without becoming another permanent damage multiplier.

### Kit and armory

The armory has three deliberately separate layers. Equipment template cards are
permanent collected capability. Weapon and Armor definitions are reusable
twelve-billet intent authored only from collected templates. The exact kit on a
marine is the materialized result. Issuing changed incoming kit consumes
base-game supplies, heavy armaments, and heavy machinery as one atomic squad
transaction; unchanged kit costs nothing, removed kit grants no refund, and food
remains available to later sustainment costs without being forced into routine
refits. Marines remain personnel cargo handled by personnel logistics.

Squad loadout collection presentation is authored data. Its tier communicates the
definition's expected power band, while rarity communicates how scarce or prestigious
that definition is in campaign acquisition and drives only its collectible visual
treatment. Provenance and a setting paragraph make the acquisition a lore-bearing
reward. None of those fields is a loot-table weight, and a definition's contents are
never rolled from a pool: every selection resolves the same ordered twelve-billet
Weapon or Armor issue, including any leader-specific and special-equipment placements.

Legacy recipes, printed counts, and fabrication materials remain save-migration
input and compatibility state for retired fire-team APIs, not live Fleet Armory
authority. Existing victory milestones grant template cards, including the
Shattercap after two victories. Faction source pools now author which cards may
enter through market, license, patron, and recovery channels. Open markets now
stock a faction-and-market-stable weighted selection that rotates monthly and
scales with market size. Favorable-or-better standing adds licensed offers, and
already-owned cards are omitted. Patron and operational-recovery consumers, plus
the full asset reachability ladder, remain planned.

A template card may exist in fleet cargo as one parameterized Starsector special
item whose data is the stable equipment-template id. Right-click learning follows
the familiar blueprint interaction and consumes one card only when it adds a new
capability to `MarineArmory`; duplicates, invalid data, and an unavailable Armory
remain unconsumed. This interaction deliberately does not implement a vanilla
blueprint provider or write player-faction hull, fighter, ship-weapon, or industry
knowledge, so infantry equipment cannot leak into ship production or the ship
editor. Market, reward, and salvage systems create the same validated cargo
payload; the S6 acquisition ladder still owns the eligibility of each entrance.

Collectible eligibility and issue cost are catalog data rather than a closed
Java list. Each enabled catalog provider may add primary family-and-grade cards,
armor cards, and special-equipment cards after the referenced equipment has
loaded. Card ids are derived from the stable equipment id, duplicate claims fail
with provider provenance, and learning keeps the same Marine-Armory-only
boundary. A provider adding a weapon or armor does not automatically make it
player collectible; it must deliberately contribute the corresponding template.
The doctrine editor derives primary family/grade, armor, and special-equipment
choices from the owned cards in that additive catalog. Persisted doctrines,
resolved billets, and materialized marines retain the contributed equipment ids
directly, so a learned provider card can be selected, saved, issued for its
authored cargo cost, and deployed without a Java enum constant. Special items
then execute through their closed typed activation and AI policy. If that
provider later disappears, save repair warns, returns unresolved primaries to
the starter field rifle and unresolved armor to field fatigues, and clears an
unresolved special slot.

Faction availability is a second additive catalog over those card ids. Exact
campaign faction ids merge offers from every enabled provider; a provider may
therefore add its own card to an existing faction without replacing that
faction's core pool. One faction/template/channel claim has one provider, and a
duplicate reports both sources rather than silently changing its weight.
Unknown factions resolve to the Independent pool. A faction with no
human-compatible player equipment declares an explicit reason instead of
quietly producing an empty or unreachable catalog. The four channel weights are
inputs for later acquisition consumers, never loot rolls performed during
application loading and never a hidden combat modifier.

Armor patterns are authored player kit with distinct defensive and mobility
tradeoffs. Some authored patterns are not presently reachable by the live
unlock ladder; their existence is not evidence of a shipped acquisition path.
The pattern owns the deployed armor pool, rating, movement modifier, and
incoming-accuracy tradeoff; `combat-durability-nouns.md` owns how battle damage
removes that armor and exposed structure. Structure remains the platform's base
pool; armor no longer adds health or applies a permanent damage-reduction
multiplier after it breaks.

The planned assault-armor role makes those trades legible without turning the
current numeric `tier` into the suit's identity. Light armor favors mobility
and concealment-capable patterns, line armor is the all-environment baseline,
and heavy battlesuits trade speed for breach-level protection while remaining
one-person infantry. Powered sealing, recoil assistance, tactical relays, and
jump assistance are setting and presentation truths until a story names a
mechanical consumer. Optical camouflage likewise requires an honest shared
observation/perception contract; an armor description alone may not grant
invisibility or erase an opponent's remembered contact.

Equipment provenance composes beside role and pattern. It lets a Hegemony line
suit and a Tri-Tachyon line suit share the same role while differing in source,
visual language, maintainability, and a bounded authored skew. Player companies
may mix recovered traditions. Provenance never rewrites the marine's weapon
family, grade, aptitude, or experience, and faction identity never supplies a
hidden universal combat bonus.

Each billet has at most one special-equipment slot. The item is a stable
loadout identity with a typed activation: weapon-like specials reference the
weapon catalog that owns their payload and any traveling round, while utility
specials own their battle action without becoming zero-damage weapons.
Progression owns template ownership, assignment, cargo issue value, and
reachability; battle AI owns when legal
issued equipment is used. The same use policy is faction-neutral even when
campaign availability differs by faction.

Planned special-equipment extensions retain that slot and activation law.
Close-contact boarding tools are weapon-like items whose catalog definitions
own damage and penetration even though their executor has no traveling round.
Combat stims are finite utility, not a weapon grade or permanent profile
upgrade. Martyr rigs and carried improvised charges are explicit faction
content with their own carrier cost and counterplay; faction flavor may not
silently graft self-detonation, aim bonuses, or shock immunity onto ordinary
infantry.

Target defenders consume one battle-frozen `GroundRosterProfile`. Its JSON
weights select among stable primary, grade, armor, special-equipment, and mech
ids already owned by their respective catalogs; they do not create factional
copies of those items or faction-only execution rules. Risk chooses which
weighted quality/protection tables apply, while mission setup retains force
scale and support gates. The current armor enum and art may still carry palette
names, but roster data uses semantic armor ids (`field-fatigues`, `scout`,
`combat`, `line`, `heavy`, `outlaw`, `militia`) so the next visual/content pass
can change colors without changing doctrine identity.

The five built-in identities are the rocket launcher, anti-materiel
rifle, Wayfarer smoke grenades, Breachhand mag-clamp satchel, and Shattercap
fragmentation grenades. The first two are direct-fire activations, Shattercap
is an arcing weapon activation, and smoke and satchels are utility activations,
but only the item definition owns loadout identity, catalog copy, resource
mode, initial ammunition, Armory art, activation type, AI policy, use-pose
profile, and local presentation recipe; the
referenced weapon definition owns range, damage, accuracy, impact, projectile,
and audio behavior. Persisted marines and billets use the stable `special.*`
id, while collected cards derive their stable identity from it. `MarineSecondary`
remains a built-in compatibility API and legacy-save input, not a battle-runtime
authority or second stat catalogue.

Those item definitions load from the built-in special-equipment JSON catalog
after the weapon registry. Parsing and reference validation fail loud:
unknown activation/resource/policy/pose vocabularies, invalid resource
combinations, duplicate ids, malformed presentation transforms, or a
weapon-like item pointing outside the marine-secondary mount class stop load.
JSON selects only closed, typed execution policies; it does not name Java
classes or inject simulation scripts.

An equipment presentation separates catalog art, actor-local carrier layers,
deployed-world art, and preview state. Carrier transforms are authored in
shoulder-width coordinates with carried/using states, pivot, occlusion,
optional recoil, and whether activation temporarily replaces the primary.
The live layered-unit renderer and development preview gallery resolve those
transforms through one composition helper. A later Armory sample-soldier scene
should consume that same helper rather than recreate placement in UI code; the
existing Armory thumbnail remains the current production surface until that
scene is built.

The anti-materiel rifle is a four-round precision answer to visible hardened
targets. Its long brace locks one target and cancels when the target or direct
firing solution becomes illegal. The physical heavy round uses ordinary
ballistic collision, has no splash or wall damage, and cannot intentionally
select soft infantry. Squad reservation counts both carriers already bracing
and committed direct rounds so scarce shots do not overkill. The same policy
drives player and defender carriers; current authored availability is one
starter player rifle plus one carrier in high-risk non-militia defender
fireteams. Its report is an ordinary localized shot noise, not omniscient
identity disclosure.

Smoke grenades are two-canister tactical utility. A short authored throw
creates a simulation-owned, several-cell cloud for one maneuver bound. Its
cells are temporary opacity in the shared tactical line-of-sight layer: they
block observation and direct fire for both factions, but never movement,
ballistic travel, audio, damage, or remembered belief. Overlapping clouds
reference-count that opacity until the last field expires.

Smoke is planned at squad level, not fired as a target-of-opportunity weapon.
An exposed objective bound reserves one carrier and lane, pauses until the
cloud is actually opaque, then resumes; a breaking squad may supplement its
fallback with one useful throw while the other survivors keep moving. Active
or airborne coverage prevents redundant throws. The same rules drive player
and defender carriers. Player issue includes two starter canisters and a
Screen template; medium/high-risk non-militia defender fireteams may carry
smoke explicitly.

The Breachhand is reusable close-contact demolition for a marine who already
happens to be beside a hostile hardened target. Opportunity AI considers only
living turrets, drone hubs, and heavy mechs already inside contact range and
honest line of sight; it never assigns an approach or overrides survival
movement to manufacture a plant. An interrupted channel releases its target
reservation at no cost. A completed plant starts the carrier's personal
equipment cooldown rather than consuming battle ammunition.

The armed pack attaches to the target and follows even a moving mech until its
fixed fuse expires. The shared detonation authority resolves a compact,
friendly-fire-capable anti-materiel blast; light hardened targets die while a
heavy platform may survive damaged. The planting faction knows the temporary
hazard and routes friendlies out, while opponents gain no omniscient avoidance.
Player starter collection includes the Breach template. Cargo-backed issue
materializes a carried kit; battle placements do not consume campaign cargo. Ground
placement, infantry targeting, traps, disarming, and wall breaching are not
part of this equipment identity.

The Shattercap is a three-grenade short-arc anti-personnel special. Its
registry-owned weapon definition owns throw range, release scatter, flight,
arc, compact lethal area payload, negligible penetration, zero wall damage,
projectile art, and detonation audio. The special-equipment item owns the
finite three-use battle resource, loadout identity, activation policy,
carrier/throw presentation and template identity. A released grenade
remains a real in-flight projectile and detonates if its carrier dies; ordinary
cover, armor, friendly fire, telemetry, and anonymous detonation noise remain
shared combat authority.

Opportunity AI requires at least two recent direct-contact soft combatants in
one useful footprint. Audio-only contacts, isolated targets, hardened targets,
and stale tracks do not justify a throw. Safety expands the blast by authored
scatter and checks both friendly positions and committed movement paths.
Friendly overlapping landing reservations and in-flight frag footprints block
redundant throws, while genuinely separate clusters may be engaged. A carrier
does not interrupt mission-priority work, survival withdrawal, or its moving
half of a bound to begin a throw. Friendly squads know their own grenade;
opponents evade only when a squad member can honestly see the incoming body.
The battle HUD shows FRG stock and an amber friendly/red observed-hostile
landing ring, so unseen enemy throws provide no warning.

The player template card unlocks after two victories. The built-in Fleet Assault
weapon doctrine issues exactly one
frag carrier across its twelve billets. All built-in defender roster profiles
author their own low/medium/high bulk and elite weights: the execution policy
is faction-neutral, while availability remains faction-shaped and risk-scaled.

### Telemetry and career

`CombatTelemetryService` records combat evidence at the shared firing and
damage seams. It records every designated combatant for a mission's debug
table, including non-campaign units. Damage attribution is based on applied,
post-mitigation damage clamped to the target's remaining health, rather than a
requested damage value; overkill is not credited output. Friendly fire is
separate and never grants a kill. One arriving round counts once even if its
explosion harms many targets, while each defeated victim is still a kill.
Unattributed damage records what happened to the target without inventing an
attacker.

Telemetry survives a combatant's death transition. At mission end,
the gathered report is immutable and detached from battle entity handles.
`MissionOutcome` freezes only campaign-marine rows for campaign use, while the
full report remains a balance readout. `MarineRoster.applySoldierOutcome` uses
the outcome manifest to count every deployed marine, then folds any available
telemetry into `SoldierCareer`. Thus deployment is not inferred from having
fired, and a fallen marine retains the record of their final mission. Career
retention is deliberately lifetime totals rather than a per-mission journal.

The standing law is **outcome declares participation; telemetry supplies
evidence**. Telemetry must never become the authority for campaign identity,
casualty disposition, or deployment membership.

## Flow and authority boundaries

1. The personnel domain creates and persists a marine; progression retains
   only that marine's quality-bearing fields and assigned kit.
2. The armory collects templates and authors reusable definitions; one atomic
   roster command consumes ordinary fleet cargo for changed incoming kit and
   materializes it on marines.
3. Deployment carries the marine's profile and kit into battle. The battle
   resolves shots and damage independently of campaign persistence.
4. Battle telemetry records combatant evidence and follows the death path.
5. Mission resolution freezes a `MissionOutcome`; the campaign roster applies
   the authoritative disposition and folds eligible evidence into careers.
6. Campaign UI and battle presentation should expose quality without feeding
   presentation state back into simulation.

Campaign personnel owns who a marine is and whether they return. The battle
system owns whether and how an attack resolves. Campaign loot owns salvage
manifest and settlement. Progression owns neither, but consumes their stable
outputs for troop-quality advancement and explanation.

`company-view-nouns.md` owns squad-definition presentation and atomic assignment
transactions. Progression supplies equipment-template ownership, cargo issue
costs, and the quality meaning those transactions materialize.

## Presentation law

Progression information must be readable where the player assigns equipment,
chooses personnel, and observes combat. It must remain presentation-only:
simulation determinism and combat results cannot depend on UI or render state.
Equipment signal belongs to grade capability, while person-driven signal
belongs to the marine's profile, career, and current contribution. Avoid
stacking redundant battlefield overlays; start with the closest decision
surface and add in-world signal only when it materially improves play.

## Growth directions

The following are direction, not current behavior:

- Replace flat survivor XP with deterministic, bounded performance-derived
  awards from frozen outcomes, while preserving meaningful participation and
  learning from losses.
- Expand primary families and special-equipment options, and extend the unlock
  ladder so every authored player asset has either starter status or a
  reachable path. `stories.md` owns the concrete primary, contact-tool, stim,
  grenade, and faction-demolition additions and their ordering.
- Add world-reactive template-card acquisition through operations, patrons,
  salvage, and markets while keeping advanced progression operation-gated.
- Make grade, aptitude, experience, career, and captain traits legible in
  campaign and battle surfaces without changing simulation authority.
- Give only traits with an observable, domain-appropriate consequence a
  mechanic, and define a deliberate acquisition model before promising
  level-up rewards.

## Invariants for future work

- Aptitude is permanent; experience is earned; captain rank is a separate
  leadership progression.
- Family, delivery mechanism, grade, profile, armor role, armor pattern, and
  provenance stay composable rather than being fused into faction-specific
  unit variants.
- A new mechanism name earns a weapon entry only when its authored behavior or
  presentation supports a distinct tactical identity. Slug, gauss, pulse, and
  laser labels do not create parallel stat clones.
- Assault-armor role is not quality: a rare high-end recon suit may remain
  light, while a crude industrial battlesuit may remain heavy.
- A heavy battlesuit remains a one-person infantry billet using infantry
  weapons, cover, pathing, and casualty authority. Mech chassis, mounts,
  lances, and support delivery remain Mechs authority.
- A template card is permanent capability, a squad definition is reusable
  intent, fleet cargo pays changed incoming issue, and materialized kit belongs
  to the marine. None is a synonym for another.
- A cargo template card is consumed only by successful Armory learning. It never
  becomes player-faction ship-production knowledge.
- A billet carries at most one special item; utilities do not become
  `WeaponDef` entries merely because they share that loadout slot with guns.
- Special-equipment use policy is simulation-owned and faction-neutral;
  template cards express issue, not hidden battle orders.
- Special-equipment data selects a closed activation and AI policy; executors
  remain typed code, and presentation state never becomes simulation input.
- Live carrier art and preview carrier art resolve the same actor-local recipe;
  a UI mannequin may choose a pose but must not own a second placement table.
- Career totals are lifetime evidence; adding a per-mission history requires a
  new retention and UI commitment.
- All combat telemetry may inform balance; only identity-bound campaign rows
  may affect campaign careers or rewards.
- Attribution measures resolved outcomes, not requested damage or visual
  effects. Resolved armor and structure loss may be reported separately while
  remaining one aggregate career contribution.
- Persistent experience is awarded from a frozen campaign outcome; battle-local
  state must not become a second progression authority.
- Presentation conveys existing quality but never changes sim state.
