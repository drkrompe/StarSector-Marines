package com.dillon.starsectormarines.battle.air;

/**
 * Pure render-state derivations for an air craft from its {@code APPEARANCE}
 * component ({@code altitudeT} + {@code flightPhase}). The visual scale,
 * altitude Y-offset, and engine intensity are <em>computed</em> from those two
 * authored scalars rather than stored — there is one source of truth for "how
 * high am I right now" and everything visual falls out of it.
 *
 * <p>Stateless; the wobble/cruise/idle tunables live here as the one home for the
 * air visual-feel constants (they previously straddled {@code AirSystem} and the
 * dissolved {@code Shuttle} handle). {@code AirSystem} advances {@code altitudeT}
 * and {@code flightPhase} into the component each tick; the render + audio passes
 * read them by id and call these helpers.
 */
public final class AirAppearance {

    /** Visual scale of a craft at cruising altitude (sells "I am up high"). Ground scale is 1.0. */
    public static final float CRUISE_SCALE = 1.5f;
    /** Frequency (Hz) of the in-flight scale wobble. Slower than a heartbeat — reads as atmospheric drift, not a flicker. */
    public static final float WOBBLE_HZ = 0.7f;
    /** Peak amplitude of the wobble, in scale units. ±0.04 on top of a 1.5 cruise = ~2.7%; well inside the 5% target. */
    public static final float WOBBLE_AMPLITUDE = 0.04f;
    /** Peak screen-Y offset (cells) at {@code altitudeT == 1} to sell altitude in the top-down view. Render-only; sim-space position is unchanged. */
    public static final float VISUAL_ALT_PEAK_CELLS = 3.0f;
    /** Engine intensity while parked on the ground — quiet hum, not silent. */
    public static final float IDLE_INTENSITY = 0.3f;

    private AirAppearance() {}

    /**
     * Render scale multiplier derived from altitude + wobble phase. 1.0 on the
     * ground (the wobble is gated by {@code altitudeT}, so it dies cleanly at 0),
     * rising to ~{@link #CRUISE_SCALE} at altitude.
     */
    public static float scaleMult(float altitudeT, float flightPhase) {
        float base = 1f + (CRUISE_SCALE - 1f) * altitudeT;
        float wobble = (float) Math.sin(flightPhase) * WOBBLE_AMPLITUDE * altitudeT;
        return base + wobble;
    }

    /** Render-only Y offset (cells) added to {@code body.y} to sell altitude in the top-down view. */
    public static float visualAltitudeOffsetCells(float altitudeT) {
        return altitudeT * VISUAL_ALT_PEAK_CELLS;
    }

    /**
     * Normalized engine loudness/pitch driver for the engine loop + FX, in [0, 1].
     * Full throttle at cruise, idles on the ground, blends via {@code altitudeT}.
     * Off-map craft (PENDING/GONE) return 0 so they don't contribute — callers
     * pass {@code onMap == false} for those (today every caller pre-filters them).
     */
    public static float engineIntensity(boolean onMap, float altitudeT) {
        if (!onMap) return 0f;
        return IDLE_INTENSITY + (1f - IDLE_INTENSITY) * altitudeT;
    }
}
