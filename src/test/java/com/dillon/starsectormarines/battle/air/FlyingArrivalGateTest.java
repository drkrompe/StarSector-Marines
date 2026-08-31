package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flying arrival gate is a gate the craft it is for can actually be found
 * inside.
 *
 * <p>An arrival gate is a distance a craft has to be sampled within on some
 * tick, and there are two ways a craft never is. It steps clean over a gate
 * narrower than one tick's travel; and it settles into an orbit around a point
 * it cannot turn tightly enough to reach, holding station about a turn radius
 * out for the rest of the battle. Neither of those is a craft that is nearly
 * there — both are craft that will never be there — so the authored numbers are
 * floors and the gate has to admit both. That has to stay closed however fast
 * air craft are later dialled to fly.
 */
class FlyingArrivalGateTest {

    /** Every authored floor in the flying set, so none of them can be widened alone. */
    private static final float[] FLOORS = { 0.2f, 1.0f, 1.2f, 2.0f };

    /** A handling profile that is nothing but a turn rate, which is all the gate reads of one. */
    private record Flying(float maxTurnRateDegPerSec) implements AirHandling {
        @Override public float maxSpeed() { return 12f; }
        @Override public float accel() { return 1f; }
        @Override public float brakingAccel() { return 1f; }
        @Override public float lateralDriftDamping() { return 1f; }
        @Override public float stationDamping() { return 1f; }
    }

    /** A transport's turn rate: it can pivot, so its circle is never the binding term. */
    private static final AirHandling NIMBLE = new Flying(130f);
    /** A fighter's, after the atmosphere turn mult: the circle is most of the gate. */
    private static final AirHandling WINGED = new Flying(49.5f);

    /** A body making {@code speed} cells/sec down +X, which is all the gate reads of one. */
    private static AirBody movingAt(float speed) {
        AirBody body = new AirBody();
        body.vx = speed;
        body.vy = 0f;
        return body;
    }

    /**
     * The gate outruns the craft, whatever the craft and whichever gate.
     *
     * <p>The range spans a hovering bus at three cells a second to something
     * far faster than anything currently flies, because the point of deriving
     * the gate is that it survives a re-dial nobody re-checked it against.
     */
    @Test
    void noCraftCanStepOverItsOwnArrivalGate() {
        float dt = BattleSimulation.TICK_DT;
        for (float speed = 3f; speed <= 90f; speed += 1.5f) {
            AirBody body = movingAt(speed);
            float step = speed * dt;
            for (float floor : FLOORS) {
                float gate = AirSystem.flyingArrivalDist(floor, body, NIMBLE, dt);
                assertTrue(gate > step,
                        "a craft at " + speed + " cells/sec crosses " + step
                                + " cells a tick and would step over a " + gate
                                + "-cell gate derived from a floor of " + floor);
            }
        }
    }

    /**
     * The gate admits the circle the craft is flying, not only the step.
     *
     * <p>The second and worse way an arrival never happens, and the one a
     * tick-step gate alone does not close: a body steered at a point inside its
     * own turn circle orbits it instead, about a radius out and about ninety
     * degrees off the bearing to it, indefinitely. Watched at the wide turn
     * radius, a Broadsword sent to a landing zone circled it three and a half
     * cells out at four cells a second and never got closer.
     */
    @Test
    void aCraftIsNeverAskedToReachInsideItsOwnTurningCircle() {
        float dt = BattleSimulation.TICK_DT;
        float turnRateRad = (float) Math.toRadians(WINGED.maxTurnRateDegPerSec());
        for (float speed = 3f; speed <= 45f; speed += 1.5f) {
            float orbit = speed / turnRateRad;
            for (float floor : FLOORS) {
                float gate = AirSystem.flyingArrivalDist(floor, movingAt(speed), WINGED, dt);
                assertTrue(gate >= orbit,
                        "a craft at " + speed + " cells/sec orbits " + orbit
                                + " cells out and would never be sampled inside a " + gate
                                + "-cell gate derived from a floor of " + floor);
            }
        }
    }

    /**
     * A craft that has been braked keeps exactly the tolerance it was authored
     * with.
     *
     * <p>Half of these arrivals are flown braked. A gate widened by the speed
     * the craft flew <em>in</em> at would land a transport most of a cell short
     * of the pad it was carefully braked onto, and the snap that follows would
     * be a visible jump rather than a correction. Both terms read what the body
     * is actually making, so both close as it slows — which is what keeps the
     * derivation inert everywhere it is not needed.
     */
    @Test
    void aBrakedArrivalIsLeftExactlyAsAuthored() {
        float dt = BattleSimulation.TICK_DT;
        // Down to a crawl on short final: a tenth of a cell a second.
        AirBody onShortFinal = movingAt(0.1f);
        for (AirHandling flight : new AirHandling[]{ NIMBLE, WINGED }) {
            for (float floor : FLOORS) {
                assertEquals(floor,
                        AirSystem.flyingArrivalDist(floor, onShortFinal, flight, dt), 1e-5f,
                        "deriving moved a braked craft's " + floor + "-cell gate");
            }
        }
    }

    /**
     * A craft flown along a path is not given the circle, because it cannot
     * orbit anything.
     *
     * <p>The orbit term is for a body steered <em>at</em> a point. A craft on a
     * solved approach is chasing a carrot sliding along a polyline that runs on
     * past its destination, so the failure the circle exists to admit cannot
     * happen to it — and admitting it anyway costs exactly its width. A fighter
     * turns inside seventeen cells at approach speed, so a landing gated on the
     * steered rule fired seventeen cells short of the numbers: measured on the
     * shipped airfield the aircraft touched its wheels down at x=79 on a strip
     * that ends at 62.5, and an approach from the other end put them down at
     * x=-9.6, off the map. Both landed. Neither landed on the runway.
     */
    @Test
    void aPathFollowedArrivalIsGatedOnTheStepAlone() {
        float dt = BattleSimulation.TICK_DT;
        float turnRateRad = (float) Math.toRadians(WINGED.maxTurnRateDegPerSec());
        for (float speed = 3f; speed <= 45f; speed += 1.5f) {
            AirBody body = movingAt(speed);
            float step = speed * dt;
            for (float floor : FLOORS) {
                float flown = AirSystem.flownArrivalDist(floor, body, dt);
                assertTrue(flown > step,
                        "a craft at " + speed + " cells/sec would step over its " + flown
                                + "-cell gate");
                assertTrue(flown <= Math.max(floor, 2f * step) + 1e-5f,
                        "the path-followed gate at " + speed + " cells/sec came out at "
                                + flown + ", which is wider than the step it is made of");
                assertTrue(flown < speed / turnRateRad,
                        "the path-followed gate still admits the whole turning circle at "
                                + speed + " cells/sec: " + flown);
            }
        }
    }

    /** The floor is a floor: the gate never comes out under it. */
    @Test
    void theAuthoredNumberIsNeverUndercut() {
        float dt = BattleSimulation.TICK_DT;
        for (float speed = 0f; speed <= 90f; speed += 3f) {
            for (float floor : FLOORS) {
                assertTrue(AirSystem.flyingArrivalDist(floor, movingAt(speed), WINGED, dt) >= floor,
                        "the gate came out under its " + floor + "-cell floor at " + speed);
            }
        }
    }
}
