# Faction lore guide

Status: ACTIVE — canonical reference for translating Starsector faction identity into Marine Operations content.

Written: 2026-08-26

Updated: 2026-08-26 — linked shipped equipment provenance and faction availability to the faction guide catalog.

## Purpose

Use this guide before authoring faction-shaped weapons, armor, units, facilities,
missions, dialogue, or command behavior. It is an interpretation boundary, not a
parallel rules system: faction identity selects authored content and bounded
preferences, while the owning feature documents still define mechanics.

`equipment-lore-catalog.md` records the shipped in-universe models, their mod-authored
origins, credible circulation, and deliberate faction absences.

The guide catalog covers the human factions most likely to own a market, hire the
player, or field ground forces, plus Remnants as a distinct non-human adversary.
Lion's Guard is treated inside the Sindrian Diktat guide because it is an elite
institution of that polity rather than a general fallback faction.

## Evidence tiers

Every faction claim belongs to one of three tiers:

- **Canon anchor** — stated directly by Starsector 0.98a-RC8 faction, campaign,
  commodity, hull, weapon, or location text. Installed game data is the primary
  source because it matches the version this mod targets.
- **Strong inference** — a conservative translation of repeated canon signals,
  including fleet doctrine, known equipment, industry, law, institutions, and
  authored behavior. It should remain true even if no infantry codex entry exists.
- **Mod direction** — a deliberate Marine Operations interpretation where vanilla
  is silent. It may be changed for play, clarity, or production needs without
  claiming Starsector canon changed.

Do not promote community summaries, remembered dialogue, generated prose, or a
single evocative item description into canon without checking the installed game.
When sources conflict, prefer explicit current-version text over old mechanics or
fandom convention. Ambiguity is useful setting texture; preserve it rather than
inventing a definitive answer.

## Guide catalog

| Vanilla identity | Ground-combat reference |
| --- | --- |
| `hegemony` | `hegemony-lore-guide.md` |
| `tritachyon` | `tri-tachyon-lore-guide.md` |
| `persean` | `persean-league-lore-guide.md` |
| `luddic_church` | `luddic-church-lore-guide.md` |
| `knights_of_ludd` | `knights-of-ludd-lore-guide.md` |
| `luddic_path` | `luddic-path-lore-guide.md` |
| `sindrian_diktat`, `lions_guard` | `sindrian-diktat-lore-guide.md` |
| `pirates` | `pirates-lore-guide.md` |
| `independent`, `mercenary` | `independents-lore-guide.md` |
| `remnant` | `remnants-lore-guide.md` |

Unknown or modded factions use the Independent gameplay fallback unless merged
content supplies a profile. That technical fallback does not assert that every
unknown society shares Independent culture.

## Shared setting laws

### Orbit matters, but does not erase the ground game

Fleet control creates access, isolation, reconnaissance, transport, and fire-support
advantages. It does not make every surface action a consequence-free bombardment.
Hardened batteries, missile silos, bunkers, shields, dense population, buried
infrastructure, valuable machinery, uncertain target identification, political
legitimacy, and the client's need to possess the objective afterward all create
credible reasons to send ground forces.

Maps should make those reasons tangible. A shield generator, fire-control center,
anti-orbital emplacement, hardened command bunker, power nexus, or hostage-adjacent
industrial site is not scenery when its state explains what support is safe or what
the campaign can do next. `target-faction-facility-treatment.md` owns geometry and
`command-powers-nouns.md` owns support execution.

### Destruction is a political choice

The owner of a functioning world normally values its people, productive base,
administrative records, defenses, and future tax or supply output. Even an
authoritarian or ruthless patron usually prefers seizure, restoration, intimidation,
or deniable sabotage to indiscriminate ruin on its own territory. Destruction
contracts therefore require a concrete motive: denial during retreat, an enemy or
rebel asset, taboo technology, punitive terror, evidence removal, or a target whose
continued operation is worse than its loss.

Faction affects which motives are credible and how often they appear; it never grants
an always-on license to destroy infrastructure. `faction-ground-contract-policy.md`
owns offer eligibility and collateral posture.

### Technology is fragmented, not neatly tiered

All major factions inherit Domain technology, salvage, trade, captured equipment,
and local manufacture. Their identity is expressed through procurement preference,
maintenance culture, concentration of scarce gear, training, and the jobs equipment
is used for—not exclusive colored arsenals.

The mod's slug, gauss, pulse/laser, shredder, micro-missile, breaching-tool, combat-
stim, neural-uplink, and demolition vocabularies are setting translations. A faction
guide may weight or contextualize them, but `progression-nouns.md` and its S2 stories
own their actual behavior. Powered armor follows the same law: role and pattern are
composable, and `powered-assault-armor-roles.md` owns mechanics.

### Faction flavor is not a hidden bonus

A Tri-Tachyon marine is not universally more accurate, a Hegemony marine is not
universally tougher, and a Pather is not automatically a suicide bomber. An effect
exists only when an issued item, unit definition, command profile, facility, or
mission rule names it. Faction profiles control availability and preference through
the authorities defined by `target-faction-ground-rosters.md`,
`target-faction-command-doctrine.md`, and `target-faction-facility-treatment.md`.

## Implementation lookup

When adding factional content, decide in this order:

1. Identify the faction's institutional motive and the local world's interests.
2. Choose the operation outcome: seize, preserve, extract, disable, expose, rescue,
   punish, or destroy.
3. Choose an existing mechanical role for every unit, item, and structure.
4. Apply faction procurement, visual, naming, and tactical preferences from its guide.
5. Record any new mechanical behavior in the owning noun document or a discrete story.
6. Check the guide's anti-caricature rules before finalizing copy or balance.

For a new or modded faction, write the same three evidence layers. Do not begin with a
weapon list; begin with who holds authority, what they can sustain, what they fear,
and what they need the battlefield to look like after victory.

## Source spine

The primary source set is the installed Starsector 0.98a-RC8 data under
`starsector-core/data`:

- `strings/descriptions.csv` — faction, market, ship, weapon, and world prose;
- `world/factions/*.faction` — current doctrine, equipment access, law, ranks, and
  behavioral flags;
- `campaign/rules.csv` — authored encounters, commissions, institutional voice, and
  quest context;
- campaign economy and world-generation data — industries, defenses, market
  conditions, and faction ownership.

External references are supporting navigation only. The community-maintained
Starsector Wiki is useful for locating current game facts, but a guide should cite the
  installed row or file when the distinction matters.
