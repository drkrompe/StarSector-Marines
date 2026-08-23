# D3 — Durability evidence and UI

> If armor is a decision, the player and the balance report must be able to
> see it.

Status: PLANNED
Written: 2026-08-23

Read `combat-durability-nouns.md` before implementing this story.

## Goal

Expose armor, structure, penetration, and armor break without creating a
second simulation authority.

## Scope

- Record applied armor and structure damage separately while retaining a
  compatible aggregate resolved-damage total.
- Preserve structure-zero kill attribution and lifecycle-stable evidence for
  fallen combatants.
- Add armor and structure bars to battle presentation plus a bounded armor-
  break cue.
- Replace Armory and Mech Lab damage-block / anti-armor-multiplier language
  with armor pool, rating, structure, and penetration.
- Keep historical career totals readable; new split fields begin at zero when
  loading older saves.

## Acceptance

- Reports reconcile aggregate damage with armor plus structure damage and do
  not credit resisted energy or overkill.
- The player can distinguish intact armor, exposed structure, and a defeated
  actor at combat scale.
- Every equipment surface reads values from the same authorities used by the
  simulation.
- Presentation state cannot alter damage or armor-break timing.

