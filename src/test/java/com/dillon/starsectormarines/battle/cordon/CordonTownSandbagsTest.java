package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sandbag lines across the town compound's postern approaches ({@link CordonTownSandbags}). */
class CordonTownSandbagsTest {

    /** A compound whose wall's line runs x 20..40 by y 20..40, its cells the wall; a postern three wide at (30, 40). */
    private static final int[] WALL = {20, 20, 40, 40};
    private static final int[] POSTERN = {30, 40, 3};

    /** Ground of 80 by 80 cells: the wall's line solid but for its gates, the given cells built on. */
    private static CordonTownSandbags.Site site(Set<Long> built, List<int[]> gates, CordonCorridor.CellTest convoy) {
        return new CordonTownSandbags.Site() {
            @Override public boolean open(int x, int y) { return clear(x, y); }
            @Override public boolean clear(int x, int y) {
                if (x < 0 || y < 0 || x >= 80 || y >= 80 || built.contains(key(x, y))) return false;
                boolean onWall = (x == WALL[0] || x == WALL[2]) && y >= WALL[1] && y <= WALL[3]
                        || (y == WALL[1] || y == WALL[3]) && x >= WALL[0] && x <= WALL[2];
                if (!onWall) return true;
                for (int[] g : gates) {
                    boolean across = g[1] == WALL[1] || g[1] == WALL[3];
                    if (across ? y == g[1] && Math.abs(x - g[0]) <= g[2] / 2 : x == g[0] && Math.abs(y - g[1]) <= g[2] / 2) return true;
                }
                return false;
            }
            @Override public boolean convoy(int x, int y) { return convoy.test(x, y); }
        };
    }

    private static Set<Long> box(int x0, int y0, int x1, int y1) {
        Set<Long> out = new HashSet<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) out.add(key(x, y));
        return out;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    private static List<CordonTownSandbags.Line> lines(Set<Long> built, CordonCorridor.CellTest convoy) {
        return CordonTownSandbags.lines(site(built, List.of(POSTERN), convoy), WALL, List.of(POSTERN));
    }

    @Test
    void onOpenGroundALineCrossesThePosternsApproachFacingOutWithAGapAndAnL() {
        List<CordonTownSandbags.Line> lines = lines(Set.of(), (x, y) -> false);
        assertEquals(1, CordonTownSandbags.lineCount(lines));
        CordonTownSandbags.Line line = lines.get(0);
        assertArrayEquals(new int[]{0, 1}, line.facing());
        assertEquals(CordonTownSandbags.LONGEST, line.cells().size());
        int y = line.cells().get(0)[1];
        assertTrue(y >= 40 + CordonTownSandbags.NEAR && y <= 40 + CordonTownSandbags.FAR);
        int lo = line.cells().get(0)[0], hi = line.cells().get(line.cells().size() - 1)[0];
        assertTrue(lo - CordonTownSandbags.GAP <= 30 && hi + CordonTownSandbags.GAP >= 30, "line and gap span the axis");
        assertEquals(2, lines.size(), "with its return");
    }

    @Test
    void aBuildingAcrossTheApproachLeavesNoFieldOfFireSoNoLine() {
        // A row of houses across the whole approach eight cells out, no way round it near the postern.
        assertTrue(lines(box(0, 48, 79, 49), (x, y) -> false).isEmpty());
        // One house ahead of part of the approach: every bag laid still has its whole field clear.
        Set<Long> house = box(26, 49, 29, 52);
        List<CordonTownSandbags.Line> lines = lines(house, (x, y) -> false);
        assertFalse(lines.isEmpty());
        for (CordonTownSandbags.Line line : lines) {
            for (int[] c : line.cells()) {
                for (int k = 1; k <= CordonTownSandbags.FIELD; k++) {
                    assertFalse(house.contains(key(c[0] + line.facing()[0] * k, c[1] + line.facing()[1] * k)));
                }
            }
        }
    }

    @Test
    void noBagStandsWithinTwoCellsOfABuildingOrTheWall() {
        Set<Long> house = box(34, 41, 36, 46);
        CordonTownSandbags.Site ground = site(house, List.of(POSTERN), (x, y) -> false);
        assertFalse(CordonTownSandbags.fits(ground, 32, 46, new int[]{0, 1}), "two cells from the house");
        assertFalse(CordonTownSandbags.fits(ground, 25, 42, new int[]{0, 1}), "two cells from the wall");
        assertTrue(CordonTownSandbags.fits(ground, 25, 43, new int[]{0, 1}));
        for (CordonTownSandbags.Line line : lines(house, (x, y) -> false)) {
            for (int[] c : line.cells()) {
                for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) {
                    assertFalse(house.contains(key(c[0] + dx, c[1] + dy)));
                }
                assertTrue(c[1] >= 43, "off the wall");
            }
        }
    }

    @Test
    void everyBagKeepsOffTheConvoysRouteAndTheYardGatesSwing() {
        List<int[]> route = new ArrayList<>();
        for (int y = 40; y < 80; y++) route.add(new int[]{36, y});
        CordonCorridor.CellTest kept = CordonTownSandbags.convoyKept(route, List.of(), WALL);
        for (CordonTownSandbags.Line line : lines(Set.of(), kept)) {
            for (int[] c : line.cells()) assertTrue(Math.abs(c[0] - 36) > CordonTownSandbags.CONVOY_CLEAR);
        }
        // A yard gate nine wide in the left side at (20, 30): its swing runs eight out, and four past its half-width.
        CordonCorridor.CellTest swing = CordonTownSandbags.convoyKept(null, List.of(new int[]{20, 30, 9}), WALL);
        assertTrue(swing.test(12, 30));
        assertFalse(swing.test(11, 30));
        assertTrue(swing.test(15, 38));
        assertFalse(swing.test(15, 39));
        assertFalse(CordonTownSandbags.convoyKept(null, List.of(POSTERN), WALL).test(30, 44), "a postern has no swing");
    }

    @Test
    void theYardsGatesGetNoLine() {
        int[] yard = {20, 30, 9};
        assertTrue(CordonTownSandbags.lines(site(Set.of(), List.of(yard), (x, y) -> false), WALL, List.of(yard)).isEmpty());
    }
}
