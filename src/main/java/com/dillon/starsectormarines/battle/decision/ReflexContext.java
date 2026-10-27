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
 *        withholds it, because satchel, frag and deployable each
 *        spend a squad resource or freeze the carrier mid-bound. False also
 *        narrows rather than silences: the hardened-target reflex exists for
 *        exactly that case.
 */
public record ReflexContext(boolean opportunityFirePermitted) {

    private static final ReflexContext PERMITTED = new ReflexContext(true);
    private static final ReflexContext WITHHELD = new ReflexContext(false);

    /**
     * The shared instance for {@code opportunityFirePermitted}. A context holds
     * one boolean and is built for every unit every tick, so it is handed out
     * rather than allocated.
     */
    public static ReflexContext of(boolean opportunityFirePermitted) {
        return opportunityFirePermitted ? PERMITTED : WITHHELD;
    }
}
