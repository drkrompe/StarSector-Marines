package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
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
            simulation.world().attachSpecialEquipment(
                    actor, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), 3);
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

    /**
     * Work starts when the worker gets there, not when a schedule says so.
     *
     * <p>The route below budgets its own walk at a twentieth of a cell a second
     * — several minutes for this leg — while the actor covers it in a few. Under
     * a sampled clock that gap is spent standing at the fixture waiting for the
     * timetable to agree, which on a manned deck was four in five actor-samples
     * of a transport's whole crew and nine in ten of a capital's.
     */
    @Test
    void aDwellBeginsOnArrivalRatherThanWhenAScheduleExpectsIt() {
        try (BattleSimulation simulation = simulation()) {
            long actor = simulation.spawn(new EntitySpec(
                    "walker", Faction.MARINE, UnitType.ENGINEER, 2, 2));
            AmbientTaskRoute route = new AmbientTaskRoute(
                    "long-walk", 0f, 0.05f, 0f, AmbientThreatPolicy.NONE, List.of(
                    new AmbientTaskRoute.Stop(9.5f, 9.5f, 30f,
                            AmbientActivity.INSPECTING, 10.5f, 9.5f)));
            simulation.ambientTasks().assign(actor, route);

            AmbientActivity onArrival = null;
            for (int tick = 0; tick < 30 * 60 && onArrival == null; tick++) {
                simulation.advance(1f / 30f);
                if (simulation.movement().atCell(actor, 9, 9)
                        && simulation.movement().settled(actor)) {
                    onArrival = simulation.ambientTasks().pose(actor).activity();
                }
            }

            assertEquals(AmbientActivity.INSPECTING, onArrival,
                    "the actor reached its job and was still waiting to begin it");
        }
    }

    /**
     * Somewhere else to be beats somewhere to queue.
     *
     * <p>Both members want the one bench. The second is not entitled to it and
     * must not stand outside it either: the rotation has another job on it, so
     * that is where they go. A ship with three firing points and six hundred
     * marines is a fact about the ship; six hundred marines motionless in the
     * passage outside the range is a scheduling defect.
     */
    @Test
    void aTakenJobIsPassedOverForTheNextOneOnTheRotation() {
        try (BattleSimulation simulation = simulation()) {
            simulation.taskPoints().register(new TaskPoint(
                    "the-bench", "bench", 3.5f, 3.5f, 3.5f, 4.5f));
            simulation.taskPoints().register(new TaskPoint(
                    "the-desk", "desk", 9.5f, 9.5f, 9.5f, 10.5f));
            AmbientTaskRoute route = new AmbientTaskRoute(
                    "shop", 0f, 1f, 0f, AmbientThreatPolicy.NONE, List.of(
                    new AmbientTaskRoute.Stop(3.5f, 3.5f, 4f,
                            AmbientActivity.WORKING, 3.5f, 4.5f, "bench"),
                    new AmbientTaskRoute.Stop(9.5f, 9.5f, 4f,
                            AmbientActivity.INSPECTING, 9.5f, 10.5f, "desk")));
            long first = simulation.spawn(new EntitySpec(
                    "first", Faction.MARINE, UnitType.ENGINEER, 2, 2));
            long second = simulation.spawn(new EntitySpec(
                    "second", Faction.MARINE, UnitType.ENGINEER, 2, 3));
            simulation.ambientTasks().assign(first, route);
            simulation.ambientTasks().assign(second, route);
            simulation.ambientTasks().settle();

            for (int tick = 0; tick < 30 * 30; tick++) simulation.advance(1f / 30f);

            assertEquals("the-bench", simulation.taskPoints().claimedPoint(first).id());
            assertEquals("the-desk", simulation.taskPoints().claimedPoint(second).id(),
                    "the second member waited for a bench instead of taking the desk");
            assertEquals(AmbientActivity.INSPECTING,
                    simulation.ambientTasks().pose(second).activity());
        }
    }

    /**
     * A rotation with nowhere free left on it keeps somebody at the job they
     * have, rather than sending them out to stand in a corridor.
     */
    @Test
    void aWorkerWithNowhereElseToGoStaysAtTheJobTheyHave() {
        try (BattleSimulation simulation = simulation()) {
            simulation.taskPoints().register(new TaskPoint(
                    "the-bench", "bench", 3.5f, 3.5f, 3.5f, 4.5f));
            simulation.taskPoints().register(new TaskPoint(
                    "the-desk", "desk", 9.5f, 9.5f, 9.5f, 10.5f));
            AmbientTaskRoute route = new AmbientTaskRoute(
                    "shop", 0f, 1f, 0f, AmbientThreatPolicy.NONE, List.of(
                    new AmbientTaskRoute.Stop(3.5f, 3.5f, 1f,
                            AmbientActivity.WORKING, 3.5f, 4.5f, "bench"),
                    new AmbientTaskRoute.Stop(9.5f, 9.5f, 1f,
                            AmbientActivity.INSPECTING, 9.5f, 10.5f, "desk")));
            long worker = simulation.spawn(new EntitySpec(
                    "worker", Faction.MARINE, UnitType.ENGINEER, 2, 2));
            long squatter = simulation.spawn(new EntitySpec(
                    "squatter", Faction.MARINE, UnitType.ENGINEER, 9, 9));
            simulation.ambientTasks().assign(worker, route);
            simulation.ambientTasks().settle();
            // The desk is spoken for by somebody outside the rotation, so the
            // worker's only other job is unavailable for the whole run.
            simulation.taskPoints().claimNearest(squatter, "desk", 9.5f, 9.5f);

            for (int tick = 0; tick < 30 * 20; tick++) simulation.advance(1f / 30f);

            assertEquals("the-bench", simulation.taskPoints().claimedPoint(worker).id());
            assertEquals(AmbientActivity.WORKING,
                    simulation.ambientTasks().pose(worker).activity(),
                    "a worker with nowhere else to be stopped working");
        }
    }

    /**
     * Yielding is stepping aside, not leaving the ship's books.
     *
     * <p>The failure this guards accumulates rather than crashes. A crew member
     * released for a passing threat used to be released for good, so on a home
     * deck — where the disturbance is routinely the crew's own live-fire range —
     * marines dropped out of the ship's life one at a time and stood in the
     * butts for the rest of the voyage.
     */
    @Test
    void somebodyWhoStoodDownGoesBackToWorkOnceItIsQuiet() {
        try (BattleSimulation simulation = simulation()) {
            long worker = simulation.spawn(new EntitySpec(
                    "worker", Faction.CIVILIAN, UnitType.ENGINEER, 2, 2)
                    .role(UnitRole.FLEE));
            simulation.ambientTasks().assign(worker, oneStop(
                    AmbientActivity.WORKING, AmbientThreatPolicy.ANY_COMBATANT, 6f));
            long stranger = simulation.spawn(new EntitySpec(
                    "stranger", Faction.MARINE, UnitType.MARINE, 4, 2));

            simulation.ambientTasks().advance(0f);
            assertFalse(simulation.ambientTasks().isControlling(worker),
                    "an armed stranger walked up and the worker carried on regardless");
            assertTrue(simulation.ambientTasks().isStoodDown(worker),
                    "the work was forgotten rather than set aside");

            // The stranger leaves. Quiet has to last before work resumes, so one
            // tick is deliberately not enough.
            simulation.world().setPos(stranger, 40f, 40f);
            simulation.ambientTasks().advance(0.5f);
            assertFalse(simulation.ambientTasks().isControlling(worker),
                    "the worker turned back to the bench the instant the radius cleared");

            for (int tick = 0; tick < 30 * 8; tick++) simulation.ambientTasks().advance(1f / 30f);

            assertTrue(simulation.ambientTasks().isControlling(worker),
                    "the worker never went back to work after the stranger left");
            assertFalse(simulation.ambientTasks().isStoodDown(worker));
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
        BattleSimulation simulation = new BattleSimulation(grid, topology, 7L);
        // Ambient work is not a mission. A simulation with no objectives installs
        // the backstop pair, so a floor holding nobody but friendly workers wins
        // the moment it is built and stops ticking - which reads, from a test
        // that advances more than a second, as a worker who stopped working.
        simulation.setMissionCompletionEnabled(false);
        return simulation;
    }
}
