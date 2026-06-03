package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link VehicleController#turnIsInfeasibleForward} — the proactive
 * kinematic detector that decides, from the instantaneous pose-vs-carrot
 * geometry alone, whether a carrot is reachable by any forward arc. A bicycle
 * cannot reach a target inside either of its two minimum-turn circles; those
 * cases must trip the reverse-to-feasible maneuver before the truck ever enters
 * the open-space orbit. The truck faces north (0°) throughout, so heading is
 * {@code +Y} and the two turn-circle centers sit at {@code (±R, 0)} relative to
 * the body.
 */
public class VehicleControllerTurnFeasibilityTest {

    private static final float R = 4f;

    private static boolean infeasible(float tx, float ty) {
        return VehicleController.turnIsInfeasibleForward(0f, 0f, 0f, tx, ty, R);
    }

    @Test
    public void carrotStraightAheadIsReachable() {
        // Dead ahead sits far from both side-centers (~6.4 cells) — a gentle
        // forward run, never an orbit.
        assertFalse(infeasible(0f, 5f), "a carrot straight ahead is reachable forward");
    }

    @Test
    public void carrotBesideTheTruckIsUnreachableForward() {
        // Right at the right turn-circle center (R,0): the tightest possible arc
        // still can't reach a point it would have to orbit around.
        assertTrue(infeasible(R, 0f), "a carrot at a turn-circle center can't be reached by a forward arc");
    }

    @Test
    public void carrotBehindToTheSideIsUnreachableForward() {
        // The dump's signature: carrot off to one side and slightly behind the
        // beam — deep inside a turn circle, the orbit case.
        assertTrue(infeasible(3f, -1f), "a carrot beside-and-behind is unreachable forward → reverse");
    }

    @Test
    public void carrotFarToTheSideIsReachableViaAGentlerArc() {
        // Well outside both min-circles: a wider arc (radius > R) sweeps to it.
        assertFalse(infeasible(10f, 0f), "a carrot beyond the min-circles is reachable by a gentler arc");
    }

    @Test
    public void carrotRidingExactlyOnTheMinCircleDoesNotFalseTrip() {
        // A perfectly-tracked min-radius corner puts the carrot on the circle
        // (distance == R from its center); the margin keeps that from tripping.
        assertFalse(infeasible(R, R), "a carrot on the min-turn circle is the tightest feasible corner, not a stall");
    }
}
