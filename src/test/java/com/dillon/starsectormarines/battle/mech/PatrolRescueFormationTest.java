package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatrolRescueFormationTest {

    @Test
    void quietPickupMechCyclesThroughAuthoredSquadPoints() {
        BattleSimulation sim = simulation();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        long mech = sim.spawn(new EntitySpec(
                "pickup mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 10, 10).squad(squadId));
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.centroidX = 10.5f;
        squad.centroidY = 10.5f;
        squad.rescuePickupMech = true;
        squad.rescuePatrolCells = new int[]{10, 4, 16, 8, 14, 15, 6, 15, 4, 8};
        squad.rescuePatrolIndex = -1;
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 10, 10);

        assertEquals(1f, PatrolRescueFormationGoal.INSTANCE.relevance(
                WorldState.EMPTY, squad, sim));

        PatrolRescueFormation.INSTANCE.execute(mech, squad, sim);

        assertEquals(10, squad.patrolWaypointX);
        assertEquals(10, squad.patrolWaypointY);
        assertEquals(0, squad.rescuePatrolIndex);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(22, 20);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(22, 20));
    }
}
