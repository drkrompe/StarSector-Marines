# Faction-ground contract policy

Status: PLANNED — follows accepted-offer lifecycle repair and sparse persisted supply, and should coordinate with tiered production offers.

Written: 2026-08-24

Updated: 2026-09-01 — made the industry catalog an upstream persisted-offer input rather than a planet-local board.

Read `contracts-nouns.md`, `themes.md`, `mission-tier-nouns.md`, and
`faction-lore-nouns.md` before implementing this story.

## Problem

Production offer selection currently chooses a contract type from patron rank
and random weights before it knows whether the target, asset, and requested
effect make sense together. The faction-direct industry board likewise chooses
from every archetype attached to a present industry. This can make a planet's
owner appear to commission routine destruction of its own refinery, port, or
military infrastructure, and it makes factions differ mostly in names and
colors rather than in the work they authorize.

Mission type is also carrying too much implied meaning. RAID, SABOTAGE,
ASSAULT, and CONQUEST describe tactical shape, but they do not say whether the
client wants a facility defended, captured intact, selectively disabled, or
destroyed.

## Goal

Generate production work from an admissible combination of employer, target,
real target asset, and objective mandate, then use faction identity and patron
flavor to weight and describe that valid pool.

The policy order is:

1. resolve employer, target owner, their relationship, and the real target
   asset or protected location;
2. enumerate mandates that make sense for that relationship and asset;
3. apply operation-tier and contract eligibility gates;
4. weight the remaining candidates by faction and patron flavor; and
5. persist the selected mandate and collateral policy as accepted terms.

Flavor never rescues an inadmissible candidate. A faction profile may make a
kind of work common, rare, or absent from ordinary generation, but an authored
story or political chain must state why it overrides the standard matrix.

## Objective mandates

The first closed vocabulary is:

| Mandate | Required result | Typical use |
| --- | --- | --- |
| DEFEND | Named asset remains operational and under friendly control | Relief, garrison response, infrastructure protection |
| CAPTURE_INTACT | Asset changes hands without being destroyed | Depots, ports, command sites, shield relays |
| RECOVER | Named people, data, or materiel leave through the mission's extraction boundary | Defectors, datacores, schematics, specialist equipment |
| DISABLE | Named capability becomes inoperable; preservation is optional unless collateral terms say otherwise | Fire control, communications, production interruption |
| DESTROY | Named asset is physically denied | Irrecoverable weapons, containment failures, explicit strategic denial |

Collateral policy is a separate accepted term: PROTECTED_INFRASTRUCTURE,
LIMITED_DAMAGE, or UNRESTRICTED. It constrains what the employer accepts beyond
the named objective; it is not a hidden morality score or a generic structural
damage quota.

## Relationship and asset fit

- Employer-owned assets normally produce DEFEND, CAPTURE_INTACT after hostile
  seizure, RECOVER, or clearance work. DESTROY requires an authored denial,
  contamination, or containment reason.
- Allied or neutral assets require authorization, protection, or an explicitly
  deniable political source. Routine random sabotage is not admissible.
- Rival-owned assets may produce seizure, recovery, disruption, or destruction,
  but productive infrastructure should still prefer capture or precise disable
  unless the employer's strategic aim is denial.
- Pirate, rebel, or decivilized control may produce reclamation, hostage/data
  recovery, clearance, or denial when recovery is impossible.
- A mission candidate must name an asset that exists at the selected market.
  Generic flavor cannot invent a refinery, shield, battery, vault, or port.

## Core-faction direction

Implement this as mergeable data keyed by vanilla faction id with an honest
Independent baseline for unknown or modded factions. The first catalog covers
every core faction:

| Faction | Common ground-contract motives | Destruction posture |
| --- | --- | --- |
| Hegemony | Recapture, secure, suppress, inspect, seize prohibited technology | Deliberate denial of military or prohibited targets; preserves governed infrastructure |
| Tri-Tachyon | Prototype/data recovery, deniable seizure, precise disruption, corporate security | Targeted against rival capability; strongly protects owned productive assets |
| Persean League | Relief, territorial defense, port security, rival military disruption | Selective and politically bounded rather than routine civic destruction |
| Luddic Church | Defend settlements, agriculture, shrines, and people; recover communities | Focused on condemned technological threats, not indiscriminate local ruin |
| Luddic Path | Raids, demolition, and technological denial | High weight against industrial and advanced military targets |
| Sindrian Diktat | Protect fuel infrastructure, restore control, seize logistics, punish disruption | Harsh but state-directed; owned fuel capacity remains strategically valuable |
| Pirates | Theft, ransom, plunder, intimidation, opportunistic seizure | Destruction is retaliation or denial after profit, not the default prize |
| Independents | Relief, reclamation, escort, anti-bandit work, infrastructure defense | Rare without a concrete local survival reason |

These are weights and authored motives, not absolute prohibitions. House flavor
may sharpen the presentation inside a faction but must not become a second
mission-policy axis with contradictory eligibility.

## Implementation boundary

- Replace type-first random selection in standard production offers with one
  deterministic candidate policy shared by generation and acceptance.
- Extend the industry-mission content schema so faction-direct candidates state
  mandate, collateral policy, and relationship gates rather than exposing every
  destructive archetype to every client.
- Consume those industry candidates while minting the geographically bounded
  persisted offer. Do not restore a client-side industry enumeration path or
  create work when the player opens a market.
- Persist the accepted mandate and policy through `Mission`, briefing, launch,
  and outcome. Legacy offers receive an explicit compatibility default based on
  their frozen contract/mission type; production generation must not use that
  default as policy.
- The owning operation freezes one objective result per named asset at its
  terminal boundary: PRESERVED, CAPTURED_INTACT, DISABLED_INTACT, DESTROYED, or
  NOT_SECURED. Contract settlement evaluates that immutable result against the
  accepted mandate, and `intact-installation-recovery.md` may consume the same
  fact without querying the ended battle.
- Briefing presents orbit/support constraints, mandate, and collateral policy
  before launch. It does not infer them from flavor prose.
- Battle mission owners decide how their objective satisfies the mandate.
  Contracts consume the frozen result; they do not implement capture,
  demolition, extraction, or structural damage.

## Acceptance

- An owning faction does not generate ordinary sabotage or destruction of its
  own intact productive/civic infrastructure.
- Each core faction produces a recognizably different distribution of valid
  work across representative owned, allied, rival, pirate-held, and decivilized
  targets, with deterministic fixtures for the same day and patron.
- Unknown/modded factions use the documented Independent baseline and never
  fail generation because no flavor profile exists.
- Every production mission names an asset present at its target and carries one
  explicit mandate and collateral policy through save/load and launch.
- Replaying resolution cannot change or duplicate a named asset's frozen
  objective result, contract settlement, or downstream recovery input.
- Generation and acceptance call the same admissibility policy; a stale or
  edited offer cannot bypass relationship, asset, tier, or mandate validation.
- Briefing distinguishes tactical mission type from the employer's required
  disposition, including why bombardment is unavailable or contractually
  unacceptable when applicable.

## Out of scope

- Faction-specific defender kit and composition —
  `target-faction-ground-rosters.md`.
- Faction-specific tactical decisions — `target-faction-command-doctrine.md`.
- Facility geometry or fire-support denial —
  `hard-installation-first-map-feature.md` and
  `shielded-fire-support-zones.md`.
- Moral-compass writeback for violating terms. A later story must name an
  explicit attributable choice and exactly-once source before conduct becomes a
  moral fact.
