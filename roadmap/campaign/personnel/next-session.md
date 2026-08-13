# Campaign personnel — next session

## State of play

The persistent rank-and-file loop is shipped. New campaigns receive one free
starting complement; later recruits come from player cargo. Marines live in
stable six-person fireteams plus a reserve pool, can receive individual or
atomic fireteam equipment issues, deploy through the canonical briefing, and
return with deterministic RTD/WIA/MIA/KIA outcomes. WIA recover on the campaign
clock and Results retains the fireteam breakdown.

Deployment now preserves the employer/player boundary, fails closed when an
explicit squad selection is stale, and reports whether a shortfall needs squad
assignment or new personnel. Mission-scoped armory visits bulk-enlist only from
real cargo and return to the originating screen.

Debug-picker missions now carry explicit debug sources and selectable
Recruit/Mixed/Veteran fixtures. They fill the current 40-drop override without
campaign personnel and their outcomes perform no campaign writeback.

## Complete — captain-to-fireteam command

The contract is locked in
[`complete/captain-fireteam-command.md`](complete/captain-fireteam-command.md):
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

Slice 3 is shipped (`f6247ace`): ordinary briefings default to the selected
active captain's home formation, bounded borrowing respects the captain's
whole-fireteam rank cap, selected/max fireteams are visible, and initialized
empty selections fail closed instead of silently loading the whole company.
Debug and stationing retain their documented exceptions. Focused policy,
readiness, deployment-freeze, and domain tests passed, followed by the complete
isolated Gradle build once the concurrent tree settled.

Slice 4 is shipped (`48471e93`): `MissionOutcome` freezes briefing-ordered
deployed fireteam ids beside its commander snapshot, and Results uses those ids
instead of rediscovering teams from later roster state. Legacy outcomes retain
their disposition-based fallback. Focused compatibility tests and the complete
isolated Gradle build passed.

The deployment-reachability correction is shipped and recorded in
[`complete/deployment-readiness.md`](complete/deployment-readiness.md).
The named-stationing lifecycle is complete in
[`stories/named-stationing.md`](stories/named-stationing.md). New stationing
offers bind named, rank-bounded fireteams without cargo mutation; incidents
freeze and settle their identities; every completion, withdrawal, failure, and
default-extraction path resolves exactly once. Anonymous saves retain their
original scalar cargo behavior. Cross-save repair, compaction protection, and
named/legacy Results presentation are covered by the full focused matrix.

The next personnel story is now contract-locked in
[`stories/captain-trait-drift.md`](stories/captain-trait-drift.md). Start Slice
1: append the two outlook traits and persist/backfill each captain's exactly-once
outlook authority. Do not add combat modifiers, transfers, reversals, or any
numeric moral surface. Manual UI/balance validation remains queued, as requested
for the current automated-only session.

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
- `605cda22` — isolate debug missions behind selectable personnel fixtures.
- `59c4864b` — add exact readiness accounting and cargo-backed bulk enlistment.
- `75413bfc` — route briefing shortfalls through assignment or the armory.
- `f6247ace` — enforce captain formation defaults and whole-fireteam briefing limits.
- `48471e93` — freeze commander/fireteam deployment context into mission outcomes.
- `49eb9297` — lock the named-stationing lifecycle contract.
- `1432dda1` — persist and bind named stationing fireteams.
- `379c4874` — lock stationed roster and armory mutations.
- `113c4603` — accept named stationing detachments without cargo.
- `d529805d` — select and manage named stationing fireteams.
- `a31e9a2a` / `0039f9b5` / `c05999e2` — close the named release lifecycle.
- `492864ef` / `1a2fc58e` / `9e4c1ac2` — freeze and settle battle identities.
- `721372d8` / `a8b72598` / `496cea19` — close compaction, repair, and debrief.
