package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting.Anchors;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting.Point;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortSiting.Valued;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The siting geometry alone: where a mission's forts ought to stand relative to the landing and the keep. */
class FortSitingTest {

    private static final Anchors WEST_TO_EAST = new Anchors(new Point(480, 168), new Point(80, 168), 560, 336);

    @Test
    void aSingleBatteryStandsOnTheApproachHalfWayFromTheKeep() {
        List<Point> targets = FortSiting.batteryTargets(WEST_TO_EAST, 1);
        assertEquals(List.of(new Point(280, 168)), targets);
    }

    @Test
    void batteryFortsAreSpreadAcrossTheApproachAndStaggeredInDepth() {
        List<Point> targets = FortSiting.batteryTargets(WEST_TO_EAST, 2);
        assertEquals(2, targets.size());
        // Spread across the line of advance (y), a fort's width apart, on either side of it.
        assertEquals(FortSiting.BATTERY_LATERAL_SPACING, Math.abs(targets.get(0).y() - targets.get(1).y()));
        assertEquals(168, (targets.get(0).y() + targets.get(1).y()) / 2);
        // Staggered, never in one rank, and always between the landing and the keep.
        assertTrue(targets.get(0).x() != targets.get(1).x());
        for (Point p : targets) assertTrue(p.x() > 80 && p.x() < 480, "between the ends: " + p);
    }

    @Test
    void theSpreadFollowsTheApproachWhateverItsDirection() {
        // Landing south of the keep: the approach runs along y and the forts spread along x.
        Anchors south = new Anchors(new Point(280, 290), new Point(280, 40), 560, 336);
        List<Point> targets = FortSiting.batteryTargets(south, 2);
        assertEquals(FortSiting.BATTERY_LATERAL_SPACING, Math.abs(targets.get(0).x() - targets.get(1).x()));
        for (Point p : targets) assertTrue(p.y() > 40 && p.y() < 290, "between the ends: " + p);
    }

    @Test
    void targetsStayInsideTheMapEdge() {
        List<Point> targets = FortSiting.batteryTargets(
                new Anchors(new Point(30, 10), new Point(10, 10), 100, 60), 4);
        for (Point p : targets) {
            assertTrue(p.x() >= 0 && p.x() < 100 && p.y() >= 0 && p.y() < 60, "inside the map: " + p);
        }
    }

    @Test
    void theShieldFortAimsAtTheKeep() {
        assertEquals(WEST_TO_EAST.keep(), FortSiting.shieldTarget(WEST_TO_EAST));
    }

    @Test
    void aFortThatCannotHaveItsExactGroundSlidesAlongTheApproachBeforeLeavingIt() {
        Point target = new Point(280, 168);
        long exact = FortSiting.approachCost(WEST_TO_EAST, target, 280, 168);
        long slid = FortSiting.approachCost(WEST_TO_EAST, target, 330, 168);
        long wide = FortSiting.approachCost(WEST_TO_EAST, target, 280, 218);
        assertEquals(0, exact);
        // The same distance off costs more across the line of advance than along it.
        assertTrue(slid < wide, "slid " + slid + " wide " + wide);
        // Behind the landing and beyond the keep are not on the approach at all.
        assertTrue(FortSiting.approachCost(WEST_TO_EAST, target, 40, 168) >= FortSiting.OFF_APPROACH);
        assertTrue(FortSiting.approachCost(WEST_TO_EAST, target, 500, 168) >= FortSiting.OFF_APPROACH);
        assertTrue(FortSiting.approachCost(WEST_TO_EAST, target, 150, 168) < FortSiting.OFF_APPROACH);
    }

    @Test
    void aDomeCoversTheWeightOfWhatLiesWithinItsReach() {
        List<Valued> garrison = List.of(new Valued(100, 100, 6), new Valued(110, 100, 2), new Valued(140, 100, 2));
        assertEquals(8, FortSiting.domeCoverage(garrison, 105, 100, 16f));
        assertEquals(6, FortSiting.domeCoverage(garrison, 90, 100, 16f));
        assertEquals(0, FortSiting.domeCoverage(garrison, 100, 160, 16f));
    }

    // ------------------------------------------------------------------------------------------------- slide

