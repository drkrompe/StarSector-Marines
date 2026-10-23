package com.dillon.starsectormarines.battle.world.gen.layout;

/** When in generation a {@link Deferred} piece is resolved against the ground. */
public enum Phase {
    /** Once the places are built and before the map is finished: structures are raised here. */
    STRUCTURES,
    /** Once the places, roads and lanes stand, before the landscape grows round them. */
    WORKS
}
