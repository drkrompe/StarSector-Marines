package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where on a hull a round lands once its contact circle has caught it: on the footprint, not the circle's rim. */
class BallisticHullContactTest {

    @Test
    void aRoundIntoALongHullSideOnLandsOnItsSide() {
        // A 4.8 x 1.65 hull lying along X (facing 90), its circle 2.4 out; a round from the south enters the rim.
        float u = BallisticResolver.hullContactDistance(0f, -2.4f, 0f, 1f, 0f, 0f, 90f, 2.4f, 0.825f, 4.8f);
        assertEquals(2.4f - 0.825f, u, 1e-4f);
    }

    @Test
    void aRoundDownTheHullsLengthLandsOnItsNose() {
        float u = BallisticResolver.hullContactDistance(-2.4f, 0f, 1f, 0f, 0f, 0f, 90f, 2.4f, 0.825f, 4.8f);
        assertEquals(0f, u, 1e-4f);
    }

    @Test
    void aLineThatMissesTheFootprintLandsNearestTheCentreAndABodyWithoutOneIsItsCircle() {
        // Passing 1.5 cells off the centre line, outside the 0.825 half width.
        float u = BallisticResolver.hullContactDistance(-1.5f, -1.9f, 0f, 1f, 0f, 0f, 0f, 2.4f, 0.825f, 3.8f);
        assertEquals(1.9f, u, 1e-4f);
        assertEquals(0f, BallisticResolver.hullContactDistance(0f, -2f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 4f));
    }
}
