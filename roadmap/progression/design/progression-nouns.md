# Progression nouns

Status: ACTIVE — 11 open stories; reusable contact-demolition satchels are shipped
Written: 2026-08-23
Updated: 2026-08-23 — shipped reusable contact-demolition satchels.

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
- **Equipment family** — the weapon's tactical identity: firing pattern,
  baseline behavior, and family presentation. A family is not a quality tier.
- **Equipment grade** — the four-step manufacturing/condition quality axis:
  Surplus, Service, Milspec, Masterwork. Grade composes with family and profile;
  it does not create a separate unit type.
- **Special equipment** — one optional billet item carried alongside the
  marine's primary. Rocket launchers, anti-materiel rifles, and fragmentation
  grenades activate weapon definitions; smoke and satchel charges activate
  utility behavior. “Secondary” remains a transitional code/catalog name, not
  the enduring player-facing category.
- **Armor pattern** — a player-owned infantry protection and appearance package.
  Unlike grade, it changes survivability and movement tradeoffs as its own kit
  choice.
- **Armory** — the persistent campaign inventory of permanent recipes, finite
  printed gear, and fabrication resources. Recipes grant permission; stock is
  what may be allocated.
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

The standing law is **family supplies role; grade supplies quality; profile
supplies person**. New content must preserve this separation. A high-grade
weapon is still its family, and a skilled soldier does not become a bespoke
carrier type.

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

The armory has two layers: durable recipe unlocks and finite printed stock.
Allocation must respect stock, except basic field-rifle service issue. The
current armory has one fabrication-material currency and currently receives
that material through victory recording. Its existing victory milestones
unlock only a short primary-weapon ladder; the broader parts channels,
two-currency split, recipe recovery, and full asset reachability are planned.

Armor patterns are authored player kit with distinct defensive and mobility
tradeoffs. Some authored patterns are not presently reachable by the live
unlock ladder; their existence is not evidence of a shipped acquisition path.
The pattern owns the deployed armor pool, rating, movement modifier, and
incoming-accuracy tradeoff; `combat-durability-nouns.md` owns how battle damage
removes that armor and exposed structure. Structure remains the platform's base
pool; armor no longer adds health or applies a permanent damage-reduction
multiplier after it breaks.

Each billet has at most one special-equipment slot. The item is a stable
loadout identity with a typed activation: weapon-like specials reference the
weapon catalog that owns their round, while utility specials own their battle
action without becoming zero-damage weapons. Progression owns recipe, stock,
assignment, fabrication value, and reachability; battle AI owns when legal
issued equipment is used. The same use policy is faction-neutral even when
campaign availability differs by faction.

The first four built-in identities are the rocket launcher, anti-materiel
rifle, Wayfarer smoke grenades, and Breachhand mag-clamp satchel. The first two
are direct-fire activations, while smoke and satchels are utility activations,
but only the item definition owns
loadout identity, initial ammunition, Armory art, and activation type; the
referenced weapon definition owns range, damage, accuracy, impact, projectile,
and audio behavior. Persisted marines, billets, stock, and recipes use the
stable `special.*` id. `MarineSecondary` remains a transitional battle handle
and legacy-save input, not a second stat catalogue.

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
Player issue includes two physical kits and a Breach template. Physical Armory
stock limits equipped billets; it does not count battle placements. Ground
placement, infantry targeting, traps, disarming, and wall breaching are not
part of this shipped identity.

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
2. The armory unlocks and prints gear; the roster allocates finite stock to
   marines.
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

## Presentation law

Progression information must be readable where the player assigns equipment,
chooses personnel, and observes combat. It must remain presentation-only:
simulation determinism and combat results cannot depend on UI or render state.
Equipment signal belongs to grade capability, while person-driven signal
belongs to the marine's profile, career, and current contribution. Avoid
stacking redundant battlefield overlays; start with the closest decision
surface and add in-world signal only when it materially improves play.

## Planned direction

The following are direction, not current behavior:

- Replace flat survivor XP with deterministic, bounded performance-derived
  awards from frozen outcomes, while preserving meaningful participation and
  learning from losses.
- Expand primary families and special-equipment options, and extend the unlock
  ladder so every authored player asset has either starter status or a
  reachable path. The anti-materiel rifle is shipped; the next planned
  addition is fragmentation grenades; AMR, smoke, and satchels are shipped.
- Split common printable feedstock from operation-earned advanced components;
  advanced progression remains operation-gated rather than purchasable.
- Make grade, aptitude, experience, career, and captain traits legible in
  campaign and battle surfaces without changing simulation authority.
- Give only traits with an observable, domain-appropriate consequence a
  mechanic, and define a deliberate acquisition model before promising
  level-up rewards.

## Invariants for future work

- Aptitude is permanent; experience is earned; captain rank is a separate
  leadership progression.
- Family, grade, profile, and armor stay composable rather than being fused
  into special unit variants.
- A recipe is not stock, and an authored item is not necessarily obtainable.
- A billet carries at most one special item; utilities do not become
  `WeaponDef` entries merely because they share that loadout slot with guns.
- Special-equipment use policy is simulation-owned and faction-neutral;
  template cards express issue, not hidden battle orders.
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
