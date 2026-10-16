package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandedScaleTest {

    @Test
    void aLandedHullIsMorrowsCellsPerPixel() {
        // Morrow's 92-pixel Shepherd is 24 cells on the ground; a Venture (190 px) and a Hound (72 px) follow.
        assertEquals(24f, 92f * LandedScale.CELLS_PER_HULL_PX, 1e-4f);
        assertEquals(49.6f, 190f * LandedScale.CELLS_PER_HULL_PX, 0.05f);
        assertEquals(18.8f, 72f * LandedScale.CELLS_PER_HULL_PX, 0.05f);
        assertEquals(LandedScale.CELLS_PER_HULL_PX, AirScale.cellsForHeightPx(1f) * LandedScale.berthPresence(), 1e-5f);
    }

    @Test
    void theBerthFactorIsARatioToFlightPresence() {
        assertEquals(LandedScale.berthPresence() / 4f, LandedScale.berthFactor(4f), 1e-5f);
        assertEquals(1f, LandedScale.berthFactor(LandedScale.berthPresence()), 1e-5f);
        assertThrows(IllegalArgumentException.class, () -> LandedScale.berthFactor(0f));
    }

    @Test
    void aLiftEasesFromTheBerthToFlightWithoutAJump() {
        float berth = LandedScale.berthFactor(4f);
        assertEquals(berth, LandedScale.bodyFactor(berth, -1f), 0f);
        assertEquals(berth, LandedScale.bodyFactor(berth, 0f), 1e-6f);
        assertEquals(1f, LandedScale.bodyFactor(berth, LandedScale.LIFT_SECONDS), 1e-6f);
        assertEquals(1f, LandedScale.bodyFactor(berth, LandedScale.LIFT_SECONDS * 3f), 1e-6f);
        float dt = 1f / 60f;
        float previous = LandedScale.bodyFactor(berth, 0f);
        float largestStep = 0f;
        for (float t = dt; t <= LandedScale.LIFT_SECONDS + dt; t += dt) {
            float now = LandedScale.bodyFactor(berth, t);
            assertTrue(now <= previous + 1e-6f, "the body only shrinks toward flight scale");
            largestStep = Math.max(largestStep, (previous - now) / previous);
            previous = now;
        }
        // Under one percent of the body in any one frame at sixty frames a second: no pop.
        assertTrue(largestStep < 0.01f, "largest per-frame change " + largestStep);
    }

    @Test
    void identityWhenFlightIsAlreadyAtBerthScale() {
        for (float t = -1f; t < LandedScale.LIFT_SECONDS + 1f; t += 0.5f)
            assertEquals(1f, LandedScale.bodyFactor(1f, t), 1e-6f);
        assertEquals(0f, LandedScale.liftProgress(-1f), 0f);
        assertEquals(1f, LandedScale.liftProgress(LandedScale.LIFT_SECONDS), 0f);
    }
}
