package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * One Harrier in the air: its body (the world {@code KINEMATICS} column holds the same instance), how high it is,
 * and where it is in its pack's dive.
 */
public final class HarrierFlight {

    /** Seconds to fold the wings from spread to the dive, or open them again. */
    public static final float FOLD_SECONDS = 0.3f;

    public final long id;
    public final AirBody body;
    /** Which drawing it wears ({@link HarrierLook}); presentation only. */
    public final HarrierLook look;
    /** 0 at the bottom of a dive, 1 loitering. */
    public float altitude = 1f;
    /** Whether it has landed its strike on this dive. */
    public boolean struck;
    /** Its place round the loiter ring. */
    public int slot;
    /** Wingbeat clock, seconds; seeded per body so a pack does not flap in step. Presentation only. */
    public float wingClock;
    /**
     * How far its wings are folded for the dive, 0 spread to 1 folded: it folds over {@link #FOLD_SECONDS} as it
     * goes into a dive and opens again as it climbs out. Presentation only.
     */
    public float fold;
    /** Whether it went low on the dive in hand: the dive half of dive-and-pull-up. */
    public boolean wentLow;

    public HarrierFlight(long id, AirBody body, HarrierLook look) {
        this.id = id;
        this.body = body;
        this.look = look;
    }
}
