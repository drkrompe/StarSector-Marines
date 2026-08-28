# S11 — Card-sourced experience

> A squad's quality should be something the player collected and can see,
> not something hidden in twelve individual XP counters.

Status: READY — the band vocabulary, the distribution idiom, and the profile
constructor all already exist.
Written: 2026-08-27

Read `progression-nouns.md` before changing this story. It coordinates with
`s4-performance-derived-experience.md`, which owns the band span this story
depends on to be felt.

## Problem

Rank-and-file experience is currently an individual, invisible, slowly
accumulated number. Three consequences:

1. **It is unreadable.** To know whether a squad is dangerous the player would
   have to inspect twelve marines, and inspect them again after replacements.
   Nothing in any screen answers "is this formation a threat" at a glance.
2. **It makes quality a logistics problem.** A company whose veterans die can
   only recover by grinding, which pushes toward hoarding and toward avoiding
   the fights that make the game interesting.
3. **It duplicates the progression axis.** The Armory's collected templates and
   reusable definitions are already the intended growth track. A parallel
   per-marine ladder competes with it for the same design job.

Meanwhile `InfantryLoadoutRolls` already generates defender infantry from
declared band distributions per unit type and risk. The idiom this story wants
is shipped — it is simply not pointed at the player's own squads, which instead
use one flat `playerProfile` roll of 10/70/18.

## Goal

A squad loadout definition declares an experience standard. Issuing it resolves
each billet's band. Quality becomes a property of collected, visible kit.

## Slice 1 — Standard on the definition

- Add an experience standard to the squad loadout definition: a band
  distribution for the rank-and-file billets, and a separate one for the leader
  billet. Reuse the existing percentage shape rather than inventing a second.
- Author standards across the shipped definitions so the collectible tier and
  the declared standard agree. A low-tier definition should read as recruits
  with a mediocre NCO; a prestige one as a special-operations element.
- Validate on load like every other authored catalog: a definition whose
  distribution does not sum correctly fails loud.

## Slice 2 — Resolve at issue

- Replace `InfantryLoadoutRolls.playerProfile`'s flat 10/70/18 roll with
  resolution against the issued definition's standard.
- `InfantryLoadoutRolls.profileAtTier` already converts a band into a plausible
  profile; reuse it unchanged.
- Aptitude stays per marine, persisted, and untouched by this story.
- Resolution must be deterministic for a frozen deployment so replay and the
  headless evidence harnesses stay stable.

## Slice 3 — Re-source the leader gate

`MarineRoster` currently selects the enlisted leader by testing accumulated
`experienceXp` against the veteran threshold. With experience issued rather than
accumulated, that gate needs the definition's leader standard as its source
instead. Leadership remains re-derived from current membership and fitness, as
`personnel-nouns.md` requires.

## Out of scope

- **The band span itself.** Widening `ExperienceTier` belongs to
  `s4-performance-derived-experience.md` and is the reason this story is worth
  doing; do not retune multipliers here.
- **Per-marine health.** A separate axis with its own balance risk; see the
  open question in `s1-lethality-feel-pass.md` about TTK quantization.
- **Defender loadout vocabulary.** Giving hostile formations the same readable
  definitions is a follow-on, not this story.
- Captain XP and the `Rank` ladder, which remain a working leadership system.

## Acceptance

- Issuing two different definitions to two squads produces materially different
  resolved bands, and the difference is visible on the squad's card without
  drilling into individuals.
- Two squads issued the same definition resolve to the same band distribution;
  any remaining difference is bounded innate aptitude.
- A squad reinforced from generic cargo marines fights at its definition's
  standard, with no recovery period and no grind.
- Deterministic under replay for a frozen deployment.
- Defender generation is untouched and its existing evidence still passes.

## Open questions

- **Does losing marines need any quality consequence at all?** Under this model
  it does not: casualties cost bodies, credits, and recovery clocks, but a
  refilled squad performs at standard. That is deliberate and matches the
  collection-as-progression goal. Flagged because it is the one place this
  model softens failure, and `[[feedback_hard_failure_preference]]` argues the
  other way. An understrength squad already fights with fewer guns, which may
  be consequence enough.
- **What happens to persisted `MarineSoldier.experienceXp`?** It stops being a
  mechanical input. Either it stays as a displayed service statistic or it is
  deleted outright. `xp-authority-cleanup.md` should decide this, since it is
  already the story that owns XP authority.
