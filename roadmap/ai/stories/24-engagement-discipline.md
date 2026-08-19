# Story 24 — Engagement discipline

## Player-visible contract

When an infantry target withdraws into a hostile cluster, its pursuers stop at
their last useful firing line. They may keep shooting enemies already visible
and in range, but they do not path toward the clustered target. The behavior is
faction-neutral: marine and defender infantry use the same rule.

The squad debug state must make the decision legible: the rejected target and
its nearby-hostile count are recorded, the goal remains an engagement goal,
and the current action reads `Overwatch` while the hold is active.

## Decision boundary

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
  clears its generic pursuit path and target, the current plan fails, and the
  next replan selects an `Overwatch` hold. Opportunity fire remains enabled.
- The latch releases when the rejected target dies, its cluster falls below
  the density threshold, or any member sees a low-density alternative.

The density read deliberately uses current ground truth. It is a cheap tactical
down-payment and a named swap site for the future per-squad belief map; this
slice does not build a new perception system.

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

## Out of scope

- Squad belief maps or commander influence maps.
- Cross-squad coordination.
- Suppression, surrender, or morale changes.
- Changing objective-specific advance, breach, flank, fallback, or formation
  leashes.
