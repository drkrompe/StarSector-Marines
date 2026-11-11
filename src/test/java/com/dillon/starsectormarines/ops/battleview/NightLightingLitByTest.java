package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightLightingLitByTest {

    /** A street lamp lights the open ground and a freestanding wall, never a building's wall under its roof. */
    @Test
    void aStreetLampLeavesARoofedWallDark() {
        CellTopology ground = new CellTopology(4, 3);
        ground.setTag(1, 1, Tag.WALL, true);
        ground.setBuildingId(2, 1, 5);
        ground.setTag(0, 2, Tag.WALL, true);
        assertTrue(NightLightingRenderSystem.litBy(ground, 0, 0, 0), "open ground");
        assertTrue(NightLightingRenderSystem.litBy(ground, 0, 2, 0), "a garden wall in the open");
        assertFalse(NightLightingRenderSystem.litBy(ground, 1, 1, 0), "the wall under the roof beside it");
        assertFalse(NightLightingRenderSystem.litBy(ground, 2, 1, 0), "the room under the roof");
    }
}
