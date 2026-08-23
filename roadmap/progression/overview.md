# Progression — making the company visibly get better

> The player should be able to look at a marine after twenty missions and
> *see* that they are not the same marine who walked off the first shuttle.
> Today the numbers move and nothing else does.

## What this track is

A cross-tier feature thread covering the four progression axes that a
merc-company sub-game lives on:

1. **Lethality and equipment tiering** — battle tier. Weapons that kill,
   and upgrades that are felt rather than computed.
2. **Earned experience** — battle-to-campaign. Per-soldier performance
   tracked during a mission, converted into progression and retained as
   career history.
3. **The parts economy** — campaign tier. Where fabrication feedstock
   comes from, and how advanced gear is chased.
4. **Legibility** — UI. Aptitude, experience, traits, and career stats
   surfaced where the player makes decisions and where they watch the
   fight.

It sits between tiers deliberately, the same way
[`../campaign-battle-bridge/`](../campaign-battle-bridge/overview.md) does.
Weapon lethality is battle-side, the parts economy is campaign-side, and the
telemetry story is the seam between them. Splitting the track by tier would
lose the through-line.

## Why now

[`audit.md`](audit.md) is the measured baseline. The short version:

- End-to-end **damage** spread across the entire upgrade ladder is
  **8-13%**. Accuracy spans about 1.85x. Nothing in the ladder reads as a
  power step.
- A 25 HP marine takes roughly **20 seconds** to drop under sustained
  pulse-rifle fire. Firefights are attritional mush rather than decisive.
- The unlock ladder is **four milestones** and ends at victory five. Four
  armor patterns — including the best one in the game — are authored and
  **unreachable**.
- Parts come from **one source**: winning. No market, no loot, no salvage,
  no reward channel.
- XP is **flat per survivor**. Nothing a marine did feeds it. The in-battle
  XP entry point (`CombatService.addExperience`) exists and is unwired.
- **No career history exists at all**, which also means there is no data to
  balance against.
- `SoldierAptitude`, `ExperienceTier`, and `Trait` are all load-bearing and
  all effectively invisible. Six traits are inert enums with no UI.

## Design commitments

Locked before the stories were written; change these here, not in a story.

1. **Raise the lethality floor *and* widen the tier gap.** Both, as
   separable work. Faster time-to-kill makes cover, positioning, and the
   [`../ballistics/`](../ballistics/overview.md) work matter; a wider grade
   spread makes upgrades a reward rather than a rounding error.
2. **Parts come from five channels**: vanilla market purchase/conversion,
   battlefield loot, breakdown of recovered enemy gear, special-mission
   rewards, and victories. **Advanced/masterwork feedstock is loot-gated
   only** — chase items require a real operation, never a shopping trip.
3. **High-grade gear is visibly high-grade.** Masterwork reads at a glance
   in the field: distinct chrome, and effects where the weapon supports it.
   A player should be able to spot their best marine without opening a
   screen. See [[feedback_compose_effects_not_carrier]] — key the visual to
   the grade capability, not to a bespoke unit type.
4. **Telemetry is a first-class balance artifact, not just a reward
   input.** Per-soldier combat stats are retained so tuning is measured
   rather than guessed.
5. **Aptitude stays innate.** It is the lottery axis and does not improve;
   the fix is conveyance, not mutability.
6. **Progression is earned in the field, not bought at a menu.** Consistent
   with [[feedback_world_reactive_over_expressive]] and
   [[feedback_hard_failure_preference]] — the market is a floor, not a
   ladder.
7. **Progression is lateral as well as vertical.** Factional equipment
   identity (S6 Slice 3) is the second axis: gear has *character*, not only
   tier, so the question becomes "who do I work for and where do I fight"
   rather than "what tier am I on". Faction is a thin modifier layer over
   the weapon family, not a faction x family x grade cross product.
8. **The font floor is a type scale, not a single size.** Investigation
   (recorded in S8) found the Orbitron 20 minimum is really a floor for
   *Orbitron* — a display face used at all 224 text call sites — and that
   the mod never reads `getScreenScaleMult()`, so the floor was set in the
   wrong unit at one unstated UI scale. Display/header keep Orbitron;
   body and data use vanilla's actual text faces. Bars and icons still
   carry density before text does.

## Story decomposition

Ordered by dependency, not necessarily by ship order. Each links to its doc
in [`stories/`](stories/).

| Story | Theme | Depends on |
| --- | --- | --- |
| [S1 — Lethality floor and tier spread](stories/s1-lethality-and-tier-spread.md) | 1 | — |
| [S2 — Weapon catalog expansion](stories/s2-weapon-catalog-expansion.md) | 1 | S1 |
| S3 — Per-soldier combat telemetry (`s3-per-soldier-telemetry.md`) | 2 | — |
| [S4 — Performance-derived experience](stories/s4-performance-derived-experience.md) | 2 | S3 |
| [S5 — Parts acquisition channels](stories/s5-parts-acquisition-channels.md) | 3 | — |
| [S6 — Unlock ladder expansion](stories/s6-unlock-ladder-expansion.md) | 3 | S5, S2 |
| [S7 — Grade visual identity](stories/s7-grade-visual-identity.md) | 1, 4 | S1 |
| [S8 — Roster legibility: aptitude, career, traits](stories/s8-roster-legibility.md) | 4 | S3 |
| [S9 — In-battle quality conveyance](stories/s9-in-battle-quality-conveyance.md) | 4 | S3 |
| [S10 — Trait mechanics](stories/s10-trait-mechanics.md) | 4 | S8 |

### Suggested ship order

**S1 first.** It is self-contained, needs no new systems, and is the single
change that most alters how the game feels. Everything else is a reward
layered on combat that is worth having rewards for.

Then **S3** — it unblocks S4, S8, and S9, and it starts accumulating the
balance data that S1's tuning pass will want anyway.

Then **S5 → S6** as a pair (an economy with nowhere to spend, or a ladder
with no income, is half a feature), and **S2** alongside to give S6
something to unlock.

**S7, S8, S9** are the legibility sweep and can land in any order once S3
is in. **S10** is last: traits want a UI to be read in before they get
mechanics worth reading.

## Relationship to other tracks

- [`../ballistics/`](../ballistics/overview.md) owns the *resolution
  mechanism* — how a round flies, what it hits, what stops it. This track
  owns the *catalog and the tiering* on top of it. S1 and S2 assume S1-S4a
  ballistics semantics and should not reopen them.
- [`../campaign/personnel/`](../campaign/personnel/overview.md) owns the
  personnel *lifecycle* — identity, fireteams, casualties, recovery,
  stationing, captain command. This track adds progression *on top of* those
  identities and must not fork the roster authority. `MarineRoster` stays
  the single owner.
- [`../campaign/loot/`](../campaign/loot/overview.md) owns the salvage
  manifest and settlement. S5 consumes it as a parts channel rather than
  building a parallel drop system.
- [`../command-powers/`](../command-powers/overview.md) has its own
  meta-progression spine (S5, command-point budget). That is the *player
  agency* ladder; this is the *troop quality* ladder. They should stay
  distinct and are allowed to reference each other.

## How this directory is laid out

- **`overview.md`** (this file) — concept, commitments, decomposition.
- **`audit.md`** — the measured pre-work baseline. Historical once work
  lands; keep it, do not edit it to match new reality.
- **`stories/`** — active story docs.
- **`complete/`** — shipped stories with commit hashes and
  landed-vs-planned notes.
- **`next-session.md`** — cold-start handoff.