    private static final FortSiting.Blocked NOTHING = (x, y, half) -> false;

    @Test
    void aFreeTargetDoesNotSlide() {
        Point target = new Point(280, 168);
        assertEquals(target, FortSiting.slide(WEST_TO_EAST, target, 46, true, NOTHING, List.of()));
    }

    @Test
    void aBlockedBatteryTargetSlidesAlongTheLineOfAdvanceAndStaysOnTheApproach() {
        Point target = new Point(280, 168);
        // A programmed place holds the ground round the target: a wide band across the way, a short run along it.
        FortSiting.Blocked place = (x, y, half) -> Math.abs(x - 280) < 30 + half && Math.abs(y - 168) < 30 + half;
        Point slid = FortSiting.slide(WEST_TO_EAST, target, 46, true, place, List.of());
        assertTrue(!slid.equals(target), "it did not slide");
        assertTrue(!place.box(slid.x(), slid.y(), 46), "slid onto the blocked ground: " + slid);
        assertTrue(FortSiting.approachCost(WEST_TO_EAST, target, slid.x(), slid.y()) < FortSiting.OFF_APPROACH,
                "left the approach: " + slid);
    }

    @Test
    void aBatteryFortPrefersToSlideAlongTheLineOverAcrossIt() {
        Point target = new Point(280, 168);
        // Free ground exists both 60 along and 60 across; along is the cheaper move.
        FortSiting.Blocked ring = (x, y, half) -> {
            boolean along = Math.abs(x - 340) <= 3 && Math.abs(y - 168) <= 3;
            boolean across = Math.abs(x - 280) <= 3 && Math.abs(y - 228) <= 3;
            return !(along || across);
        };
        Point slid = FortSiting.slide(WEST_TO_EAST, target, 46, true, ring, List.of());
        assertEquals(168, slid.y(), 4, "moved across the line: " + slid);
        assertEquals(340, slid.x(), 4);
    }

    @Test
    void aShieldFortTakesTheNearestFreeGroundToTheKeep() {
        Point keep = new Point(480, 168);
        // The keep's own place holds everything within 90 cells of it.
        FortSiting.Blocked garrison = (x, y, half) -> Math.hypot(x - 480, y - 168) < 90 + half;
        Point at = FortSiting.slide(WEST_TO_EAST, keep, 42, false, garrison, List.of());
        double reach = Math.hypot(at.x() - 480, at.y() - 168);
        assertTrue(reach >= 90 + 42 && reach < 90 + 42 + 12, "not the nearest free ground: " + reach);
    }

    @Test
    void reservedFortsStandApart() {
        Point first = new Point(280, 168);
        Point second = FortSiting.slide(WEST_TO_EAST, new Point(284, 168), 46, true, NOTHING, List.of(first));
        assertTrue(Math.hypot(second.x() - first.x(), second.y() - first.y()) >= FortSiting.SEPARATION,
                "stand flush: " + second);
    }

    @Test
    void aTargetWithNowhereToGoStaysWhereItWas() {
        Point target = new Point(280, 168);
        assertEquals(target, FortSiting.slide(WEST_TO_EAST, target, 46, true, (x, y, half) -> true, List.of()));
    }

    // -------------------------------------------------------------------------------------------------- dome

    @Test
    void aDomeCoversItsOwnWorksAtLeast() {
        assertEquals(31f, FortSiting.domeRadius(new Point(100, 100), 31f, null));
    }

    @Test
    void aDomeSitedToProtectTheKeepReachesItsFarthestCorner() {
        // A fort 70 cells west of a keep whose box is 20 wide and 10 tall.
        float radius = FortSiting.domeRadius(new Point(100, 100), 31f, new int[]{170, 95, 189, 104});
        // The farthest corner of the box is the cell edge at (190, 105): a little over 90 cells away.
        assertTrue(radius >= Math.hypot(90, 5), "does not reach the far corner: " + radius);
        assertTrue(radius <= 93f, "far larger than the keep needs: " + radius);
    }

    @Test
    void aDomeIsCappedSoAFarKeepIsSimplyNotCovered() {
        assertEquals(FortSiting.MAX_DOME_RADIUS, FortSiting.domeRadius(new Point(0, 0), 30f, new int[]{900, 0, 910, 10}));
    }
}
