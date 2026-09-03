package com.dillon.starsectormarines.battle.world.gen.precinct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route a lane takes, asked of the class that decides it.
 *
 * <p>Everything here is arithmetic on one path against one frame — no
 * generator, no map, no battle — because that is the whole of what a path is.
 */
class LanePathTest {

    private static final int WIDTH = 560;
    private static final int HEIGHT = 336;

    /** Lane 1 of 3 on a west-to-east map: the middle strip, 112 cells of it. */
    private static LanePath.Frame frame() {
        return new LanePath.Frame(true, 93, 466,
                LaneGeometry.startInclusive(1, 3, HEIGHT),
                LaneGeometry.endInclusive(1, 3, HEIGHT), WIDTH, HEIGHT);
    }

    private static final float[] FRACTIONS = {0.30f, 0.55f, 0.80f};

    @Test
    @DisplayName("a straight path runs up the middle of the lane's own strip")
    void straightRunsUpTheCentreline() {
        LanePath.Frame frame = frame();
        List<int[]> cells = LanePath.straight(frame, FRACTIONS, 30).cells(WIDTH, HEIGHT);
        assertEquals(3, cells.size());
        for (int[] cell : cells) {
            assertEquals(frame.lateralCentre(), cell[1],
                    "a straight path does not leave the centreline");
        }
        assertEquals(205, cells.get(0)[0], "the outermost rung, 0.30 of the way in");
        assertEquals(298, cells.get(1)[0]);
        assertEquals(391, cells.get(2)[0]);
    }

    @Test
    @DisplayName("waypoints run in path order, the attacker's end first")
    void waypointsRunInPathOrder() {
        List<int[]> cells = LanePath.straight(frame(), FRACTIONS, 30).cells(WIDTH, HEIGHT);
        for (int i = 1; i < cells.size(); i++) {
            assertTrue(cells.get(i)[0] > cells.get(i - 1)[0],
                    "waypoint " + i + " stands further toward the objective than " + (i - 1));
        }
    }

    @Test
    @DisplayName("a meandering path drifts sideways and never leaves its own strip")
    void meanderingStaysInTheStrip() {
        LanePath.Frame frame = frame();
        LanePath path = LanePath.meandering(frame, FRACTIONS, 30, new Random(42));
        List<int[]> cells = path.cells(WIDTH, HEIGHT);
        boolean drifted = false;
        for (int[] cell : cells) {
            assertTrue(cell[1] >= frame.laneStart() && cell[1] <= frame.laneEnd(),
                    "waypoint at lateral " + cell[1] + " left the strip "
                            + frame.laneStart() + ".." + frame.laneEnd());
            if (cell[1] != frame.lateralCentre()) drifted = true;
        }
        assertTrue(drifted, "a meandering path that never left the centreline is a straight one");
        // The forward positions are the same fractions of the same axis: a lane
        // bends across its strip, it does not move its rungs up and down it.
        List<int[]> straight = LanePath.straight(frame, FRACTIONS, 30).cells(WIDTH, HEIGHT);
        for (int i = 0; i < cells.size(); i++) {
            assertEquals(straight.get(i)[0], cells.get(i)[0]);
        }
    }

    @Test
    @DisplayName("the same seed meanders the same way and a different one does not")
    void meanderIsSeeded() {
        LanePath.Frame frame = frame();
        LanePath first = LanePath.meandering(frame, FRACTIONS, 30, new Random(7));
        LanePath again = LanePath.meandering(frame, FRACTIONS, 30, new Random(7));
        LanePath other = LanePath.meandering(frame, FRACTIONS, 30, new Random(8));
        assertEquals(first, again);
        assertNotEquals(first, other);
    }

    @Test
    @DisplayName("a waypoint on refused ground slides along the path until it is clear")
    void refusedWaypointSlidesAlongThePath() {
        LanePath.Frame frame = frame();
        LanePath path = LanePath.straight(frame, FRACTIONS, 30);
        int refusedX = path.cells(WIDTH, HEIGHT).get(1)[0];
        // A band of refused ground across the middle waypoint, and nothing else
        // on the map refused: a claim standing where a rung was told to stand.
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT,
                (x, y) -> Math.abs(x - refusedX) > 10, 120);

