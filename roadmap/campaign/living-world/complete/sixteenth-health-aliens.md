# Sixteenth-health aliens — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `15a11674`

## Outcome

- Generic `ALIEN` health is halved from 3.75 to 1.875 HP.
- Rescue `SWARM_RUNNER` health is halved from 3 to 1.5 HP.
- Both archetypes now carry one sixteenth of their original 30/24 HP pools.
  Their speed, damage, avoidance pressure, targeting, and population cadence
  are unchanged.

## Marine weapon breakpoints

Against either alien with service-grade equipment, landed rounds now kill in:

- two pulse-rifle hits at 1.0 damage each;
- three SMG hits at 0.7 damage each;
- one DMR hit at 4.0 damage.

A three-round pulse burst therefore needs any two hits, while an SMG burst must
land all three. The normal equipment-grade damage range is 0.95x–1.08x; these
breakpoints remain stable except that a masterwork SMG can kill a 1.5-HP runner
in two hits.

## Verification

- `SwarmRunnerContractTest` locks the exact health pools, retained 0.8 runner
  ratio, and service-grade pulse/SMG/DMR hit counts.
- `gradlew.bat test` passes 1,807 root tests plus the asset-pipeline test
  (1,808 total).
