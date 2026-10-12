package com.dillon.starsectormarines.battle.warship;

import java.awt.Color;

/**
 * The moment a carrier let its warheads go: a flash and a puff of smoke at the
 * split point, kept for a moment so the presentation can draw it once for the
 * whole sheaf rather than once per warhead.
 */
public final class SplitEvent {

    /** How long a split stays readable, in sim-seconds. */
    public static final float SECONDS = 0.7f;

    public final VanillaWeapon weapon;
    public final float x;
    public final float y;
    /** The relative size of the flash. */
    public final float scale;
    /** The smoke the carrier's spec names, or {@code null}. */
    public final Color smoke;
    private float age;

    SplitEvent(VanillaWeapon weapon, float x, float y, float scale, Color smoke) {
        this.weapon = weapon;
        this.x = x;
        this.y = y;
        this.scale = scale;
        this.smoke = smoke;
    }

    /** Seconds since the split. */
    public float age() {
        return age;
    }

    /** 1 at the instant of the split fading to 0. */
    public float freshness() {
        return Math.max(0f, 1f - age / SECONDS);
    }

    boolean advance(float dt) {
        age += dt;
        return age >= SECONDS;
    }
}
