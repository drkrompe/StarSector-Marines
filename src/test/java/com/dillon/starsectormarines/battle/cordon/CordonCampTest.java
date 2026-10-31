package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        CordonCamp.Plan plan = CordonCamp.plan(road, 20, 100, 1, 60, 200, (x, y) -> true, List.of());
        assertNotNull(plan);
        assertEquals(100 - CordonCamp.BEHIND_GAP, plan.dropIndex(), "a buffer behind the line, off the rear edge");
        for (CordonCamp.Bag bag : plan.bags()) {
            assertTrue(Math.abs(bag.x() - 30) > CordonCamp.ROAD_KEEP, "no bag on the road through the camp");
        }
        for (int[] c : plan.waiting()) assertTrue(Math.abs(c[0] - 30) > CordonCamp.ROAD_KEEP + 1, "nobody waits on the road");
        assertEquals(CordonCamp.GUARDS, plan.guards().size());
        for (int[] g : plan.guards()) assertEquals(plan.cy() + CordonCamp.HALF - 1, g[1], "a team stands on the front side");
        assertEquals(CordonCamp.GUARDS, plan.rearGuards().size());
        for (int[] g : plan.rearGuards()) assertEquals(plan.cy() - CordonCamp.HALF + 1, g[1], "a team stands on the rear");
    }

    @Test
    void theRingIsWholeButForTheRoadWithItsCornersAndAGunInEachCorner() {
        CordonCamp.Plan plan = CordonCamp.plan(road(), 20, 100, 1, 60, 200, (x, y) -> true, List.of());
        assertNotNull(plan);
        int side = 2 * CordonCamp.HALF + 1, road = 2 * (2 * CordonCamp.ROAD_KEEP + 1);
        assertEquals(4 * (side - 1) - road, plan.bags().size(), "every ring cell off the road carries a bag");
        long corners = plan.bags().stream().filter(b -> b.art().startsWith("corner-")).count();
        assertEquals(4, corners);
        assertEquals(4, plan.guns().size());
        for (int[] g : plan.guns()) {
            assertEquals(CordonCamp.HALF - 1, Math.abs(g[0] - 30));
            assertEquals(CordonCamp.HALF - 1, Math.abs(g[1] - plan.cy()));
        }
    }

    @Test
    void aCornerBagIsNamedForItsCorner() {
        assertEquals("corner-ne", CordonCamp.bagArt(12, 12));
        assertEquals("corner-sw", CordonCamp.bagArt(-12, -12));
        assertEquals("n", CordonCamp.bagArt(3, 12));
        assertEquals("w", CordonCamp.bagArt(-12, 4));
    }

    @Test
    void theStationsStandOffTheRoadAndNobodyWaitsOnThem() {
        CordonCamp.Plan plan = CordonCamp.plan(road(), 20, 100, 1, 60, 200, (x, y) -> true, List.of());
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
        CordonCamp.Plan plan = CordonCamp.plan(road, 20, 150, 1, 60, 200, (x, y) -> true, List.of(new int[]{30, 80}));
        assertNotNull(plan);
        assertTrue(Math.abs(plan.cy() - 80) > CordonCamp.HALF + CordonCamp.KEEP_OFF);
    }

    @Test
    void noCampWhereTheGroundIsShut() {
        assertNull(CordonCamp.plan(road(), 20, 100, 1, 60, 200, (x, y) -> false, List.of()));
    }

    @Test
    void theCampIsWantedABufferBehindTheLineOrAtTheBaseWhereThatIsNearer() {
        assertEquals(300 - CordonCamp.BEHIND_GAP, CordonCamp.wanted(6, 300));
        assertEquals(250, CordonCamp.wanted(250, 300));
        assertEquals(1, CordonCamp.wanted(0, 20));
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
        assertFalse(CordonConvoys.stopsAtCamp(50 + CordonConvoys.CAMP_QUEUE + 1, 50, true));
    }

    /**
     * A 140 by 120 open map: the corridor up x 20 from the rear edge to y 80, east along it to x 120 and up to the
     * front, the line at y 100; a base's buildings at x 70..90, y 40..60.
     */
    private static final int[] BASE = {70, 40, 90, 60};

    private static List<int[]> bent() {
        List<int[]> road = new ArrayList<>();
        for (int y = 1; y <= 80; y++) road.add(new int[]{20, y});
        for (int x = 21; x <= 120; x++) road.add(new int[]{x, 80});
        for (int y = 81; y < 118; y++) road.add(new int[]{120, y});
        return road;
    }

    private static NavigationGrid open(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkable(x, y, true);
        return grid;
    }

    private static int gap(List<int[]> road) {
        for (int i = 0; i < road.size(); i++) if (road.get(i)[1] >= 100) return i;
        return road.size() - 1;
    }

    @Test
    void besideTheBaseTheCampStandsWestOfItAcrossAnApronWithItsOwnRoadOntoTheCorridor() {
        List<int[]> road = bent();
        CordonCamp.Beside beside = CordonCamp.beside(open(140, 120), (x, y) -> false, road, gap(road), 1, BASE,
                (x, y) -> true, List.of());
        assertNotNull(beside);
        CordonCamp.Plan plan = beside.plan();
        assertEquals(BASE[0] - CordonCamp.APRON - 1, plan.cx() + CordonCamp.HALF, "an apron between camp and base");
        assertEquals((BASE[1] + BASE[3]) / 2, plan.cy(), "level with the base");
        List<int[]> route = beside.corridor();
        assertArrayEquals(new int[]{plan.cx(), plan.cy()}, route.get(0), "the road sets out from the camp's middle");
        assertArrayEquals(road.get(road.size() - 1), route.get(route.size() - 1), "and runs on to the town");
        for (int i = 1; i < route.size(); i++) {
            int[] a = route.get(i - 1), b = route.get(i);
            assertEquals(1, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]), "a drivable way, cell by cell");
        }
        int[] drop = route.get(plan.dropIndex());
        assertEquals(BASE[0] - CordonCamp.APRON + CordonCamp.APRON / 2 - 1, drop[0], "the trucks stop in the apron's middle");
        assertEquals(plan.cy(), drop[1]);
        for (CordonCamp.Bag bag : plan.bags()) {
            assertFalse(bag.y() == plan.cy() && bag.x() > plan.cx(), "the ring is open where the road leaves it");
        }
        assertEquals(4, plan.bags().stream().filter(b -> b.art().startsWith("corner-")).count());
    }

    @Test
    void aBaseWithNoRoomWestTakesTheCampOnItsEast() {
        List<int[]> road = bent();
        List<int[]> keepOff = List.of(new int[]{40, 50});
        CordonCamp.Beside beside = CordonCamp.beside(open(140, 120), (x, y) -> false, road, gap(road), 1, BASE,
                (x, y) -> x > 60, keepOff);
        assertNotNull(beside);
        assertEquals(BASE[2] + CordonCamp.APRON + 1, beside.plan().cx() - CordonCamp.HALF);
    }

    @Test
    void noBaseOrNoWayMeansNoCampBesideIt() {
        List<int[]> road = bent();
        assertNull(CordonCamp.beside(open(140, 120), (x, y) -> false, road, gap(road), 1, null, (x, y) -> true,
                List.of()));
        assertNull(CordonCamp.beside(new NavigationGrid(140, 120), (x, y) -> false, road, gap(road), 1, BASE,
                (x, y) -> true, List.of()), "nothing drivable, no way to the corridor");
    }

    @Test
    void theBasesFootprintIsTheBoundsOfItsBuildings() {
        assertArrayEquals(new int[]{95, 102, 110, 104},
                CordonCamp.footprint((x, y) -> x >= 95 && x <= 110 && y >= 102 && y <= 104, 100, 100));
        assertNull(CordonCamp.footprint((x, y) -> x > 200, 100, 100), "buildings beyond its reach are not its own");
    }
}
