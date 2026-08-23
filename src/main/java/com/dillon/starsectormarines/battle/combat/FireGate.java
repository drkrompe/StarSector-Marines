package com.dillon.starsectormarines.battle.combat;

/**
 * Durable result of the most recently consumed primary-fire intent. The
 * consume-once target itself is gone by the time presentation and dump tools
 * run, so this value records why the intent did or did not become a shot.
 */
public enum FireGate {
    NONE,
    FIRED,
    REGISTERING,
    COOLDOWN,
    TARGET_GONE,
    OUT_OF_RANGE,
    NO_LOS;

    public static final FireGate[] VALUES = values();
}
