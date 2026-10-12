package com.dillon.starsectormarines.battle.control;

/**
 * Immutable input snapshot, with movement axes and world aim coordinates; the carrier interprets the axes.
 * {@code firing} is the primary trigger; {@code secondary} is the second one, which only a carrier with a
 * second battery (a warship's missiles) reads.
 */
public record ManualIntent(float moveX, float moveY, float aimX, float aimY,
                           boolean firing, boolean sprint, boolean secondary) {
    public static final ManualIntent NEUTRAL = new ManualIntent(0f, 0f, Float.NaN, Float.NaN, false, false);

    /** Carriers with one trigger have no secondary. */
    public ManualIntent(float moveX, float moveY, float aimX, float aimY, boolean firing, boolean sprint) {
        this(moveX, moveY, aimX, aimY, firing, sprint, false);
    }

    /** Legacy and non-infantry callers have no sprint command. */
    public ManualIntent(float moveX, float moveY, float aimX, float aimY, boolean firing) {
        this(moveX, moveY, aimX, aimY, firing, false, false);
    }

    public ManualIntent {
        moveX = Float.isFinite(moveX) ? Math.max(-1f, Math.min(1f, moveX)) : 0f;
        moveY = Float.isFinite(moveY) ? Math.max(-1f, Math.min(1f, moveY)) : 0f;
        boolean aimed = Float.isFinite(aimX) && Float.isFinite(aimY);
        firing = firing && aimed;
        secondary = secondary && aimed;
    }

    public ManualIntent neutralized() {
        return new ManualIntent(0f, 0f, aimX, aimY, false, false, false);
    }
}
