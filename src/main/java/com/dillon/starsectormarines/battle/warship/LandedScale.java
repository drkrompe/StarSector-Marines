package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;

/**
 * The true scale a ship is drawn at while it sits in a berth, and the ease that carries its body to the airborne
 * scale as it lifts ({@code landed-ships-true-scale.md}).
 *
 * <p>The convention is the grounded Shepherd's: Morrow's 92-pixel hull blocks 24 cells of ground beside one-cell
 * infantry, so a landed hull is {@link #CELLS_PER_HULL_PX} cells per pixel of its vanilla sprite: a Hound is about
 * nineteen cells long and a Venture about fifty. In flight a hull is drawn at {@link WarshipScale#HULL_PRESENCE}
 * times the shared air density, which is smaller. A lifting hull eases from one to the other over
 * {@link #LIFT_SECONDS} rather than changing size at once, so nothing pops; the factor is a ratio to whatever
 * flight presence is in force.
 */
public final class LandedScale {

    /** Cells per vanilla hull pixel for a landed ship: Morrow's 24 cells over her 92-pixel sprite. */
    public static final float CELLS_PER_HULL_PX = 24f / 92f;

    /** Seconds over which a lifting hull's body eases from berth to flight scale. */
    public static final float LIFT_SECONDS = 6f;

    private LandedScale() {}

    /** A landed hull's size as a multiple of the shared air density (about 5.8). */
    public static float berthPresence() {
        return CELLS_PER_HULL_PX * AirScale.METERS_PER_CELL / AirScale.METERS_PER_PX;
    }

    /** How many times larger a landed body is drawn than the same hull in flight. */
    public static float berthFactor() {
        return berthFactor(WarshipScale.HULL_PRESENCE);
    }

    /** {@link #berthFactor()} against a stated flight presence. */
    public static float berthFactor(float flightPresence) {
        if (!(flightPresence > 0f)) throw new IllegalArgumentException("Flight presence must be positive");
        return berthPresence() / flightPresence;
    }

    /**
     * The body factor of a hull that sat at {@code berthFactor} and has lifted for {@code liftSeconds}: the berth
     * factor until it lifts (negative seconds mean it has not), easing by smoothstep to one at
     * {@link #LIFT_SECONDS}, and one thereafter.
     */
    public static float bodyFactor(float berthFactor, float liftSeconds) {
        if (liftSeconds < 0f) return berthFactor;
        float t = Math.min(1f, liftSeconds / LIFT_SECONDS);
        float eased = t * t * (3f - 2f * t);
        return berthFactor + (1f - berthFactor) * eased;
    }

    /** How far through its lift a hull is, zero while berthed, one once lifted (or never berthed). */
    public static float liftProgress(float liftSeconds) {
        return liftSeconds < 0f ? 0f : Math.min(1f, liftSeconds / LIFT_SECONDS);
    }
}
