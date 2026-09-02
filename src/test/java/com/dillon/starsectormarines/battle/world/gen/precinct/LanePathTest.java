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
    private static final int MARGIN = 30;

    /** The ground every lane on this map shares: the beachhead and the keep. */
    private static final int START_FORWARD = 93;
    private static final int END_FORWARD = 466;
    private static final int SHARED_LATERAL = 168;

    /** One lane of three on a west-to-east map, with the shared ends on it. */
    private static LanePath.Frame frame(int lane) {
        return new LanePath.Frame(true, START_FORWARD, SHARED_LATERAL,
                END_FORWARD, SHARED_LATERAL,
                LaneGeometry.startInclusive(lane, 3, HEIGHT),
                LaneGeometry.endInclusive(lane, 3, HEIGHT), WIDTH, HEIGHT);
    }

    private static final float[] FRACTIONS = {0.30f, 0.55f, 0.80f};

    @Test
    @DisplayName("the spread is nothing at the beachhead, everything at the middle rung, "
            + "and closing at the keep")
    void theSpreadEnvelopeOpensAndCloses() {
        float peak = LanePath.peakOf(FRACTIONS);
        assertEquals(0.55f, peak, "the middle rung is where the fan is widest");
        assertEquals(0f, LanePath.spreadAt(0f, peak), 1e-6f);
        assertEquals(1f, LanePath.spreadAt(peak, peak), 1e-6f);
        assertEquals(0f, LanePath.spreadAt(1f, peak), 1e-6f);
        // Between the ends and the peak it opens and closes without turning
        // back: a lane that widened, narrowed and widened again would be a
        // zig-zag rather than a fan.
        float last = -1f;
        for (float at = 0f; at <= peak; at += 0.05f) {
            float now = LanePath.spreadAt(at, peak);
            assertTrue(now >= last, "the fan narrows on the way out at " + at);
            last = now;
        }
        last = 1f;
        for (float at = peak; at <= 1f; at += 0.05f) {
            float now = LanePath.spreadAt(at, peak);
            assertTrue(now <= last, "the fan widens on the way in at " + at);
            last = now;
        }
    }

    @Test
    @DisplayName("every lane begins at the beachhead and ends at the keep")
    void everyLaneSharesBothEnds() {
        float peak = LanePath.peakOf(FRACTIONS);
        for (int lane = 0; lane < 3; lane++) {
            LanePath.Frame frame = frame(lane);
            assertEquals(START_FORWARD, frame.forwardAt(0f, MARGIN),
                    "lane " + lane + " starts short");
            assertEquals(SHARED_LATERAL, frame.fanLateralAt(0f, peak, MARGIN),
                    "lane " + lane + " does not begin at the beachhead");
            assertEquals(END_FORWARD, frame.forwardAt(1f, MARGIN),
                    "lane " + lane + " stops short");
            assertEquals(SHARED_LATERAL, frame.fanLateralAt(1f, peak, MARGIN),
                    "lane " + lane + " does not arrive at the keep");
        }
    }

    @Test
    @DisplayName("a fanned lane stands widest at the middle rung and closes either side")
    void theFanIsWidestInTheMiddle() {
        LanePath.Frame frame = frame(0);
        List<int[]> cells = LanePath.fanned(frame, FRACTIONS, MARGIN).cells(WIDTH, HEIGHT);
        assertEquals(3, cells.size());
        assertEquals(SHARED_LATERAL + frame.laneOffset(), cells.get(1)[1],
                "at its widest a lane stands its own third off the shared axis");
        int outer = Math.abs(cells.get(0)[1] - SHARED_LATERAL);
        int widest = Math.abs(cells.get(1)[1] - SHARED_LATERAL);
        int inner = Math.abs(cells.get(2)[1] - SHARED_LATERAL);
        assertTrue(outer < widest, "the outermost rung stands " + outer
                + " off the axis against the middle rung's " + widest);
        assertTrue(inner < widest, "the innermost rung stands " + inner
                + " off the axis against the middle rung's " + widest);
        // The forward positions are the fractions of the shared axis, which is
        // now measured from the beachhead rather than from the attacker region.
        assertEquals(205, cells.get(0)[0], "the outermost rung, 0.30 of the way in");
        assertEquals(298, cells.get(1)[0]);
        assertEquals(391, cells.get(2)[0]);
    }

    @Test
    @DisplayName("three lanes stand apart in the middle and together at the ends")
    void threeLanesFanOut() {
        int[] middles = new int[3];
        for (int lane = 0; lane < 3; lane++) {
            middles[lane] = LanePath.fanned(frame(lane), FRACTIONS, MARGIN)
                    .cells(WIDTH, HEIGHT).get(1)[1];
        }
        assertTrue(middles[0] < middles[1] && middles[1] < middles[2],
                "the three lanes are laid out across the map: "
                        + middles[0] + "," + middles[1] + "," + middles[2]);
    }

    @Test
    @DisplayName("waypoints run in path order, the beachhead's end first")
    void waypointsRunInPathOrder() {
        List<int[]> cells = LanePath.fanned(frame(1), FRACTIONS, MARGIN)
                .cells(WIDTH, HEIGHT);
        for (int i = 1; i < cells.size(); i++) {
            assertTrue(cells.get(i)[0] > cells.get(i - 1)[0],
                    "waypoint " + i + " stands further toward the objective than " + (i - 1));
        }
    }

    @Test
    @DisplayName("a meandering path drifts sideways and never leaves its own fan line")
    void meanderingStaysNearTheFanLine() {
        LanePath.Frame frame = frame(0);
        float peak = LanePath.peakOf(FRACTIONS);
        int drift = (frame.laneEnd() - frame.laneStart() + 1) / 6;
        LanePath path = LanePath.meandering(frame, FRACTIONS, MARGIN, new Random(42));
        List<int[]> cells = path.cells(WIDTH, HEIGHT);
        boolean drifted = false;
        for (int i = 0; i < cells.size(); i++) {
            int fan = frame.fanLateralAt(FRACTIONS[i], peak, MARGIN);
            assertTrue(Math.abs(cells.get(i)[1] - fan) <= drift,
                    "waypoint at lateral " + cells.get(i)[1] + " wandered "
                            + Math.abs(cells.get(i)[1] - fan) + " off the fan line at "
                            + fan + ", which is more than " + drift);
            if (cells.get(i)[1] != fan) drifted = true;
        }
        assertTrue(drifted, "a meandering path that never left the fan line is a ruled one");
        // The forward positions are the same fractions of the same axis: a lane
        // bends across the map, it does not move its rungs up and down it.
        List<int[]> fanned = LanePath.fanned(frame, FRACTIONS, MARGIN).cells(WIDTH, HEIGHT);
        for (int i = 0; i < cells.size(); i++) {
            assertEquals(fanned.get(i)[0], cells.get(i)[0]);
        }
    }

    @Test
    @DisplayName("the same seed meanders the same way and a different one does not")
    void meanderIsSeeded() {
        LanePath.Frame frame = frame(0);
        LanePath first = LanePath.meandering(frame, FRACTIONS, MARGIN, new Random(7));
        LanePath again = LanePath.meandering(frame, FRACTIONS, MARGIN, new Random(7));
        LanePath other = LanePath.meandering(frame, FRACTIONS, MARGIN, new Random(8));
        assertEquals(first, again);
        assertNotEquals(first, other);
    }

    @Test
    @DisplayName("a waypoint on refused ground slides along the path until it is clear")
    void refusedWaypointSlidesAlongThePath() {
        LanePath.Frame frame = frame(1);
        LanePath path = LanePath.fanned(frame, FRACTIONS, MARGIN);
        int refusedX = path.cells(WIDTH, HEIGHT).get(1)[0];
        // A band of refused ground across the middle waypoint, and nothing else
        // on the map refused: a claim standing where a rung was told to stand.
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT,
                (x, y) -> Math.abs(x - refusedX) > 10, 120);

        assertEquals(1, fit.moved().size(), "one waypoint moved, and only one");
        LanePath.Move move = fit.moved().get(0);
        assertEquals(1, move.index());
        List<int[]> cells = fit.path().cells(WIDTH, HEIGHT);
        assertTrue(cells.get(1)[0] > refusedX + 10,
                "it slid clear of the refused band, toward the objective");
    }

    @Test
    @DisplayName("the last waypoint slides backward, because ahead of it is the objective")
    void theDeepestWaypointGivesWayBackward() {
        LanePath.Frame frame = frame(1);
        LanePath path = LanePath.fanned(frame, FRACTIONS, MARGIN);
        int refusedFrom = path.cells(WIDTH, HEIGHT).get(2)[0] - 10;
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT, (x, y) -> x < refusedFrom, 120);

        assertEquals(1, fit.moved().size());
        assertEquals(2, fit.moved().get(0).index());
        assertTrue(fit.path().cells(WIDTH, HEIGHT).get(2)[0] < refusedFrom,
                "the deepest waypoint gave way backward");
    }

    @Test
    @DisplayName("a waypoint with nowhere at all to go keeps its place")
    void nowhereToGoKeepsItsPlace() {
        LanePath path = LanePath.fanned(frame(1), FRACTIONS, MARGIN);
        LanePath.Fit fit = path.fitted(WIDTH, HEIGHT, (x, y) -> false, 120);
        assertEquals(List.of(), fit.moved(), "nothing moved, because nothing could");
        assertEquals(path, fit.path(), "and the path is the one that was stated");
    }

    @Test
    @DisplayName("ground that allows everything moves nothing")
    void openGroundMovesNothing() {
        LanePath path = LanePath.meandering(frame(0), FRACTIONS, MARGIN, new Random(3));
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
        LanePath.Frame frame = new LanePath.Frame(false, 56, 280, 280, 280,
                LaneGeometry.startInclusive(0, 3, WIDTH),
                LaneGeometry.endInclusive(0, 3, WIDTH), WIDTH, HEIGHT);
        List<int[]> cells = LanePath.fanned(frame, FRACTIONS, MARGIN).cells(WIDTH, HEIGHT);
        assertEquals(280 + frame.laneOffset(), cells.get(1)[0],
                "lateral is x on this axis, and the fan is widest in the middle");
        for (int i = 1; i < cells.size(); i++) {
            assertTrue(cells.get(i)[1] > cells.get(i - 1)[1], "forward is y on this axis");
        }
    }
}
