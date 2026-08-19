# Rescue playtest safety and spacing — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `bf6fdd64`

## Outcome

- Marine direct-fire resolution omits civilian-faction bodies from both locked
  and incidental unit contacts. Defender fire still targets and intersects
  civilians normally, so aliens remain the threat to the evacuation cohort.
- Swarm avoidance still bends, slows, or reverses authored marine movement,
  but its combined velocity is now capped at the unit's movement-speed stat.
- Rescue militia retain the distinct formation point assigned by their
  delivery shuttle. The recurring rescue commander no longer overwrites all
  five posts with the shared lift-center objective; an unassigned guard still
  falls back to the center as a defensive recovery path.

## Verification

- Focused resolver coverage proves marine rounds pass a colonist and reach the
  locked hostile, while defender rounds can still target that colonist.
- Focused avoidance coverage proves perpendicular pathing plus avoidance stays
  within both the movement-speed and per-tick displacement limits.
- Focused commander coverage proves authored guard posts survive command ticks
  and missing posts receive the center fallback.
- `gradlew.bat test` passes 1,811 root tests plus the asset-pipeline test
  (1,812 total).

## Manual follow-up

Play the rescue mission through several reinforcement cycles and confirm the
five militia squads visibly occupy the full star after deboarding and after
contact. Friendly explosive splash remains a separate physical damage path;
this correction is scoped to direct-fire target and stray-round eligibility.
