package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.SweepSector;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SweepAssignedSectorGoalTest {

    @Test
    void noContactBuildsMovingSweepPlanAndContactYieldsToEngagement() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, 2, 2));
        int squadId = sim.mintSquad(Faction.MARINE, marine);
        sim.squad().assignSquad(marine, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = 2.5f;
        squad.centroidY = 2.5f;
        squad.assignedObjective = ObjectiveAssignment.sweepSector(squadId, 16, 7);

        assertTrue(SweepAssignedSectorGoal.INSTANCE.relevance(
                WorldState.EMPTY, squad, sim) > 0f);
        SquadPlan plan = SweepAssignedSectorGoal.INSTANCE.customPlan(squad, sim);
        SweepSector action = (SweepSector) plan.currentStep().action;
        assertEquals(ActionStatus.RUNNING, action.execute(marine, squad, sim));
        assertEquals(16, Paths.destX(sim.world().path(marine)));
        assertEquals(7, Paths.destY(sim.world().path(marine)));

        WorldState contact = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
        assertEquals(0f, SweepAssignedSectorGoal.INSTANCE.relevance(
                contact, squad, sim),
                "identified contact must hand control to EliminateEnemies");
    }

    @Test
    void changedOrderInvalidatesOldSweepAndClearsItsPath() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, 2, 2));
        int squadId = sim.mintSquad(Faction.MARINE, marine);
        sim.squad().assignSquad(marine, squadId);
        long wing = sim.spawn(new EntitySpec(
                "wing", Faction.MARINE, UnitType.MARINE, 2, 3));
        sim.squad().assignSquad(wing, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 2;
        squad.assignedObjective = ObjectiveAssignment.sweepSector(squadId, 16, 7);
        SweepSector old = new SweepSector(16, 7);
        assertEquals(ActionStatus.RUNNING, old.execute(marine, squad, sim));
        assertEquals(ActionStatus.RUNNING, old.execute(wing, squad, sim));
        assertTrue(Paths.cellCount(sim.world().path(marine)) > 0);
        assertTrue(Paths.cellCount(sim.world().path(wing)) > 0);

        squad.assignedObjective = ObjectiveAssignment.sweepSector(squadId, 4, 7);
        assertEquals(ActionStatus.FAILURE, old.execute(marine, squad, sim));
        assertEquals(0, Paths.cellCount(sim.world().path(marine)),
                "a replaced search order must not leak its old path into combat");
        assertEquals(0, Paths.cellCount(sim.world().path(wing)),
                "one member's handoff clears stale search paths squad-wide");
    }

    @Test
    void rememberedDeadContactDoesNotBlockSweepResumption() {
        BattleSimulation sim = openSim();
        long marine = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, 2, 2));
        int squadId = sim.mintSquad(Faction.MARINE, marine);
        sim.squad().assignSquad(marine, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.assignedObjective = ObjectiveAssignment.sweepSector(squadId, 16, 7);
        long enemy = sim.spawn(new EntitySpec(
                "enemy", Faction.DEFENDER, UnitType.MILITIA, 3, 2));
        sim.advance(BattleSimulation.TICK_DT);
        sim.getRoster().release(enemy);

        assertTrue(squad.hasBelievedContacts(),
                "the observation should remain as non-actionable memory");
        SweepSector action = new SweepSector(16, 7);
        assertEquals(ActionStatus.RUNNING, action.execute(marine, squad, sim));
        assertEquals(16, Paths.destX(sim.world().path(marine)));
        assertEquals(7, Paths.destY(sim.world().path(marine)));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 10));
    }
}
