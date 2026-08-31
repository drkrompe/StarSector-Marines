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

    /**
     * Visual scale of a craft standing on the ground.
     *
     * <p>An aircraft's drawn size comes from its hull's own sprite height (see
     * {@code HullFootprintResolver}), which puts a fighter at a true-to-scale
     * handful of cells — correct, and far too small to read on a map where the
     * thing next to it is a marine. This is the deliberate departure from that
     * scale, applied on the ground and carried up with the altitude term, so a
     * parked aircraft is a recognisable object rather than a speck on an apron.
     */
    public static final float GROUND_SCALE = 1.5f;

    /**
     * How much larger a craft draws at cruising altitude than on the ground.
     *
     * <p>The altitude cue is this <em>ratio</em>, not either scale by itself, so
     * it is the thing held fixed when the ground scale changes.
     */
    public static final float ALTITUDE_SCALE_GAIN = 1.5f;

    /** Visual scale of a craft at cruising altitude (sells "I am up high"). */
    public static final float CRUISE_SCALE = GROUND_SCALE * ALTITUDE_SCALE_GAIN;
    /** Frequency (Hz) of the in-flight scale wobble. Slower than a heartbeat — reads as atmospheric drift, not a flicker. */
    public static final float WOBBLE_HZ = 0.7f;
    /**
     * Peak amplitude of the wobble, as a fraction of the cruise scale.
     *
     * <p>Proportional rather than absolute, so growing the aircraft does not
     * quietly flatten the drift: 2.7% of whatever cruise happens to be, which
     * is inside the 5% target at any scale.
     */
    public static final float WOBBLE_FRACTION = 0.027f;
    /** Peak screen-Y offset (cells) at {@code altitudeT == 1} to sell altitude in the top-down view. Render-only; sim-space position is unchanged. */
    public static final float VISUAL_ALT_PEAK_CELLS = 3.0f;
    /** Engine intensity while parked on the ground — quiet hum, not silent. */
    public static final float IDLE_INTENSITY = 0.3f;

    private AirAppearance() {}

    /**
     * Render scale multiplier derived from altitude + wobble phase.
     * {@link #GROUND_SCALE} on the ground (the wobble is gated by
     * {@code altitudeT}, so it dies cleanly at 0), rising to
     * ~{@link #CRUISE_SCALE} at altitude.
     */
    public static float scaleMult(float altitudeT, float flightPhase) {
        float base = GROUND_SCALE + (CRUISE_SCALE - GROUND_SCALE) * altitudeT;
        float wobble = (float) Math.sin(flightPhase)
                * (WOBBLE_FRACTION * CRUISE_SCALE) * altitudeT;
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

    /**
     * Visible thruster plume for a craft in {@code state}.
     *
     * <p>Separate from {@link #engineIntensity}, which is the audible engine
     * and is right to keep an idle floor: a machine sitting on a hardstand
     * hums. A plume is not a hum. An aircraft taxiing is rolling on its wheels
     * at walking pace, and drawing full afterburner while it does that reads as
     * a craft hovering an inch off the ground rather than one being towed round
     * a corner.
     *
     * <p>The takeoff roll is the exception and the reason the state is asked
     * for rather than the altitude: it is the one ground phase where the
     * engines are doing everything they can, and at the start of it the
     * aircraft is still at zero altitude.
     */
    public static float thrusterPlume(ShuttleState state, float altitudeT) {
        switch (state) {
            case TAKEOFF_ROLL:
                return 1f;
            case LOADING:
            case TAXI_OUT:
            case HOLDING_SHORT:
            case LANDING_ROLL:
            case TAXI_IN:
                return 0f;
            case PENDING:
            case GONE:
                return 0f;
            default:
                return altitudeT;
        }
    }
}
