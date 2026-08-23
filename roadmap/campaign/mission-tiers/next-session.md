# Mission tiers — Next Session

## Where we are

**Slice 1 shipped 2026-08-23.** Track opened the same day from a playtest
observation: CONQUEST is late-game content by type, variance will keep
growing, and there is a beginner tier `RiskLevel` has no room for.

`OperationTier` exists and owns scale. The ladder is **the debug company's
vocabulary** — First Contract / Established / Veteran / Reinforced / Full
Strength — so a job's tier and the company that should take it are the same
words read from two sides.

Read [`overview.md`](overview.md) first — it holds the finding and the
two-axis proposal.

## The finding in one paragraph

`RiskLevel` (three constants, 106 usages, 27 files) decides map size, lift,
enemy count, enemy quality, payout, loot, requirements text, and mission
availability. Scale and variance are different questions sharing one word.
The opening ladder already had to bypass it — `OpeningOperationKind`
authored its own setup "instead of overloading generic `LOW` risk" — and
`early-operations/overview.md` already asks for the missing concept by name
("an explicit campaign career bracket").

## What shipped in slice 1

- `OperationTier` with `defenderBase`, `drops`, `squadsDemanded` per tier.
- `MissionType.tierFloor` + `defenderWeight`; **CONQUEST floors at
  REINFORCED**, so a beginner's conquest is no longer expressible.
- `RiskLevel` narrowed to variance, keeping a modest `forceMult`
  (0.85 / 1.0 / 1.15) — risk colours a tier rather than replacing it.
- `Mission.tier`, defaulted from risk and clamped to the type's floor.
- `MapScale.forTier`, `DefenderRoster.forMission(type, tier, risk)`,
  `MissionGenerator.requiredDropsFor(type, tier)`.
- Tier-aware `BattleSetup` factories for sabotage, catch-all and conquest;
  `MissionLaunch` passes `m.tier`.
- **The debug board now enumerates (type x tier)** at MEDIUM risk, which is
  the surface a force-ratio playtest actually wants.

The fifteen hand-written `(type, risk)` defender numbers are gone, replaced
by a tier curve times a type weight. The one number play had measured is
preserved: CONQUEST at FULL_STRENGTH / HIGH is **322** defenders against the
old table's 320.

## What is settled

- **CONQUEST/HIGH is correctly tuned.** 320 defenders at 40% elite against
  ~408 marines of lift is 1.25 : 1 attacking prepared positions. Needing
  200-400 marines is the design working, not a bug. Do not "fix" it.
- **Scale and risk split into two axes.** `OperationTier` owns scale and
  offering; `RiskLevel` narrows to variance.
- **A mission type declares a tier floor.** That is what "CONQUEST is a
  late-game battle" means in code.

## What is not settled

- **The tier ladder itself** — names, count, and the company size each
  implies. Authorial, and it sets campaign pacing. `overview.md` carries a
  four-tier sketch (Beginner / Standard / Major / Late) explicitly marked as
  discussion, not decision.
- Whether risk still nudges scale slightly within a tier, or is purely
  qualitative.
- Whether `RiskLevel` keeps its name once it means only variance.

## Recommended next slice

**Play first.** Every number in the tier curve is a first guess except
CONQUEST's top rung, and the debug board now enumerates tiers precisely so
they can be measured. Pair it with the C12 squad dial: set a tier, dial the
company, find the ratio.

Then, in rough order:

1. **Reward follows tier.** `computePayout` and `LootRoller` still read only
   risk, so a Full Strength job pays like a beginner's one at the same risk.
   This is the most visible remaining incoherence.
2. **Offer generation reads tier.** `MissionGenerator`'s production paths
   still derive tier from risk via the compatibility mapping, so the
   campaign never actually offers a Reinforced raid. Tier should come from
   the patron and the player's standing.
3. **Retire the bridges.** `OperationTier.forRisk`, `MapScale.forRisk` and
   the two-arg `DefenderRoster.forMission` exist so the migration did not
   have to touch twenty factory overloads at once. Deleting them is the
   marker that the split is finished — `createCivilianRescue` and
   `createSilentColony` are the two paths still on them.

## Consequence worth knowing

The C12 debug company's stage ladder (First Contract / Established /
Veteran Company / Reinforced / Full Strength) is the *same idea from the
other side* — what the player brings versus what the work demands. If the
two ladders end up with unrelated vocabularies, the briefing can never say
"this job wants a company you do not have". Worth aligning them when the
tier names are picked.
