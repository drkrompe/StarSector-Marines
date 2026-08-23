# Progression nouns

**Status:** ACTIVE

**Written:** 2026-08-23

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
same threat does not restart the delay, and sustained cadence remains a
weapon-and-handling concern. This applies to trained humanoid infantry, not
to mechs, turrets, drones, or fauna.

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

### Telemetry and career

`CombatTelemetryService` records combat evidence at the shared firing and
damage seams. It records every designated combatant for a mission's debug
table, including non-campaign units. Damage attribution is based on applied,
post-mitigation damage, rather than a requested damage value; friendly fire is
separate, and one arriving round counts once even if its explosion harms many
targets.

Telemetry survives a combatant's death transition. At mission end,
`MissionOutcome` freezes only campaign-marine rows for campaign use, while
the full report remains a balance readout. `MarineRoster.applySoldierOutcome`
uses the outcome manifest to count every deployed marine, then folds any
available telemetry into `SoldierCareer`. Thus deployment is not inferred from
having fired, and a fallen marine retains the record of their final mission.

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
- Expand weapon families, secondary options, and the unlock ladder so every
  authored player asset has either starter status or a reachable path.
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
- Career totals are lifetime evidence; adding a per-mission history requires a
  new retention and UI commitment.
- All combat telemetry may inform balance; only identity-bound campaign rows
  may affect campaign careers or rewards.
- Attribution measures resolved outcomes, not requested damage or visual
  effects.
- Presentation conveys existing quality but never changes sim state.
