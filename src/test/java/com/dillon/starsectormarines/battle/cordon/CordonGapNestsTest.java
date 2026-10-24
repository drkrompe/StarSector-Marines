package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonGapNestsTest {

    private static List<CordonGapNests.Candidate> row(int y, int x0, int x1) {
        List<CordonGapNests.Candidate> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++) out.add(new CordonGapNests.Candidate(x, y, false));
        return out;
    }

    private static double widest(List<int[]> west, List<int[]> east, List<int[]> nests, List<int[]> added) {
        List<int[]> all = new ArrayList<>(nests);
        all.addAll(added);
        return CordonGapLaw.widestStep(west, east, all);
    }

    @Test
    void closesAWideGapWithTheFewestNestsAlongTheLine() {
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{150, 0});
        List<int[]> added = CordonGapNests.close(west, east, List.of(), row(0, 1, 149), CordonGapNests.REACH, 8);
        assertEquals(2, added.size());
        assertTrue(widest(west, east, List.of(), added) <= CordonGapLaw.LIMIT);
    }

    @Test
    void addsNothingWhereTheChainAlreadyHolds() {
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{100, 0});
        List<int[]> nests = List.of(new int[]{50, 0});
        assertTrue(CordonGapNests.close(west, east, nests, row(0, 1, 99), CordonGapNests.REACH, 8).isEmpty());
    }

    @Test
    void bridgesOnlyTheBrokenStepAndJoinsTheNestsAlreadyDug() {
        // Nests dug close to the west leave one long step to the east tower.
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{160, 0});
        List<int[]> nests = List.of(new int[]{40, 0}, new int[]{80, 0});
        List<int[]> added = CordonGapNests.close(west, east, nests, row(0, 1, 159), CordonGapNests.REACH, 8);
        assertEquals(1, added.size());
        assertTrue(added.get(0)[0] > 80);
        assertTrue(widest(west, east, nests, added) <= CordonGapLaw.LIMIT);
    }

    @Test
    void prefersAStreetMouthThatDoesNearlyAsWell() {
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{100, 0});
        List<CordonGapNests.Candidate> candidates = new ArrayList<>(row(0, 1, 99));
        candidates.set(47, new CordonGapNests.Candidate(48, 0, true));
        // A mouth that would leave the far step open costs a nest more, and is passed over.
        candidates.set(39, new CordonGapNests.Candidate(40, 0, true));
        List<int[]> added = CordonGapNests.close(west, east, List.of(), candidates, CordonGapNests.REACH, 8);
        assertEquals(1, added.size());
        assertArrayEquals(new int[]{48, 0}, added.get(0));
    }

    @Test
    void leavesTheGapBrokenWhenNoCellMakesWay() {
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{150, 0});
        assertTrue(CordonGapNests.close(west, east, List.of(), row(0, 100, 120), CordonGapNests.REACH, 8).isEmpty());
        List<int[]> capped = CordonGapNests.close(west, east, List.of(), row(0, 1, 149), CordonGapNests.REACH, 1);
        assertEquals(1, capped.size());
        assertFalse(widest(west, east, List.of(), capped) <= CordonGapLaw.LIMIT);
    }

    /** A 40 x 20 ground, front toward +y: a street up column 20 from the line's row 4 to the front, a wall at 30,5. */
    private static CordonGapNests.Ground street() {
        return new CordonGapNests.Ground() {
            @Override public boolean takes(int x, int y) { return x >= 0 && y >= 0 && x < 40 && y < 20 && !road(x, y) && !wall(x, y); }
            @Override public boolean road(int x, int y) { return x == 20 && y >= 0 && y < 20; }
            @Override public boolean seeThrough(int x, int y) { return !wall(x, y); }
            @Override public int tier(int x, int y) { return 0; }
            private boolean wall(int x, int y) { return x == 17 && y == 5; }
        };
    }

    @Test
    void aStreetMouthStandsBesideAStreetRunningToTheFrontInViewOfIt() {
        CordonGapNests.Ground g = street();
        assertTrue(CordonGapNests.mouth(g, 23, 4, 1));
        assertFalse(CordonGapNests.mouth(g, 30, 4, 1), "too far from the street");
        assertFalse(CordonGapNests.mouth(g, 16, 4, 1), "a wall stands between it and the street");
        assertFalse(CordonGapNests.mouth(g, 23, 4, -1), "the street does not run on toward a front behind");
    }

    @Test
    void candidatesKeepOffTheStreetAndOutOfTheRedoubtsGround() {
        List<CordonGapNests.Candidate> cs = CordonGapNests.candidates(street(), new int[]{0, 4}, new int[]{39, 4}, 1,
                List.of(new int[]{0, 0, 5, 10}), true);
        boolean mouth = false;
        for (CordonGapNests.Candidate c : cs) {
            assertTrue(Math.abs(c.x() - 20) >= 2, "beside the road at " + c.x());
            assertTrue(c.x() > 6, "inside the redoubt's ground at " + c.x());
            assertTrue(c.y() >= 4 - CordonGapNests.BUILT_REAR_BAND && c.y() <= 4 + CordonGapNests.BUILT_FRONT_BAND);
            mouth |= c.mouth();
        }
        assertTrue(mouth);
    }

    @Test
    void inOpenCountryANestStandsOnTheCrestNeverBelowItsCliff() {
        // The crest is row 4 and behind; the front (+y) falls away a tier from row 5.
        CordonGapNests.Ground ridge = new CordonGapNests.Ground() {
            @Override public boolean takes(int x, int y) { return y >= 0 && y < 20; }
            @Override public boolean road(int x, int y) { return false; }
            @Override public boolean seeThrough(int x, int y) { return true; }
            @Override public int tier(int x, int y) { return y <= 4 ? 1 : 0; }
        };
        List<CordonGapNests.Candidate> cs = CordonGapNests.candidates(ridge, new int[]{0, 4}, new int[]{30, 4}, 1,
                List.of(), false);
        assertFalse(cs.isEmpty());
        for (CordonGapNests.Candidate c : cs) {
            assertTrue(c.y() <= 4 && c.y() >= 4 - CordonGapNests.REAR_BAND, "off the crest at " + c.y());
            assertFalse(c.mouth());
        }
    }

    private static List<CordonGapNests.Candidate> withMouths(int... at) {
        List<CordonGapNests.Candidate> out = new ArrayList<>(row(0, 1, 149));
        for (int x : at) out.set(x - 1, new CordonGapNests.Candidate(x, 0, true));
        return out;
    }

    @Test
    void standsTheWholeGapAtStreetMouthsForOneNestMore() {
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{150, 0});
        List<int[]> added = CordonGapNests.place(west, east, List.of(), withMouths(30, 70, 110), CordonGapNests.REACH, 8);
        assertEquals(3, added.size());
        for (int[] c : added) assertTrue(c[0] == 30 || c[0] == 70 || c[0] == 110, "not a mouth at " + c[0]);
        assertTrue(widest(west, east, List.of(), added) <= CordonGapLaw.LIMIT);
    }

    @Test
    void keepsToOpenGroundWhenMouthsWouldCostTwoNestsMore() {
        List<int[]> west = List.of(new int[]{0, 0}), east = List.of(new int[]{150, 0});
        List<int[]> added = CordonGapNests.place(west, east, List.of(), withMouths(30, 60, 90, 120),
                CordonGapNests.REACH, 8);
        assertEquals(2, added.size());
    }
}
