package com.dillon.starsectormarines.battle.world.gen.layout;

/** What a {@link Site} holds its ground for. */
public enum SitePurpose {
    /** Open ground for a set piece or an event: reported, and kept clear of trees, rises and props once laid. */
    OPEN,
    /**
     * Ground held for authored terrain: kept clear of roads, buildings, fields and works from before the places grow,
     * and left free for ridges and rises.
     */
    GROUND,
    /**
     * Ground held for the layout's own buildings: kept clear of the town's roads, buildings and fields and of a
     * garrison as {@link #GROUND} is, but its {@link Structure}s are raised on it (a port's warehouse rows between
     * its lines).
     */
    BUILT;

    /** Whether the ground is held off the places' growth, their fields and a garrison's square claim. */
    public boolean held() {
        return this == GROUND || this == BUILT;
    }

    /** Whether the layout's structures are raised on it; every other site holds its ground against them. */
    public boolean takesStructures() {
        return this == BUILT;
    }
}
