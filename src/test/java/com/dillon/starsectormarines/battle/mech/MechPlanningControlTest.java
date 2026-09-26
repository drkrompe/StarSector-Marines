package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechPlanningControlTest {
    @Test
    void controlledLeaderLeavesRolesAndFormationButRetainsPhysicalMembership() {
        BattleSimulation sim = openSimulation();
        int id = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long leader = spawn(sim, id, MechRole.BALANCED, 4);
        long follower = spawn(sim, id, MechRole.ASSAULT, 7);
        Squad squad = finish(sim, id, leader, 2);
        ObjectiveAssignment mission = ObjectiveAssignment.attackMove(id, 24, 8);
        squad.assignedObjective = mission;
        GoapMechBehavior.replanIfNeeded(squad, sim);
        SquadPlan previous = squad.currentPlan;
        assertNotNull(previous.currentStep().slotOf(leader));

        squad.setControlledMember(leader, sim);
        sim.world().setPos(leader, 25.5f, 8.5f);
        GoapMechBehavior.replanIfNeeded(squad, sim);

        assertTrue(previous.steps().stream().allMatch(step -> step.assignments.isEmpty()));
        assertNull(squad.currentPlan.currentStep().slotOf(leader));
        assertNotNull(squad.currentPlan.currentStep().slotOf(follower));
        assertEquals(follower, squad.autonomousLeader(sim));
        assertEquals(0L, BreachAndAssault.lanceCohesionAnchor(follower, squad, sim));
        assertEquals(leader, squad.leaderId);
        assertEquals(2, squad.aliveMembers);
        assertEquals(2, sim.squadMemberCount(id));
        assertSame(mission, squad.assignedObjective);

        squad.setControlledMember(0L, sim);
        assertNull(squad.currentPlan);
        GoapMechBehavior.replanIfNeeded(squad, sim);
        assertNotNull(squad.currentPlan.currentStep().slotOf(leader));
        assertNotNull(squad.currentPlan.currentStep().slotOf(follower));
        assertEquals(leader, BreachAndAssault.lanceCohesionAnchor(follower, squad, sim));
        assertSame(mission, squad.assignedObjective);
    }

    @Test
    void soloControlledLanceHasNoPhantomPlanAndCanRejoin() {
        BattleSimulation sim = openSimulation();
        int id = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawn(sim, id, MechRole.BALANCED, 4);
        Squad squad = finish(sim, id, mech, 1);
        squad.setControlledMember(mech, sim);
        GoapMechBehavior.replanIfNeeded(squad, sim);
        assertNull(squad.currentPlan);
        assertNull(squad.currentGoal);
        assertTrue(Float.isFinite(squad.centroidX));
        assertTrue(Float.isFinite(squad.centroidY));
        assertEquals(1, squad.aliveMembers);
        squad.setControlledMember(0L, sim);
        GoapMechBehavior.replanIfNeeded(squad, sim);
        assertNotNull(squad.currentPlan.currentStep().slotOf(mech));
    }

    @Test
    void controlledDoctrineDoesNotSelectTheLancesGoal() {
        BattleSimulation sim = openSimulation();
        int id = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawn(sim, id, MechRole.BALANCED, 4);
        Squad squad = finish(sim, id, mech, 1);
        squad.assignedObjective = ObjectiveAssignment.attackMove(id, 24, 8);
        squad.lastSeenEnemyX = 20;
        squad.lastSeenEnemyY = 8;
        WorldState state = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
        MechRole[] roles = {MechRole.BALANCED, MechRole.ASSAULT,
                MechRole.LR_SUPPORT, MechRole.ARMORED_SUPPORT};
        Goal[] goals = {BalancedContactGoal.INSTANCE, AssaultAssignedObjectiveGoal.INSTANCE,
                OverwatchKillZoneGoal.INSTANCE, BackstopAssignedSquadGoal.INSTANCE};
        for (int i = 0; i < roles.length; i++) {
            sim.world().mechLoadout(mech).applyBattleOverride(roles[i]);
            assertTrue(goals[i].relevance(state, squad, sim) > 0f, roles[i].name());
            squad.setControlledMember(mech, sim);
            assertEquals(0f, goals[i].relevance(state, squad, sim), roles[i].name());
            squad.setControlledMember(0L, sim);
        }
    }

    @Test
    void manualSupportBodyDoesNotAnchorAutonomousAssaultOrTank() {
        BattleSimulation sim = openSimulation();
        int assaultId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long assault = spawn(sim, assaultId, MechRole.ASSAULT, 4);
        Squad assaultSquad = finish(sim, assaultId, assault, 1);
        int supportId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long support = sim.spawn(new EntitySpec("support", Faction.MARINE,
                UnitType.MARINE, 7, 8).squad(supportId));
        Squad supportSquad = finish(sim, supportId, support, 1);
        assertEquals(support, BreachAndAssault.nearestSupport(assault, assaultSquad, sim));
        assertTrue(BackstopAssignedSquad.isEligibleAnchor(supportSquad, assaultSquad, sim));
        supportSquad.setControlledMember(support, sim);
        assertEquals(0L, BreachAndAssault.nearestSupport(assault, assaultSquad, sim));
        assertFalse(BackstopAssignedSquad.isEligibleAnchor(supportSquad, assaultSquad, sim));
        assertEquals(1, supportSquad.aliveMembers);
    }

    private static long spawn(BattleSimulation sim, int id, MechRole role, int x) {
        MechVariant variant = MechVariant.BULWARK;
        long mech = sim.spawn(variant.applyTo(new EntitySpec("mech", Faction.MARINE,
                UnitType.HEAVY_MECH, x, 8).squad(id)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(role));
        return mech;
    }

    private static Squad finish(BattleSimulation sim, int id, long leader, int count) {
        Squad squad = sim.getSquad(id);
        squad.leaderId = leader;
        squad.aliveMembers = count;
        squad.originalSize = count;
        squad.centroidX = sim.world().x(leader);
        squad.centroidY = sim.world().y(leader);
        return squad;
    }

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(32, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(32, 16));
    }
}
