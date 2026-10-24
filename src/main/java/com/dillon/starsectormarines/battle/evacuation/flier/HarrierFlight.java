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
    /**
     * The height it is drawn and shadowed at, following {@link #altitude} smoothly ({@link HarrierAltitude}),
     * and how fast that is changing. Presentation only.
     */
    public float shownAltitude = 1f, shownRate;
    /** Whether it is done with this dive: its strike landed, or its prey was lost. */
    public boolean struck;
    /**
     * Its own prey on this dive, and where that prey is: each member of a pack dives on its own body, near the
     * one the pack chose ({@link HarrierTargeting#assign}). 0 for the pack's own target.
     */
    public long prey;
    public float preyX, preyY, preyRadius;
    /** Seconds into a dive it keeps circling before it goes in, so a pack comes in staggered. Seeded. */
    public float diveDelay;
    /** Its place round the loiter ring. */
    public int slot;
    /** Wingbeat clock, seconds; seeded per body so a pack does not flap in step. Presentation only. */
    public float wingClock;
    /**
     * How far its wings are folded for the dive, 0 spread to 1 folded: it folds over {@link #FOLD_SECONDS} as it
     * goes into a dive and opens again as it climbs out. Presentation only.
     */
    public float fold;
    /** Its body under the wings: waist, tail, head and pump ({@link HarrierBodyRig}). Presentation only. */
    public final HarrierBodyRig rig = new HarrierBodyRig();
    /** Whether it went low on the dive in hand: the dive half of dive-and-pull-up. */
    public boolean wentLow;

    public HarrierFlight(long id, AirBody body, HarrierLook look) {
        this.id = id;
        this.body = body;
        this.look = look;
    }
}
