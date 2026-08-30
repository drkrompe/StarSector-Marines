package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How big an aircraft draws, and how much of that is altitude.
 *
 * <p>A hull's drawn size comes from its own sprite height, which is true to
 * scale and unreadable: a parked fighter was a speck on an apron next to a
 * marine. The departure from true scale is deliberate and lives in one place,
 * so these pin the two things that are easy to break while changing it — that
 * a craft on the ground is drawn at the authored ground scale rather than at
 * 1.0, and that the altitude cue is a <em>ratio</em> which survives the ground
 * scale moving.
 */
class AirAppearanceScaleTest {

    private static final float EPS = 1e-4f;

    /** Phase 0 puts the wobble's sine at zero, so these read the base curve. */
    private static final float NO_WOBBLE = 0f;

    @Test
    void aCraftOnTheGroundIsDrawnLargerThanItsTrueScale() {
        float onTheGround = AirAppearance.scaleMult(0f, NO_WOBBLE);
        assertEquals(AirAppearance.GROUND_SCALE, onTheGround, EPS);
        assertTrue(onTheGround > 1f,
                "a parked aircraft is drawn at true sprite scale, which is a speck");
    }

    /**
     * The altitude cue is the ratio between the two ends, and it is what a
     * reader actually perceives — so growing the aircraft must not flatten it.
     */
    @Test
    void heightReadsAsTheSameGainHoweverBigTheAircraftIsDrawn() {
        float onTheGround = AirAppearance.scaleMult(0f, NO_WOBBLE);
        float atCruise = AirAppearance.scaleMult(1f, NO_WOBBLE);
        assertEquals(AirAppearance.ALTITUDE_SCALE_GAIN, atCruise / onTheGround, EPS,
                "climbing no longer changes the drawn size by the authored gain");
    }

    /** And it grows monotonically between them, so a climb reads as a climb. */
    @Test
    void climbingOnlyEverMakesItBigger() {
        float previous = AirAppearance.scaleMult(0f, NO_WOBBLE);
        for (int step = 1; step <= 10; step++) {
            float scale = AirAppearance.scaleMult(step / 10f, NO_WOBBLE);
            assertTrue(scale > previous,
                    "scale did not grow between altitude " + (step - 1) / 10f
                            + " and " + step / 10f);
            previous = scale;
        }
    }

    /**
     * The wobble is a fraction of the cruise scale rather than a fixed number of
     * scale units, so a bigger aircraft drifts by the same visible proportion
     * instead of appearing to steady itself.
     */
    @Test
    void theDriftStaysTheSameProportionOfTheAircraft() {
        float peak = AirAppearance.scaleMult(1f, (float) (Math.PI / 2));
        float flat = AirAppearance.scaleMult(1f, NO_WOBBLE);
        float proportion = (peak - flat) / flat;
        assertEquals(AirAppearance.WOBBLE_FRACTION, proportion, 1e-3f);
        assertTrue(proportion < 0.05f, "the drift is past the 5% target");
    }

    /** On the ground there is no drift at all, whatever the phase happens to be. */
    @Test
    void aParkedAircraftDoesNotBreathe() {
        for (int step = 0; step < 8; step++) {
            float phase = (float) (step * Math.PI / 4);
            assertEquals(AirAppearance.GROUND_SCALE,
                    AirAppearance.scaleMult(0f, phase), EPS,
                    "a parked aircraft changed size at phase " + phase);
        }
    }
}
