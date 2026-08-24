# Target-faction ground rosters

Status: PLANNED — requires the target profile to be the sole target-market read at launch.

Written: 2026-08-24

Updated: 2026-08-24 — replaced generic armor treatment with shared powered-armor roles and concrete faction issue.

Read `reinforcement-nouns.md`, `campaign-battle-bridge-nouns.md`,
`progression-nouns.md`, and `powered-assault-armor-roles.md` before implementing
this story.

## Problem

`FactionUnitRoster` is keyed by the battle-side `Faction`. Every defender
therefore receives the same militia bulk unit, red elite, and heavy-mech slot
regardless of whether the target belongs to the Hegemony, Tri-Tachyon, the
League, a Luddic faction, the Diktat, pirates, or Independents. Initial
defenders and reinforcement payloads share that generic answer, so faction
identity carried by `TargetProfile` has no ground-force consumer.

## Goal

Resolve one immutable **ground roster profile** from the target's vanilla
faction id at mission launch and use it for every defender creation path:
initial forces, authored garrisons, convoy passengers, shuttle payloads,
walk-ins, and eligible heavy support.

The profile chooses thematic bulk/elite identities, equipment family and grade
weights, concrete armor-pattern weights from `powered-assault-armor-roles.md`,
special-equipment availability, and optional heavy support. It does not choose
force count, risk, tier, reinforcement tickets, delivery means, mission
objective, or AI behavior.

## Core-faction catalog

The first mergeable catalog covers all core factions and an explicit fallback:

| Faction | Bulk and elite character | Equipment/composition direction |
| --- | --- | --- |
| Hegemony | Disciplined armored regulars with durable elites | Rugged ballistic families, dependable grades, standardized line armor, and utilitarian heavy battlesuits; light recon remains specialist issue |
| Tri-Tachyon | Compact corporate-security teams with high-quality specialists | Energy/high-tech side-grades, smoke and precision tools, composite light/line suits, and drone-paired specialists; compact identity without changing tier-authored count |
| Persean League | Balanced line infantry and flexible local regulars | Midline family mix, dependable service/milspec issue, broad support rather than one extreme |
| Luddic Church | Local militia stiffened by well-equipped faithful | Rugged low-tech weapons, maintained legacy line suits, and artisan heavy armor concentrated among spiritually sanctioned elites; limited taboo equipment |
| Luddic Path | Fanatical assault cells and demolition specialists | Crude/high-output weapons, explosives, industrial exoskeletons, welded light/line plate, and rare brutal heavy rigs; no hidden immunity to their risks |
| Sindrian Diktat | Rigid state-security troops and selected guard formations | Mixed ballistic/energy issue, state-security line suits, prestige heavy guard armor, and fuel-state industrial treatment |
| Pirates | Irregular raiders with dangerous specialists | Scavenged family mix, broad grade variance, stolen recon suits, civilian load frames, scrap plate, and dangerously modified heavy rigs |
| Independents | Local militia with mercenary or professional stiffening | Existing neutral baseline with mixed light/line patterns, acquired heavy suits, and the widest compatibility fallback |

Unknown and modded factions use the Independent profile unless merged content
declares their own entry. Missing art or equipment references fail validation
loudly; an unknown faction id itself does not fail the battle.

## Data and authority

- Use stable vanilla faction ids only at the campaign boundary. Battle code
  receives a plain ground-roster identity/profile and never looks up a campaign
  faction object.
- Keep faction treatment compositional: unit tier remains bulk/elite, equipment
  family remains role, grade remains quality, armor remains defense, and profile
  weights choose among those existing authorities. Do not create a full
  faction x weapon x grade enum cross-product.
- Armor selection chooses concrete patterns whose light, line, or heavy role
  remains distinct from encounter tier. A high-risk profile may admit more
  heavy suits, but it may also admit high-end light specialists; risk does not
  mechanically promote every defender into the next armor weight.
- Player marines continue to use their persisted individual kits. The target
  faction profile is defender content, not a way to overwrite player stock or
  templates.
- Reinforcement requests carry the resolved defender roster identity or read it
  from immutable battle setup. A means must not re-resolve the campaign faction
  or substitute its own payload theme.
- Special-equipment activation remains faction-neutral. The profile controls
  availability, never a hidden faction-only execution rule.
- Neural uplinks, drone integration, combat stims, and optical camouflage exist
  only where the selected pattern or composition has an implemented typed
  capability. Faction prose must not create invisible blanket modifiers.

## Acceptance

- Initial defenders, compound garrisons, convoy passengers, shuttle elites, and
  walk-ins all use the same frozen target-faction profile in one representative
  Conquest battle per core faction.
- Each core profile is observably distinct in at least composition, equipment,
  or protection without changing the operation tier's force count or the
  reinforcement means ladder.
- Hegemony, Tri-Tachyon, Church, Path, and pirate fixtures demonstrate their
  distinct suit-role weights and concrete patterns; every selected heavy
  battlesuit still enters the ordinary infantry lifecycle rather than the mech
  or vehicle roster.
- Unknown/modded factions produce the documented Independent baseline and can
  override it through merged data without Java changes.
- Risk and target hardening still decide whether elite/heavy candidates are
  admitted. A faction catalog entry cannot bypass encounter support gates.
- Deterministic fixtures prove identical launch facts produce identical roster
  selections and that no delivery path falls back to the old generic defender
  roster mid-battle.
- Live acceptance confirms the profiles read as faction character rather than
  simple vertical power tiers.

## Out of scope

- Faction mission eligibility and motives —
  `faction-ground-contract-policy.md`.
- Faction tactical decision-making — `target-faction-command-doctrine.md`.
- Faction facility geometry — `target-faction-facility-treatment.md`.
- Player schematic acquisition and factional recipe provenance —
  `s6-unlock-ladder-expansion.md`.
- Armor-role mechanics and concrete pattern catalog —
  `powered-assault-armor-roles.md`.
- New mech chassis or vehicle families. Existing heavy-support eligibility may
  choose only currently authored content until those owning features expand it.
