package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        float firstProgress = sim.movement().moveProgress(marine);
        sim.getOccupancyMap()[sim.getGrid().index(
                Paths.destX(path), Paths.destY(path))] = 1;

        EscortAssignedCivilians.INSTANCE.execute(marine, squad, sim);

        assertSame(path, sim.movement().path(marine));
        assertTrue(sim.movement().moveProgress(marine) > firstProgress);
    }

    @Test
    void onlyLeadSquadClosesTightlyBeforeShelterRelief() {
        BattleSimulation sim = simulation();
        Squad lead = sim.getSquad(sim.squad().squadId(addMarine(sim, 2, 4)));
        Squad support = sim.getSquad(sim.squad().squadId(addMarine(sim, 2, 6)));

        assertEquals(EscortAssignedCivilians.RELIEF_RADIUS,
                EscortAssignedCivilians.standoffRadius(lead, sim));
        assertEquals(EscortAssignedCivilians.ESCORT_RADIUS,
                EscortAssignedCivilians.standoffRadius(support, sim));
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

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(20, 10));
    }
}
