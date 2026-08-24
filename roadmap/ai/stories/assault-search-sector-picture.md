# Assault search-sector picture

Status: DRAFT — coordinate and explain a two-dimensional search without turning Assault into a directional front.

Written: 2026-08-24

Read `ai-nouns.md` before planning this story.

## Intent

Make Assault squads search a lethal, partially known battlefield as a shared
operation. Preserve the useful rectangular sector sweep, but publish which
areas are searched, suspected, active, or reinforced so squads converge on
remaining contact without duplicating the same route or reading hidden units.

## Scope

- Publish an immutable Assault command picture with sector search progress,
  assigned squads, believed contacts, current sweep legs, and assignment reason.
- Keep sector identity stable enough for squads to complete useful search work,
  while allowing a cleared squad to reinforce a neighboring active or suspected
  sector.
- Distinguish a directly active sector from one being searched because of stale
  belief or incomplete coverage.
- Expose the picture through selected-squad presentation and the state dump.

## Constraints

- Assault is a two-dimensional search-and-destroy mission, not a Conquest front;
  it has no canonical forward axis, territorial ownership, or keep convergence.
- Hostile sector state derives from marine beliefs. Live hidden defenders may
  remain objective truth for victory, but may not appear in the command picture.
- Search coverage belongs to command and persists across tactical replans; local
  contact doctrine still decides how a squad reacts once contact is made.

## Exit

Fold durable search-sector vocabulary and laws into `ai-nouns.md`, add the story
to the AI shipped ledger, and delete it when implementation and live acceptance
ship.