        assertEquals(1, fit.moved().size(), "one waypoint moved, and only one");
        LanePath.Move move = fit.moved().get(0);
        assertEquals(1, move.index());
        assertEquals(11, move.cells(), "slid just clear of the refused band");
        List<int[]> cells = fit.path().cells(WIDTH, HEIGHT);
        assertEquals(refusedX + 11, cells.get(1)[0], "and it slid toward the objective");
        assertEquals(frame.lateralCentre(), cells.get(1)[1],
                "sliding is along the path, not across it");
    }

    @Test
    @DisplayName("the last waypoint slides backward, because ahead of it is the objective")
    void theDeepestWaypointGivesWayBackward() {
        LanePath.Frame frame = frame();
        LanePath path = LanePath.straight(frame, FRACTIONS, 30);
        int refusedFrom = path.cells(WIDTH, HEIGHT).get(2)[0] - 10;
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT, (x, y) -> x < refusedFrom, 120);

        assertEquals(1, fit.moved().size());
        assertEquals(2, fit.moved().get(0).index());
        assertEquals(refusedFrom - 1, fit.path().cells(WIDTH, HEIGHT).get(2)[0]);
    }

    @Test
    @DisplayName("a waypoint with nowhere at all to go keeps its place")
    void nowhereToGoKeepsItsPlace() {
        LanePath path = LanePath.straight(frame(), FRACTIONS, 30);
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT, (x, y) -> false, 120);
        assertEquals(List.of(), fit.moved(), "nothing moved, because nothing could");
        assertEquals(path, fit.path(), "and the path is the one that was stated");
    }

    @Test
    @DisplayName("ground that allows everything moves nothing")
    void openGroundMovesNothing() {
        LanePath path = LanePath.meandering(frame(), FRACTIONS, 30, new Random(3));
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT, LanePath.Ground.ANYWHERE, 120);
        assertEquals(path, fit.path());
        assertTrue(fit.moved().isEmpty());
    }

    @Test
    @DisplayName("a path lays out the same shape whatever size the map is")
    void fractionsScaleWithTheMap() {
        LanePath path = LanePath.ofCells(WIDTH, HEIGHT, 140, 168, 280, 84, 420, 168);
        List<int[]> big = path.cells(1120, 672);
        // Twice the map, twice the cell — within the rounding a fraction costs.
        assertTrue(Math.abs(big.get(0)[0] - 280) <= 1, "x scaled: " + big.get(0)[0]);
        assertTrue(Math.abs(big.get(0)[1] - 336) <= 1, "y scaled: " + big.get(0)[1]);
        assertTrue(Math.abs(big.get(1)[1] - 168) <= 1, "and the bend with it: " + big.get(1)[1]);
    }

    @Test
    @DisplayName("a path with no waypoints is not a path")
    void emptyPathIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new LanePath(List.of()));
    }

    @Test
    @DisplayName("a waypoint off the map is refused")
    void waypointOffTheMapIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new LanePath.Waypoint(1.4f, 0.5f));
        assertThrows(IllegalArgumentException.class, () -> new LanePath.Waypoint(0.5f, -0.1f));
    }

    @Test
    @DisplayName("a lane laid across y instead of x is the same arithmetic")
    void aSouthToNorthLaneUsesTheOtherAxis() {
        LanePath.Frame frame = new LanePath.Frame(false, 56, 280,
                LaneGeometry.startInclusive(0, 3, WIDTH),
                LaneGeometry.endInclusive(0, 3, WIDTH), WIDTH, HEIGHT);
        List<int[]> cells = LanePath.straight(frame, FRACTIONS, 30).cells(WIDTH, HEIGHT);
        for (int[] cell : cells) {
            assertEquals(frame.lateralCentre(), cell[0], "lateral is x on this axis");
        }
        for (int i = 1; i < cells.size(); i++) {
            assertTrue(cells.get(i)[1] > cells.get(i - 1)[1], "forward is y on this axis");
        }
    }
}
