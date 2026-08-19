# Rescue pre-touchdown swarm roaming — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `100110c2`

## Outcome

- Rescue runners no longer stand motionless during the shuttle fly-in merely
  because every ground target is still protected or airborne.
- A targetless runner selects a deterministic local roaming destination and
  continues along that route until arrival.
- Roaming rejects occupied, unwalkable, unreachable destinations and rejects
  any complete path that crosses the protected shelter or civilian pickup
  footprints. The opening grace space therefore remains intact.
- Once a marine becomes eligible, ordinary opportunistic target selection
  replaces the roaming route immediately.
- The behavior remains deterministic and also gives a targetless pressure
  runner sensible motion outside rescue missions.

## Verification

- Behavior coverage proves a targetless protected runner receives a non-empty
  route whose every cell remains outside both opening protection zones.
- Factory coverage advances the production rescue by one tick and proves the
  opening swarm has begun roaming while the shelter remains protected and no
  shuttle has deboarded.
- The full Gradle suite passes: 1,849 main-project tests and one asset-pipeline
  test, zero failures.

## Manual follow-up

Watch the canonical DEBUG rescue fly-in and confirm the opening swarm reads as
locally restless without noticeably collapsing toward either objective before
the first marines touch down.
