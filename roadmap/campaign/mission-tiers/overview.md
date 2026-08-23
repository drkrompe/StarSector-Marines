# Mission tiers — separating scale from risk

> `RiskLevel` says LOW, MEDIUM, HIGH. It decides map size, enemy count,
> enemy quality, lift, payout, loot, and which missions exist. A "LOW risk
> conquest" is thirty-six defenders; a HIGH one is three hundred and twenty.
> One word is carrying an entire difficulty model, and it has run out.

**Status:** contracted 2026-08-23, from a playtest observation — CONQUEST is
late-game content by *type*, variance is going to keep growing, and there is
a beginner tier the enum has no room for. **Slice 1 (the tier and its scale
duties) shipped the same day.**

## The finding

`RiskLevel` has three constants and 106 usages across 27 files. It is doing
at least five separate jobs:

| Job | Where |
| --- | --- |
| **Scale** — map size, lift, enemy count | `MapScale.forRisk`, `MissionGenerator.requiredDropsFor`, `DefenderRoster.totalFor` |
| **Quality** — enemy gear and experience | `InfantryLoadoutRolls`, `DefenderRoster.eliteRatioFor` |
| **Reward** — payout, loot, AI cores | `LootRoller`, `AiCoreLootRules` |
| **Presentation** — colour, requirements text | `RiskLevel.color`, `MissionGenerator.requirementsFor` |
| **Gating** — what work is offered at all | `OpeningOperationStory` |

Scale and risk are different questions. *How big is this operation* and
*how likely is it to go wrong* have been the same number since there was
only ever one kind of mission.

### It has already been worked around once

From `early-operations/overview.md`, describing the opening ladder:

> `OpeningOperationKind` selects an authored setup **instead of overloading
> generic `LOW` risk**.

The first two missions in the game could not be expressed as LOW, so they
bypassed the axis entirely and authored their own setup. That is the
beginner tier, already built, wearing a disguise. The same doc's follow-ups
ask for exactly the missing concept:

> Replace the fixed green-company thresholds with an explicit **campaign
> career bracket** if the broader contract layer gains one.

### CONQUEST/HIGH is not mistuned

Worth stating plainly, because the opposite was suggested while chasing the
400-marine question. The numbers:

| | CONQUEST/HIGH |
| --- | ---: |
| Defenders (`DefenderRoster.totalFor`) | **320** |
| Elite fraction | 40% |
| Required drops / seats | 40 / 480 |
| Player marines at full lift | ~408 |

That is roughly **1.25 : 1 attacking prepared positions** — against a
doctrinal 3 : 1, which the lift ceiling could not supply even in principle.
The player is expected to close that gap with quality, mechs, air and
command powers rather than numbers. "Takes 200-400 marines" is the design
working. The problem was never the number; it is that the number sits on an
axis that also has to describe a beginner's first job.

And `CONQUEST` at LOW is 36 defenders — the same mission type, same name,
one ninth the fight. That range is the incoherence.

## Proposal — two axes

**`OperationTier`** — where on the campaign arc this work sits. Owns
**scale**: map size, required drops, defender count, payout band, and
whether a mission type is offered at all.

**`RiskLevel`** — how much this particular job can deviate from its tier's
expectation. Owns **variance**: defender quality rolls, elite fraction,
intel reliability, reinforcement budget, loot quality.

A mission is then *(type, tier, risk)*. `CONQUEST` declares a tier floor and
simply does not appear below it, which is what "CONQUEST is a late-game
battle" means in code. A beginner job and a late-game siege stop competing
for the same three words, and variance can grow richer — more risk
constants, per-axis rolls, unreliable intel — without moving scale a
millimetre.

### What this buys immediately

- The opening ladder stops being an authored bypass and becomes tier
  `BEGINNER`, which the ordinary generator can serve.
- `DefenderRoster.totalFor`'s `switch (type) { switch (risk) }` — fifteen
  hand-written numbers that already encode tier *inside* type — becomes a
  tier curve with a risk multiplier.
- The C12 debug company's stage ladder and the mission tier become the same
  vocabulary, so "field the company that should be taking this job" is a
  question the briefing can answer.
- `c13-the-task-force.md`'s command scaling gets a natural pacing partner:
  tier says how many squads the work wants, rank says how many one officer
  leads.

## The ladder — decided 2026-08-23

**It is the debug company's vocabulary**, so a job's tier and the company
that should take it are the same words read from two sides. A briefing can
say "this wants a Reinforced company and you field Established" instead of
leaving the player to infer it from a colour.

| Tier | Defenders (MEDIUM) | Drops | Squads demanded | Map |
| --- | ---: | ---: | ---: | --- |
| First Contract | 14 | 3 | 1 | SMALL |
| Established | 44 | 8 | 6 | MEDIUM |
| Veteran | 105 | 18 | 10 | MEDIUM |
| Reinforced | 175 | 28 | 17 | LARGE |
| Full Strength | 280 | 40 | 34 | LARGE |

Those are the base curve; a mission type scales it by `defenderWeight`
(CONQUEST 1.00, ASSAULT 0.75, EXTRACTION 0.65, RAID 0.60, SABOTAGE 0.45) and
risk nudges it by `forceMult` (0.85 / 1.00 / 1.15). CONQUEST at Full
Strength / HIGH lands on **322** — the old table's 320, preserved so the
measurement play already made still means something.

Mission tier still owns the base infantry force, but high-impact defender
units also pass through a battle-start force score. Attacker infantry seats
include employer and allied waves; defender militia, regulars, mech chassis,
and static turret emplacements consume one opposing score budget. This is
intentionally not whole-encounter rubber-banding: committing more lift does not
add infantry defenders or reroll the map. It only allows otherwise eligible
heavy-support candidates to remain when the combined attack has enough force
to answer them. Allied and employer fighter sorties add to the attacking score;
enemy fighter wings consume defender score after the roster's mech candidates,
with static turrets then keeping a deterministic prefix from what remains.
Conventional strafing carries a fixed per-sortie cost, so its attrition is
significant against a small force but proportionally fades in a large battle.
Missile profiles carry an AoE premium because they remain meaningful against
large clustered forces. Wings are indivisible commitments: the whole scheduled
wing fits or it stays out. Balanced-out guns leave their fortification geometry
behind, while a post with no live guns is not linked to a Conquest guard squad.
The score table is provisional; equipment, command powers, terrain, and
telemetry-derived values remain future inputs.

## Scope boundary

This is a **model** change, not a content one. It does not retune any
mission; it gives the existing numbers a second axis to sit on so they can
be retuned honestly afterward. The migration is mechanical but wide (27
files), so it wants slicing — the tier enum and its scale duties first,
consumers moved one subsystem at a time, `RiskLevel` narrowed to variance
last.

## Cross-references

- `early-operations/overview.md` — the beginner tier as already built, and
  the "career bracket" follow-up this answers.
- `c12-the-debug-company.md` — the stage ladder that should share this
  vocabulary.
- `c13-the-task-force.md` — command scale, the other half of "how big can
  an operation be".
- `contracts/overview.md` — offer generation, which will read tier when
  deciding what a patron may offer.
