# C6 — After-action by fireteam

> The card the player deployed should be the card they get back.

**Status:** not started. Depends on
[C1](c1-fireteam-identity-through-the-drop.md).

## Problem

`ResultsScreen` renders a centered summary: outcome line, payout,
casualties, captain status change, XP. `MissionOutcome` carries
`marinesEngaged` and `marinesLost` as **scalars** — the debrief can say
"3 lost" but not *which team lost them*.

The player deployed named formations. They get back a number. That breaks
the loop the card view is trying to build: the whole reason to organize
marines into teams the player can name is that the names should come back
carrying what happened to them.

The per-soldier writeback already exists — deterministic RTD/WIA/MIA/KIA
outcomes and recovery are shipped in the personnel spine, and
`MissionResolver.apply` mutates roster state before the screen renders. The
grouping is what is missing.

## Goal

The debrief reports per fireteam: who deployed, who came back, who is
wounded and until when, who is gone.

## Design

- With C1's `campaignFireteamId` on the deployed seats, the resolver can
  attribute each per-soldier outcome to the team the marine deployed with —
  frozen at deploy time, so a post-battle roster edit cannot retroactively
  rewrite the debrief.
- `MissionOutcome` gains a per-fireteam breakdown list (id, label,
  deployed, returned, wounded, missing, lost) alongside the existing
  scalars. Keep the scalars — they are read elsewhere and by save-compatible
  code paths; the breakdown is additive.
- `ResultsScreen` renders the breakdown as the same fireteam-row vocabulary
  C3 establishes, so the debrief and the roster look like the same object
  in two states. A team that came back whole should be visually quiet; a
  team that took losses should not be.
- Recovery days come from the already-persisted `unavailableUntilDay`, so
  the debrief can say when the team is whole again — the number the player
  needs to plan the next contract.

## Slices

1. **Attribution in the resolver.** Per-fireteam outcome rollup built from
   the frozen deployment; `MissionOutcome` field added.
2. **Debrief rendering.** Fireteam rows on `ResultsScreen`.

## Acceptance

- A two-team sortie that loses one marine reports the loss against the
  correct team.
- Outcomes without campaign personnel (debug fixtures, employer-only
  forces) render exactly as today — the breakdown is empty, not wrong.
- Replay-safe and idempotent: re-entering the screen re-renders, never
  re-applies. `MissionResolver.apply` remains the only mutation point.
- The scalars (`marinesEngaged`, `marinesLost`) stay consistent with the
  sum of the breakdown.
- `MissionOutcome` stays xstream-friendly if it is persisted anywhere on
  the context path — verify before adding a collection field.

## Files touched

- `ops/MissionOutcome.java` — additive breakdown.
- `ops/MissionResolver.java` — attribution.
- `ops/ResultsScreen.java` — rendering.

## Out of scope

- Per-marine battle telemetry (rounds landed, kills). That is progression
  [S3](../../progression/stories/s3-per-soldier-telemetry.md); this story
  reports survival, not performance. When S3 lands, its per-marine numbers
  slot into these rows.
- Changing casualty determination or recovery timing.

## Open questions

- Does the breakdown want a "what the team did" line — the objective it was
  assigned when it took its losses? The commander tier knows
  (`ObjectiveAssignment`), the battle is transient, and nothing currently
  carries that out. It would make the debrief narrative rather than
  statistical. Cheap to add at the seam if it is wanted; do not build it
  speculatively.
