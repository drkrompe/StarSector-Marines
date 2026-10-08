# Retreat defeat settlement

Status: IN PROGRESS — implementation ready for live acceptance

Written: 2026-10-08

Updated: 2026-10-08 — terminal boundary and shared Results writeback implemented.

Read `campaign-nouns.md`, `personnel-nouns.md`, and `contracts-nouns.md`.

## Scope

Confirmed Retreat ends an active operation as a defeat and enters the existing
Results settlement path. Preserve the actual casualty and boat-loss ledgers;
do not kill surviving or unlanded personnel merely for withdrawing. Apply the
existing defeat rules for officer recovery, rewards, contracts, campaign events,
and opening-operation retry. Debug missions retain their non-persistent source
policy. This adds no extraction sequence or separate withdrawal penalty.

## Acceptance

- Cancellation changes no battle or campaign state.
- Confirmed Retreat freezes defeat and terminal civilian evacuation facts.
- A completed battle cannot have its winner replaced by Retreat.
- Results settlement runs once for a battle, including repeated exit input.
- No engagement and no losses is not a total wipe for officer casualty rolls.
- Live: retreat before landing and after losses; inspect Results, named roster,
  boat losses, officer recovery, zero payout/loot, opening retry, contract
  failure, and event/stationing resolution. Verify debug has no writeback.

## Remaining acceptance

Run the live cases above in Starsector. Focused unit coverage exercises terminal
winner preservation, civilian report sealing, confirmation cancellation, and
zero-engagement wipe classification. It does not establish live campaign
settlement acceptance or the feel of the existing defeat recovery policy.
