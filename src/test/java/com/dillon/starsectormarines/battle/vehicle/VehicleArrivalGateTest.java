package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arrival gate is a distance and the body samples once a tick, so the gate
 * has to be wider than the step or a fast vehicle drives straight through its
 * own LZ without ever arriving.
 */
class VehicleArrivalGateTest {

    private static final float DT = BattleSimulation.TICK_DT;

    private static GroundBody bodyAt(float speed) {
        GroundBody body = VehicleType.HEAVY_APC.createBody();
        body.teleport(0f, 0f, 0f);
        body.speed = speed;
        return body;
    }

    @Test
    void theShippedApcIsUnaffected() {
        // 2.8 cells/sec over a 1/30 tick is 0.093 cells: well inside 0.25, so
        // the guard must not widen the authored tolerance for today's fleet.
        GroundBody apc = bodyAt(VehicleType.HEAVY_APC.maxSpeed);
        assertEquals(VehicleController.LZ_ARRIVAL_DIST,
                VehicleController.arrivalDist(VehicleController.LZ_ARRIVAL_DIST, apc, DT), 1e-6f,
                "the APC never outruns its own LZ gate, so the gate stays exactly as authored");
        assertEquals(VehicleController.EXIT_ARRIVAL_DIST,
                VehicleController.arrivalDist(VehicleController.EXIT_ARRIVAL_DIST, apc, DT), 1e-6f);
    }

    @Test
    void aFasterVehicleGetsAGateItCanActuallyCross() {
        // The planned light scout is specified as faster than the APC. At 8
        // cells/sec the step is 0.267 cells — wider than the 0.25 LZ gate, so
        // an authored-constant gate would be jumped every single approach.
        float scoutSpeed = 8f;
        float step = scoutSpeed * DT;
        assertTrue(step > VehicleController.LZ_ARRIVAL_DIST,
                "fixture check: this speed must actually outrun the authored gate");

        float gate = VehicleController.arrivalDist(
                VehicleController.LZ_ARRIVAL_DIST, bodyAt(scoutSpeed), DT);

        assertTrue(gate > step, "a gate no wider than the step is a gate the body steps over"
                + " (gate=" + gate + ", step=" + step + ")");
    }

    @Test
    void aCrawlingVehicleKeepsTheTightAuthoredTolerance() {
        // Reading actual speed rather than the type maximum is what preserves
        // the invisible snap: a truck easing onto a tight LZ still has to get
        // within the authored quarter-cell.
        assertEquals(VehicleController.LZ_ARRIVAL_DIST,
                VehicleController.arrivalDist(VehicleController.LZ_ARRIVAL_DIST, bodyAt(0.2f), DT), 1e-6f);
    }

    @Test
    void reversingCountsAsGroundCovered() {
        // Reverse speed is negative and still moves the body a step per tick.
        assertEquals(VehicleController.arrivalDist(VehicleController.LZ_ARRIVAL_DIST, bodyAt(8f), DT),
                VehicleController.arrivalDist(VehicleController.LZ_ARRIVAL_DIST, bodyAt(-8f), DT), 1e-6f,
                "a gate derived from speed must not be narrower going backwards");
    }
}
