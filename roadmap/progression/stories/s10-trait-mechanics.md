# S10 — Trait mechanics

> Six of eleven traits are enums that do nothing. The Javadoc promises
> level-up trait rewards that no code path grants.

Status: PLANNED — follows `s8-roster-legibility.md` so traits are readable first.
Written: 2026-08-22
Updated: 2026-08-23 — migrated dependencies and references to stable slugs.

## Problem

`Trait` has eleven values. Four do anything:

| Trait | Effect | Site |
| --- | --- | --- |
| `NATURAL_LEADER` | x1.5 captain XP | `MissionResolver:243` |
| `SALVAGE_EXPERT` | loot recovery bonus | `LootRecoveryModifiers:24` |
| `FIELD_MEDIC` | casualty reduction | `MissionResolver:174` |
| `IDEALIST` / `CYNICAL` | flavor only, **deliberately** no combat effect | `MarineCaptain.resolveMoralOutlook` |

Inert: `SIEGE_SPECIALIST`, `SAPPER`, `SCOUT`, `COMBAT_ENGINEER`, `VETERAN`,
`LOGISTICS_CHIEF`.

Separately, `Trait`'s own Javadoc says traits are "granted on recruitment
**or as a level-up reward**". The only grant site is `CaptainCandidate:60`
at creation. Promotion awards a rank and a commendation string. **Captains
are static after hire**, apart from the one-shot moral outlook — which
means the entire captain XP ladder delivers exactly one kind of reward
(command breadth) and nothing else.

## Goal

Make traits a real axis: each one does something legible, and captains
acquire them over a career.

## Slice 1 — Wire the inert six

Each needs a mechanic that matches its stated intent and is *observable*.
A trait the player cannot notice is no better than an inert one.

| Trait | Intended effect | Natural implementation seam |
| --- | --- | --- |
| `SIEGE_SPECIALIST` | Bonus vs fortified targets | Anti-structure damage or emplacement handling; the shipped `vsTurretMult` path is the obvious hook |
| `SAPPER` | Reduces collateral / civilian casualties | Civilian-rescue and living-world scenarios already track this precisely |
| `SCOUT` | More recon info before the raid | Ties to fog-of-war initial reveal and the recon command power |
| `COMBAT_ENGINEER` | Bonuses operating vehicles / mechs | Reads against the shipped mech roster and convoy tracks |
| `VETERAN` | Flat combat bonus | The dull one. Consider replacing it with something shaped, or cutting it — an undifferentiated multiplier is exactly the kind of invisible progression this whole track exists to fix |
| `LOGISTICS_CHIEF` | Larger effective squad cap | Cleanest of the six: modifies `Rank.fireteamCap()` at the formation layer |

Guiding rule: prefer traits that change **what the player can do** over
traits that change a number. `SCOUT` granting real pre-mission information
is a better trait than `VETERAN` granting 5% damage, even if they are worth
the same on a spreadsheet.

## Slice 2 — Trait acquisition

Deliver on the promise the enum already makes.

- **Promotion grants a trait choice.** A captain reaching a new `Rank`
  picks from a small offered set. This gives the captain XP ladder a second
  reward axis and turns promotion into a decision rather than a
  notification.
- Offered sets should be shaped by what the captain has actually done —
  a captain who has run sieges is offered `SIEGE_SPECIALIST`. This makes
  traits a *record* of a career, consistent with
  [[feedback_world_reactive_over_expressive]], and pairs naturally with the
  commendation log that already accumulates.
- Trait acquisition must be **exactly-once and replay-safe**, following the
  pattern `resolveMoralOutlook` already established.
- Conflicts and exclusivity need a rule. `IDEALIST`/`CYNICAL` already model
  a mutually exclusive pair with legacy repair; reuse that shape.

## Out of scope

- The trait UI — `s8-roster-legibility.md`.
- Giving `IDEALIST`/`CYNICAL` combat effects. The moral-outlook story
  named that an explicit non-goal and it stays one.
- Trait removal, transfer, or trading — also named non-goals by the personnel
  lifecycle. Do not reopen them here.

## Acceptance

- Every `Trait` either has an observable effect or is explicitly documented
  as flavor. No silent no-ops remain.
- Each functional trait's effect is verifiable in a focused test and
  noticeable in play.
- Acquisition is deterministic, exactly-once, and legacy-safe; existing
  captains in old saves are not retroactively granted or stripped.
- The `Trait` Javadoc is corrected to match reality — whichever way that
  goes.

## Open questions

- **Should rank-and-file marines carry traits?** A specialist mark earned
  in the field would make individual marines distinct and give S8's roster
  view much more to show. It is also a significant scope increase: 40+
  marines with traits is a different balance and UI problem than 5 captains
  with traits. Leaning: captains first, evaluate after.
- Is `VETERAN` worth keeping? It duplicates what `ExperienceTier` already
  does, less visibly. Leaning: repurpose it into something behavioral, or
  cut it and keep the save identity reserved.
- Should trait choice be presented at promotion, or accumulated and chosen
  later in the Personnel screen? Promotion-time is a stronger beat;
  deferred choice is friendlier when a promotion lands mid-campaign-flow.
