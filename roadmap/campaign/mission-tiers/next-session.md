# Mission tiers — Next Session

## Where we are

**Design stage. Nothing implemented.** Track opened 2026-08-23 from a
playtest observation: CONQUEST is late-game content by type, variance will
keep growing, and there is a beginner tier `RiskLevel` has no room for.

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

## Recommended first slice

The tier enum plus its **scale** duties only — `MapScale.forRisk` →
`forTier`, `requiredDropsFor(type, risk)` → `(type, tier)`,
`DefenderRoster.totalFor` → a tier curve with a risk multiplier. Leave
reward, presentation and gating on `RiskLevel` until the scale half is
proven in play. The migration is mechanical but wide, so one subsystem per
slice, and `RiskLevel` narrows to variance last.

Do not start before the ladder is chosen — every table in the first slice is
indexed by it.

## Consequence worth knowing

The C12 debug company's stage ladder (First Contract / Established /
Veteran Company / Reinforced / Full Strength) is the *same idea from the
other side* — what the player brings versus what the work demands. If the
two ladders end up with unrelated vocabularies, the briefing can never say
"this job wants a company you do not have". Worth aligning them when the
tier names are picked.
