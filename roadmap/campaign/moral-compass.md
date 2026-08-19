# Moral compass — the silent track

**Status:** THREE SOURCE FAMILIES + FIRST DIEGETIC REACTION CODE COMPLETE
(2026-08-19). See
[`personnel/complete/captain-trait-drift.md`](personnel/complete/captain-trait-drift.md).

**Implemented:** `facfa007`, `6765eac6`, `2a1924e7`, `cf8b717b`,
`5fd8969d`, `1b2afcb4`, `946262b2`

> The hidden foundation is a locked spec; later reaction surfaces remain design
> discussion. The discipline here (never surface the numbers) is load-bearing —
> read "Design discipline" before proposing any UI for this.

A silent moral-compass track records the *character* of the player's
choices over time. The track is **never visible to the player as a
number**. It surfaces only through diegetic channels, and only when
those surfaces are narratively earned.

## Why it exists

Every other player-progression track we've designed is mechanical and
optimizable — credits, MRB rep, stake share, contract count. The moral
compass is the *one* track that resists optimization. The player can't
grind it; they can only *be themselves* across hundreds of choices, and
learn what they've been only when the world reflects it back. This is the
structural prerequisite for the Kingdom-of-Heaven-style kingmaker capstone
speech (see [`t3-endgame/overview.md`](t3-endgame/overview.md)).

## Foundation shape — locked v1

Four global signed axes live on `CampaignState`, each clamped to `-100..100`.
The names and values are internal vocabulary only; no player-facing surface may
render a number, progress bar, threshold, or `+axis` notification.

| Axis | Negative pole | Positive pole | What it means |
| --- | --- | --- | --- |
| `mercy` | ruthless | merciful | treatment of helpless people and defeated enemies |
| `integrity` | expedient | principled | willingness to keep commitments when betrayal would pay |
| `stewardship` | exploitative | protective | whether populations/resources are costs or responsibilities |
| `institutionalism` | insurgent | establishment | preference for overturning or preserving political order |

These axes are deliberately independent. Supporting a rebel claimant is not
automatically merciful, principled, or protective; defending an incumbent is
not automatically cruel. A source changes only the dimensions its persisted
facts actually establish.

## Append-only choice ledger — locked v1

Aggregate values alone are not replay-safe and cannot later explain a capstone
line. Every applied source therefore appends one immutable `moralChoices[]` row:

| Column | Meaning |
| --- | --- |
| `moralChoiceId` | Stable row identity |
| `moralChoiceSourceType` | Append-only discriminator describing the choice family/outcome |
| `moralChoiceSourceId` | Stable id in that source namespace |
| four axis deltas | Exact signed values applied to the aggregates |
| `moralChoiceHappenedTick` | Day the underlying choice/outcome became true |
| `moralChoiceRecordedTick` | Day the ledger consumed it |

`(sourceType, sourceId)` is unique. Recording checks for that pair before
mutation, clamps the four aggregates, then appends the immutable row in the same
local operation. Save/load retries and later source-table compaction therefore
cannot double-count a choice. Future source families append enum values and call
the same recorder rather than adding one-off applied flags across their tables.

Linear uniqueness scans are intentional for the foundation: moral rows are
rare, player-global, and orders of magnitude smaller than contracts or stakes.
Add a composite index only if real volume justifies it.

## First source — civil-war outcome

Only a terminal outcome whose player-reputation consequence reached `APPLIED`
qualifies. This reuses the completed attribution validation and keeps autonomous,
stale, malformed, merely accepted, failed, or abandoned work neutral.

| Successful contribution | Institutionalism delta |
| ---: | ---: |
| 1–29 | 5 toward the supported side |
| 30–59 | 10 toward the supported side |
| 60+ | 20 toward the supported side |

An applied claimant victory records `CIVIL_WAR_CLAIMANT` and a negative delta
(overturning the established order). A same-day decisive incumbent victory
records `CIVIL_WAR_INCUMBENT` and a positive delta (preserving it). Both use the
source chain id as `moralChoiceSourceId`; claimant `happenedTick` is the handoff
application day, while incumbent `happenedTick` is the failed-chain resolution
day. The other three axes receive zero because side selection alone establishes
nothing about mercy, integrity, or stewardship.

The foundation implementation is shipped: aggregate axes and ledger columns
grow/backfill safely, `MoralChoiceRecorder` owns source-pair uniqueness and
clamped actual-delta snapshots, and the daily consumer runs only after validated
civil-war player consequences. There is intentionally no debug-intel readout or
other numeric surface.

