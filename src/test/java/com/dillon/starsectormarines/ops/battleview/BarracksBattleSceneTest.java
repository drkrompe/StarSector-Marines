package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarracksBattleSceneTest {

    @Test
    void quartersUseOneBoundedBattleGridAndThreeFourPersonBayAreas() {
        assertEquals(12, BarracksSceneLayout.MARINE_TASKS.size());
        assertTrue(BarracksSceneLayout.PROPS.size() >= 30);
        assertTrue(BarracksSceneLayout.wall(23, 4));
        assertFalse(BarracksSceneLayout.wall(23, 7));
        BattleCamera camera = BarracksBattleScene.cameraForSurface(1280f, 650f);
        assertTrue(camera.cellPxSize() > 0f);

        try (BattleSimulation simulation = BarracksBattleScene.buildSimulation()) {
            assertEquals(BarracksBattleScene.GRID_WIDTH, simulation.getGrid().getWidth());
            assertEquals(BarracksBattleScene.GRID_HEIGHT, simulation.getGrid().getHeight());
        }
    }

    @Test
    void rangeRotationFiresIssuedPrimaryThroughTheRealShotService() {
        MarineSoldier soldier = new MarineSoldier("marine-1", "Marine 001", null);
        try (BattleSimulation simulation = BarracksBattleScene.buildSimulation(
                List.of(soldier))) {
            simulation.advance(60f);

            assertFalse(simulation.getShotsThisFrame().isEmpty());
            assertEquals(soldier.primaryDef(),
                    simulation.getShotsThisFrame().get(0).primaryWeaponDef);
        }
    }
}
