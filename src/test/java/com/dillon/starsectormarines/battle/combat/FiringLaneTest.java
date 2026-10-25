package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the decision-side lane test: whether one of the shooter's own stands
 * in the lane to the target, by the same lane the firing gate holds on.
 * {@code LaneSidestep} is built on it.
 */
public class FiringLaneTest {

    private static final int SHOOTER_X = 10;

    private static FiringLane.Friendlies at(float x, float y, float radius) {
        FiringLane.Friendlies allies = new FiringLane.Friendlies();
        allies.add(x, y, radius);
        return allies;
    }

    @Test
    public void somebodyStandingOnTheLineIsInTheWay() {
        assertTrue(FiringLane.blocked(at(20f, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }

    @Test
    public void somebodyStandingWellOffTheLineIsNot() {
        assertFalse(FiringLane.blocked(at(20f, 24f, 0.4f), 10f, 20f, 30f, 20f),
                "four cells to the side of the lane");
    }

    /**
     * The lane is the firing gate's, as wide as the body in it and a margin. A
     * squadmate a shoulder off the line leaves it clear: a lane drawn wider than
     * the gate's walls off every cell beside a packed squad, so the sidestep
     * found nowhere to go while the gate held the trigger.
     */
    @Test
    public void somebodyAShoulderOffTheLineLeavesItClearAsTheGateDoes() {
        float x = 20f, y = 21f, radius = 0.4f;
        assertFalse(FiringLane.blocked(at(x, y, radius), 10f, 20f, 30f, 20f));
        assertFalse(BallisticResolver.inLane(10f, 20f, 30f, 20f, x, y, radius));
    }

    /**
     * A friend in melee with the target, just past him, is where a missed round
     * goes first, and the gate holds on him. A lane that stopped at the target
     * declined to step on a lane the gate would not fire down.
     */
    @Test
    public void somebodyJustPastTheTargetIsInTheWayAsTheGateHolds() {
        float x = 31f, y = 20f, radius = 0.4f;
        assertTrue(FiringLane.blocked(at(x, y, radius), 10f, 20f, 30f, 20f));
        assertTrue(BallisticResolver.inLane(10f, 20f, 30f, 20f, x, y, radius));
    }

    /**
     * The one boundary shared with the ballistic model rather than restated: a
     * body inside the muzzle's zero-catch distance cannot take the round, so it
     * is not in the way however exactly it stands on the line.
     */
    @Test
    public void somebodyAtTheMuzzleIsNotInTheWayBecauseTheRoundCannotCatchThem() {
        float justInside = SHOOTER_X + BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE - 0.5f;
        assertFalse(FiringLane.blocked(at(justInside, 20f, 0.4f), 10f, 20f, 30f, 20f));
        float justOutside = SHOOTER_X + BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE + 0.5f;
        assertTrue(FiringLane.blocked(at(justOutside, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }

    @Test
    public void somebodyWellBeyondTheTargetIsNotInTheWay() {
        assertFalse(FiringLane.blocked(at(40f, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }
}
