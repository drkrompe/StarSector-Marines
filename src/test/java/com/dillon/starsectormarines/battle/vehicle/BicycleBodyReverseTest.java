package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BicycleBodyReverseTest {

    private static float settledReverse(BicycleBody body) {
        for (int i = 0; i < 600; i++) body.tickManual(-1f, 0f, 1f / 30f);
        return -body.speed;
    }

    @Test
    void anOrdinaryHullReversesAtHalfItsTopSpeed() {
        assertEquals(1.5f, settledReverse(new BicycleBody(2f, 30f, 90f, 1f, 3f, 3f)), 1e-4f);
    }

    @Test
    void aReverseFractionCapsBackingUpBelowHalf() {
        assertEquals(.9f, settledReverse(new BicycleBody(2f, 30f, 90f, 1f, 3f, 3f, .3f)), 1e-4f);
    }

    /** A Bulwark walks at 1.05 cells/s; a tank that backs up faster than that kites it forever. */
    @Test
    void bothTanksBackUpSlowerThanABulwarkWalks() {
        for (VehicleType type : new VehicleType[] {VehicleType.BREAKTHROUGH_TANK, VehicleType.LASER_TANK}) {
            float reverse = settledReverse((BicycleBody) type.createBody());
            assertTrue(reverse < 1.05f, type + " reverses at " + reverse);
        }
    }

    @Test
    void aFractionOutsideTheUnitIntervalIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new BicycleBody(2f, 30f, 90f, 1f, 3f, 3f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new BicycleBody(2f, 30f, 90f, 1f, 3f, 3f, 1.5f));
    }
}
