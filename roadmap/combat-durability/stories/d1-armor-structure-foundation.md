# D1 — Armor and structure foundation

> Give protection a real state before teaching the rest of the game to react
> to it.

Status: IN PROGRESS
Written: 2026-08-23

Read `combat-durability-nouns.md` before implementing this story.

## Goal

Ship the first complete simulation slice of the armor model: actors may carry
an optional armor pool and rating, attacks carry penetration, and one shared
calculation converts post-cover damage into clamped armor and structure loss.

## Scope

- Add lifecycle-correct live armor state alongside health/structure. Armor is
  removed with the live combat archetype on death and is absent for armorless
  actors.
- Add allocation-free world access and damage-queue columns for penetration.
- Replace `vsHardenedMult` / `vsTurretMult` as damage inputs. Update every live
  attack source, including data-owned handheld weapons and transitional mech,
  turret, explosive, and scripted paths.
- Apply the canonical armor-efficiency and overflow law after existing cover
  mitigation. Death remains structure-zero only.
- Migrate infantry armor patterns from bonus HP plus permanent damage reduction
  to armor pool plus rating while preserving their movement and incoming-hit
  tradeoffs.
- Give Bulwark, Hound, Sirocco, turrets, and drone hubs authored durability
  profiles without treating their old HP totals as requirements.
- Provide focused tests for unarmored damage, low-penetration chip, matched and
  overmatched penetration, exact armor break, overflow, cover ordering,
  overkill clamping, and queued/inline parity.

## Constraints

- One calculation owns prediction and application semantics; consumers may not
  recreate the formula from raw fields.
- Preserve the SoA mailbox and its steady-state zero-allocation behavior.
- Do not add damage types, armor facings, regeneration, or through-armor
  structure bypass.
- Do not fold wall HP or authored `wallDamage` into actor armor.
- `w3-remaining-catalogs.md` still owns data-driving mech weapons. This story
  supplies penetration wherever the current live authority resides and does
  not create a duplicate catalogue.
- AI targeting, armor-break morale, telemetry presentation, and final balance
  are follow-on stories. Existing descriptive targeting behavior may remain
  temporarily, but it no longer changes damage.

## Acceptance

- An unarmored actor loses structure equal to post-cover damage.
- Intact armor takes efficiency-scaled loss and prevents structure loss until
  the breaking hit's unspent damage overflows.
- Penetration at or above rating removes armor one-for-one and never increases
  exposed-structure damage.
- Armor break occurs once and does not itself kill the target.
- No damage path consults a target-type hardened predicate or target-specific
  weapon multiplier.
- Existing focused combat tests pass after intentional expectation updates,
  and a new durability suite covers the calculation independently.

## Implementation order

1. Land the pure resolution model and its unit tests.
2. Add component/world/mailbox plumbing.
3. Migrate weapon inputs and damage callers atomically.
4. Seed infantry and armored-platform profiles.
5. Run focused combat tests, then the full build.

