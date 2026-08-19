# Protected rescue shelter garrison — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `822f5a2a`

## Outcome

- The exact residential compound selected for the evacuation cohort now spawns
  a four-person local militia squad in unused walkable interior cells.
- The squad rolls weapons from the existing risk-aware randomized militia pool
  and receives a compound hold objective instead of joining the mobile escort.
- Shelter protection is symmetric. While the civilians remain sealed, the
  garrison clears paths and targets, performs no infantry planning or attacks,
  and is excluded from both sensed and strategic swarm-runner targeting.
- The garrison cannot count as its own relief force or civilian escort anchor.
  Responding marines must still reach the shelter entrance before the cohort
  begins moving.
- After relief, the militia become ordinary targetable defenders and retain
  their authored compound post as a local rear guard.
- If a responding marine force has been observed and is then wiped before
  reaching the shelter, protection releases. This exposes the last stand and
  prevents the defender elimination objective from deadlocking on untargetable
  militia.
- Factory placement retries the map when the selected residential structure
  cannot hold all four defenders without overlapping civilian spawn cells.

## Verification

- Factory coverage verifies one complete four-person militia squad, randomized
  primary weapons, and a shared positive residential building identity.
- Behavior coverage verifies the sealed squad neither fights nor draws swarm
  aggro, then becomes eligible prey after relief.
- Evacuation coverage verifies the garrison cannot self-open the barricade,
  cannot leash civilians, and releases after an observed response-force wipe.
- The full Gradle suite passes: 1,832 main-project tests and one asset-pipeline
  test, zero failures.

## Manual follow-up

Play the canonical rescue and confirm the four holdouts read as a sealed local
defense rather than visible idle AI. After relief, confirm they provide useful
rear security without pulling the civilian column back toward the compound.
