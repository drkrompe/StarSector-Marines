package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarracksBattleSceneTest {

    @Test
    void quartersUseOneBoundedBattleGridAndThreeFourPersonBayAreas() {
        assertEquals(12, BarracksSceneLayout.MARINES.size());
        BattleCamera camera = BarracksBattleScene.cameraForSurface(1280f, 650f);
        assertTrue(camera.cellPxSize() > 0f);

        try (BattleSimulation simulation = BarracksBattleScene.buildSimulation()) {
            assertEquals(BarracksBattleScene.GRID_WIDTH, simulation.getGrid().getWidth());
            assertEquals(BarracksBattleScene.GRID_HEIGHT, simulation.getGrid().getHeight());
        }
    }
}
