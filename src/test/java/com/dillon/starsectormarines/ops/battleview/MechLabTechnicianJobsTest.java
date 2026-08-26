package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.ambient.AmbientActivity;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskPose;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabTechnicianJobsTest {

    @Test
    void authoredPhaseOffsetsShowDifferentJobsInTheSameFrame() {
        AmbientTaskPose welder = AmbientTaskService.sample(
                MechLabSceneLayout.TECHNICIAN_JOBS.get(0), 0f);
        AmbientTaskPose courier = AmbientTaskService.sample(
                MechLabSceneLayout.TECHNICIAN_JOBS.get(1), 0f);

        assertEquals(AmbientActivity.WORKING, welder.activity());
        assertFalse(welder.moving());
        assertEquals(AmbientActivity.WALKING, courier.activity());
        assertTrue(courier.moving());
        assertTrue(courier.locomotionPhase() > 0f);
    }

    @Test
    void jobsMoveRealEngineerEntitiesAndAuthorTheirWalkPose() {
        try (BattleSimulation simulation = MechLabBattleScene.buildSimulation(
                List.of(MechVariant.BULWARK))) {
            long technician = firstEngineer(simulation);
            simulation.ambientTasks().seek(0f);
            float startX = simulation.world().x(technician);
            float startY = simulation.world().y(technician);
            int startFlags = simulation.getEntityWorld().getInt(technician,
                    simulation.getBattleComponents().LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FLAGS);

            simulation.ambientTasks().seek(6f);

            assertEquals(0, startFlags);
            assertNotEquals(startX, simulation.world().x(technician));
            assertNotEquals(startY, simulation.world().y(technician));
            assertEquals(LayeredAppearance.FLAG_MOVING,
                    simulation.getEntityWorld().getInt(technician,
                            simulation.getBattleComponents().LAYERED_ANIMATION,
                            BattleComponents.LAYERED_FLAGS));
        }
    }

    private static long firstEngineer(BattleSimulation simulation) {
        for (int index = 0; index < simulation.getRoster().liveCount(); index++) {
            long id = simulation.getRoster().get(index);
            if (simulation.identity().type(id) == UnitType.ENGINEER) return id;
        }
        throw new AssertionError("garage should contain a technician");
    }
}
