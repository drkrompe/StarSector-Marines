package com.dillon.starsectormarines.battle.control;

/** Immutable input snapshot, with movement axes and world aim coordinates; the carrier interprets the axes. */
public record ManualIntent(float moveX, float moveY, float aimX, float aimY, boolean firing) {
    public static final ManualIntent NEUTRAL = new ManualIntent(0f, 0f, Float.NaN, Float.NaN, false);

    public ManualIntent {
        moveX = Float.isFinite(moveX) ? Math.max(-1f, Math.min(1f, moveX)) : 0f;
        moveY = Float.isFinite(moveY) ? Math.max(-1f, Math.min(1f, moveY)) : 0f;
        firing = firing && Float.isFinite(aimX) && Float.isFinite(aimY);
    }

    public ManualIntent neutralized() {
        return new ManualIntent(0f, 0f, aimX, aimY, false);
    }
}
