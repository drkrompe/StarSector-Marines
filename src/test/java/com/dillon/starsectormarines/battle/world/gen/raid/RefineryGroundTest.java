package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** How {@link RefineryGround} lays a frame over the disc its fixture stands on. */
class RefineryGroundTest {

    /** A column's frame is 2.5 discs across with the disc's middle 0.4 along: drawn 20 wide, starting 4 before it. */
    @Test
    void aFrameLiesWithTheDiscsMiddleWhereTheArtPutsIt() {
        assertArrayEquals(new int[]{20, 6}, RefineryGround.over(8, 2.5f, .4f, 10));
        // The disc's middle, 10 + 4, falls 0.4 of the way along the drawn 20: 6 + 8.
        assertArrayEquals(new int[]{8, 10}, RefineryGround.over(8, 1f, .5f, 10));
    }

    /** A frame that would not start or span a whole number of cells is not drawn, rather than drawn askew. */
    @Test
    void aFrameOffTheCellGridIsRefused() {
        assertNull(RefineryGround.over(5, 2.5f, .4f, 0));
        assertNull(RefineryGround.over(6, 2f, .375f, 0));
    }
}
