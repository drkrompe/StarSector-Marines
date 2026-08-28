package com.dillon.starsectormarines.battle.smoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeObscurationTest {

    @Test
    void clearLanePaysNothing() {
        assertEquals(1f, SmokeObscuration.accuracyMultiplier(0));
        assertEquals(1f, SmokeObscuration.accuracyMultiplier(-3),
                "a negative depth is no smoke, not a bonus");
    }

    @Test
    void penaltyCompoundsPerCrossedCell() {
        assertEquals(SmokeObscuration.ACCURACY_MULT_PER_CELL,
                SmokeObscuration.accuracyMultiplier(1), 1e-6f);
        assertEquals(SmokeObscuration.ACCURACY_MULT_PER_CELL
                        * SmokeObscuration.ACCURACY_MULT_PER_CELL,
                SmokeObscuration.accuracyMultiplier(2), 1e-6f);

        float shallow = SmokeObscuration.accuracyMultiplier(2);
        float deep = SmokeObscuration.accuracyMultiplier(5);
        assertTrue(deep < shallow, "more cloud in the lane must cost more");
    }

    @Test
    void aScreenIsWorseThanAnyCoverLevelButStillWorthShootingThrough() {
        // A thrown screen is about a cloud diameter deep. It should bite
        // harder than authored cover ever does (0.55 at level 3) while
        // leaving a real chance — that gap is the whole point of grading it.
        float screen = SmokeObscuration.accuracyMultiplier(4);
        assertTrue(screen < 0.55f, "a deliberate screen must beat hard cover");
        assertTrue(screen > SmokeObscuration.MIN_ACCURACY_MULT,
                "a screen is not a wall");
    }

    @Test
    void depthNeverDrivesAccuracyToZero() {
        assertEquals(SmokeObscuration.MIN_ACCURACY_MULT,
                SmokeObscuration.accuracyMultiplier(50), 1e-6f);
        assertTrue(SmokeObscuration.accuracyMultiplier(Integer.MAX_VALUE) > 0f,
                "obscuration degrades a shot; it never gates one");
    }
}
