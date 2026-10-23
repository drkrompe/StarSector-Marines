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
    NO_LOS,
    INVALID_AIM,
    UNSUPPORTED_WEAPON,
    FRIENDLY_TARGET,
    /** The shooter {@linkplain StaggerService can't act}: knocked down, stalled or staggered. */
    STAGGERED,
    /** The primary's magazine is empty or being reloaded. */
    RELOADING,
    /** A friend stands in the lane to the target; the trigger is held until it clears. */
    FRIENDLY_IN_LANE;

    public static final FireGate[] VALUES = values();
}
