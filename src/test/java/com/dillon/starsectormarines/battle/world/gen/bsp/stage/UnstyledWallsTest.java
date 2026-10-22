package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnstyledWallsTest {

    @Test
    void aWallNoStageStyledTakesConcreteAndAStyledOneKeepsItsMaterial() {
        CellTopology topology = new CellTopology(4, 1);
        topology.setWall(0, 0, true);
        topology.setWall(1, 0, true);
        topology.setSurface(1, 0, topology.surfaceIndex("colony.masonry-wall"));
        topology.setWall(2, 0, true);
        topology.setSurface(2, 0, topology.surfaceIndex(FinalizeStage.RETIRED_WALL));

        FinalizeStage.dressUnstyledWalls(topology);

        assertEquals(InstallationWalls.BUILDING, topology.getSurfaceId(0, 0));
        assertEquals("colony.masonry-wall", topology.getSurfaceId(1, 0));
        assertEquals(InstallationWalls.BUILDING, topology.getSurfaceId(2, 0));
        // Floor is left alone.
        assertNull(topology.getSurfaceId(3, 0));
    }
}
