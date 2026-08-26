package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        List<MarineSoldier> soldiers = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new MarineSoldier(
                        "marine-" + index, "Marine " + index, null))
                .toList();
        try (BattleSimulation simulation = BarracksBattleScene.buildSimulation(
                soldiers)) {
            boolean emitted = false;
            boolean visible = false;
            ShotEvent firstShot = null;
            for (int frame = 0; frame < 60 * 10; frame++) {
                simulation.advance(1f / 60f);
                emitted |= !simulation.getShotsThisFrame().isEmpty();
                visible |= !simulation.getActiveShots().isEmpty();
                if (firstShot == null && !simulation.getShotsThisFrame().isEmpty()) {
                    firstShot = simulation.getShotsThisFrame().get(0);
                }
            }

            assertTrue(emitted, "range rotation should emit a shot on a normal display frame");
            assertTrue(visible, "range rotation should leave a shot visible for rendering");
            assertNotNull(firstShot);
            assertEquals(soldiers.get(0).primaryDef(), firstShot.primaryWeaponDef);
        }
    }
}
