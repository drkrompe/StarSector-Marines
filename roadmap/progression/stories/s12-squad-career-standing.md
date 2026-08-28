# S12 — Squad career standing

> The formation is the thing with a history. The marines rotate through it.

Status: IN PROGRESS — slices 1 and 2 are shipped; only the standing view remains.
Written: 2026-08-27
Updated: 2026-08-27 — the squad record and its frozen-attribution fold landed.

Read `progression-nouns.md` before changing this story. The attribution seam is
shared with `c6-after-action-by-fireteam.md`; the two should land together.
Presentation coordinates with `s8-roster-legibility.md`.

## Problem

`SoldierCareer` already accumulates lifetime evidence per marine, folded from
frozen telemetry by `MarineRoster.applySoldierOutcome`. Nothing accumulates at
the grain the player actually names, deploys, and cares about.

With rank-and-file experience issued with the armour pattern rather than earned
(`progression-nouns.md`, aptitude and experience), the individual record loses
its mechanical job. That makes the squad the right holder of continuity: a squad persists
across replacements, carries a name the player chose, and is what the player
means when they say a unit is good.

## Goal

Every squad accumulates a lifetime record, and the player can read the company
as a standing.

## Slice 1 — The record (shipped)

- A squad career holding the same measures `SoldierCareer` already holds:
  deployments, wins, rounds fired and landed, damage dealt and taken, kills,
  and casualties suffered.
- Persisted on the squad, with the same `readResolve` legacy repair every other
  persisted marine-domain type uses, so an older save loads zeroed rather than
  null.
- Lifetime totals only. A per-mission journal is a separate retention and UI
  commitment, as the standing invariant says.

## Slice 2 — The fold (shipped)

- Credit the squad a marine **deployed with**, taken from the frozen deployment
  tag rather than current membership, so a post-battle roster edit cannot
  rewrite history. This is the same `CampaignSquadTag` attribution
  `c6-after-action-by-fireteam.md` needs; build it once.
- Fold at the existing `MarineRoster.applySoldierOutcome` seam. Do not add a
  second write path.
- Casualties credit the squad that took them, including for marines who did not
  survive — telemetry already survives the death transition.

## Slice 3 — The standing (remaining)

A company-wide view ranking squads by their record. Rank on measures with
character, not only kills:

| Measure | Reads as |
| --- | --- |
| kills, damage dealt | raw effectiveness |
| landed fraction | marksmanship |
| damage taken, casualties | who takes the brunt |
| friendly-fire damage | who is dangerous to stand near |

Friendly-fire damage is deliberately kept un-netted from damage dealt at the
telemetry seam, which makes it honestly reportable here rather than a derived
guess.

## Standing rules

- **A squad career survives everything except the squad's identity ending.** It
  survives replacement, and it survives a total loss and reconstitution under
  the same squad. This is the deliberate choice: the unit outlives its people.
- **The record is never a quality input.** It is evidence and presentation.
  Feeding a standing back into combat resolution would reintroduce the hidden
  modifier that `progression-nouns.md` now forbids.
- Renaming a squad carries its record with it; the record follows identity, not
  the label.

## Out of scope

- Per-mission history at either grain.
- Officer and captain records, which `c13-the-task-force.md` owns.
- Retiring or deleting a squad, which is a personnel-lifecycle question rather
  than a career-evidence one.

## Acceptance

- A squad that loses every member and is refilled from cargo retains its full
  prior record.
- A marine's deployment credits exactly one squad — the one frozen at launch —
  and editing the roster after the battle does not change the attribution.
- Marines killed in action still credit their squad for the mission they died
  on.
- Squad totals reconcile against the sum of the per-marine folds for the same
  mission.
- Headless mission evidence covers the fold without launching the game.

## Open questions

- Should the standing be readable **per contract or patron** as well as
  lifetime — "how has 2nd Squad done for this employer"? Cheap to derive later
  if the fold keeps enough shape, but explicitly not a per-mission journal.
