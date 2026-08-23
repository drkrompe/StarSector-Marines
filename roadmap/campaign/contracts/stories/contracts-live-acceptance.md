# Contracts live acceptance

Status: PARKED

Written: 2026-08-23

## Goal

Run the deferred in-game acceptance passes for the shipped contract surfaces.
This is validation only: it must not invent new contract rules or substitute
debug controls for a player-visible flow.

## Scope

- Observe a naturally generated eligible Escort offer through acceptance,
  briefing, operation, and settlement.
- Accept a stationing offer in game, assign the intended captain and detachment,
  then verify the local management surface reflects the live commitment.
- Arm a Garrison defense or Cadre incident and verify the event card's layout,
  controls, remote response route, write-off confirmation, and reminder behave
  correctly at representative resolutions.
- Confirm a displayed pending response remains the same persisted work after a
  save/load and cannot leave the player stranded in dialog chrome.

## Constraints

Use normal campaign play for the evidence. The established response route is
the only route to its briefing; the check must not add a second launcher or
change response, reputation, or deadline policy. Time progression is owned by
`campaign-framework-nouns.md`.

## Acceptance

- Each flow is observed end to end without an unexpected eligibility, roster,
  dialog, or settlement break.
- The event card is legible and dismissible, does not overlay another dialog,
  and its three choices preserve their documented domain effects.
- Record only actionable defects; a successful pass ships this story through
  the normal fold-and-ledger process.
