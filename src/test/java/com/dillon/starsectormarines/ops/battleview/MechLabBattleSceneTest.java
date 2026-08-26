package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabBattleSceneTest {

    @Test
    void embeddedCameraFramesOneGantryWithoutChangingBattleCellScale() {
        BattleCamera camera = MechLabBattleScene.cameraForSurface(900f, 520f, 1);

        assertEquals(MechLabBattleScene.GRID_WIDTH, camera.worldCellsW());
        assertEquals(MechLabBattleScene.GRID_HEIGHT, camera.worldCellsH());
        assertEquals(2.48832f, camera.zoom(), 1e-5f);
        assertEquals(57.42277f, camera.cellPxSize(), 1e-4f);
        assertEquals(450f,
                camera.cellToScreenX(MechLabBattleScene.mechWorldX(1)), 1e-4f);
        assertEquals(260f,
                camera.cellToScreenY(MechLabBattleScene.mechWorldY(1)), 1e-4f);
        assertEquals(camera.cellPxSize(),
                camera.cellToScreenX(2f) - camera.cellToScreenX(1f), 1e-4f);
        assertEquals(camera.cellPxSize(),
                camera.cellToScreenY(2f) - camera.cellToScreenY(1f), 1e-4f);

        BattleCamera next = MechLabBattleScene.cameraForSurface(900f, 520f, 2);
        assertEquals(camera.cellPxSize(), next.cellPxSize(), 1e-4f);
        assertEquals(8f, next.panCellX() - camera.panCellX(), 1e-4f);
    }

    @Test
    void wideCameraKeepsTheEntireLanceInsideTheWorkshopView() {
        BattleCamera camera = MechLabBattleScene.cameraForSurface(
                900f, 520f, MechLabCameraController.widePose(4));

        float first = camera.cellToScreenX(MechLabBattleScene.mechWorldX(0));
        float last = camera.cellToScreenX(MechLabBattleScene.mechWorldX(3));
        assertTrue(first > 0f);
        assertTrue(last < 900f);
        assertTrue(last - first > 600f);
        assertEquals(camera.cellPxSize(),
                camera.cellToScreenX(2f) - camera.cellToScreenX(1f), 1e-4f);
        assertEquals(camera.cellPxSize(),
                camera.cellToScreenY(2f) - camera.cellToScreenY(1f), 1e-4f);
    }

    @Test
    void garageIsARealBattleSceneWithFourGantryPadsAndLanceDolls() {
        List<MechVariant> variants = List.of(
                MechVariant.BULWARK, MechVariant.HOUND,
                MechVariant.SIROCCO, MechVariant.HOUND);
        try (BattleSimulation simulation = MechLabBattleScene.buildSimulation(variants)) {
            assertEquals(MechLabBattleScene.GRID_WIDTH, simulation.getGrid().getWidth());
            assertEquals(MechLabBattleScene.GRID_HEIGHT, simulation.getGrid().getHeight());
            assertEquals(8, simulation.getRoster().liveCount());
            assertEquals(128, simulation.getDoodads().size());

            int mechs = 0;
            int engineers = 0;
            for (int index = 0; index < simulation.getRoster().liveCount(); index++) {
                long id = simulation.getRoster().get(index);
                UnitType type = simulation.identity().type(id);
                if (type == UnitType.HEAVY_MECH) {
                    MechLabSceneLayout.Gantry gantry = MechLabSceneLayout.GANTRIES.get(mechs);
                    assertEquals(variants.get(mechs), simulation.identity().mechVariant(id));
                    assertEquals(gantry.cellX(), simulation.world().cellX(id));
                    assertEquals(gantry.cellY(), simulation.world().cellY(id));
                    mechs++;
                } else if (type == UnitType.ENGINEER) {
                    engineers++;
                    assertTrue(simulation.world().hasLayeredAppearance(id));
                }
            }
            assertEquals(4, mechs);
            assertEquals(4, engineers);
        }
    }

    @Test
    void authoredGarageKeepsSouthEntranceOpenAndUsesRealAtlasPadCells() {
        assertFalse(MechLabSceneLayout.wall(7, 0));
        assertTrue(MechLabSceneLayout.wall(0, 0));
        assertTrue(MechLabSceneLayout.wall(7, MechLabSceneLayout.HEIGHT - 1));
        assertEquals(120, MechLabSceneLayout.floorOverlays().size());
        long safetyStripeCells = MechLabSceneLayout.floorOverlays().stream()
                .filter(cell -> cell.tileColumn() == 1 && cell.tileRow() == 3)
                .count();
        long gratedCells = MechLabSceneLayout.floorOverlays().size() - safetyStripeCells;
        assertEquals(72, safetyStripeCells);
        assertEquals(48, gratedCells);
    }
}
