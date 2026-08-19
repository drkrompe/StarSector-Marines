# Rescue pickup, militia reserve, and engaged bounds — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `2395397f`, `b6b0dd6f`

## Outcome

- Civilian rescue now includes a physical, unarmed civilian Valkyrie at the
  outer-band pickup. Evacuees can enter the pickup zone while it approaches,
  but remain on the ground until its shuttle mission reaches `LANDED`.
- Boarding removes each representative from the ground roster, records it in
  the shuttle's passenger count, and releases the craft for departure only
  after every representative is evacuated or lost.
- The pickup footprint stays empty through a twelve-second opening grace period
  so the player's force can enter and draw swarm pressure before the position
  becomes active. The civilian Valkyrie and first militia Aeroshuttle then
  launch, with the remaining defender sorties staggered four seconds apart.
- Eight local allied militia arrive as two physical four-person Aeroshuttle
  drops and form fixed pickup-perimeter squads. Their rescue-guard identity
  keeps them out of the mobile escort command and out of the shelter-relief
  lead-squad election.
- A Valkyrie between those militia drops delivers one local heavy mech. Battle
  seed parity chooses a Bulwark armored backstop or Sirocco long-range support
  unit, and the mech's own rescue-guard squad remains anchored to the pickup.
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
  the empty opening grace period, staged militia/mech cargo, deterministic mech
  choice, engaged timed bounds, fixed pickup assignments, shelter-lead
  exclusion, and casualty-triggered capped sorties.
- The full root `gradlew test` suite passes 1,765 root tests and the
  asset-pipeline test, including map, rendering, air, and evacuation regressions.

## Manual follow-up

Play LOW/MEDIUM/HIGH rescue missions to tune the five-second/two-cell bound,
eight/five/four militia population policy, twelve-second opening and reserve
cadences, four-second arrival spacing, mech variant mix, and the visual spacing
between the pickup Valkyrie and support LZ.
