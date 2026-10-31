package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sandbag lines on the approaches to the town compound's ways in ({@link CordonTownSandbags}). */
class CordonTownSandbagsTest {

    /** A compound whose wall's line runs x 20..40 by y 20..40; a postern three wide in its top side at (30, 40). */
    private static final int[] WALL = {20, 20, 40, 40};
    private static final int[] POSTERN = {30, 40, 3};

    @Test
    void aLineStandsEitherSideOfAPosternsApproachJustPastTheRingFacingOutWithThePassGapOpen() {
        List<CordonTownSandbags.Line> lines = CordonTownSandbags.lines((x, y) -> true, WALL, List.of(POSTERN));
        assertEquals(2, CordonTownSandbags.lineCount(lines), "one either side of the way in");
        for (CordonTownSandbags.Line line : lines) {
            boolean main = line.cells().size() > CordonTownSandbags.RETURN;
            if (main) assertArrayEquals(new int[]{0, 1}, line.facing(), "facing out down the approach");
            for (int[] c : line.cells()) {
                assertTrue(c[1] >= 40 + CordonTownSandbags.FROM - CordonTownSandbags.RETURN, "past the ring");
                assertTrue(Math.abs(c[0] - 30) > 1 + CordonTownSandbags.PASS, "the postern's lane and a cell beside it open");
            }
            if (main) {
                assertEquals(40 + CordonTownSandbags.FROM, line.cells().get(0)[1], "on the nearest row past the ring");
                assertTrue(line.cells().size() >= CordonTownSandbags.SHORTEST
                        && line.cells().size() <= CordonTownSandbags.LONGEST);
            }
        }
        assertEquals(4, lines.size(), "each with its return, an L");
    }

    @Test
    void theLinesKeepOffRoadAndAWideGatesRoadIsTheirPassGap() {
        // The convoys' road comes in five wide on the axis of a nine-wide gate in the left side at (20, 30).
        int[] yard = {20, 30, 9};
        CordonCorridor.CellTest free = (x, y) -> Math.abs(y - 30) > 3;
        List<CordonTownSandbags.Line> lines = CordonTownSandbags.lines(free, WALL, List.of(yard));
        assertEquals(2, CordonTownSandbags.lineCount(lines));
        for (CordonTownSandbags.Line line : lines) {
            for (int[] c : line.cells()) {
                assertTrue(free.test(c[0], c[1]), "never on the road nor beside it");
                assertTrue(Math.abs(c[1] - 30) > 4 + CordonTownSandbags.PASS, "the gate's whole width left open");
                assertTrue(c[0] < 20, "outside the wall");
            }
        }
    }

    @Test
    void aStreetWiderThanThePosternIsSkippedAndWhereTheTownStandsCloseTheLinesComeInsideTheRing() {
        // The postern's street runs five wide up its axis; houses stand from seven cells out.
        CordonCorridor.CellTest free = (x, y) -> Math.abs(x - 30) > 3 && y < 40 + CordonTownSandbags.FROM;
        List<CordonTownSandbags.Line> lines = CordonTownSandbags.lines(free, WALL, List.of(POSTERN));
        assertEquals(2, CordonTownSandbags.lineCount(lines));
        for (CordonTownSandbags.Line line : lines) {
            if (line.cells().size() <= CordonTownSandbags.RETURN) continue;
            int[] first = line.cells().get(0);
            assertEquals(40 + CordonTownSandbags.FROM - 1, first[1], "the row just inside the ring");
            assertEquals(4, Math.abs(first[0] - 30), "starting at the street's edge");
        }
    }

    @Test
    void whereNothingFitsNoLineIsLaidAndNoMoreThanTheCapOverAll() {
        assertTrue(CordonTownSandbags.lines((x, y) -> false, WALL, List.of(POSTERN)).isEmpty());
        List<int[]> gates = List.of(new int[]{30, 40, 3}, new int[]{30, 20, 3}, new int[]{40, 30, 3},
                new int[]{20, 25, 3}, new int[]{20, 35, 3});
        assertTrue(CordonTownSandbags.lineCount(CordonTownSandbags.lines((x, y) -> true, WALL, gates))
                <= CordonTownSandbags.MAX_LINES);
        assertFalse(CordonTownSandbags.lines((x, y) -> true, WALL, List.of(new int[]{20, 20, 3})).iterator().hasNext(),
                "a corner gate has no one way out");
    }
}
