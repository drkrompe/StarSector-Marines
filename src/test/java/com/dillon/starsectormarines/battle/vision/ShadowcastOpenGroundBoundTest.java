package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cast writes into a caller-sized buffer and has no idea how big it is, so
 * {@link Shadowcast#maxCells} is the only thing standing between an observer in
 * open ground and an {@link ArrayIndexOutOfBoundsException}. Open ground is the
 * worst case — every cell of the disc is visible and nothing is elided — and it
 * is the case a walled map never reaches.
 */
final class ShadowcastOpenGroundBoundTest {

    @Test
    void aCastInOpenGroundFitsTheBufferItIsPromised() {
        int range = 60;
        int span = 2 * range + 3;
        NavigationGrid grid = new NavigationGrid(span, span);
        for (int y = 0; y < span; y++) {
            for (int x = 0; x < span; x++) grid.setWalkableFloor(x, y);
        }

        int[] out = new int[Shadowcast.maxCells(range)];
        int count = Shadowcast.castFrom(grid, span / 2, span / 2, range, 0f, out, 0);

        assertTrue(count <= out.length,
                "an open-ground cast wrote " + count + " cells into a buffer of "
                        + out.length);
        assertTrue(count > (int) (Math.PI * range * range),
                "an open-ground cast should see the whole disc and then some, "
                        + "since the octants share their boundary rays; wrote " + count);
    }
}
