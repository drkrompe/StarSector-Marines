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

Slice 1 is shipped (`9c4c4ee8`): the persisted domain binding, whole-fireteam
rank cap, validated reassignment, captain-removal cleanup, and ordered legacy
repair are covered.

Slice 2 is shipped (`aa26d3ec`): formation management on the armory surface,
including home-command identity, rank use/capacity, unavailable feedback, and
bounded roster-order assignment.

Before Slice 3, close the deployment-reachability regression surfaced by the
persistent gate: ordinary missions need exact shortfall plus bulk recruitment
routing, while debug missions need explicit Recruit/Mixed/Veteran fixtures that
do not consume or mutate campaign personnel. Stationing keeps its existing
captain plus anonymous-marine contract until named detachment semantics are
designed as one complete lifecycle.

The corrective contract is locked in
[`stories/deployment-readiness.md`](stories/deployment-readiness.md). Build its
debug-fixture slice first so the mission picker is immediately usable, then land
production shortfall/bulk enlistment before resuming captain-command Slice 3.

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
- `23db413a` — lock the captain-to-fireteam command contract.
- `9c4c4ee8` — persist and validate captain home formations.
- `aa26d3ec` — manage home command on the armory formation surface.
