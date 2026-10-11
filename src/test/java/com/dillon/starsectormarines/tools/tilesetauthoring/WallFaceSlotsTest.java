package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallFaceSlotsTest {
    @Test
    void authoringSlotsAddressTheSameFacesAsTheRuntime() {
        var layout = GridLayout.WALL_FACES_4X4;
        assertEquals(layout, GridLayout.fromJson(TilesetExport.jsonLayout(layout)));
        var slots = BlockSlots.of(layout);
        assertEquals(16, slots.size());
        for (int mask = 0; mask < 16; mask++) {
            String slot = slots.get(mask);
            assertTrue(BlockSlots.fits(layout, slot));
            assertFalse(BlockSlots.fits(GridLayout.WALL_3X3, slot));
            assertArrayEquals(layout.resolve(0, 0, (mask & 1) != 0, (mask & 4) != 0,
                    (mask & 2) != 0, (mask & 8) != 0), BlockSlots.offset(slot));
        }
        assertFalse(BlockSlots.fits(layout, "nw"));
        assertTrue(BlockSlots.describe("faces-5").contains("north and south"));
    }
}
