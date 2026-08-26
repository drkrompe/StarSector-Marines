package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendTrack;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefendAssignedTrackGoalTest {

    @Test
    void infantryAndMechPlannersShareRallyGoalWhichYieldsToContact() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec("reserve", Faction.DEFENDER,
                UnitType.MILITIA, 2, 2).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.centroidX = 2;
        squad.centroidY = 2;
        squad.assignedObjective = ObjectiveAssignment.defendTrack(squadId, 16, 7);

        assertTrue(GoapInfantryBehavior.INFANTRY_GOALS.contains(DefendAssignedTrackGoal.INSTANCE));
        assertTrue(GoapMechBehavior.MECH_GOALS.contains(DefendAssignedTrackGoal.INSTANCE));
        assertTrue(DefendAssignedTrackGoal.INSTANCE.relevance(
                WorldState.EMPTY, squad, sim) > 0f);
        SquadPlan plan = DefendAssignedTrackGoal.INSTANCE.customPlan(squad, sim);
        DefendTrack action = (DefendTrack) plan.currentStep().action;
        assertEquals(ActionStatus.RUNNING, action.execute(member, squad, sim));
        assertEquals(16, Paths.destX(sim.world().path(member)));
        assertEquals(7, Paths.destY(sim.world().path(member)));

        assertEquals(0f, DefendAssignedTrackGoal.INSTANCE.relevance(
                WorldState.EMPTY.with(Predicate.HAS_TARGET, true), squad, sim));
    }

    @Test
    void multiFireteamRallyUsesDistinctMemberDestinations() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        Squad squad = sim.getSquad(squadId);
        long[] members = new long[8];
        for (int i = 0; i < members.length; i++) {
            members[i] = sim.spawn(new EntitySpec("reserve-" + i,
                    Faction.DEFENDER, UnitType.MILITIA,
                    2 + i % 2, 1 + i).squad(squadId));
        }
        squad.assignedObjective = ObjectiveAssignment.defendTrack(squadId, 16, 7);
        DefendTrack action = new DefendTrack(16, 7);
        Set<String> destinations = new HashSet<>();

        for (long member : members) {
            assertEquals(ActionStatus.RUNNING, action.execute(member, squad, sim));
            destinations.add(Paths.destX(sim.world().path(member)) + ","
                    + Paths.destY(sim.world().path(member)));
        }

        assertEquals(members.length, destinations.size(),
                "fireteams should establish a footprint instead of one occupied rally cell");
    }

    @Test
    void attackerLaneAdvanceSharesRallyMotionAndYieldsToContact() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 2, 2).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.centroidX = 2;
        squad.centroidY = 2;
        squad.assignedObjective = ObjectiveAssignment.advanceTrack(
                squadId, 16, 7);

        assertTrue(GoapInfantryBehavior.INFANTRY_GOALS.contains(
                AdvanceAssignedTrackGoal.INSTANCE));
        assertTrue(GoapMechBehavior.MECH_GOALS.contains(
                AdvanceAssignedTrackGoal.INSTANCE));
        assertTrue(AdvanceAssignedTrackGoal.INSTANCE.relevance(
                WorldState.EMPTY, squad, sim) > 0f);
        SquadPlan plan = AdvanceAssignedTrackGoal.INSTANCE.customPlan(squad, sim);
        DefendTrack action = (DefendTrack) plan.currentStep().action;
        assertEquals(AssignmentKind.ADVANCE_TRACK, action.assignmentKind());
        assertEquals("AdvanceTrack", action.name());
        assertEquals(ActionStatus.RUNNING, action.execute(member, squad, sim));

        assertEquals(0f, AdvanceAssignedTrackGoal.INSTANCE.relevance(
                WorldState.EMPTY.with(Predicate.HAS_TARGET, true), squad, sim));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 10));
    }
}
