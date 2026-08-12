# Campaign personnel — next session

## State of play

The persistent rank-and-file loop is shipped. New campaigns receive one free
starting complement; later recruits come from player cargo. Marines live in
stable six-person fireteams plus a reserve pool, can receive individual or
atomic fireteam equipment issues, deploy through the canonical briefing, and
return with deterministic RTD/WIA/MIA/KIA outcomes. WIA recover on the campaign
clock and Results retains the fireteam breakdown.

Deployment now preserves the employer/player boundary and fails closed when an
explicit squad selection is stale. Both cases have automated coverage.

## Next up

Write the captain-to-fireteam command contract before implementing it. Resolve:

- whether one captain owns one fireteam or commands a formation of fireteams;
- how captain rank changes deployment capacity without invalidating six-person
  fireteam identity;
- how stationing assignments borrow or retain company personnel;
- how captain injury, reassignment, and absence affect a fireteam;
- which traits earn a visible effect in deployment, resolution, or recovery.

Keep manual UI/balance validation deferred for the current automated-only
session.

## Commit chain

- `208b87ed` — persistent tiered marine armory.
- `aee9b9cf` — persistent fireteams, deployment selection, casualty states, and
  Results debrief.
- `b7bb10db` — cargo-backed enlistment, reserve assignment, and demobilization.
- `b3da11ad` — atomic whole-fireteam equipment presets.
- `4737404d` — fail closed on stale explicit squad selection.
- `c35e88d4` — preserve generated employer seats when overlaying player
  personnel.
- `822572b7` — lock deterministic casualty disposition under replay.

