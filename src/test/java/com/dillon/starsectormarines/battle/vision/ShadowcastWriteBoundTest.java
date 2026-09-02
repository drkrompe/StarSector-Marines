package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cast writes into a caller-sized buffer and has no idea how big it is, so
 * {@link Shadowcast#maxCells} is the only thing between an observer and an
 * {@link ArrayIndexOutOfBoundsException} thousands of ticks into a battle.
 *
 * <p>Two things have to hold for that bound to mean anything: an unobstructed
 * cast has to fit it, and an obstructed one must not write the same cell over
 * and over. The eight octants scan their boundary rays inclusively, so the four
 * axes and the four diagonals are legitimately written twice; anything written
 * three times is a wedge being re-scanned, which is both duplicate writes and
 * vision through the wall that caused them.
 */
final class ShadowcastWriteBoundTest {

    private static final int RANGE = 60;

    @Test
    void aCastInOpenGroundFitsTheBufferItIsPromised() {
        int span = 2 * RANGE + 3;
        NavigationGrid grid = openGrid(span);

        int[] out = new int[Shadowcast.maxCells(RANGE)];
        int count = Shadowcast.castFrom(grid, span / 2, span / 2, RANGE, 0f, out, 0);

        assertTrue(count <= out.length,
                "an open-ground cast wrote " + count + " cells into a buffer of "
                        + out.length);
        assertTrue(count > (int) (Math.PI * RANGE * RANGE),
                "an open-ground cast should see the whole disc and then some, "
                        + "since the octants share their boundary rays; wrote " + count);
    }

    /**
     * The geometry that used to blow the buffer up: a scattering of walls to
     * recurse around, and a disc wider than the map so most rows end on cells
     * nobody can ask about. A cell reached from more than the two octants that
     * share its ray means an octant re-scanned a wedge it had already covered.
     * On this field one cell was written eighteen times before the fix, and on
     * a generated Conquest map two hundred and fifty-seven.
     */
    @Test
    void anObstructedCastWritesNoCellThreeTimes() {
        int span = 130;
        NavigationGrid grid = openGrid(span);
        Random walls = new Random(11);
        for (int y = 0; y < span; y++) {
            for (int x = 0; x < span; x++) {
                if (walls.nextInt(100) < 5) grid.setWalkable(x, y, false);
            }
        }

        int[] out = new int[Shadowcast.maxCells(RANGE)];
        Random from = new Random(3);
        for (int i = 0; i < 200; i++) {
            int sx = from.nextInt(span);
            int sy = from.nextInt(span);
            int count = Shadowcast.castFrom(grid, sx, sy, RANGE, 0f, out, 0);

            Map<Integer, Integer> writes = new HashMap<>();
            int worst = 0;
            for (int j = 0; j < count; j++) {
                worst = Math.max(worst, writes.merge(out[j], 1, Integer::sum));
            }
            assertTrue(worst <= 2,
                    "casting from " + sx + "," + sy + " wrote one cell " + worst
                            + " times; only the four axes and four diagonals are "
                            + "shared between octants");
        }
    }

    private static NavigationGrid openGrid(int span) {
        NavigationGrid grid = new NavigationGrid(span, span);
        for (int y = 0; y < span; y++) {
            for (int x = 0; x < span; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
