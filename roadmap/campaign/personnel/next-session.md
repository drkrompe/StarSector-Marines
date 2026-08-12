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

## Active — captain-to-fireteam command

The contract is locked in
[`stories/captain-fireteam-command.md`](stories/captain-fireteam-command.md):
each line fireteam may have one durable home captain; a captain commands a
rank-scaled formation of whole fireteams; home command supplies mission defaults
without preventing another active captain from borrowing the team. The
briefing-selected captain remains the outcome authority.

Next: Slice 1, the persisted domain binding and legacy repair. Stationing keeps
its existing captain plus anonymous-marine contract until named detachment
semantics are designed as one complete lifecycle.

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
