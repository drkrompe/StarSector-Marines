package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CordonGapLawTest {

    @Test void twoGunsMeetInTheMiddleUpToTwiceTheirReachAndTheirSpreadAndSplash() {
        assertEquals(2 * CordonGapLaw.REACH + 2 * (CordonGapLaw.HIT_SPREAD + CordonGapLaw.SPLASH), CordonGapLaw.LIMIT,
                1e-9);
        int edge = (int) Math.floor(CordonGapLaw.LIMIT);
        assertTrue(CordonGapLaw.measure(1, 2, List.of(new int[]{0, 0}), List.of(new int[]{edge, 0}), List.of()).holds());
        assertFalse(CordonGapLaw.measure(1, 2, List.of(new int[]{0, 0}), List.of(new int[]{edge + 1, 0}), List.of())
                .holds());
    }

    @Test void picketNestsCloseAGapTheTowersAloneLeave() {
        List<int[]> west = List.of(new int[]{10, 50}, new int[]{30, 50});
        List<int[]> east = List.of(new int[]{150, 50}, new int[]{170, 50});
        CordonGapLaw.Gap bare = CordonGapLaw.measure(1, 2, west, east, List.of());
        assertFalse(bare.holds());
        assertEquals(120, bare.towerToTower(), 1e-9);

        List<int[]> nests = List.of(new int[]{70, 52}, new int[]{110, 48});
        CordonGapLaw.Gap chained = CordonGapLaw.measure(1, 2, west, east, nests);
        assertTrue(chained.holds(), "towers and nests 40 cells apart cover the line: " + chained);
        assertEquals(120, chained.towerToTower(), 1e-9, "the direct reading is unchanged");
        assertEquals(Math.hypot(40, 4), chained.widestStep(), 1e-9);
    }

    @Test void theWidestStepIsTheBestChainsNotTheWorstGun() {
        // A nest off the line is never the way across; the step it would leave is not the gap's.
        List<int[]> nests = List.of(new int[]{40, 0}, new int[]{40, 200});
        assertEquals(40, CordonGapLaw.widestStep(List.of(new int[]{0, 0}), List.of(new int[]{80, 0}), nests), 1e-9);
    }

    @Test void aRedoubtWithoutTowersLeavesItsGapsOpen() {
        CordonGapLaw.Gap gap = CordonGapLaw.measure(2, 3, List.of(), List.of(new int[]{0, 0}), List.of());
        assertFalse(gap.holds());
        assertEquals(gap, CordonGapLaw.broken(List.of(
                CordonGapLaw.measure(1, 2, List.of(new int[]{0, 0}), List.of(new int[]{10, 0}), List.of()), gap)));
    }
}
