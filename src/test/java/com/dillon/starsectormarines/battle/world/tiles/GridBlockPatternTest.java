package com.dillon.starsectormarines.battle.world.tiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A pool laid as a pattern draws its row-major cells by position, repeating across the grid, with the picture's
 * top row at the top: grid y runs up the screen.
 */
class GridBlockPatternTest {

    private static final int[][] CELLS = {{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {5, 0}};

    @Test
    void cellsAreLaidByPositionAndRepeat() {
        GridBlockDef pattern = GridBlockDef.variantPool("deck", "sheet", 64, CELLS).asPattern(3);
        // Two rows of three: the top row {0,1,2} sits at y = 1, the bottom row {3,4,5} at y = 0.
        assertArrayEquals(new int[]{3, 0}, pattern.resolve(false, false, false, false, 0, 0));
        assertArrayEquals(new int[]{5, 0}, pattern.resolve(false, false, false, false, 2, 0));
        assertArrayEquals(new int[]{1, 0}, pattern.resolve(false, false, false, false, 1, 1));
        assertArrayEquals(new int[]{1, 0}, pattern.resolve(false, false, false, false, 7, 3));
        assertArrayEquals(new int[]{2, 0}, pattern.resolve(false, false, false, false, -1, -1));
    }

    @Test
    void patternWidthMustDivideThePool() {
        GridBlockDef pool = GridBlockDef.variantPool("deck", "sheet", 64, CELLS);
        assertThrows(IllegalArgumentException.class, () -> pool.asPattern(4));
    }
}
