package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * Where a Harrier is in its wingbeat, off its own clock ({@link HarrierFlight#wingClock}): a loitering flier at
 * height glides and beats in short bursts that start and end on the glide; one climbing out of a dive, or low,
 * beats steadily and harder. The drawing ({@code HarrierPose}) and the body's pump and tail sway
 * ({@link HarrierBodyRig}) read the same phase. Presentation only.
 */
public final class HarrierWingbeat {

    /** Seconds per wingbeat climbing out or flying low. */
    public static final float BEAT_SECONDS = 0.32f;
    /** Seconds per wingbeat in a loiter burst: an easier stroke. */
    public static final float LOITER_BEAT_SECONDS = 0.4f;
    /** Wingbeats in one loiter burst, out of every {@link #GLIDE_CYCLE_SECONDS}. */
    public static final int LOITER_BEATS = 2;
    public static final float GLIDE_CYCLE_SECONDS = 2.2f;
    /** At or above this altitude a loitering flier is cruising, and glides between bursts. */
    public static final float CRUISE_ALTITUDE = 0.95f;

    private HarrierWingbeat() {}

    /**
     * The phase of the stroke, 0 to 1 from wings spread through the upstroke (to 0.5) and the downstroke back to
     * spread; {@code -1} while it glides.
     */
    public static float phase(HarrierFlight flight, HarrierPack.Phase phase) {
        if (phase == HarrierPack.Phase.LOITER && flight.altitude >= CRUISE_ALTITUDE) {
            float c = flight.wingClock % GLIDE_CYCLE_SECONDS;
            if (c >= LOITER_BEATS * LOITER_BEAT_SECONDS) return -1f;
            return (c / LOITER_BEAT_SECONDS) % 1f;
        }
        return (flight.wingClock / BEAT_SECONDS) % 1f;
    }

    /**
     * How high the stroke carries the body, -1 to 1: it settles as the wings come up and lifts as they drive
     * down; 0 while it glides.
     */
    public static float lift(float phase) {
        return phase < 0f ? 0f : (float) -Math.sin(2 * Math.PI * phase);
    }
}
