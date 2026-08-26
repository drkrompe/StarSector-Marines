package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
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

    @Test
    void leisureRotationRemainsOnWalkableFloorWithoutDeepActorOverlap() {
        List<MarineSoldier> soldiers = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new MarineSoldier(
                        "marine-" + index, "Marine " + index, null))
                .toList();
        try (BattleSimulation simulation = BarracksBattleScene.buildSimulation(soldiers)) {
            long[] marineIds = new long[12];
            int marineCount = 0;
            for (int index = 0; index < simulation.getRoster().liveCount(); index++) {
                long actor = simulation.getRoster().get(index);
                if (simulation.identity().faction(actor) == Faction.MARINE) {
                    marineIds[marineCount++] = actor;
                }
            }
            assertEquals(12, marineCount);

            for (int tick = 0; tick < 30 * 90; tick++) {
                simulation.advance(1f / 30f);
                for (int first = 0; first < marineCount; first++) {
                    long actor = marineIds[first];
                    assertTrue(simulation.getGrid().isWalkable(
                                    simulation.world().cellX(actor),
                                    simulation.world().cellY(actor)),
                            "ambient routes must remain on walkable floor");
                    for (int second = first + 1; second < marineCount; second++) {
                        float dx = simulation.world().x(actor)
                                - simulation.world().x(marineIds[second]);
                        float dy = simulation.world().y(actor)
                                - simulation.world().y(marineIds[second]);
                        float distanceSq = dx * dx + dy * dy;
                        assertTrue(distanceSq >= 0.04f,
                                "ambient actors must not deeply overlap at tick " + tick
                                        + ": " + actor + " @ " + simulation.world().x(actor)
                                        + "," + simulation.world().y(actor) + " and "
                                        + marineIds[second] + " @ "
                                        + simulation.world().x(marineIds[second]) + ","
                                        + simulation.world().y(marineIds[second])
                                        + " distanceSq=" + distanceSq);
                    }
                }
            }
            assertEquals(BarracksSceneLayout.TASK_POINTS.size(),
                    simulation.taskPoints().registeredCount());
            assertTrue(simulation.taskPoints().claimCount()
                            <= BarracksSceneLayout.TASK_POINTS.size(),
                    "published activity sites bound concurrent station use");
        }
    }
}
