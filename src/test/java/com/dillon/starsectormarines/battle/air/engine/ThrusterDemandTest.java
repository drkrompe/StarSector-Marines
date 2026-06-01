package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for {@link ThrusterDemand} — the per-thruster engine-FX weighting.
 * No {@code Global}, no rendering: just feed an {@link AirBody} (public mutable
 * kinematics) and assert which slots bloom.
 *
 * <p>Frame reminder: facing 0° = +Y (nose), +X = starboard. A main engine sits
 * aft (negative {@code localY}) and exhausts at angle 180 (−Y), so its thrust
 * pushes the hull +Y (forward).
 */
class ThrusterDemandTest {

    /** Reference handling — round numbers so magnitudes normalize cleanly. */
    private static final AirHandling H = new AirHandling() {
        @Override public float maxSpeed()             { return 10f; }
        @Override public float accel()                { return 10f; }
        @Override public float brakingAccel()         { return 10f; }
        @Override public float maxTurnRateDegPerSec() { return 100f; }
        @Override public float lateralDriftDamping()  { return 3f; }
        @Override public float stationDamping()       { return 5f; }
    };

    private static EngineSlotData main(float lx, float ly) {
        // angle 180 → exhaust aft (−Y), thrust forward (+Y).
        return new EngineSlotData(lx, ly, 180f, 4f, 2f, 0f, "MIDLINE");
    }

    private static AirBody body(float vx, float vy, float ax, float ay,
                                float facing, float angVel) {
        AirBody b = new AirBody();
        b.vx = vx; b.vy = vy;
        b.ax = ax; b.ay = ay;
        b.facingDegrees = facing;
        b.angVelDegPerSec = angVel;
        return b;
    }

    @Test
    void aftMainBloomsOnForwardAccelAndDimsOnCoast() {
        EngineSlotData[] slots = { main(0f, -1f) };

        // Cruising forward AND accelerating forward — max work.
        float accel = ThrusterDemand.compute(slots, body(0f, 5f, 0f, 10f, 0f, 0f), H)[0];
        // Coasting forward, no acceleration — only the velocity-sustain term.
        float coast = ThrusterDemand.compute(slots, body(0f, 5f, 0f, 0f, 0f, 0f), H)[0];

        assertTrue(accel > coast, "accelerating main should glow more than coasting: "
                + accel + " vs " + coast);
        assertTrue(coast > 0f, "a coasting forward main should still be lit: " + coast);
        assertEquals(1f, accel, 1e-4, "full forward accel should saturate the aft main");
    }

    @Test
    void brakingDimsTheForwardMain() {
        EngineSlotData[] slots = { main(0f, -1f) };
        // Moving forward but decelerating: accel points aft, against the thrust
        // axis → the accel term contributes nothing.
        float brake = ThrusterDemand.compute(slots, body(0f, 5f, 0f, -10f, 0f, 0f), H)[0];
        float accel = ThrusterDemand.compute(slots, body(0f, 5f, 0f, 10f, 0f, 0f), H)[0];
        assertTrue(brake < accel, "braking main should glow less than accelerating: "
                + brake + " vs " + accel);
    }

    @Test
    void turnLightsTheManeuveringSideAsymmetrically() {
        // Two laterally-offset mains; pure rotation (no translation) isolates
        // the torque term.
        EngineSlotData starboard = main(1f, 0f);   // +X
        EngineSlotData port      = main(-1f, 0f);   // −X
        EngineSlotData[] slots = { starboard, port };

        float[] ccw = ThrusterDemand.compute(slots, body(0f, 0f, 0f, 0f, 0f, 50f), H);
        assertTrue(ccw[0] > ccw[1], "CCW turn should favour the starboard thruster: "
                + ccw[0] + " vs " + ccw[1]);

        float[] cw = ThrusterDemand.compute(slots, body(0f, 0f, 0f, 0f, 0f, -50f), H);
        assertTrue(cw[1] > cw[0], "CW turn should favour the port thruster: "
                + cw[1] + " vs " + cw[0]);

        float[] straight = ThrusterDemand.compute(slots, body(0f, 0f, 0f, 0f, 0f, 0f), H);
        assertEquals(straight[0], straight[1], 1e-4,
                "no turn → the mirrored pair should be symmetric");
    }

    @Test
    void parkedBodyDemandsNothing() {
        EngineSlotData[] slots = { main(0f, -1f), main(1f, 0f), main(-1f, 0f) };
        float[] d = ThrusterDemand.compute(slots, body(0f, 0f, 0f, 0f, 0f, 0f), H);
        for (int i = 0; i < d.length; i++) {
            assertEquals(0f, d[i], 1e-4, "parked slot " + i + " should demand zero");
        }
    }
}
