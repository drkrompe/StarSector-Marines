package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/**
 * What one of a boat's hardpoints holds, and what a refit leaves on it.
 *
 * <p>A hardpoint is 2x2 bricks with two positions, {@link #TOP} and {@link #BOTTOM}. A
 * 2x2 gun fills it and is held in the top position; a {@linkplain BoatGun#half 2x1} gun
 * takes one position, so two of them share the mount and fly as one turret with two
 * barrels (owner pick, 2026-10-27). Either position may stand empty.
 *
 * <p>A hardpoint is passed as a two-entry array, top then bottom, with null for an empty
 * position. These rules are pure so {@link BoatWorkshop} and the Boat Deck read one
 * answer to "what comes off".
 */
public final class BoatHardpoint {

    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    /** How many positions a hardpoint has. */
    public static final int POSITIONS = 2;

    private BoatHardpoint() { }

    /** Whether {@code position} names one of a hardpoint's positions. */
    public static boolean valid(int position) {
        return position == TOP || position == BOTTOM;
    }

    /** The gun filling {@code position}: a 2x2 gun on top fills the bottom as well. Null for none. */
    public static BoatGun occupant(BoatGun[] pair, int position) {
        if (pair == null || !valid(position)) return null;
        BoatGun top = pair[TOP];
        if (top != null && !top.half()) return top;
        return pair[position];
    }

    /**
     * The hardpoint once {@code gun} goes into {@code position}, or, with null, once the gun
     * filling it comes off. A 2x2 gun takes the whole hardpoint, so it goes on top whichever
     * position it was let go over and everything there comes off; a 2x1 gun put beside a 2x2
     * one takes its own position and the 2x2 gun comes off.
     */
    public static BoatGun[] placed(BoatGun[] pair, int position, BoatGun gun) {
        BoatGun[] after = pair == null ? new BoatGun[POSITIONS] : pair.clone();
        if (!valid(position)) return after;
        if (gun != null && !gun.half()) return new BoatGun[]{gun, null};
        if (after[TOP] != null && !after[TOP].half()) after[TOP] = null;
        after[position] = gun;
        return after;
    }

    /** The guns a refit from {@code before} to {@code after} takes off, top first. */
    public static List<BoatGun> displaced(BoatGun[] before, BoatGun[] after) {
        List<BoatGun> off = new ArrayList<>(POSITIONS);
        for (int position = 0; position < POSITIONS; position++) {
            BoatGun was = before == null ? null : before[position];
            BoatGun now = after == null ? null : after[position];
            if (was != null && was != now) off.add(was);
        }
        return off;
    }

    /** The guns on a hardpoint, top first, leaving out empty positions. */
    public static List<BoatGun> guns(BoatGun[] pair) {
        List<BoatGun> guns = new ArrayList<>(POSITIONS);
        if (pair == null) return guns;
        for (BoatGun gun : pair) if (gun != null) guns.add(gun);
        return guns;
    }
}
