# Company ship selection

Status: PROPOSED

Written: 2026-08-27

Read `ship-interiors-nouns.md` and `company-ship.md` before implementing this
story. Depends on `company-ship-deck-adoption.md`: there has to be a real deck
to choose before choosing one means anything.

Let the player pick which ship in their fleet the company lives aboard, and let
them move it later. Today nothing designates a ship at all, so the interior has
no owner and the two operations screens depict a place that is not in the
fleet.

## Scope

- A designated company ship recorded on the campaign, referencing a fleet member
  the player owns.
- Selection at founding, before the company exists.
- A transfer screen listing the fleet's candidate hulls, showing for each what
  its deck would hold, so the trade is legible before it is committed.
- Both entry points read the same candidacy rules and produce the same record.

## Constraints

- A candidate's form comes from what that hull can do when whole, and its
  damage is read separately, per law 18. The preview must be generated the same
  way the deck will be, or the screen is advertising a ship the player will not
  get.
- A hull that cannot hold a facility reports that it cannot, per law 19. The
  comparison's value is in what a candidate *loses*, and a screen that only
  shows gains is a worse screen than none.
- Candidates are compared on working capacity, not nominal, per law 4. A
  battered capital and a sound frigate is the interesting comparison and the
  one the player is actually making; a screen that rates hulls by class cannot
  show it.
- Selection is a campaign decision recorded on campaign state; ship interiors
  publishes what a candidate would hold and owns nothing about the transaction.
- No new persistence shape for the deck itself. The deck is derived from the
  chosen ship, so only the choice is worth recording.

## Acceptance

- A new company cannot reach the operations screens without a ship designated,
  and the designation survives save and load.
- The transfer screen lists every candidate in the fleet and, for each, the
  facilities it would hold, the ones it would lose, and the ones it has no room
  for at all — counted in fixtures actually in service, so a damaged hull reads
  as the smaller ship it currently is.
- Transferring changes what the Barracks and Mech Lab screens show, without
  either screen learning that a transfer happened.
- A ship that leaves the fleet while designated is handled explicitly rather
  than by leaving the company pointing at nothing.

## Open questions

- What makes a hull a candidate. Any ship with a boardable hull class is the
  loosest rule and probably too loose: a tanker technically has an interior and
  is a strange place to keep a mech.
- Whether transfer costs anything, and whether facility contents move with the
  company or stay with the hull.
- What happens when the company ship is destroyed rather than merely damaged.
  This is the sharpest version of the question and the one most worth answering
  deliberately.

## Out of scope

Refit, upgrade transactions, and any economy. Installing Additional Berthing is
the base game's refit screen doing its own job; this story only has to read the
result.
