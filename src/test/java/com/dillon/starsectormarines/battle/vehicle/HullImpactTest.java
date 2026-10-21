package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullImpactTest {

    /** A 3 t striker at 4 cells/s into the side of a 2 x 1 cell hull facing north, at {@code offset} along it. */
    private static HullImpact.Result side(float hullMass, float offset) {
        return HullImpact.strike(0f, 0f, 0f, 1f, .5f, hullMass, 0f, 0f, 0f,
                -1f, offset, 4f, 0f, 3f, .2f);
    }

    @Test
    void momentumIsConservedAndTheLighterHullTakesMore() {
        HullImpact.Result light = side(2.5f, 0f), heavy = side(45f, 0f);
        assertEquals(0f, 3f * light.strikerVx() + 2.5f * light.hullVx(), 1e-4f);
        assertEquals(0f, 3f * heavy.strikerVx() + 45f * heavy.hullVx(), 1e-4f);
        assertTrue(light.hullVx() > 5f * heavy.hullVx());
        assertTrue(heavy.strikerVx() < -4f, "off a heavy hull the striker rebounds");
    }

    @Test
    void anOffCentreHitTurnsTheHullAndACentredOneDoesNot() {
        assertEquals(0f, side(2.5f, 0f).hullOmega(), 1e-6f);
        assertTrue(side(2.5f, .8f).hullOmega() < 0f, "struck ahead of its centre from the left, it turns clockwise");
        assertTrue(side(2.5f, -.8f).hullOmega() > 0f);
    }

    @Test
    void partingBodiesExchangeNothing() {
        HullImpact.Result r = HullImpact.strike(0f, 0f, 0f, 1f, .5f, 2.5f, 0f, 0f, 0f,
                -1f, 0f, -4f, 0f, 3f, .2f);
        assertEquals(0f, r.impulse());
    }

    @Test
    void wheelsRollOnWhereTracksAndSideScrubStop() {
        assertEquals(.5f, HullSlide.scrub(1f, .5f), 1e-6f);
        assertEquals(0f, HullSlide.scrub(.3f, .5f));
        assertEquals(-.5f, HullSlide.scrub(-1f, .5f), 1e-6f);
    }
}
