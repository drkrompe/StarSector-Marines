package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tear itself: what it covers, what shape it is, and that it is the same tear twice. */
class HullBreakupTest {

    private static final int CELLS = HullBreakup.GRID * HullBreakup.GRID;

    /**
     * Every cell of the hull is in exactly one piece.
     *
     * <p>The load-bearing property. A cell claimed twice draws the same metal
     * in two places, and one claimed by nobody is a hole in a hull that is
     * supposed to be merely broken.
     */
    @Test
    void thePiecesCoverTheHullExactlyOnce() {
        for (long seed : new long[]{0L, 1L, 7L, 4242L, -19L}) {
            Set<Integer> seen = new HashSet<>();
            for (HullBreakup.Piece piece : HullBreakup.of(seed).pieces()) {
                for (HullBreakup.Run run : piece.runs()) {
                    for (int i = 0; i < run.colCount(); i++) {
                        int cell = run.row() * HullBreakup.GRID + run.firstCol() + i;
                        assertTrue(seen.add(cell),
                                "cell claimed twice at seed " + seed + ": " + cell);
                    }
                }
            }
            assertEquals(CELLS, seen.size(), "cells left out at seed " + seed);
        }
    }

    /** Three pieces, and none of them is a rounding error. */
    @Test
    void everyPieceIsWorthDrawing() {
        for (long seed : new long[]{0L, 1L, 7L, 4242L, -19L}) {
            var pieces = HullBreakup.of(seed).pieces();
            assertEquals(3, pieces.size());
            for (HullBreakup.Piece piece : pieces) {
                int cells = piece.runs().stream().mapToInt(HullBreakup.Run::colCount).sum();
                assertTrue(cells > CELLS / 20,
                        "a piece of " + cells + " cells at seed " + seed + " is debris, not a section");
            }
        }
    }

    /**
     * The cut is a V, not a line.
     *
     * <p>Asked of the result rather than of the formula: the nose piece has to
     * reach further aft down the middle of the hull than it does at either
     * edge. That is what makes it read as a fuselage that burst rather than a
     * sprite that was sliced, and it is the one thing the random walk must not
     * be allowed to wander away.
     */
    @Test
    void theNoseComesOffAsAV() {
        for (long seed : new long[]{0L, 1L, 7L, 4242L, -19L}) {
            HullBreakup.Piece nose = HullBreakup.of(seed).pieces().get(HullBreakup.NOSE);
            int[] depth = new int[HullBreakup.GRID];
            for (HullBreakup.Run run : nose.runs()) {
                for (int i = 0; i < run.colCount(); i++) {
                    int col = run.firstCol() + i;
                    depth[col] = Math.max(depth[col], run.row() + 1);
                }
            }
            int middle = Math.max(depth[HullBreakup.GRID / 2 - 1], depth[HullBreakup.GRID / 2]);
            int edge = Math.max(depth[0], depth[HullBreakup.GRID - 1]);
            assertTrue(middle > edge + 2,
                    "seed " + seed + ": centre reaches " + middle + " and the edge " + edge
                            + " — that is a line, not a V");
        }
    }

    /**
     * The tear is ragged, and ragged differently every time.
     *
     * <p>A hull that always broke on the same two lines would be an authored
     * decal with extra steps.
     */
    @Test
    void everyHullTearsDifferently() {
        assertNotEquals(signature(HullBreakup.of(1L)), signature(HullBreakup.of(2L)));
        assertNotEquals(signature(HullBreakup.of(2L)), signature(HullBreakup.of(3L)));
    }

    /** And the same hull tears the same way every time it is asked. */
    @Test
    void oneHullTearsTheSameWayTwice() {
        assertEquals(signature(HullBreakup.of(88L)), signature(HullBreakup.of(88L)));
    }

    /** The spine tear wanders off the centreline rather than running straight down it. */
    @Test
    void theSpineTearWanders() {
        HullBreakup.Piece port = HullBreakup.of(31L).pieces().get(HullBreakup.PORT_REAR);
        Set<Integer> widths = new HashSet<>();
        for (HullBreakup.Run run : port.runs()) widths.add(run.colCount());
        assertFalse(widths.size() < 2, "every row the same width is a straight cut");
    }

    private static String signature(HullBreakup breakup) {
        StringBuilder out = new StringBuilder();
        for (HullBreakup.Piece piece : breakup.pieces()) {
            for (HullBreakup.Run run : piece.runs()) {
                out.append(run.row()).append(':').append(run.firstCol())
                        .append('+').append(run.colCount()).append(' ');
            }
            out.append('|');
        }
        return out.toString();
    }
}
