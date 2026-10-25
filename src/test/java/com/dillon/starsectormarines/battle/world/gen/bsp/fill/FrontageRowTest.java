package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontageRowTest {

    private static final List<FrontageRow.Character> CHARACTERS = List.of(FrontageRow.SHOPS, FrontageRow.TERRACE,
            FrontageRow.VILLAS, FrontageRow.SHOPFRONTS);

    @Test
    void aRowCoversTheWholeFrontageWithNeighboursSharingTheirPartyWall() {
        for (FrontageRow.Character ch : CHARACTERS) {
            for (int length = 2 * ch.minWidth() - 1; length <= 40; length++) {
                for (long seed = 0; seed < 8; seed++) {
                    int[][] spans = FrontageRow.spans(length, ch.minWidth(), ch.maxWidth(), new Random(seed));
                    assertNotNull(spans, ch + " found no row on " + length);
                    assertTrue(spans.length >= 2);
                    assertEquals(0, spans[0][0]);
                    assertEquals(length - 1, spans[spans.length - 1][1], "row stops short of the corner on " + length);
                    for (int i = 0; i < spans.length; i++) {
                        int width = spans[i][1] - spans[i][0] + 1;
                        assertTrue(width >= ch.minWidth() && width <= ch.maxWidth(),
                                "a " + width + "-cell plot in " + ch + " on " + length);
                        if (i > 0) assertEquals(spans[i - 1][1], spans[i][0], "a gap or overlap between plots");
                    }
                }
            }
        }
    }

    @Test
    void aFrontageTooShortForTwoPlotsIsNoRow() {
        // One plot is an ordinary building: the lot keeps the filler it had.
        assertNull(FrontageRow.spans(10, 6, 9, new Random(1)));
        assertNotNull(FrontageRow.spans(11, 6, 9, new Random(1)));
    }

    @Test
    void theSameSeedCutsTheSameRow() {
        int[][] a = FrontageRow.spans(37, 6, 9, new Random(42)), b = FrontageRow.spans(37, 6, 9, new Random(42));
        for (int i = 0; i < a.length; i++) assertEquals(a[i][1], b[i][1]);
    }

    @Test
    void aRowStandsBehindItsFrontGardenAndLeavesAYardWhereTheLotIsDeep() {
        FrontageRow.Character ch = FrontageRow.TERRACE;
        assertEquals(-1, ch.rowDepth(ch.minDepth() - 1), "too shallow for a house");
        assertEquals(0, ch.setbackOn(ch.minDepth()), "a lot only a house deep keeps no front garden");
        assertEquals(ch.minDepth(), ch.rowDepth(ch.minDepth()), "a shallow lot is all house");
        assertEquals(ch.setback(), ch.setbackOn(ch.setback() + ch.minDepth()));
        for (int depth = ch.setback() + ch.maxDepth() + 1; depth <= 30; depth++) {
            int row = ch.rowDepth(depth);
            assertTrue(row >= ch.minDepth() && row <= ch.maxDepth());
            assertTrue(depth - ch.setback() - row >= 1, "no yard behind a row on a " + depth + "-deep lot");
        }
        List<FrontageRow.Plot> plots = FrontageRow.plan(20, 14, ch, false, new Random(3));
        assertFalse(plots.isEmpty());
        for (FrontageRow.Plot p : plots) {
            assertFalse(p.back());
            assertEquals(ch.setback(), p.depthLo());
            assertTrue(p.depthHi() < 14 - ch.yard() + 1 || p.depthHi() - p.depthLo() + 1 == ch.minDepth());
        }
    }

    @Test
    void aDeepLotWithAStreetBehindTakesTwoRowsBackToBackWithAnAlleyBetween() {
        FrontageRow.Character ch = FrontageRow.SHOPFRONTS;
        int depth = 2 * (ch.setback() + ch.minDepth()) + FrontageRow.ALLEY;
        List<FrontageRow.Plot> plots = FrontageRow.plan(30, depth, ch, true, new Random(5));
        int frontHi = -1, backLo = Integer.MAX_VALUE;
        boolean anyBack = false;
        for (FrontageRow.Plot p : plots) {
            if (p.back()) {
                anyBack = true;
                backLo = Math.min(backLo, p.depthLo());
                assertEquals(depth - 1 - ch.setback(), p.depthHi(), "rear row not on its own street");
            } else {
                frontHi = Math.max(frontHi, p.depthHi());
            }
        }
        assertTrue(anyBack);
        assertTrue(backLo - frontHi - 1 >= FrontageRow.ALLEY, "alley narrower than " + FrontageRow.ALLEY);
        // Without a street behind, the rear row would face a neighbour's wall: one row and a yard.
        for (FrontageRow.Plot p : FrontageRow.plan(30, depth, ch, false, new Random(5))) assertFalse(p.back());
    }

    @Test
    void aCharacterMustLeaveEveryPlotAHollowShellAndCoverEveryFrontage() {
        assertThrows(IllegalArgumentException.class, () -> new FrontageRow.Character(3, 6, 0, 8, 10, 2));
        assertThrows(IllegalArgumentException.class, () -> new FrontageRow.Character(6, 5, 0, 8, 10, 2));
        // 9..12 cannot cover a 24-cell frontage: two plots reach 23 at most, three need 25.
        assertThrows(IllegalArgumentException.class, () -> new FrontageRow.Character(9, 12, 0, 8, 10, 2));
    }
}
