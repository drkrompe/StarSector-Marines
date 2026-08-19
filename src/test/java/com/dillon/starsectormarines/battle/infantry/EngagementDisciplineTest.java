package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story 24 target-release, held-overwatch, and hard pursuit-leash coverage. */
class EngagementDisciplineTest {

    private record Fixture(BattleSimulation sim, Squad squad, long member,
                           long rejected, long buddyA, long buddyB) {}

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(50, 20);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static Faction opposing(Faction faction) {
        return faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
    }

    private static Fixture clusteredRunner(Faction pursuingFaction, boolean hideRunner) {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(pursuingFaction, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 2;
        squad.originalSize = 2;
        squad.centroidX = 5.5f;
        squad.centroidY = 6f;

        long member = sim.spawn(new EntitySpec("pursuer", pursuingFaction,
                UnitType.MARINE, 5, 5).squad(squadId));
        sim.world().setAttackRange(member, 8f);
        sim.spawn(new EntitySpec("wing", pursuingFaction,
                UnitType.MARINE, 5, 6).squad(squadId));
        Faction targetFaction = opposing(pursuingFaction);
        long rejected = sim.spawn(new EntitySpec("runner", targetFaction,
                UnitType.MARINE, 25, 5));
        long buddyA = sim.spawn(new EntitySpec("buddy-a", targetFaction,
                UnitType.MARINE, 25, 6));
        long buddyB = sim.spawn(new EntitySpec("buddy-b", targetFaction,
                UnitType.MARINE, 26, 5));
        if (hideRunner) {
            for (int y = 0; y < sim.getGrid().getHeight(); y++) {
                sim.getGrid().setWalkable(15, y, false);
            }
        }
        sim.world().setTargetId(member, rejected);
        sim.setPath(member, new int[]{5, 5, 6, 5, 7, 5});
        return new Fixture(sim, squad, member, rejected, buddyA, buddyB);
    }

    @Test
    void marineAndDefenderBothDropHiddenRunnerIntoCluster() {
        assertClusteredRunnerHold(Faction.MARINE);
        assertClusteredRunnerHold(Faction.DEFENDER);
    }

    private static void assertClusteredRunnerHold(Faction faction) {
        Fixture f = clusteredRunner(faction, true);

        assertEquals(ActionStatus.FAILURE,
                ApproachPosture.INSTANCE.execute(f.member, f.squad, f.sim));
        assertTrue(f.squad.engagementDisciplineHold);
        assertEquals(f.rejected, f.squad.engagementDisciplineTargetId);
        assertEquals(2, f.squad.engagementDisciplineThreatDensity);
        assertEquals(0L, f.sim.targetOf(f.member));
        assertTrue(Paths.isEmpty(f.sim.world().path(f.member)));
    }

    @Test
    void visibleOutOfRangeFormationIsHeldButInRangeFormationCanBeFiredOn() {
        Fixture distant = clusteredRunner(Faction.MARINE, false);
        assertEquals(0L, EngagementDiscipline.targetForPursuit(
                distant.member, distant.squad, distant.sim));
        assertTrue(distant.squad.engagementDisciplineHold,
                "visibility does not authorize movement into a formation");

        Fixture close = clusteredRunner(Faction.MARINE, false);
        close.sim.world().setCellPos(close.rejected, 9, 5);
        close.sim.world().setCellPos(close.buddyA, 9, 6);
        close.sim.world().setCellPos(close.buddyB, 10, 5);
        close.sim.getUnitIndex().rebuild(close.sim.getRoster());

        assertEquals(close.rejected, EngagementDiscipline.targetForPursuit(
                close.member, close.squad, close.sim));
        assertFalse(close.squad.engagementDisciplineHold,
                "shooting from the current line is legal because no pursuit is needed");
    }

    @Test
    void isolatedVisibleAlternativeReplacesRunnerWithoutEnteringHold() {
        Fixture f = clusteredRunner(Faction.MARINE, true);
        long alternative = f.sim.spawn(new EntitySpec("isolated", Faction.DEFENDER,
                UnitType.MARINE, 10, 10));

        assertEquals(alternative, EngagementDiscipline.targetForPursuit(
                f.member, f.squad, f.sim));
        assertEquals(alternative, f.sim.targetOf(f.member));
        assertFalse(f.squad.engagementDisciplineHold);
    }

    @Test
    void holdSelectsOverwatchAndStillPermitsFireFromCurrentLine() {
        Fixture f = clusteredRunner(Faction.MARINE, true);
        EngagementDiscipline.targetForPursuit(f.member, f.squad, f.sim);
        WorldState state = WorldStateBuilder.build(f.squad, f.sim);

        assertTrue(state.get(Predicate.THREAT_DENSITY_HIGH_AT_TARGET));
        Goal picked = Goal.pickMostRelevant(
                List.of(EliminateEnemiesGoal.INSTANCE,
                        HoldEngagementLineGoal.INSTANCE), state, f.squad, f.sim);
        assertSame(HoldEngagementLineGoal.INSTANCE, picked);
        SquadPlan plan = picked.customPlan(f.squad, f.sim);
        assertSame(OverwatchPosture.INSTANCE, plan.steps().get(0).action);
        assertTrue(OverwatchPosture.INSTANCE.permitsOpportunityFire());

        // Put the rejected formation inside weapon range without releasing
        // the already-latched hold: Overwatch remains planted but the normal
        // opportunity-fire pass can legally shoot from here.
        f.sim.world().setCellPos(f.rejected, 9, 5);
        f.sim.world().setCellPos(f.buddyA, 9, 6);
        f.sim.world().setCellPos(f.buddyB, 10, 5);
        f.sim.getUnitIndex().rebuild(f.sim.getRoster());
        f.sim.setPath(f.member, new int[]{5, 5, 6, 5});

        assertEquals(ActionStatus.RUNNING,
                OverwatchPosture.INSTANCE.execute(f.member, f.squad, f.sim));
        assertTrue(Paths.isEmpty(f.sim.world().path(f.member)));
        assertTrue(InfantryUnitPrep.tryOpportunityPrimary(f.member, f.sim));
    }

    @Test
    void clusterDispersalAndRejectedTargetDeathReleaseHold() {
        Fixture dispersed = clusteredRunner(Faction.MARINE, true);
        EngagementDiscipline.targetForPursuit(
                dispersed.member, dispersed.squad, dispersed.sim);
        TestUnits.kill(dispersed.sim, dispersed.buddyA);
        TestUnits.kill(dispersed.sim, dispersed.buddyB);
        dispersed.sim.getUnitIndex().rebuild(dispersed.sim.getRoster());

        assertEquals(ActionStatus.FAILURE, OverwatchPosture.INSTANCE.execute(
                dispersed.member, dispersed.squad, dispersed.sim));
        assertFalse(dispersed.squad.engagementDisciplineHold);

        Fixture dead = clusteredRunner(Faction.DEFENDER, true);
        EngagementDiscipline.targetForPursuit(dead.member, dead.squad, dead.sim);
        TestUnits.kill(dead.sim, dead.rejected);
        assertEquals(ActionStatus.FAILURE,
                OverwatchPosture.INSTANCE.execute(dead.member, dead.squad, dead.sim));
        assertFalse(dead.squad.engagementDisciplineHold);
    }

    @Test
    void genericPursuitPathIsClippedToSquadCentroidLeash() {
        Squad squad = new Squad(7, Faction.MARINE);
        squad.aliveMembers = 4;
        squad.centroidX = 5.5f;
        squad.centroidY = 5.5f;
        int[] path = new int[21 * 2];
        for (int i = 0; i < 21; i++) {
            path[i * 2] = 5 + i;
            path[i * 2 + 1] = 5;
        }

        int[] clipped = InfantryCohesion.clampPursuitPath(path, squad);

        assertEquals(17, Paths.destX(clipped));
        assertEquals(5, Paths.destY(clipped));
        for (int i = 0; i < Paths.cellCount(clipped); i++) {
            float dx = Paths.cellX(clipped, i) + 0.5f - squad.centroidX;
            float dy = Paths.cellY(clipped, i) + 0.5f - squad.centroidY;
            assertTrue(dx * dx + dy * dy
                    <= InfantryCohesion.COHESION_RADIUS * InfantryCohesion.COHESION_RADIUS);
        }
    }

    @Test
    void approachAndEngageBothApplyTheHardPursuitLeash() {
        assertPosturePathClipped(ApproachPosture.INSTANCE);
        assertPosturePathClipped(EngagePosture.INSTANCE);
    }

    private static void assertPosturePathClipped(Action posture) {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 2;
        squad.centroidX = 5.5f;
        squad.centroidY = 5.5f;
        long member = sim.spawn(new EntitySpec("member", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        sim.world().setAttackRange(member, 8f);
        sim.spawn(new EntitySpec("wing", Faction.MARINE,
                UnitType.MARINE, 5, 6).squad(squadId));
        long target = sim.spawn(new EntitySpec("isolated", Faction.DEFENDER,
                UnitType.MARINE, 30, 5));
        sim.world().setTargetId(member, target);

        assertEquals(ActionStatus.RUNNING, posture.execute(member, squad, sim));
        int[] path = sim.world().path(member);
        assertFalse(Paths.isEmpty(path));
        float dx = Paths.destX(path) + 0.5f - squad.centroidX;
        float dy = Paths.destY(path) + 0.5f - squad.centroidY;
        assertTrue(dx * dx + dy * dy
                <= InfantryCohesion.COHESION_RADIUS * InfantryCohesion.COHESION_RADIUS,
                posture.name() + " must not author a destination beyond the leash");
        assertTrue(Paths.destX(path) < 22,
                "the unclipped open-field firing position would lie beyond the leash");
    }
}
