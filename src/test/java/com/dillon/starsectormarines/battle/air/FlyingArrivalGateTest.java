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
 * tick. A craft crossing it at speed is inside it only between two samples, so
 * a gate narrower than one tick's travel is one the craft steps clean over —
 * and a craft that steps over its arrival gate does not arrive, it flies a
 * circuit round its own destination for the rest of the battle. That is the
 * whole reason the authored numbers are floors rather than gates, and it is the
 * fault that has to stay closed however fast air craft are later dialled to
 * fly.
 */
class FlyingArrivalGateTest {

    /** Every authored floor in the flying set, so none of them can be widened alone. */
    private static final float[] FLOORS = { 0.2f, 1.0f, 1.2f, 2.0f };

    /** A body making {@code speed} cells/sec down +X, which is all the gate reads. */
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
                float gate = AirSystem.flyingArrivalDist(floor, body, dt);
                assertTrue(gate > step,
                        "a craft at " + speed + " cells/sec crosses " + step
                                + " cells a tick and would step over a " + gate
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
     * be a visible jump rather than a correction. Reading what the body is
     * actually making is what keeps the derivation inert everywhere it is not
     * needed — a landing approach, a hold, a craft stopped altogether.
     */
    @Test
    void aBrakedArrivalIsLeftExactlyAsAuthored() {
        float dt = BattleSimulation.TICK_DT;
        // Down to a crawl on short final: a tenth of a cell a second.
        AirBody onShortFinal = movingAt(0.1f);
        for (float floor : FLOORS) {
            assertEquals(floor, AirSystem.flyingArrivalDist(floor, onShortFinal, dt), 1e-5f,
                    "deriving moved a braked craft's " + floor + "-cell gate");
        }
    }

    /** The floor is a floor: the gate never comes out under it. */
    @Test
    void theAuthoredNumberIsNeverUndercut() {
        float dt = BattleSimulation.TICK_DT;
        for (float speed = 0f; speed <= 90f; speed += 3f) {
            for (float floor : FLOORS) {
                assertTrue(AirSystem.flyingArrivalDist(floor, movingAt(speed), dt) >= floor,
                        "the gate came out under its " + floor + "-cell floor at " + speed);
            }
        }
    }
}