## Second source — civilian rescue

The civilian-rescue black-swan foundation establishes mercy and stewardship
from explicit persisted facts rather than from mission-state inference. An
explicit refusal records -5 mercy / -10 stewardship. A positive rescued count
records +5/+5 below half, +10/+10 from half through partial rescue, or +15/+20
for everyone saved. Integrity and institutionalism remain unchanged.

Passive expiry and committed zero-rescue outcomes are neutral. Refusal and
rescue use separate source namespaces keyed by stable event id, so daily retries
cannot double-count either fact. The player sees neither the axis names nor the
ledger consequence; future event prose may describe people and costs, never the
hidden score.

## Third source — defector asylum

The defector-asylum event adds the first integrity-bearing source family.
Refusal before a promise records -5 mercy. Keeping asylum records +20 integrity
and +10 stewardship; explicitly selling the defector back records -25 integrity
and -10 stewardship. Passive expiry before commitment is neutral, while silence
after commitment protects the defector because the promise remains in force.
All terminal variants share the source-unique `DEFECTOR_ASYLUM` namespace keyed
by event id.

## How it surfaces — diegetic only

Never as a UI number. No "Honor: 47/100" display, ever. The track exists;
the player never sees it. It leaks through:

- **Captain trait drift** — a captain may pick up `CYNICAL` after enough
  morally-questionable contracts.
- **Soldier overheard comments** in roster review ("Sgt. Hale said the
  Jangala job didn't sit right with her").
- **Captain transfers** — a captain whose values diverge enough from the
  player's pattern may put in for transfer.
- **NPC dialog gates** — some patrons refuse contracts with players whose
  compass reads "ruthless" beyond a threshold; others *prefer* them.
- **Briefing flavor shifts** — patrons of an alignment write differently to
  a player they perceive as similar (see
  [`narrative/overview.md`](narrative/overview.md)).
- **The kingmaker capstone** — the one moment the compass is *explicitly*
  surfaced: the deposed ruler's testament names what the player chose to
  become. Reserved for narrative apex.

## What feeds the compass

- Contract acceptance/refusal (refusing a fallen-noble for ethics ticks
  honor; accepting a SUSPICIOUS patron's grey contracts ticks ruthlessness
  — see [`narrative/overview.md`](narrative/overview.md) archetypes).
- Mission outcomes (collateral damage, civilians killed, surrendered
  defenders' treatment).
- [Black-swan event](events.md) responses — the densest compass-touching
  moments.
- Salvage choices (taking blueprints/weapons vs leaving them for the
  population — see [`loot/overview.md`](loot/overview.md)).
- Chain participation (running an ELEVATE_HEIR vs a SABOTAGE_PROMOTION
  shifts different axes).

## Design discipline — withholding

The discipline is *withholding*. The future temptation will be to add
"+honor" notifications on actions, or a dashboard for the player to
optimize against. **Resist.** The compass is a system for the *world* to
read, not the player. Optimization-resistance is the entire point — it's
the silent "show, don't tell" track until the big reveals. See
[[feedback-world-reactive-over-expressive]] (the compass *is* the world
reacting to player character) and
[[feedback-patron-narrative-discoverable]] (it's one of the silent threads
the player learns to read).

## Implementation surfaces

- `CampaignState`: four bounded aggregate ints plus the append-only primitive
  `moralChoices[]` ledger.
- `MoralChoiceRecorder`: the only aggregate mutation seam; source-unique and
  exactly once.
- `MoralCompassSystem` consumes attributed terminal civil-war outcomes and
  explicit terminal civilian-rescue facts after their prerequisite lifecycles.
- A `CaptainTraitDriftSystem` — the locked first reaction reads captain-local
  ledger history plus time-in-service and grants one deterministic, irreversible
  `CYNICAL` or `IDEALIST` outlook without exposing the hidden scores. See
  [`personnel/complete/captain-trait-drift.md`](personnel/complete/captain-trait-drift.md).
- NPC dialog gates — content-side, read the compass via a getter.
- Capstone scene infrastructure — the locked story contract lives in
  [`t3-endgame/stories/kingmaker-capstone.md`](t3-endgame/stories/kingmaker-capstone.md).

## Related

- [black-swan events](events.md) — highest-density compass-touching content.
- [`t3-endgame/overview.md`](t3-endgame/overview.md) — the kingmaker
  capstone reveal.
- [`narrative/overview.md`](narrative/overview.md) — patron dialog gates +
  briefing flavor the compass feeds.
