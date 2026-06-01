package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirHandling;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for {@link HullKinematics} — the scrape → ground-scale conversion.
 * Uses real vanilla {@code ship_data.csv} maneuver values so the asserted feel
 * is the actual hull data, not a fixture. (max speed / accel / decel / turn:)
 * <pre>
 *   Talon   (interceptor) 325 / 400 / 300 / 150
 *   Trident (bomber)      130 / 150 / 125 /  30
 * </pre>
 */
class HullKinematicsTest {

    private static AirHandling talon()   { return HullKinematics.fromSpec(325f, 400f, 300f, 150f); }
    private static AirHandling trident() { return HullKinematics.fromSpec(130f, 150f, 125f,  30f); }

    @Test
    void interceptorOutrunsAndOutturnsBomber() {
        AirHandling t = talon(), b = trident();
        assertTrue(t.maxSpeed() > b.maxSpeed(),
                "interceptor should be faster: " + t.maxSpeed() + " vs " + b.maxSpeed());
        assertTrue(t.maxTurnRateDegPerSec() > b.maxTurnRateDegPerSec(),
                "interceptor should turn harder: " + t.maxTurnRateDegPerSec() + " vs " + b.maxTurnRateDegPerSec());
        assertTrue(t.accel() > b.accel(), "interceptor should accelerate harder");
        assertTrue(t.brakingAccel() > b.brakingAccel(), "interceptor should brake harder");
    }

    @Test
    void turnRatePassesThroughByTheAtmosphereMult() {
        assertEquals(150f * HullKinematics.TURN_ATMO_MULT, talon().maxTurnRateDegPerSec(), 1e-4,
                "angular rate is scale-invariant; only the atmosphere turn mult applies");
    }

    @Test
    void linearStatsLandInGroundScaleBand() {
        // 325 su * METERS_PER_PX(0.045) * SPEED_ATMO_MULT(1.3) ~= 19 cells/s —
        // fast over the battlefield but not teleporting across it.
        float talonSpeed = talon().maxSpeed();
        assertTrue(talonSpeed > 8f && talonSpeed < 40f,
                "talon maxSpeed should be a battlefield-sane cells/s, was " + talonSpeed);
        assertEquals(325f * HullKinematics.CELLS_PER_SU, talonSpeed, 1e-4);
    }

    @Test
    void dampingKnobsArePositive() {
        AirHandling t = talon();
        assertTrue(t.lateralDriftDamping() > 0f, "lateral damping is the boat-feel knob");
        assertTrue(t.stationDamping() > 0f, "station damping keeps a hover settled");
    }

    @Test
    void negativeStatsClampToZeroNotNaN() {
        AirHandling junk = HullKinematics.fromSpec(-5f, -1f, -1f, -10f);
        assertEquals(0f, junk.maxSpeed(), 1e-6);
        assertEquals(0f, junk.maxTurnRateDegPerSec(), 1e-6);
    }
}
