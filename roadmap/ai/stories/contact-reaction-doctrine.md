# Contact-reaction doctrine

Status: IN PROGRESS — live battle found and implementation closes the contact-initiative gap.

Written: 2026-08-23

Updated: 2026-08-26 — automated coverage now protects dispersed contact locality, actionable belief validity, movement resumption after a cleared contact, fixing movement, and flank handoff.

Read `ai-nouns.md` before running this acceptance.

## Acceptance

- [ ] Enter direct contact, lose it for one or more ticks, and reacquire it.
  Confirm only a new contact episode interrupts the plan immediately; added
  hostiles during the same episode do not cause a visible replan loop.
- [ ] Put an advancing squad under pressure from front, flank, and rear with
  favorable, even, and unfavorable nearby forces. Confirm its published
  ADVANCE, HOLD, or DISENGAGE doctrine reads as stable and its short
  lost-contact hold does not strand it for the full memory lifetime.
- [ ] Give only one member or fireteam a legal shot on a lateral or withdrawing
  direct contact. Confirm the published initiative is PROSECUTE: legal shooters
  hold and fire while the remainder close to bounded firing positions using
  the shared primary track. Then advance the enemy on a useful squad firing
  line and confirm the initiative becomes RECEIVE rather than surrendering
  cover and lines of fire.
- [ ] Ambush one fireteam of a multi-team squad. Confirm the exposed team
  displaces while an unexposed sibling holds and covers, with ordinary
  bounding behavior still intact on a committed advance.
- [ ] Spread a squad so one fireteam alone makes contact. Confirm that element's
  live contact remains in the squad picture, fixing members acquire reachable
  supporting fire positions, and the maneuver hands off instead of restarting
  ReinforceContact when it arrives or cannot reach its flank waypoint.
- [ ] Observe ordinary open-ground movement and narrow passages. Confirm team
  echelons/readable arrival footprints relax for doorways and constrained
  navigation rather than deadlocking the squad.
- [ ] Observe several similarly placed hostiles. Confirm acquisition remains
  stable long enough for legal fire and that visual turning stays legible
  without becoming a new fire gate.
- [ ] Select the squad and create a state dump during both initiatives. Confirm
  the UI and JSON agree on initiative, engageable members, engageable teams,
  total live teams, and whether the advancing hard hold is active.

## Out of scope

- Retuning combat damage, weapon values, morale model, or belief lifetime as a
  substitute for observing the doctrine.
- Adding mechanical suppression, commander assignment consumers, cross-squad
  briefings, or reserve commitment.
- Adding a new mech role or changing chassis/loadout policy.

## Exit

Fold a durable tactical law into `ai-nouns.md`, add this story to
`shipped.md`, and delete it when the live pass is complete.
