# Battle Radio — Next Session

## State

Background radio v1 shipped in `ba6f3b5a`: contact and morale-break calls now
follow marine infantry squad transitions, with sparse acknowledgements during
sustained engagements. Standalone and hybrid battles share the same policy.
Deployment follow-up `476b2be6` makes `deployMod` generate the gitignored audio
outputs first, preventing manifest/file skew on newly-added sound pools.

## Completed

- [Background radio v1](complete/background-radio-v1.md)

## Active

- [Expanded combat events](stories/expanded-combat-events.md)

## Next

1. Run an in-game mix and voice-content feel pass; tune only from observed
   overlap/readability issues.
2. Contract a new story before adding more event vocabulary. Objective,
   reinforcement, casualty, command-power, and extraction events are the next
   useful candidates.
3. Replace the proprietary placeholder clips before a stable public release.
