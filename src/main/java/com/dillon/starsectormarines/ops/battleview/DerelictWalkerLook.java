package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;

/**
 * Which derelict walker rig a battle draws while the owner's pick is open (`derelict-security-network.md`, slice 9):
 * three legs or four, reaching to walls or walking the floor only.
 */
final class DerelictWalkerLook {

    /**
     * Legs on the walker, 3 or 4 (default 4). Temporary A/B for the owner's derelict-walker-legs pick: remove with the
     * losing arm once it is answered.
     */
    static final String LEGS_PROPERTY = "battle.derelictWalker.legs";
    /**
     * Whether the walker's legs reach to plant on walls (default true). Temporary A/B for the owner's
     * derelict-walker-wall-reach pick: remove with the losing arm once it is answered.
     */
    static final String WALL_REACH_PROPERTY = "battle.derelictWalker.wallReach";

    private DerelictWalkerLook() {
    }

    /** A new walker rig in the look the switches name. */
    static DerelictLegRig newRig() {
        int legs = Integer.getInteger(LEGS_PROPERTY, 4) == 3 ? 3 : 4;
        boolean wallReach = Boolean.parseBoolean(System.getProperty(WALL_REACH_PROPERTY, "true"));
        return new DerelictLegRig(legs, wallReach);
    }
}
