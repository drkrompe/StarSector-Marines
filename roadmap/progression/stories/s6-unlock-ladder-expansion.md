# S6 — Unlock ladder expansion

> The ladder is four rungs long and ends at mission five. Four armor
> patterns are fully authored and unreachable.

**Status:** not started. Depends on [S5](s5-parts-acquisition-channels.md)
for income and the two-currency split, and on
[S2](s2-weapon-catalog-expansion.md) for things worth unlocking.

## Problem

`MarineArmory.recordVictory` is the whole progression ladder:

- 2 victories: `PULSE_RIFLE` MILSPEC
- 3: `SMG` MILSPEC
- 4: `DMR` MILSPEC
- 5 victories and at least one high-risk: `DMR` MASTERWORK

Then flat, forever. Consequences:

- **No armor or secondary is ever unlocked.** `BLUE_SCOUT`, `RED_ELITE`,
  `OUTLAW`, and `MILITIA` have stats, icons, and sprite layers and cannot
  be reached in a real campaign. `RED_ELITE` is the best armor in the game.
- Masterwork exists for exactly one weapon.
- A pure victory counter is the least interesting possible gate: it does
  not care what you fought, where, for whom, or how.

## Goal

A ladder that stays alive for 30+ missions, reaches every authored asset,
and gates on things the player recognizes as achievements.

## Slice 1 — Close the stranded assets

The smallest correct fix, shippable on its own:

- Every `MarineArmorPattern` gets a reachable unlock, laddered by its own
  `tier` field: tier 2 (`BLUE_SCOUT`, `OUTLAW`, `MILITIA`) early, tier 3
  (`CHARCOAL`, `ARMY_GREEN`) mid — both currently starter issue, so decide
  whether they stay starter — and tier 4 (`RED_ELITE`) as a genuine chase.
- Fill the grade matrix: MILSPEC and MASTERWORK for every primary family
  and for secondaries, not just `DMR`.
- Add a **ships-nothing-stranded check**: a test that asserts every
  `MarineWeapon` x `EquipmentGrade`, every `MarineSecondary`, and every
  `MarineArmorPattern` is either starter issue or reachable through some
  unlock path. This is the guard that stops the audit's finding from
  recurring the next time an asset is authored.

## Slice 2 — Reframe recipes as recoverable blueprints

**Recommended direction.** Replace victory-count milestones with
**recovered fabrication schematics** — a recipe is something you *find*,
not something a counter hands you.

Why this is the right reframe:

- It is native to Starsector. The player already understands blueprints as
  a thing you salvage, are paid in, and go looking for.
- It makes recipes a **loot and reward payload**, which plugs straight into
  S5's channels instead of needing a parallel system.
- It makes the ladder *world-reactive* — what you unlock depends on where
  you have been fighting and who you have been working for
  ([[feedback_world_reactive_over_expressive]]).
- It gives special missions a reward that is not money
  ([[feedback_patron_narrative_discoverable]]).

Sources for a schematic: high-risk operation loot, patron contract reward,
story/special mission, MRB licensing tier, and a modest set of
early-campaign milestone grants so a green company is not gated behind luck
in its first hours.

## Slice 3 — Multi-axis gating

Where a milestone gate is still the right tool, gate on more than a count:

- Total and high-risk victories (existing).
- Advanced components held or spent — ties the ceiling to S5's chase
  currency.
- MRB licensing tier, which is already computed
  (`ContractEligibility`) and currently gates only patron access. Extending
  it to armory access makes company reputation mean something concrete.
- Patron standing with a specific house — a Corporate patron opening
  fabrication lines reads correctly.
- Named operational achievements, e.g. clearing a hardened military site.

## Out of scope

- Where parts come from — [S5](s5-parts-acquisition-channels.md).
- New gear to unlock — [S2](s2-weapon-catalog-expansion.md).
- Visual differentiation of unlocked tiers —
  [S7](s7-grade-visual-identity.md).
- Any change to printing costs beyond repricing against S5's stated income
  curve.

## Acceptance

- Every authored weapon, grade, secondary, and armor pattern is reachable,
  enforced by the stranded-asset test.
- The ladder has meaningful rungs past mission 30, verified against S5's
  stated income curve at missions 5, 15, and 30.
- No rung is reachable by money alone.
- Legacy saves migrate: existing `unlockedRecipes` are honored, and a
  long-running save is not retroactively stripped of anything it had.
  `MarineArmory.readResolve` already carries this responsibility — extend
  it, do not replace it.
- An in-game pass confirming the early-company experience still gets a
  visible upgrade in its first few missions. The ladder growing longer must
  not make the opening feel emptier.

## Open questions

- Should `CHARCOAL` and `ARMY_GREEN` remain starter issue? Making the
  player start armorless and *earn* their first real armor is a stronger
  opening beat, but it interacts with early-operations balance, which is
  currently tuned around a company that has them.
- Do schematics fully replace milestone unlocks, or coexist? Leaning:
  coexist, with milestones covering the guaranteed early ladder and
  schematics covering everything above it — so no player is ever hard-stuck
  behind a drop that did not come.
