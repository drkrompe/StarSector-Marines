# Early operations

> The first battles must validate a green, poorly lifted marine company rather
> than quietly asking it to behave like an invasion force.

## Player promise

The opening job board offers work a starting detachment can actually do:
one or two player shuttle sorties, mixed personal gear, no assumed fighter or
heavy-ground support, and local troops who make the battle larger than the
player's roster without replacing the player's contribution.

The opposing forces are genuinely ragtag. A mission tagged as an early
operation cannot inherit regular infantry, mechs, defense-post turrets,
fighter cover, or an open-ended reinforcement budget just because the target
market happens to be well developed.

## Opening ladder

The Independent broker owns a two-mission, one-shot ladder:

1. **Hold Until Relieved** — one employer militia lift and one player sortie
   reinforce two local militia fireteams already holding a rally point. A
   finite bandit force actively advances on that line.
2. **Take Back the Depot** — after the relief succeeds, one employer militia
   lift joins two player sorties in a counterattack against another finite
   bandit force.

Both missions are `LOW` risk, carry no air support, and use militia equipment
and experience rolls on both non-player forces. The ordinary persistent
personnel overlay still owns the player seats.

## Availability contract

- The first operation appears only while the company has at most eighteen
  soldiers and every soldier remains below the Regular experience threshold.
- Completing it permanently unlocks the counterattack, even if the survivors
  cease to be green in the process.
- Each operation is recorded through the existing story-mission completion
  set and remains retryable after a defeat.
- The always-present Independent broker is the sole entry point, so the ladder
  is reachable at any inhabited market without adding a synthetic client.

## Reusable battle seam

`OpeningOperationKind` selects an authored setup instead of overloading generic
`LOW` risk. The setup owns:

- militia-only allied and hostile rosters;
- employer-shuttle seat roles;
- a local defensive anchor for relief missions;
- attacker-side commander pressure for bandits; and
- the deliberate absence of heavy support and reinforcement systems.

The small symmetric advance commander is intentionally faction-agnostic. It
is the reusable substrate for later local assaults, AI-position relief, and
other missions where either side must actively close on a finite enemy force.

## Follow-ups

- Add a third variant around rescuing a pinned AI assault after the two opening
  missions have had a feel pass.
- Give local-force survival an optional payout/debrief bonus once mission
  outcomes can carry allied casualty facts.
- Replace the fixed green-company thresholds with an explicit campaign career
  bracket if the broader contract layer gains one.
