# C8 — Rejoin state for late arrivals

Status: READY — slices 1–3 shipped; slice 4 remains
Written: 2026-08-22
Updated: 2026-08-23 — folded team capacity, multi-lift joining, and form-up into `company-view-nouns.md`.

Read `company-view-nouns.md` before changing this story.

## Open outcome

A marine who lands after their battle squad has already advanced must rejoin
without walking into contact alone or immediately inheriting an engaged squad's
plan. Once back within cohesion, the marine returns to normal squad behavior.

This is the remaining edge after the shipped form-up gate. It covers a genuinely
late arrival after the gate times out or a later replacement wave; it does not
reopen whole-fire-team lift capacity, campaign-squad grouping, or initial form-up.

## Design boundary

Build the rejoin intent on the existing `RegroupPosture` and
`InfantryCohesion.cohesionOverride` seam. Do not introduce a second cohesion
mechanism. The state should suppress initiating contact while the marine closes
on their squad and retire automatically when cohesion is restored.

## Acceptance

- A late arrival paths toward its squad instead of pursuing the squad's current
  enemy or objective alone.
- The marine may defend itself while rejoining but does not initiate contact.
- Reaching cohesion removes the rejoin state and restores ordinary dispatch.
- Normal initial form-up and untagged generated-personnel behavior are unchanged.

## Out of scope

- Rebalancing shuttle capacity, wave timing, landing-zone selection, or AA.
- Changing split-landing behavior or the shipped form-up timeout.
- Adding a new player command or exposing fire teams as order targets.
