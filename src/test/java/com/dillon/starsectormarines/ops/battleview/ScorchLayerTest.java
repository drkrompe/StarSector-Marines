package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireFuel;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScorchLayerTest {

    @Test
    void grassUnderAFenceCharsOnceItHasBurnt() {
        NavigationGrid grid = new NavigationGrid(2, 1);
        CellTopology topology = new CellTopology(2, 1);
        grid.setWalkableFloor(0, 0);
        grid.setWalkable(1, 0, false);
        topology.setGroundKind(0, 0, GroundKind.GRASS);
        topology.setGroundKind(1, 0, GroundKind.GRASS);
        assertEquals(ScorchShading.SOIL, ScorchLayer.ground(grid, topology, 1, 1, 0, FireFuel.GRASS.bit),
                "burnt grass under a fence chars like the grass beside it");
        assertEquals(ScorchShading.NONE, ScorchLayer.ground(grid, topology, 1, 1, 0, 0),
                "a fixture cell that never burnt takes no singe");
        assertEquals(ScorchShading.SOIL, ScorchLayer.ground(grid, topology, 0, 0, 0, 0));
    }
}
