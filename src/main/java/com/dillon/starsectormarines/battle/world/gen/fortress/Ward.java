package com.dillon.starsectormarines.battle.world.gen.fortress;

/**
 * The depth a fortress building belongs at, measured from the side the attacker
 * comes from.
 *
 * <p>The compound answer to a deck's longitudinal zone, and a functional prior
 * rather than decoration: the gatehouse and the motor pool have to be able to
 * get out, the barracks want to be off the parade ground, and the magazine and
 * the keep are put as far from the wall as the ground allows. A ward is how a
 * building finds its depth without a coordinate table.
 *
 * <p>Deliberately three bands and not a continuous gradient. A building either
 * belongs near the wall, in the middle, or at the back; a finer scale would
 * imply a precision the packer cannot honour once shapes start wedging against
 * each other.
 */
public enum Ward {

    /** Against the wall the attacker faces: the gatehouse, guard posts, the motor pool. */
    FRONTAGE,
    /** The working middle: barracks, workshops, stores, the parade ground between them. */
    YARD,
    /** As deep as the ground allows: the keep, the magazine, the generator hall. */
    REAR
}
