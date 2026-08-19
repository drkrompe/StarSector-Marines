# 24 — Engagement discipline

**Shipped 2026-08-19 in `43c619ff` and `8888e6f8`.**

## Player-visible contract

When an infantry target withdraws into a hostile cluster, its pursuers stop at
their last useful firing line. They may keep shooting enemies already visible
and in range, but they do not path toward the clustered target. The behavior is
faction-neutral: marine and defender infantry use the same rule.

The squad debug state must make the decision legible: the rejected target and
its nearby-hostile count are recorded, the goal remains an engagement goal,
and the current action reads `Overwatch` while the hold is active.

## Shipped decision boundary

- Threat density is the count of other hostile combatants within four cells of
  a candidate. Two or more neighbors make movement toward that candidate
  unsafe.
- A visible target already inside the acting infantry member's effective
  weapon range remains legal: firing without advancing does not violate the
  discipline rule.
- A target requiring movement is rejected when it is inside a high-density
  cluster, whether or not it is currently visible. This closes the existing
  visible-but-out-of-range chase as well as the original LOS-loss case.
- Before holding, the member may switch to the best visible candidate below
  the density threshold. The ordinary target picker keeps its blind fallback
  for patrol, objective, turret, and initial-contact callers; engagement
  discipline uses an explicit visible/safe query so it cannot immediately
  reacquire the rejected runner.
- With no safe alternative, the decision latches at squad scope. Every member
  clears its generic pursuit target, the current plan fails, and the next
  replan selects an `Overwatch` hold. A member already behind wall/doodad
  cover plants; an exposed member first takes a short lateral/backward path to
  real cover, or uses the existing away-biased fallback when none is local.
  Opportunity fire remains enabled throughout.
- The latch releases when the rejected target dies, its cluster falls below
  the density threshold, or any member sees a low-density alternative.

The original density read deliberately used current ground truth as a named
swap site. Story 25 (`db69ed73`) has since replaced the generic infantry path
with per-squad believed-contact density: only formation members that squad has
actually observed contribute to its pursuit hold.

## Cohesion boundary

Generic `Approach` and the out-of-range branch of `Engage` may not author a
path whose cells extend beyond 12 cells from the current squad centroid. The
path is clipped at the last in-leash cell, so the constraint applies to the
route as well as its destination. Existing regroup behavior remains the
backstop for a member already outside the leash.

Mission-authored movement is not clipped. `EnterZone`, bounding overwatch,
flanking, breach, fallback, cordon, escort, and mech-screen formations have
their own objective/leash contracts and are explicit permission to move.

## Existing substrate and closure

The May 2026 implementation already added density cost to target scoring and a
partial `shouldKeepPursuing` gate. It did not close the story:

- `findBestTarget` falls back to the nearest non-visible enemy and can
  immediately reacquire the runner the gate just dropped;
- visible clustered targets are always retained even when reaching them
  requires movement;
- cohesion reacts only after a member has crossed the radius; and
- `THREAT_DENSITY_HIGH_AT_TARGET` is still a stub with no diagnostic hold
  state.

This slice preserves the useful scoring substrate and closes those boundaries.

## Acceptance coverage

- Marine and defender fixtures both drop a clustered runner and hold without a
  pursuit path.
- A visible isolated alternative is selected instead of entering the hold.
- A visible, out-of-range clustered target is not chased; an in-range target
  can still be fired on from the current position.
- Cluster dispersal and target death release the hold.
- `THREAT_DENSITY_HIGH_AT_TARGET` and the squad dump expose the live latch,
  rejected target, and counted density without a schema-version field.
- Generic pursuit paths never leave the cohesion radius; explicit objective
  actions retain their existing movement contracts.
- Held members already in threat-facing cover plant, while exposed members
  settle into nearby lateral/backward wall or doodad cover without closing on
  the rejected formation.

## Verification

- `EngagementDisciplineTest` covers marine and defender pursuit release,
  visible and hidden clusters, isolated retargeting, held opportunity fire,
  cover settling, death/dispersal release, GOAP selection, and both generic
  posture leash call sites.
- Existing tactical-scoring and Overwatch posture tests passed.
- Full `gradlew.bat build` passed before integration.

## Out of scope

- Commander influence maps and cross-squad belief sharing.
- Cross-squad coordination.
- Suppression, surrender, or morale changes.
- Changing objective-specific advance, breach, flank, fallback, or formation
  leashes.
