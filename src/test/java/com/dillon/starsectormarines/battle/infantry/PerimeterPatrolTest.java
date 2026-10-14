package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PerimeterPatrolTest {

    private static PerimeterPatrol aroundBox(int left, int top, int right, int bottom) {
        return new PerimeterPatrol(new TacticalNode(TacticalNode.Kind.BEACHHEAD, (left + right) / 2, (top + bottom) / 2,
                left, top, right, bottom, Faction.MARINE, 0, 6));
    }

    @Test
    void theRingStandsOutsideTheHeldGround() {
        PerimeterPatrol patrol = aroundBox(10, 20, 30, 40);
        int m = PerimeterPatrol.RING_MARGIN;
        assertArrayEquals(new int[] {10 - m, 20 - m}, patrol.ringPoint(0));
        assertArrayEquals(new int[] {30 + m, 40 + m}, patrol.ringPoint(4));
        assertArrayEquals(new int[] {10 - m, 30}, patrol.ringPoint(7));
    }

    @Test
    void aWaypointIsPlacedOnTheRingPointNearestIt() {
        PerimeterPatrol patrol = aroundBox(10, 20, 30, 40);
        int[] corner = patrol.ringPoint(2);
        assertEquals(2, patrol.nearestRingIndex(corner[0] + 1, corner[1] - 1));
        int[] edge = patrol.ringPoint(5);
        assertEquals(5, patrol.nearestRingIndex(edge[0], edge[1]));
    }
}
