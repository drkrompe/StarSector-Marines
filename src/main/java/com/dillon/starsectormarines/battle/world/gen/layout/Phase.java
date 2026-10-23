package com.dillon.starsectormarines.battle.world.gen.layout;

/** When in generation a {@link Deferred} piece is resolved against the ground. */
public enum Phase {
    /** Before the places are built (reserved for structures; not run yet). */
    STRUCTURES,
    /** Once the places, roads and lanes stand, before the landscape grows round them. */
    WORKS
}
