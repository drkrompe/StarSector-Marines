package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundHeightPassTest {

    @Test
    void shorelineFactorFallsOffAcrossThreeWaterCells() {
        CellTopology topology = new CellTopology(9, 9);
        for (int y = 1; y < 8; y++) {
            for (int x = 1; x < 8; x++) {
                topology.setGroundKind(x, y, CellTopology.GroundKind.WATER);
            }
        }

        assertEquals(1f, GroundHeightPass.waterShoreFactor(topology, 1, 4), 1e-6f);
        assertEquals(2f / 3f, GroundHeightPass.waterShoreFactor(topology, 2, 4), 1e-6f);
        assertEquals(1f / 3f, GroundHeightPass.waterShoreFactor(topology, 3, 4), 1e-6f);
        assertEquals(0f, GroundHeightPass.waterShoreFactor(topology, 4, 4), 1e-6f);
        assertEquals(0f, GroundHeightPass.waterShoreFactor(topology, 0, 4), 1e-6f);
    }

    @Test
    void wallTaggedWaterIsNotAWaterSurface() {
        CellTopology topology = new CellTopology(3, 3);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.WATER);
        topology.setTag(1, 1, CellTopology.Tag.WALL, true);

        assertEquals(0f, GroundHeightPass.waterShoreFactor(topology, 1, 1), 1e-6f);
    }
}
