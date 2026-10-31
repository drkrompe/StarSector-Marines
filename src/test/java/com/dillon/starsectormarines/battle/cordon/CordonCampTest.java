package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonCampTest {

    /** A straight road up the middle of a 60-wide map, from the rear edge (y 0) toward the front (+y). */
    private static List<int[]> road() {
        List<int[]> road = new ArrayList<>();
        for (int y = 0; y < 200; y++) road.add(new int[]{30, y});
        return road;
    }

    @Test
    void theCampStandsOnTheEntryWithItsRoadKeptClear() {
        List<int[]> road = road();
        CordonCamp.Plan plan = CordonCamp.plan(road, 20, 150, 1, 60, 200, (x, y) -> true, List.of());
        assertNotNull(plan);
        assertEquals(20, plan.dropIndex());
        for (CordonCamp.Bag bag : plan.bags()) {
            assertTrue(Math.abs(bag.x() - 30) > CordonCamp.ROAD_KEEP, "no bag on the road through the camp");
        }
        for (int[] c : plan.waiting()) assertTrue(Math.abs(c[0] - 30) > CordonCamp.ROAD_KEEP + 1, "nobody waits on the road");
        assertFalse(plan.guards().isEmpty());
        for (int[] g : plan.guards()) assertEquals(20 + CordonCamp.HALF - 1, g[1], "the guards stand on the front side");
    }

    @Test
    void theStationsStandOffTheRoadAndNobodyWaitsOnThem() {
        CordonCamp.Plan plan = CordonCamp.plan(road(), 20, 150, 1, 60, 200, (x, y) -> true, List.of());
        assertNotNull(plan);
        assertFalse(plan.props().isEmpty());
        for (CordonCamp.Prop p : plan.props()) {
            assertTrue(p.x() > 30 + CordonCamp.ROAD_KEEP || p.x() + p.width() - 1 < 30 - CordonCamp.ROAD_KEEP);
            for (int[] w : plan.waiting()) {
                assertFalse(w[0] >= p.x() && w[0] < p.x() + p.width() && w[1] >= p.y() && w[1] < p.y() + p.height());
            }
        }
    }

    @Test
    void theCampKeepsOffTheBaseAndThePads() {
        List<int[]> road = road();
        CordonCamp.Plan plan = CordonCamp.plan(road, 20, 150, 1, 60, 200, (x, y) -> true, List.of(new int[]{30, 20}));
        assertNotNull(plan);
        assertTrue(Math.abs(plan.cy() - 20) > CordonCamp.HALF + CordonCamp.KEEP_OFF);
    }

    @Test
    void noCampWhereTheGroundIsShut() {
        assertNull(CordonCamp.plan(road(), 20, 150, 1, 60, 200, (x, y) -> false, List.of()));
    }

    @Test
    void theCampStandsBehindTheLine() {
        CordonCamp.Plan plan = CordonCamp.plan(road(), 100, 120, 1, 60, 200, (x, y) -> true, List.of());
        assertNotNull(plan);
        assertTrue(plan.dropIndex() <= 120 - CordonCamp.SHORT_OF_GAP);
    }

    @Test
    void aBagFacesOutFromTheMiddle() {
        assertEquals('n', CordonCamp.facing(0, 8));
        assertEquals('s', CordonCamp.facing(3, -8));
        assertEquals('e', CordonCamp.facing(8, 2));
        assertEquals('w', CordonCamp.facing(-8, 0));
    }

    @Test
    void aTruckHomeStopsAtTheMiddleOrBehindItsLeader() {
        assertTrue(CordonConvoys.stopsAtCamp(51, 50, false));
        assertFalse(CordonConvoys.stopsAtCamp(60, 50, false));
        assertTrue(CordonConvoys.stopsAtCamp(60, 50, true));
        assertFalse(CordonConvoys.stopsAtCamp(50 + CordonConvoys.GATE_QUEUE + 1, 50, true));
    }

    @Test
    void aWaitingTruckStopsAtItsPlaceOrBehindAnother() {
        assertTrue(CordonConvoys.parks(10, 12, false));
        assertFalse(CordonConvoys.parks(20, 12, false));
        assertTrue(CordonConvoys.parks(20, 12, true));
    }
}
