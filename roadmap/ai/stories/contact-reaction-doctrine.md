# Contact-reaction doctrine

Status: PARKED — implementation is shipped; live battle tuning remains.

Written: 2026-08-23

Read `ai-nouns.md` before running this acceptance.

## Acceptance

- [ ] Enter direct contact, lose it for one or more ticks, and reacquire it.
  Confirm only a new contact episode interrupts the plan immediately; added
  hostiles during the same episode do not cause a visible replan loop.
- [ ] Put an advancing squad under pressure from front, flank, and rear with
  favorable, even, and unfavorable nearby forces. Confirm its published
  ADVANCE, HOLD, or DISENGAGE doctrine reads as stable and its short
  lost-contact hold does not strand it for the full memory lifetime.
- [ ] Ambush one fireteam of a multi-team squad. Confirm the exposed team
  displaces while an unexposed sibling holds and covers, with ordinary
  bounding behavior still intact on a committed advance.
- [ ] Observe ordinary open-ground movement and narrow passages. Confirm team
  echelons/readable arrival footprints relax for doorways and constrained
  navigation rather than deadlocking the squad.
- [ ] Observe several similarly placed hostiles. Confirm acquisition remains
  stable long enough for legal fire and that visual turning stays legible
  without becoming a new fire gate.

## Out of scope

- Retuning combat damage, weapon values, morale model, or belief lifetime as a
  substitute for observing the doctrine.
- Adding mechanical suppression, commander assignment consumers, cross-squad
  briefings, or reserve commitment.
- Adding a new mech role or changing chassis/loadout policy.

## Exit

Fold a durable tactical law into `ai-nouns.md`, add this story to
`shipped.md`, and delete it when the live pass is complete.
