package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A craft holding station over a point comes to rest on it rather than
 * orbiting it.
 */
class AirSteeringSystemTest {

    /** A stiff brake on a fixed turn rate: what a better drive makes of a nimble hull. */
    private record StiffBrake() implements AirHandling {
        @Override public float maxSpeed() { return 16f; }
        @Override public float accel() { return 17.4f; }
        @Override public float brakingAccel() { return 17.4f; }
        @Override public float maxTurnRateDegPerSec() { return 130f; }
        @Override public float lateralDriftDamping() { return 3f; }
        @Override public float stationDamping() { return 5f; }
    }

    /**
     * Started where the limit cycle lives — half a cell out, a cell a second
     * across the bearing — the craft settles instead of holding the circle.
     * Without the turn-circle cap the braking taper keeps feeding it the
     * speed of a circle it cannot tighten, and it never gets inside 0.4 cells.
     */
    @Test
    void aStiffBrakeDoesNotOrbitItsStation() {
        AirHandling handling = new StiffBrake();
        float dt = BattleSimulation.TICK_DT;
        AirBody body = new AirBody();
        body.teleport(0.47f, 0f, AirBody.facingToward(0f, 1f));
        body.vx = 0f;
        body.vy = 1.05f;
        for (float elapsed = 0f; elapsed < 3f; elapsed += dt) {
            AirSteeringSystem.steer(body, 0f, 0f, SteeringMode.STATION, handling, dt);
        }
        assertTrue(body.distanceTo(0f, 0f) < 0.2f,
                "still " + body.distanceTo(0f, 0f) + " cells off station at " + body.speed() + " cells/sec");
        assertTrue(body.speed() < 0.5f, "still making " + body.speed() + " cells/sec");
    }
}
