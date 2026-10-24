package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * The height a Harrier is drawn and shadowed at: one continuous value that follows the flight's own
 * {@link HarrierFlight#altitude} on a critically damped spring, its climb capped, so the shadow's offset and
 * size glide where the flight's height turns a corner (into a dive, out of a strike). The beat's pump is a small
 * wobble on top. Presentation only: flak, reach and the dive read the flight's own altitude.
 */
public final class HarrierAltitude {

    /**
     * The height a loitering Harrier casts its shadow from, in the sun's units. Lower than an aircraft's: it flies
     * just over the rooftops, and its shadow should sit a wingspan or two off it, not across the street.
     */
    public static final float SHADOW_HEIGHT_CELLS = 4f;
    /** How quickly the drawn height settles on the flight's, seconds. */
    static final float SETTLE_SECONDS = 0.15f;
    /** The fastest the drawn height climbs or sinks, altitude per second. */
    static final float MAX_RATE = 1.0f;
    /** How much of the pump reaches the shadow's height: a wobble, not a hop. */
    static final float PUMP_WOBBLE = 0.5f;

    private HarrierAltitude() {}

    /** Moves {@code m}'s drawn height one step toward its flight altitude. */
    static void follow(HarrierFlight m, float dt) {
        float w = 2f / SETTLE_SECONDS;
        float accel = w * w * (m.altitude - m.shownAltitude) - 2f * w * m.shownRate;
        m.shownRate = Math.max(-MAX_RATE, Math.min(MAX_RATE, m.shownRate + accel * dt));
        m.shownAltitude = Math.max(0f, Math.min(1f, m.shownAltitude + m.shownRate * dt));
    }

    /** The height, in cells, {@code m}'s shadow is laid down-sun by. */
    public static float shadowHeightCells(HarrierFlight m) {
        return SHADOW_HEIGHT_CELLS * m.shownAltitude * (1f + (m.rig.pump - 1f) * PUMP_WOBBLE);
    }

    /** The scale {@code m}'s shadow is drawn at: a stroke that lifts the body shrinks it a little. */
    public static float shadowScale(HarrierFlight m) {
        return 1f / (1f + (m.rig.pump - 1f) * 2f * PUMP_WOBBLE);
    }
}
