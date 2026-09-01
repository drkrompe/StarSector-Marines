package com.dillon.starsectormarines.battle.decision;

/**
 * What a {@link Reflex} needs to know about the tick it is running in, gathered
 * once by the dispatcher before the chain runs.
 *
 * <p>A reflex reads the world for itself; this carries only the facts the
 * dispatcher already holds and a reflex has no way to recover — today just
 * whether the step this unit is assigned to would tolerate a shot of
 * opportunity. A record rather than a widening parameter list, so the next
 * reflex can want a second fact without every existing one changing shape.
 *
 * @param opportunityFirePermitted whether the unit's assigned step tolerates
 *        opportunistic special-equipment fire. A move-only coordinated role
 *        withholds it, because satchel, frag, deployable and close-contact each
 *        spend a squad resource or freeze the carrier mid-bound. False also
 *        narrows rather than silences: the hardened-target and onset-screen
 *        reflexes exist for exactly that case.
 */
public record ReflexContext(boolean opportunityFirePermitted) {
}
