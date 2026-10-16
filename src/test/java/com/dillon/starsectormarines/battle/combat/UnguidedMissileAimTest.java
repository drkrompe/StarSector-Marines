package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.AirMissileSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnguidedMissileAimTest {
    @Test
    void stationaryTargetUsesSlowingTravelInsteadOfCruiseOrLaunchSpeed() {
        AirMissileSpec charge = charge(4);
        float time = UnguidedMissileAim.interceptSeconds(50, 0, 4, 0, 0, charge);
        assertTrue(time > 2.5f && time < 3f);
        assertEquals(Math.hypot(50, 4), UnguidedMissileAim.distanceAfter(charge, time), .00001);
    }

    @Test
    void approachingCrossingTargetIsLedAtItsFlightAltitude() {
        AirMissileSpec charge = charge(4);
        float time = UnguidedMissileAim.interceptSeconds(50, 5, 4, -32, 3, charge);
        assertTrue(time > .9f && time < 1.1f);
        double distance = Math.sqrt(Math.pow(50 - 32 * time, 2) + Math.pow(5 + 3 * time, 2) + 16);
        assertEquals(distance, UnguidedMissileAim.distanceAfter(charge, time), .00001);
    }

    @Test
    void speedFloorContinuesTravelAndLifetimeBoundsInterception() {
        AirMissileSpec longLived = charge(10);
        assertEquals(106.25, UnguidedMissileAim.distanceAfter(longLived, 10), .000001);
        assertEquals(8.75f, UnguidedMissileAim.interceptSeconds(100, 0, 0, 0, 0, longLived), .00001f);
        assertEquals(0f, UnguidedMissileAim.interceptSeconds(100, 0, 0, 0, 0, charge(4)));
    }

    @Test
    void fasterRecedingTargetHasNoPredictedIntercept() {
        assertEquals(0f, UnguidedMissileAim.interceptSeconds(20, 0, 4, 32, 0, charge(4)));
    }

    private static AirMissileSpec charge(float lifetime) {
        return new AirMissileSpec(false, false, 20, 5, 2, 2, 0, lifetime,
                20, 2, 7.5f, 3.75f, true);
    }
}
