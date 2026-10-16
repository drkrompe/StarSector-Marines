package com.dillon.starsectormarines.battle.air;

/**
 * The heaviest {@link LiftClass} a carrier can take, or {@link #NONE} for one
 * that lifts no machine at all.
 *
 * <p>Ordered, so a rating admits every class at or below its own: a heavy
 * carrier lifts a Hound as readily as a Bulwark.
 */
public enum LiftRating {
    NONE,
    LIGHT,
    MEDIUM,
    HEAVY;

    /** Whether a payload of {@code payload}'s class fits this rating. */
    public boolean admits(LiftClass payload) {
        return payload != null && ordinal() >= of(payload).ordinal();
    }

    /** The rating that exactly carries {@code payload}. */
    public static LiftRating of(LiftClass payload) {
        if (payload == null) return NONE;
        return switch (payload) {
            case LIGHT -> LIGHT;
            case MEDIUM -> MEDIUM;
            case HEAVY -> HEAVY;
        };
    }

    /** The better of two ratings. */
    public static LiftRating max(LiftRating a, LiftRating b) {
        if (a == null) return b == null ? NONE : b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
