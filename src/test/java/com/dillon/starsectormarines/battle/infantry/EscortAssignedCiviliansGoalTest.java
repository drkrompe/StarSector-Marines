package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RescueEscortCommand;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscortAssignedCiviliansGoalTest {

    @Test
    void squadAdvancesToEscortRingThenHoldsAndFiresThere() {
        BattleSimulation sim = simulation();
        long marine = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, 2, 4));
        int squadId = sim.mintSquad(Faction.MARINE, marine);
        sim.squad().assignSquad(marine, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = 2;
        squad.centroidY = 4;
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 14, 4);

        assertEquals(0.9f, EscortAssignedCiviliansGoal.INSTANCE.relevance(
                WorldState.EMPTY, squad, sim));
        SquadPlan plan = EscortAssignedCiviliansGoal.INSTANCE.customPlan(
                squad, sim);
        assertEquals("EscortCivilians", plan.currentStep().action.name());

        ActionStatus status = plan.currentStep().action.execute(
                marine, squad, sim);

        assertEquals(ActionStatus.RUNNING, status);
        int[] path = sim.movement().path(marine);
        assertFalse(Paths.isEmpty(path));
        int dx = Paths.destX(path) - 14;
        int dy = Paths.destY(path) - 4;
        assertTrue(dx * dx + dy * dy
                <= EscortAssignedCivilians.RELIEF_RADIUS
                * EscortAssignedCivilians.RELIEF_RADIUS);

        sim.world().setCellPos(marine, 12, 4);
        plan.currentStep().action.execute(marine, squad, sim);

        assertTrue(Paths.isEmpty(sim.movement().path(marine)));
    }

    @Test
    void claimedEscortDestinationDoesNotResetMovementEachTick() {
        BattleSimulation sim = simulation();
        long marine = addMarine(sim, 2, 4);
        Squad squad = sim.getSquad(sim.squad().squadId(marine));
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 14, 4);

        EscortAssignedCivilians.INSTANCE.execute(marine, squad, sim);
        int[] path = sim.movement().path(marine);
        float firstX = sim.world().x(marine);
        sim.getOccupancyMap()[sim.getGrid().index(
                Paths.destX(path), Paths.destY(path))] = 1;

        EscortAssignedCivilians.INSTANCE.execute(marine, squad, sim);

        assertSame(path, sim.movement().path(marine));
        assertTrue(sim.world().x(marine) > firstX,
                "second execute advances the same path eastward instead of resetting it");
    }

    @Test
    void everyMobileSquadClosesOnItsOwnSlotBeforeShelterRelief() {
        BattleSimulation sim = simulation();
        Squad lead = sim.getSquad(sim.squad().squadId(addMarine(sim, 2, 4)));
        Squad support = sim.getSquad(sim.squad().squadId(addMarine(sim, 2, 6)));

        assertEquals(EscortAssignedCivilians.RELIEF_RADIUS,
                EscortAssignedCivilians.standoffRadius(lead, sim));
        assertEquals(EscortAssignedCivilians.RELIEF_RADIUS,
                EscortAssignedCivilians.standoffRadius(support, sim));
    }

    @Test
    void supportSquadFiveCellsFromItsRallyDoesNotPause() {
        BattleSimulation sim = simulation();
        long marine = addMarine(sim, 9, 4);
        Squad squad = sim.getSquad(sim.squad().squadId(marine));
        squad.assignedObjective = ObjectiveAssignment.escort(
                squad.id, 14, 4);

        EscortAssignedCivilians.INSTANCE.execute(marine, squad, sim);

        assertFalse(Paths.isEmpty(sim.movement().path(marine)),
                "a squad five cells from its own slot must keep advancing");
    }

    @Test
    void squadmatesReceiveDistinctMovingDestinationsAroundTheRallyCell() {
        BattleSimulation sim = simulation();
        List<Long> members = addSquad(sim, 4, 2, 4);
        Squad squad = sim.getSquad(sim.squad().squadId(members.get(0)));
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 14, 4);
        SquadPlan plan = new SquadPlan(List.of(new SquadPlan.Step(
                EscortAssignedCivilians.INSTANCE)));
        Map<String, List<Long>> assignments = RoleAssigner.assign(members,
                EscortAssignedCivilians.INSTANCE.roles(squad, sim));
        plan.currentStep().assignments.putAll(assignments);
        squad.currentPlan = plan;

        Set<Long> destinations = new HashSet<>();
        for (long member : members) {
            EscortAssignedCivilians.INSTANCE.execute(member, squad, sim);
            int[] path = sim.movement().path(member);
            assertFalse(Paths.isEmpty(path));
            int destX = Paths.destX(path);
            int destY = Paths.destY(path);
            assertTrue(destinations.add(cellKey(destX, destY)),
                    "every squadmate must receive a distinct destination");
            int dx = destX - 14;
            int dy = destY - 4;
            assertTrue(dx * dx + dy * dy
                    <= EscortAssignedCivilians.RELIEF_RADIUS
                    * EscortAssignedCivilians.RELIEF_RADIUS);
        }
        assertEquals(4, destinations.size());
    }

    @Test
    void unitFormationMovesWithAnUpdatedSquadRallyCell() {
        BattleSimulation sim = simulation();
        List<Long> members = addSquad(sim, 4, 2, 4);
        Squad squad = sim.getSquad(sim.squad().squadId(members.get(0)));
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 13, 4);
        SquadPlan plan = new SquadPlan(List.of(new SquadPlan.Step(
                EscortAssignedCivilians.INSTANCE)));
        plan.currentStep().assignments.putAll(RoleAssigner.assign(members,
                EscortAssignedCivilians.INSTANCE.roles(squad, sim)));
        squad.currentPlan = plan;
        for (long member : members) {
            EscortAssignedCivilians.INSTANCE.execute(member, squad, sim);
        }
        List<Long> firstDestinations = members.stream()
                .map(member -> cellKey(Paths.destX(sim.movement().path(member)),
                        Paths.destY(sim.movement().path(member))))
                .toList();

        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 14, 4);
        Set<Long> shiftedDestinations = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            long member = members.get(i);
            EscortAssignedCivilians.INSTANCE.execute(member, squad, sim);
            int destX = Paths.destX(sim.movement().path(member));
            int destY = Paths.destY(sim.movement().path(member));
            assertEquals(firstDestinations.get(i), cellKey(destX - 1, destY),
                    "each member keeps its relative formation slot as the rally advances");
            assertTrue(shiftedDestinations.add(cellKey(destX, destY)));
        }
        assertEquals(4, shiftedDestinations.size());
    }

    @Test
    void pickupGuardCannotConsumeTheShelterReliefLead() {
        BattleSimulation sim = simulation();
        Squad guard = sim.getSquad(sim.squad().squadId(addMarine(sim, 2, 4)));
        guard.rescuePickupGuard = true;
        Squad mobile = sim.getSquad(sim.squad().squadId(addMarine(sim, 2, 6)));

        assertEquals(EscortAssignedCivilians.PICKUP_GUARD_RADIUS,
                EscortAssignedCivilians.standoffRadius(guard, sim));
        assertEquals(EscortAssignedCivilians.RELIEF_RADIUS,
                EscortAssignedCivilians.standoffRadius(mobile, sim));
    }

    @Test
    void forwardScreenMakesEngagedEscortAdvanceAndFireBeforeLeashStalls() {
        BattleSimulation sim = simulation(28, 18);
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(new PointOfInterest(
                        PointOfInterest.Kind.RESIDENTIAL,
                        9, 6, 13, 10, 11, 8, 11, 8)), 412L);
        assertNotNull(payload);
        long marine = addMarine(sim, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());

        int civilianX = 13;
        int civilianY = 9;
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), civilianX, civilianY);
        }
        int[] route = GridPathfinder.findPath(sim.getGrid(), civilianX, civilianY,
                payload.placement.liftX, payload.placement.liftY);
        assertTrue(Paths.cellCount(route) > RescueEscortCommand.ADVANCE_SCREEN_CELLS);
        int stepDx = Paths.cellX(route, 1) - civilianX;
        int stepDy = Paths.cellY(route, 1) - civilianY;
        sim.world().setCellPos(marine, civilianX - stepDx * 2,
                civilianY - stepDy * 2);
        long threat = sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, Paths.cellX(route, 4),
                Paths.cellY(route, 4)));
        sim.world().setTargetId(marine, threat);

        Squad squad = sim.getSquad(sim.squad().squadId(marine));
        new RescueEscortCommand(payload.placement).tick(sim);
        EscortAssignedCivilians.INSTANCE.execute(marine, squad, sim);

        assertFalse(Paths.isEmpty(sim.movement().path(marine)),
                "marine two cells behind the cohort must push toward its forward screen");
        assertEquals(threat, sim.combat().fireTargetId(marine),
                "escort keeps a moving fire intent while it advances the screen");
    }

    private static long addMarine(BattleSimulation sim, int x, int y) {
        long marine = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, x, y));
        int squadId = sim.mintSquad(Faction.MARINE, marine);
        sim.squad().assignSquad(marine, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return marine;
    }

    private static List<Long> addSquad(BattleSimulation sim, int count,
                                       int x, int y) {
        List<Long> members = new ArrayList<>(count);
        long leader = addMarine(sim, x, y);
        members.add(leader);
        int squadId = sim.squad().squadId(leader);
        Squad squad = sim.getSquad(squadId);
        for (int i = 1; i < count; i++) {
            long member = sim.spawn(new EntitySpec("marine-" + i,
                    Faction.MARINE, UnitType.MARINE, x, y));
            sim.squad().assignSquad(member, squadId);
            members.add(member);
        }
        squad.aliveMembers = count;
        return members;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static BattleSimulation simulation() {
        return simulation(20, 10);
    }

    private static BattleSimulation simulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
