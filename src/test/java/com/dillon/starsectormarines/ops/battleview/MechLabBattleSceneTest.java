package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabBattleSceneTest {

    @Test
    void embeddedCameraUsesBattleCellsAndZoomsTheWholeRoomUniformly() {
        BattleCamera camera = MechLabBattleScene.cameraForSurface(900f, 520f);

        assertEquals(MechLabBattleScene.GRID_WIDTH, camera.worldCellsW());
        assertEquals(MechLabBattleScene.GRID_HEIGHT, camera.worldCellsH());
        assertEquals(1.2f, camera.zoom(), 1e-6f);
        assertEquals(69.333336f, camera.cellPxSize(), 1e-5f);
        assertEquals(450f,
                camera.cellToScreenX(MechLabBattleScene.mechWorldX()), 1e-5f);
        assertEquals(260f,
                camera.cellToScreenY(MechLabBattleScene.mechWorldY()), 1e-5f);
        assertEquals(camera.cellPxSize(),
                camera.cellToScreenX(2f) - camera.cellToScreenX(1f), 1e-5f);
        assertEquals(camera.cellPxSize(),
                camera.cellToScreenY(2f) - camera.cellToScreenY(1f), 1e-5f);
    }

    @Test
    void workshopIsARealBattleSceneWithOneMechAndThreeAgentDolls() {
        try (BattleSimulation simulation =
                     MechLabBattleScene.buildSimulation(MechVariant.HOUND)) {
            assertEquals(MechLabBattleScene.GRID_WIDTH, simulation.getGrid().getWidth());
            assertEquals(MechLabBattleScene.GRID_HEIGHT, simulation.getGrid().getHeight());
            assertEquals(4, simulation.getRoster().liveCount());
            assertEquals(8, simulation.getDoodads().size());

            int mechs = 0;
            int engineers = 0;
            for (int index = 0; index < simulation.getRoster().liveCount(); index++) {
                long id = simulation.getRoster().get(index);
                UnitType type = simulation.identity().type(id);
                if (type == UnitType.HEAVY_MECH) {
                    mechs++;
                    assertEquals(MechVariant.HOUND,
                            simulation.identity().mechVariant(id));
                    assertEquals(MechLabBattleScene.MECH_CELL_X,
                            simulation.world().cellX(id));
                    assertEquals(MechLabBattleScene.MECH_CELL_Y,
                            simulation.world().cellY(id));
                } else if (type == UnitType.ENGINEER) {
                    engineers++;
                    assertTrue(simulation.world().hasLayeredAppearance(id));
                }
            }
            assertEquals(1, mechs);
            assertEquals(3, engineers);
        }
    }
}
