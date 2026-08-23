# S5 — Command-level meta-progression (capacity)

Status: PLANNED — capacity progression is designed but not scheduled.

Written: 2026-05-29

Updated: 2026-08-23 — normalized around the shipped availability/deck model.

> Design-forward stub. The "level up your command" spine — **capacity only**.
> Roster entry is *not* here; acquisition determines availability, while this
> story changes how much command capacity the player can field. Read
> `command-powers-nouns.md` first.

## Goal

Persist command level + XP in the campaign tier, and scale **capacity**: the
pre-battle loadout budget, the in-battle command-point pool, its regen, and
cooldown reductions.

## Scope sketch

**In:**
- Command level + XP on `CampaignState`; award XP from mission outcomes.
- Curves: loadout-budget cap, CP pool, CP regen, cooldown multiplier as
  functions of command level.
- Persistence via `Serializable` POJO ([[starsector_persistence_pattern]]).

**Out:**
- Roster unlocks (acquisition + spoils handle that).
- The spoils-tier "super" mods themselves (their own content track).

## Open / tuning

- XP sources and curve shape — anchor thresholds to the campaign's economic
  pressure ([[feedback_paycheck_runway_window]]); early budgets should be tight
  enough that "which powers" is a real choice.

## Dependencies

- Campaign tier, mission resolution loop.
