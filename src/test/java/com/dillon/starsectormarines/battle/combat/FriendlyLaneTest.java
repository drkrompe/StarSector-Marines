package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lane a shooter checks for friends before firing ({@link BallisticResolver#inLane}): a shooter at the origin
 * aims east at a target ten cells off.
 */
class FriendlyLaneTest {

    private static final float R = 0.3f;

    private static boolean blocks(float x, float y) {
        return BallisticResolver.inLane(0f, 0f, 10f, 0f, x, y, R);
    }

    @Test
    void aFriendOnTheLineBetweenShooterAndTargetBlocksIt() {
        assertTrue(blocks(5f, 0f));
        // A shoulder off the line is still in it: the round strays laterally.
        assertTrue(blocks(5f, R + BallisticResolver.FRIENDLY_LANE_CLEARANCE - 0.01f));
    }

    @Test
    void aFriendBesideTheLaneDoesNotBlockIt() {
        assertFalse(blocks(5f, R + BallisticResolver.FRIENDLY_LANE_CLEARANCE + 0.01f));
        assertFalse(blocks(5f, -2f));
    }

    @Test
    void aSquadmateAtTheShootersShoulderDoesNotBlockIt() {
        // The resolver never lets a round strike a friend this close to the muzzle, so neither does the lane.
        assertFalse(blocks(BallisticResolver.PROXIMITY_CATCH_ZERO_DISTANCE - 0.1f, 0f));
        assertFalse(blocks(-3f, 0f));
    }

    @Test
    void theLaneRunsJustPastTheTargetWhereAMissGoesFirst() {
        assertTrue(blocks(10f + BallisticResolver.FRIENDLY_LANE_OVERSHOOT - 0.1f, 0f));
        assertFalse(blocks(10f + BallisticResolver.FRIENDLY_LANE_OVERSHOOT + 0.1f, 0f));
    }

    @Test
    void aBigBodyBlocksFromFurtherOffTheLine() {
        assertTrue(BallisticResolver.inLane(0f, 0f, 10f, 0f, 5f, 1.4f, 1.3f));
    }

    @Test
    void aDiagonalLaneIsMeasuredAlongItsOwnLine() {
        assertTrue(BallisticResolver.inLane(0f, 0f, 6f, 8f, 3f, 4f, R));
        assertFalse(BallisticResolver.inLane(0f, 0f, 6f, 8f, 4f, 3f, R));
    }
}
