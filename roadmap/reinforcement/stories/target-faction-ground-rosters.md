# Target-faction ground rosters

Status: IN PROGRESS — roster data and all standard defender creation paths are wired; merged-submod content, future equipment families, deterministic Conquest fixtures, and live faction-read acceptance remain.

Written: 2026-08-24

Updated: 2026-08-25 — added shipped shredder and squad-automatic families to faction doctrine while retaining the remaining equipment and live-acceptance work.

Read `reinforcement-nouns.md`, `campaign-battle-bridge-nouns.md`,
`progression-nouns.md`, and `powered-assault-armor-roles.md` before implementing
this story. The S2 infantry-equipment stories own the referenced weapon and
special-item behavior.

## Problem

Before the implemented backbone, `FactionUnitRoster` was keyed only by the
battle-side `Faction`. Every defender therefore received the same militia bulk
unit, red elite, and heavy-mech slot
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
| Hegemony | Disciplined armored regulars with durable elites | Standard slug rifles, gauss marksman weapons, rugged squad automatics, and limited elite pulse/laser issue; standardized line armor and utilitarian heavy battlesuits |
| Tri-Tachyon | Compact corporate-security teams with high-quality specialists | Pulse/laser-weighted primaries, precision gauss, micro-missile candidates, smoke, visible neural/HUD integration, composite light/line suits, and drone-paired specialists |
| Persean League | Balanced line infantry and flexible local regulars | Dependable slug/gauss/pulse mix, grenade and support weapons, practical light/line armor, and broad local support rather than one extreme |
| Luddic Church | Local militia stiffened by well-equipped faithful | Maintained slug/gauss arms, industrial arc cutters, legacy line suits, and artisan heavy armor among spiritually sanctioned elites; limited taboo energy issue |
| Luddic Path | Fanatical assault cells and demolition specialists | Crude slugthrowers, shredder carbines, illicit stims, carried IEDs, rare explicit martyr-rig specialists, industrial exoskeletons, and welded heavy rigs; no hidden immunity to their risks |
| Sindrian Diktat | Rigid state-security troops and selected guard formations | Mixed ballistic/energy primaries, grenade/support weapons, state-security line suits, and prestige heavy guard equipment concentrated in selected formations |
| Pirates | Irregular raiders with dangerous specialists | Scavenged slug/shredder arms, stolen energy weapons, illicit stims, carried improvised charges, civilian load frames, scrap plate, and dangerously modified heavy rigs |
| Independents | Local militia with mercenary or professional stiffening | Service slug baseline with mixed gauss, energy, breaching, and mercenary specialist gear; mixed light/line patterns and acquired heavy suits |

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
- The profile chooses explicit weapon families, mechanisms, and special-item
  ids. A “Pather” roster does not transform every marine into a martyr carrier,
  and a “Tri-Tachyon” roster does not apply an accuracy modifier when no issued
  uplink mechanic exists.
- Neural uplinks, drone integration, combat stims, and optical camouflage exist
  only where the selected pattern or composition has an implemented typed
  capability. Faction prose must not create invisible blanket modifiers.

## Acceptance

### Implemented backbone (2026-08-25)

- `GroundRosterRegistry` loads and validates eight built-in profiles covering
  the core faction ids plus Knights of Ludd, with unknown ids falling back to
  Independent.
- One immutable profile resolves from `TargetProfile.factionId()` in each
  standard battle factory and is frozen on `BattleSimulation`.
- Initial bulk/elites, heavy-support candidates, convoy passengers, shuttle
  elites, and walk-ins consume the same profile and seeded battle RNG.
- Profiles author primary, grade-by-risk, armor-by-risk, special-by-risk, and
  heavy-support weights without owning force scale, delivery, objectives, or
  AI. Armor data uses semantic ids rather than treating the current palette as
  settled faction content.
- Focused tests cover known/fallback resolution, distinct shared-roller output,
  battle-frozen initial issue, faction heavy-support cycles, and factional
  walk-in/shuttle payloads.
- The shipped shredder carbine and squad automatic now participate in those
  same weighted rolls. Hegemony, League, Church, Diktat, and Independent
  profiles issue automatic support; Path and pirate profiles retain their
  stronger close-range shredder identity without acquiring automatics by
  generic fallback.

The current catalog can only select implemented equipment. The richer cutter,
stim, IED/martyr, micro-missile, neural/drone, and powered-assault
content below remains gated by its owning progression stories. Catalog merge
discovery for submods and representative live Conquest acceptance also remain.

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
- The same fixtures visibly differ in primary and special issue: slug/gauss
  discipline, pulse/precision corporate teams, Church cutter specialists,
  rare Pather martyr/IED cells, and pirate shredder/stim demolition teams. Each
  distinction disappears when the corresponding item is not selected.
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
- Primary-family and shipped fragmentation-grenade behavior belongs to
  `progression-nouns.md`. Remaining contact-tool, stim, and demolition-item
  behavior belongs to `s2e-close-contact-boarding-tools.md`,
  `s2f-combat-stim-injectors.md`, and `s2g-martyr-rigs-and-carried-ieds.md`.
- New mech chassis or vehicle families. Existing heavy-support eligibility may
  choose only currently authored content until those owning features expand it.
