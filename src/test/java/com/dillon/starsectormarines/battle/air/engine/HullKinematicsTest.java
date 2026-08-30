package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirHandling;
import org.junit.jupiter.api.Test;

import static com.dillon.starsectormarines.battle.sim.BattleSimulation.TICK_DT;
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
        // 325 su * METERS_PER_PX(0.045) * SPEED_ATMO_MULT ~= 32 cells/s — fast
        // over the battlefield but not teleporting across it.
        float talonSpeed = talon().maxSpeed();
        assertTrue(talonSpeed > 8f && talonSpeed < 60f,
                "talon maxSpeed should be a battlefield-sane cells/s, was " + talonSpeed);
        assertEquals(325f * HullKinematics.CELLS_PER_SU, talonSpeed, 1e-4);
    }

    /**
     * Every fighter in the game flies a circuit wide enough to read as an
     * aircraft.
     *
     * <p>The one number the atmosphere calibration is actually setting. Speed
     * and turn rate are two dials with one product — {@code speed / turn rate}
     * is the tightest circle the craft can fly — and either of them alone says
     * nothing about how the aircraft reads. Passed through raw, the game's own
     * fighter stats put the whole ladder on a radius of about seven cells,
     * which is a machine that turns round inside the beaten zone of its own gun
     * run: a bee rather than something with wings.
     *
     * <p>Held as a band rather than a number, because the point is the feel and
     * not the arithmetic. Under about a dozen cells the turn stops being
     * visible as a manoeuvre; past about forty a craft cannot get round on a
     * battle map at all and the passes stop coming.
     */
    @Test
    void everyFighterFliesAWideEnoughCircuit() {
        // Real ship_data.csv rows: max speed / max turn rate.
        float[][] fighters = {
                { 200f,  90f }, { 325f, 150f }, { 450f, 225f },
                { 175f,  90f }, { 200f,  90f }, { 325f, 180f },
        };
        String[] names = { "broadsword", "talon", "thunder", "dagger", "longbow", "wasp" };
        for (int i = 0; i < fighters.length; i++) {
            AirHandling flight = HullKinematics.fromSpec(
                    fighters[i][0], 400f, 300f, fighters[i][1]);
            float radius = flight.minTurnRadiusCells();
            System.out.printf("[kinematics] %-11s %5.1f cells/s (%.3f per tick),"
                            + " %6.1f deg/s, turn radius %5.1f cells%n",
                    names[i], flight.maxSpeed(), flight.maxSpeed() * TICK_DT,
                    flight.maxTurnRateDegPerSec(), radius);
            assertTrue(radius > 12f,
                    names[i] + " turns inside " + radius + " cells, which is a bee");
            assertTrue(radius < 40f,
                    names[i] + " needs " + radius + " cells to come round, which is most"
                            + " of a battle map for one turn");
            // And it still has to be a thing the simulation can sample.
            assertTrue(flight.maxSpeed() * TICK_DT < 2f,
                    names[i] + " covers " + (flight.maxSpeed() * TICK_DT)
                            + " cells a tick, which steps over most things it should meet");
        }
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
