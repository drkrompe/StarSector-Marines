package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The nose gun's geometry: the window it fires in, the chance a round in it hits, and where to steer. */
class AirToAirGunTest {

    private static AirBody facing(float nose) {
        AirBody body = new AirBody();
        body.x = 0f;
        body.y = 0f;
        body.facingDegrees = nose - 90f;
        return body;
    }

    @Test
    void aTargetStraightAheadIsZeroOffTheNose() {
        assertEquals(0f, AirToAirGun.offNoseDegrees(facing(0f), 10f, 0f), 0.01f);
        assertEquals(90f, AirToAirGun.offNoseDegrees(facing(0f), 0f, 10f), 0.01f);
        assertEquals(180f, AirToAirGun.offNoseDegrees(facing(0f), -10f, 0f), 0.01f);
    }

    @Test
    void theWindowIsRangeAndCone() {
        assertTrue(AirToAirGun.inWindow(AirToAirGun.RANGE_CELLS, AirToAirGun.CONE_DEGREES));
        assertFalse(AirToAirGun.inWindow(AirToAirGun.RANGE_CELLS + 0.1f, 0f));
        assertFalse(AirToAirGun.inWindow(5f, AirToAirGun.CONE_DEGREES + 0.1f));
    }

    @Test
    void noChanceOutsideTheWindowAndBestAtPointBlankOnTheNose() {
        assertEquals(0f, AirToAirGun.hitChance(30f, 0f));
        assertEquals(AirToAirGun.BEST_HIT_CHANCE, AirToAirGun.hitChance(1f, 0f), 1e-5f);
        assertTrue(AirToAirGun.hitChance(15f, 0f) < AirToAirGun.hitChance(5f, 0f));
        assertTrue(AirToAirGun.hitChance(5f, 12f) < AirToAirGun.hitChance(5f, 1f));
        assertTrue(AirToAirGun.hitChance(AirToAirGun.RANGE_CELLS, AirToAirGun.CONE_DEGREES) > 0f);
    }

    @Test
    void theLeadPointIsAheadOfAMovingPreyAndOnAStationaryOne() {
        float[] still = AirToAirGun.leadPoint(0f, 0f, 20f, 10f, 0f, 0f, 0f);
        assertEquals(10f, still[0], 1e-4f);
        float[] moving = AirToAirGun.leadPoint(0f, 0f, 20f, 10f, 0f, 0f, 10f);
        assertEquals(10f, moving[0], 1e-4f);
        assertTrue(moving[1] > 0f);
    }
}
