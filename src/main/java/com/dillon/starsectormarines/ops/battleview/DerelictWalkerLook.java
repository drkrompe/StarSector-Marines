package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;

/**
 * Which derelict walker rig a battle draws while the owner's wall-reach pick is open (`derelict-security-network.md`,
 * The walker's legs): legs reaching to grip walls, or walking the floor only.
 */
final class DerelictWalkerLook {

    /**
     * Whether the walker's legs reach to plant on walls (default true). Temporary A/B for the owner's
     * derelict-walker-wall-reach pick: remove with the losing arm once it is answered.
     */
    static final String WALL_REACH_PROPERTY = "battle.derelictWalker.wallReach";

    private DerelictWalkerLook() {
    }

    /** A new walker rig in the look the switch names. */
    static DerelictLegRig newRig() {
        return new DerelictLegRig(Boolean.parseBoolean(System.getProperty(WALL_REACH_PROPERTY, "true")));
    }
}
