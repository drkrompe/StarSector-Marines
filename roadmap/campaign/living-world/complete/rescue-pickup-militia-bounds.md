# Rescue pickup, militia reserve, and engaged bounds — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `2395397f`

## Outcome

- Civilian rescue now includes a physical, unarmed civilian Valkyrie at the
  outer-band pickup. Evacuees can enter the pickup zone while it approaches,
  but remain on the ground until its shuttle mission reaches `LANDED`.
- Boarding removes each representative from the ground roster, records it in
  the shuttle's passenger count, and releases the craft for departure only
  after every representative is evacuated or lost.
- Eight local allied militia begin in two fixed pickup-perimeter squads. Their
  rescue-guard identity keeps them out of the mobile escort command and out of
  the shelter-relief lead-squad election.
- When fewer than five pickup guards remain, an unarmed Aeroshuttle sortie
  restores up to four militia without letting live plus inbound guards exceed
  the eight-unit cap. A twelve-second cadence prevents repeated dispatches,
  and the reserve stops when the rescue cohort resolves.
- The support shuttle lands seven route cells inward from the occupied pickup
  Valkyrie, avoiding overlapping craft while keeping reinforcement travel local
  to the defense line.
- The mobile escort now uses a monotonic route screen. Out of contact it keeps
  the existing five-cell lead; while engaged it holds that screen and ratchets
  it forward by two cells every five seconds. This preserves pressure-sensitive
  slowdown without allowing the column to stop or fall backward.

## Verification

- Focused tests cover landed-before-boarding behavior, passenger accounting,
  engaged timed bounds, non-regression, fixed pickup assignments, shelter-lead
  exclusion, initial militia strength, and casualty-triggered capped sorties.
- The full root `gradlew test` suite passes, including map, rendering, air,
  evacuation, and asset-pipeline regressions.

## Manual follow-up

Play LOW/MEDIUM/HIGH rescue missions to tune the five-second/two-cell bound,
eight/five/four militia population policy, twelve-second reserve cadence, and
the visual spacing between the pickup Valkyrie and militia Aeroshuttle LZ.
