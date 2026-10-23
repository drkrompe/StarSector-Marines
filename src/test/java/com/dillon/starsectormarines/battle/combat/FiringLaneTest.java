package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the decision-side lane test: whether one of the shooter's own stands
 * between the muzzle and the target, asked with the same boundaries
 * {@code BallisticResolver} uses from the round's side. {@code LaneSidestep}
 * and the mech overwatch position search are built on it.
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
                "four cells to the side of a lane a cell and a half wide");
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
    public void somebodyBeyondTheTargetIsNotBetweenAnything() {
        assertFalse(FiringLane.blocked(at(40f, 20f, 0.4f), 10f, 20f, 30f, 20f));
    }
}
