# Story 20 — Bounding overwatch ✅ SHIPPED

Shipped 2026-08-19 in `b72819fc`.

## Slice contract

This slice closes the two remaining items originally grouped as Slice 4:

- **Story C — bounding overwatch:** ship the visible two-team leapfrog during
  a threat-committed objective advance.
- **Story F — objective rush:** record as already shipped by `c54991d4`.
  `CordonForPlant` assigns one planter and per-portal holders, while
  `HoldPortalCordon` keeps the planter moving/channeling without firing and
  lets every non-planter hold and fight. No duplicate implementation belongs
  in this slice.

Story C builds on Story 19's threat-scored `EnterZone` approach. A weak or
retreating contact still produces moving shots of opportunity while the squad
presses the objective. Once the route-threat hysteresis commits the squad,
`EnterZone` partitions the assigned members into two stable teams and runs a
bounded leapfrog instead of sending every member to an independent firing
cell.

## Behavior

`EnterZone.roles` exposes `team:a` and `team:b`, split as evenly as the live
squad permits. The plan's existing `RoleAssigner` wiring freezes membership
for the life of the step.

Bounding starts only when all of these are true:

- Story 19's route score is committed;
- the primary threat is still alive;
- both teams contain at least one live member;
- the initial overwatch team has at least one legal in-range, line-of-sight
  primary shot; and
- a distinct, reachable forward firing cell can be found for every bounder.

If any gate fails, `EnterZone` retains Story 19's existing committed-contact
behavior. This matters at walls, sealed rooms, for one-member remnants, and
when the squad has detected a route threat before anybody can cover a move.

During a bound:

1. The overwatch team clears its movement path, holds position, and queues
   stanced primary fire at the route threat when it has a legal shot.
2. The bounding team moves to frozen, distinct forward cells and does not
   queue primary or secondary shots of opportunity.
3. Once every surviving bounder reaches its assigned cell, the phase flips.
   The arrived team becomes overwatch; the previous overwatch team receives a
   new set of cells beyond the current forward line.
4. The cycle repeats until a member enters the target zone, the threat score
   releases, or a phase can no longer produce legal positions. Release clears
   bounding state immediately and resumes the normal objective route.

The forward-cell picker searches around a stride point on the objective axis.
Candidates must be walkable, reachable, ahead of the member, in that member's
weapon range, and have line of sight to the current threat. It ranks directional
terrain/doodad cover first, then proximity to the stride point, and reserves a
different cell for each bounder. Each new phase advances the stride point past
the prior phase so the teams visibly leapfrog rather than oscillating in place.

## Suppression semantics

This slice represents suppression as real covering fire, not a new status
effect. `ENEMY_SUPPRESSED` exists as a reserved Stage 2 predicate but the combat
model has no morale/accuracy consumer for it; setting the bit would be inert.
Mechanical suppression remains a future combat-model story. Story C's shipped
contract is the observable coordination: one team fires from a halt while the
other moves without firing, then roles swap.

## State and diagnostics

Mutable phase state is squad-scoped and guarded by `squad.lock`: active flag,
phase number, target zone/destination, threat id, stride point, and immutable
member-to-cell arrays for the current bounding team. It never lives on the
parameterized `EnterZone` action.

Squad-state JSON advances to schema v7 and includes the phase and assigned
bounding destinations. The plan dump already exposes the stable `team:a` /
`team:b` membership.

## Acceptance

- Four-member `EnterZone` plans assign two members to each team.
- With a committed in-range route threat, one team holds and authors stanced
  fire while the other receives distinct forward paths and authors no fire.
- Reaching all bound cells flips the roles and produces a farther forward set
  of destinations for the former overwatch team.
- One-member squads, no-fire-solution contacts, or failed cover searches fall
  back to Story 19 behavior.
- Threat release clears the phase and restores ordinary objective movement.
- Existing Story 19 commit/release tests remain green.

## Verification

`BoundingOverwatchTest` covers the even role split, simultaneous stanced
overwatch and move-only bounding, distinct cells, role reversal, objective-path
restoration on threat release, Story 19 fallback without a firing solution,
secondary-fire exclusion, and directional-cover preference. The full
`gradlew.bat build` gate passed after implementation.
