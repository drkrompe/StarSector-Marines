# Retreat defeat settlement

Status: IN PROGRESS — implementation ready for live acceptance

Written: 2026-10-08

Updated: 2026-10-25 — withdrawal wounds away from the landing zone; story retry.

Read `campaign-nouns.md`, `personnel-nouns.md`, and `contracts-nouns.md`.

## Scope

Confirmed Retreat ends an active operation as a defeat and enters the existing
Results settlement path. Preserve the actual casualty and boat-loss ledgers;
do not kill surviving or unlanded personnel merely for withdrawing. Apply the
existing defeat rules for officer recovery, rewards, contracts, campaign events,
and opening-operation retry. Debug missions retain their non-persistent source
policy. This adds no extraction sequence; the withdrawal's own cost (survivors
away from a landing zone come home wounded) and the story retry are in
`campaign-nouns.md`, Ending an operation.

## Acceptance

- Cancellation changes no battle or campaign state.
- Confirmed Retreat freezes defeat and terminal civilian evacuation facts.
- A completed battle cannot have its winner replaced by Retreat.
- Results settlement runs once for a battle, including repeated exit input.
- No engagement and no losses is not a total wipe for officer casualty rolls.
- Live: withdraw with squads both at and away from the landing zone; only the
  latter return wounded. On a story operation, Restart and a lost battle's
  Retry return to the briefing on the same map with no Results, casualties or
  spent supplies; the second retry before mission select spends a story point.
- Live: retreat before landing and after losses; inspect Results, named roster,
  boat losses, officer recovery, zero payout/loot, opening retry, contract
  failure, and event/stationing resolution. Verify debug has no writeback.

## Remaining acceptance

Run the live cases above in Starsector. Focused unit coverage exercises terminal
winner preservation, civilian report sealing, confirmation cancellation, and
zero-engagement wipe classification. It does not establish live campaign
settlement acceptance or the feel of the existing defeat recovery policy.
