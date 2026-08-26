package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientTaskServiceTest {

    @Test
    void exactTimeSamplingMovesTheRealActorAndUsesItsLayeredWeapon() {
        try (BattleSimulation simulation = simulation()) {
            long actor = simulation.spawn(new EntitySpec(
                    "range marine", Faction.MARINE, UnitType.MARINE, 2, 2));
            AmbientTaskRoute route = oneStop(
                    AmbientActivity.FIRING_PRIMARY, AmbientThreatPolicy.NONE, 0f);
            simulation.ambientTasks().assign(actor, route);

            simulation.ambientTasks().seek(0.15f);

            assertEquals(4.5f, simulation.world().x(actor));
            assertEquals(5.5f, simulation.world().y(actor));
            assertEquals(LayeredAppearance.POSE_FIRING,
                    simulation.getEntityWorld().getInt(actor,
                            simulation.getBattleComponents().LAYERED_ANIMATION,
                            BattleComponents.LAYERED_WEAPON_POSE));
            int flags = simulation.getEntityWorld().getInt(actor,
                    simulation.getBattleComponents().LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FLAGS);
            assertTrue((flags & LayeredAppearance.FLAG_MUZZLE_FLASH) != 0);
        }
    }

    @Test
    void civilianWorkYieldsToNearbyArmedActorsWithoutChangingItsRole() {
        try (BattleSimulation simulation = simulation()) {
            long worker = simulation.spawn(new EntitySpec(
                    "worker", Faction.CIVILIAN, UnitType.ENGINEER, 2, 2)
                    .role(UnitRole.FLEE));
            simulation.ambientTasks().assign(worker, oneStop(
                    AmbientActivity.WORKING, AmbientThreatPolicy.ANY_COMBATANT, 6f));
            simulation.spawn(new EntitySpec(
                    "marine", Faction.MARINE, UnitType.MARINE, 4, 2));

            simulation.ambientTasks().advance(0f);

            assertFalse(simulation.ambientTasks().isControlling(worker));
            assertEquals(UnitRole.FLEE, simulation.role().role(worker));
        }
    }

    @Test
    void equipmentPracticeUsesCarriedSpecialWithoutSpendingIt() {
        try (BattleSimulation simulation = simulation()) {
            long actor = simulation.spawn(new EntitySpec(
                    "support marine", Faction.MARINE, UnitType.MARINE, 2, 2));
            simulation.world().attachSecondaryWeapon(
                    actor, MarineSecondary.ROCKET_LAUNCHER, 3);
            simulation.ambientTasks().assign(actor, oneStop(
                    AmbientActivity.PRACTICING_EQUIPMENT,
                    AmbientThreatPolicy.NONE, 0f));

            simulation.ambientTasks().seek(0.075f);

            assertEquals(LayeredAppearance.POSE_FIRING,
                    simulation.getEntityWorld().getInt(actor,
                            simulation.getBattleComponents().LAYERED_ANIMATION,
                            BattleComponents.LAYERED_WEAPON_POSE));

            simulation.ambientTasks().seek(0.80f);

            assertEquals(LayeredAppearance.POSE_ROCKET_FIRE,
                    simulation.getEntityWorld().getInt(actor,
                            simulation.getBattleComponents().LAYERED_ANIMATION,
                            BattleComponents.LAYERED_WEAPON_POSE));
            assertEquals(3, simulation.world().secondaryAmmo(actor),
                    "cosmetic practice must not consume issued equipment");
        }
    }

    @Test
    void liveFireAssignmentUsesTheOwningSimulationsShotPipeline() {
        try (BattleSimulation simulation = simulation()) {
            long actor = simulation.spawn(new EntitySpec(
                    "range marine", Faction.MARINE, UnitType.MARINE, 4, 5));
            long target = simulation.spawn(new EntitySpec(
                    "range target", Faction.DEFENDER, UnitType.RANGE_TARGET, 4, 9)
                    .role(UnitRole.STRUCTURE));
            simulation.ambientTasks().assignLiveFire(actor, oneStop(
                    AmbientActivity.FIRING_PRIMARY,
                    AmbientThreatPolicy.NONE, 0f), target);

            simulation.advance(0.2f);

            assertFalse(simulation.getShotsThisFrame().isEmpty());
            assertEquals(actor, simulation.getShotsThisFrame().get(0).shooterId);
        }
    }

    @Test
    void phaseOffsetsProduceDifferentRoutePhases() {
        AmbientTaskRoute first = twoStops(0f);
        AmbientTaskRoute second = twoStops(4f);

        AmbientTaskPose firstPose = AmbientTaskService.sample(first, 0f);
        AmbientTaskPose secondPose = AmbientTaskService.sample(second, 0f);

        assertFalse(firstPose.moving());
        assertTrue(secondPose.moving());
    }

    @Test
    void liveAmbientMovementUsesNavigationAroundBlockingTerrain() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        CellTopology topology = new CellTopology(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < 12; y++) {
            if (y != 6) grid.setWalkable(5, y, false);
        }
        try (BattleSimulation simulation = new BattleSimulation(grid, topology, 8L)) {
            simulation.setMissionCompletionEnabled(false);
            long actor = simulation.spawn(new EntitySpec(
                    "route marine", Faction.MARINE, UnitType.MARINE, 2, 2));
            AmbientTaskRoute route = new AmbientTaskRoute(
                    "through-door", 0f, 1f, 0f, AmbientThreatPolicy.NONE, List.of(
                    new AmbientTaskRoute.Stop(8.5f, 2.5f, 30f,
                            AmbientActivity.INSPECTING, 9.5f, 2.5f)));
            simulation.ambientTasks().assign(actor, route);

            for (int tick = 0; tick < 30 * 30; tick++) {
                simulation.advance(1f / 30f);
                assertTrue(grid.isWalkable(
                                simulation.world().cellX(actor), simulation.world().cellY(actor)),
                        "ambient movement must never write an actor into a wall");
            }

            assertTrue(simulation.movement().atCell(actor, 8, 2),
                    "the normal path follower should route through the authored doorway; actor ended at "
                            + simulation.world().x(actor) + "," + simulation.world().y(actor));
        }
    }

    @Test
    void ambientActorsClaimDifferentPointsFromTheSameActivityGroup() {
        try (BattleSimulation simulation = simulation()) {
            simulation.taskPoints().register(new TaskPoint(
                    "range-a", "range", 7.5f, 3.5f, 7.5f, 9.5f));
            simulation.taskPoints().register(new TaskPoint(
                    "range-b", "range", 7.5f, 7.5f, 7.5f, 10.5f));
            long first = simulation.spawn(new EntitySpec(
                    "first", Faction.MARINE, UnitType.MARINE, 2, 2));
            long second = simulation.spawn(new EntitySpec(
                    "second", Faction.MARINE, UnitType.MARINE, 2, 8));
            AmbientTaskRoute route = new AmbientTaskRoute(
                    "range", 0f, 1f, 0f, AmbientThreatPolicy.NONE, List.of(
                    new AmbientTaskRoute.Stop(7.5f, 5.5f, 30f,
                            AmbientActivity.FIRING_PRIMARY, 7.5f, 10.5f, "range")));

            simulation.ambientTasks().assign(first, route);
            simulation.ambientTasks().assign(second, route);
            simulation.advance(1f / 30f);

            assertEquals("range-a", simulation.taskPoints().claimedPoint(first).id());
            assertEquals("range-b", simulation.taskPoints().claimedPoint(second).id());
            assertEquals(2, simulation.taskPoints().claimCount());
        }
    }

    private static AmbientTaskRoute oneStop(
            AmbientActivity activity, AmbientThreatPolicy policy, float radius) {
        return new AmbientTaskRoute("test", 0f, 1f, radius, policy, List.of(
                new AmbientTaskRoute.Stop(4.5f, 5.5f, 1f, activity, 4.5f, 9.5f)));
    }

    private static AmbientTaskRoute twoStops(float offset) {
        return new AmbientTaskRoute("route-" + offset, offset, 1f, 0f,
                AmbientThreatPolicy.NONE, List.of(
                new AmbientTaskRoute.Stop(2.5f, 2.5f, 2f,
                        AmbientActivity.IDLE, 3.5f, 2.5f),
                new AmbientTaskRoute.Stop(6.5f, 2.5f, 2f,
                        AmbientActivity.WORKING, 7.5f, 2.5f)));
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        CellTopology topology = new CellTopology(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology, 7L);
    }
}
