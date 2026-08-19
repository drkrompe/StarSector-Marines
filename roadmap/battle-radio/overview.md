# Battle Radio

## Concept

Battle radio is a low-volume presentation layer that makes deployed infantry
sound like fireteams sharing a tactical net. It should reinforce events that
already happened in the simulation rather than inventing gameplay state or
becoming a constant voice track.

## Principles

- Marine infantry chatter is positional at the speaking squad's centroid.
- Calls are sparse and globally throttled so combat SFX and music remain the
  foreground mix.
- The simulation remains deterministic: audio selection and timing live in the
  presentation layer and never consume the simulation RNG.
- Mechs, defenders, and wiped squads do not use the marine radio pools.
- Both the standalone battle screen and the vanilla-combat bridge consume the
  same cue-selection policy, while each host retains its own world-space audio
  projection.

## Stories

1. **Background radio v1** — contact and fallback calls tied to squad state
   transitions, plus occasional acknowledgements from engaged squads.
2. **Expanded event vocabulary** — objective, reinforcement, casualty, command
   power, and extraction-specific pools once their event seams and suitable
   voice clips are reviewed.
3. **Faction/net identity** — alternate radio treatments or voice sets for
   patrons, defenders, alien forces, and named captains.
