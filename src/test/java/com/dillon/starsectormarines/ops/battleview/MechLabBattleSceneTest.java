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
        float expectedZoom = (float) Math.pow(1.2,
                MechLabCameraController.FITTING_ZOOM_NOTCHES);
        float expectedCell = 900f / MechLabBattleScene.GRID_WIDTH * expectedZoom;

        assertEquals(MechLabBattleScene.GRID_WIDTH, camera.worldCellsW());
        assertEquals(MechLabBattleScene.GRID_HEIGHT, camera.worldCellsH());
        assertEquals(expectedZoom, camera.zoom(), 1e-5f);
        assertEquals(expectedCell, camera.cellPxSize(), 1e-4f);
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
        assertEquals(11f, next.panCellX() - camera.panCellX(), 1e-4f);
    }

    @Test
    void wideCameraKeepsTheEntireLanceInsideTheWorkshopView() {
        BattleCamera camera = MechLabBattleScene.cameraForSurface(
                900f, 520f, new MechLabCameraController.CameraPose(
                        MechLabBattleScene.GRID_WIDTH * 0.5f,
                        MechLabBattleScene.GRID_HEIGHT * 0.5f,
                        MechLabCameraController.WIDE_ZOOM_NOTCHES));

        float first = camera.cellToScreenX(MechLabBattleScene.mechWorldX(0));
        float last = camera.cellToScreenX(MechLabBattleScene.mechWorldX(3));
        assertTrue(first > 0f);
        assertTrue(last < 900f);
        assertTrue(last - first > 600f);
        assertEquals(0f, camera.cellToScreenX(0f), 1e-4f);
        assertEquals(900f, camera.cellToScreenX(MechLabBattleScene.GRID_WIDTH), 1e-4f);
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
            assertEquals(12, simulation.getRoster().liveCount());
            assertEquals(MechLabSceneLayout.floorOverlays().size()
                    + MechLabSceneLayout.PROPS.size(), simulation.getDoodads().size());
            assertEquals(MechLabSceneLayout.FACILITY_JOBS.size(),
                    simulation.ambientTasks().assignmentCount());

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
            assertEquals(8, engineers);
        }
    }

    @Test
    void authoredGarageKeepsSouthEntranceOpenAndUsesRealAtlasPadCells() {
        assertFalse(MechLabSceneLayout.wall(7, 0));
        assertTrue(MechLabSceneLayout.wall(0, 0));
        assertTrue(MechLabSceneLayout.wall(7, MechLabSceneLayout.HEIGHT - 1));
        assertTrue(MechLabSceneLayout.wall(6, 16));
        assertFalse(MechLabSceneLayout.wall(7, 16));
        assertFalse(MechLabSceneLayout.wall(24, 16));
        assertEquals(160, MechLabSceneLayout.floorOverlays().size());
        long safetyStripeCells = MechLabSceneLayout.floorOverlays().stream()
                .filter(cell -> cell.tileColumn() == 1 && cell.tileRow() == 3)
                .count();
        long gratedCells = MechLabSceneLayout.floorOverlays().size() - safetyStripeCells;
        assertEquals(88, safetyStripeCells);
        assertEquals(72, gratedCells);
    }
}
