package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void engagedSiroccoReturnsToLzInsteadOfTakingAFieldOverwatchCell() {
        BattleSimulation sim = simulation(60, 50);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        long mech = sim.spawn(new EntitySpec("pickup sirocco",
                Faction.MARINE, UnitType.HEAVY_MECH, 2, 2)
                .squad(squadId));
        sim.world().attachMechLoadout(mech,
                MechLoadoutComponent.defaultLoadout(MechRole.LR_SUPPORT));
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.centroidX = 2.5f;
        squad.centroidY = 2.5f;
        squad.alertLevel = SquadAlertLevel.ENGAGED;
        squad.lastSeenEnemyX = 45;
        squad.lastSeenEnemyY = 35;
        squad.rescuePickupMech = true;
        squad.rescuePatrolCells = new int[]{10, 4, 16, 8, 14, 15, 6, 15, 4, 8};
        squad.rescuePatrolIndex = -1;
        squad.assignedObjective = ObjectiveAssignment.escort(squad.id, 10, 10);
        sim.setPath(mech, GridPathfinder.findPath(sim.getGrid(), 2, 2,
                squad.lastSeenEnemyX, squad.lastSeenEnemyY));

        GoapMechBehavior.replanIfNeeded(squad, sim);

        assertSame(PatrolRescueFormationGoal.INSTANCE, squad.currentGoal,
                "contact must not replace the pickup mech's LZ-defense mission");
        GoapMechBehavior.INSTANCE.update(mech, sim);
        assertEquals(0, Paths.cellCount(sim.movement().path(mech)),
                "entering the patrol cancels the old field-overwatch path");
        squad.patrolDwellTimer = 0f;

        GoapMechBehavior.INSTANCE.update(mech, sim);

        assertEquals(10, Paths.destX(sim.movement().path(mech)));
        assertEquals(10, Paths.destY(sim.movement().path(mech)),
                "the engaged pickup mech returns to the authored LZ center");
    }

    @Test
    void planlessMechDropsItsFormerObjectivePath() {
        BattleSimulation sim = simulation();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        long mech = sim.spawn(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 2, 2).squad(squadId));
        squad.leaderId = mech;
        squad.aliveMembers = 1;
        squad.centroidX = 2.5f;
        squad.centroidY = 2.5f;
        squad.currentPlan = null;
        sim.setPath(mech, GridPathfinder.findPath(sim.getGrid(), 2, 2,
                16, 14));

        GoapMechBehavior.INSTANCE.update(mech, sim);

        assertTrue(Paths.isEmpty(sim.movement().path(mech)),
                "a planless mech must not continue toward a released assignment");
    }

    private static BattleSimulation simulation() {
        return simulation(22, 20);
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
