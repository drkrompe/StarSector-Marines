package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.WithdrawAssignedGoal;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechDoctrineDispatchTest {

    @Test
    void serializedRolesHaveStablePlayerLabelsAndBalancedIsAdditive() {
        assertEquals("LR_SUPPORT", MechRole.LR_SUPPORT.name());
        assertEquals("ARMORED_SUPPORT", MechRole.ARMORED_SUPPORT.name());
        assertEquals("ASSAULT", MechRole.ASSAULT.name());
        assertEquals("Long Range Support", MechRole.LR_SUPPORT.displayName());
        assertEquals("Tank", MechRole.ARMORED_SUPPORT.displayName());
        assertEquals("Brawler", MechRole.ASSAULT.displayName());
        assertEquals("Balanced", MechRole.BALANCED.displayName());
    }

    @Test
    void centralDispatcherMapsEveryEffectiveDoctrine() {
        assertAction(MechRole.ASSAULT, BreachAndAssault.INSTANCE);
        assertAction(MechRole.ARMORED_SUPPORT, BackstopAssignedSquad.INSTANCE);
        assertAction(MechRole.LR_SUPPORT, OverwatchKillZone.INSTANCE);
        assertAction(MechRole.BALANCED, EngageAtCurrentBand.INSTANCE);
    }

    @Test
    void oneSharedRoleGoalExecutesEveryMemberDoctrineInAMixedLance() {
        BattleSimulation sim = openSimulation(64, 18);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long brawler = spawn(sim, squadId, MechVariant.HOUND,
                MechRole.ASSAULT, 10, 8);
        long tank = spawn(sim, squadId, MechVariant.BULWARK,
                MechRole.ARMORED_SUPPORT, 11, 8);
        long support = spawn(sim, squadId, MechVariant.SIROCCO,
                MechRole.LR_SUPPORT, 8, 8);
        long balanced = spawn(sim, squadId, MechVariant.BULWARK,
                MechRole.BALANCED, 12, 8);
        long contact = sim.spawn(new EntitySpec(
                "contact", Faction.DEFENDER, UnitType.MARINE, 42, 8));

        Squad squad = sim.getSquad(squadId);
        squad.leaderId = brawler;
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        squad.centroidX = 10.5f;
        squad.centroidY = 8.5f;
        squad.lastSeenEnemyX = 42;
        squad.lastSeenEnemyY = 8;
        SquadBeliefTestAccess.observeDirect(squad, contact,
                42, 8, sim.getSimTickIndex());
        sim.getUnitIndex().rebuild(sim.getRoster());

        GoapMechBehavior.replanIfNeeded(squad, sim);

        assertSame(AssaultAssignedObjectiveGoal.INSTANCE, squad.currentGoal);
        assertSame(ExecuteMechDoctrine.INSTANCE,
                squad.currentPlan.currentStep().action);
        GoapMechBehavior.INSTANCE.update(brawler, sim);
        GoapMechBehavior.INSTANCE.update(tank, sim);
        GoapMechBehavior.INSTANCE.update(support, sim);
        GoapMechBehavior.INSTANCE.update(balanced, sim);

        assertTrue(Paths.cellCount(sim.world().path(brawler)) > 0,
                "Brawler advances under the shared step");
        assertEquals(squad.id, sim.world().mechLoadout(tank).assignedSquadId,
                "Tank may anchor on its same-lance non-Tank siblings without a cycle");
        assertTrue(sim.world().mechLoadout(tank).overwatchCellX
                        > Math.floor(squad.centroidX),
                "Tank takes the threat-facing side of its supported lance");
        assertTrue(sim.world().mechLoadout(support).overwatchCellX >= 0,
                "long-range support owns an outer-band perch");
        assertTrue(Paths.cellCount(sim.world().path(balanced)) > 0,
                "Balanced closes from an LRM-capable squad's outer range into direct range");
        int balancedDestX = Paths.destX(sim.world().path(balanced));
        int balancedDestY = Paths.destY(sim.world().path(balanced));
        float dx = balancedDestX + 0.5f - sim.world().x(contact);
        float dy = balancedDestY + 0.5f - sim.world().y(contact);
        assertTrue(dx * dx + dy * dy <= Math.pow(
                sim.world().mechLoadout(balanced).mediumDirectRange(), 2));
    }

    @Test
    void freeReignRemovesTheBrawlerLeadClampForTheWholeLance() {
        int formedDestination = brawlerDestination(MechLanceOrder.FORM_ON_LEAD);
        int freeDestination = brawlerDestination(MechLanceOrder.FREE_REIGN);

        assertTrue(formedDestination <= 17,
                "formed Brawler stays within the six-cell lead of support at x=11");
        assertTrue(freeDestination >= 35,
                "free-reign Brawler closes independently on the contact");
        assertTrue(freeDestination > formedDestination + 12);
    }

    @Test
    void switchingFromFreeReignBackToFormRecallsASeparatedCloseBandBrawler() {
        BattleSimulation sim = openSimulation(64, 16);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long lead = spawn(sim, squadId, MechVariant.BULWARK,
                MechRole.BALANCED, 10, 8);
        long brawler = spawn(sim, squadId, MechVariant.HOUND,
                MechRole.ASSAULT, 30, 8);
        long contact = sim.spawn(new EntitySpec(
                "close-band-contact", Faction.DEFENDER,
                UnitType.MARINE, 32, 8));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = lead;
        squad.aliveMembers = 2;
        squad.originalSize = 2;
        squad.centroidX = 20.5f;
        squad.centroidY = 8.5f;
        int missionZone = sim.getZoneGraph().zoneIdAt(10, 8);
        squad.assignedObjective = ObjectiveAssignment.clearZone(
                squadId, missionZone);
        SquadBeliefTestAccess.observeDirect(squad, contact,
                32, 8, sim.getSimTickIndex());

        sim.getMechDoctrineService().requestLanceOrder(
                brawler, MechLanceOrder.FREE_REIGN);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);
        BreachAndAssault.INSTANCE.execute(brawler, squad, sim);
        assertTrue(Paths.isEmpty(sim.world().path(brawler)),
                "a free Brawler already in close band has no reason to move");

        sim.getMechDoctrineService().requestLanceOrder(
                brawler, MechLanceOrder.FORM_ON_LEAD);
        new MechDoctrineSystem(sim.getMechDoctrineService()).tick(sim);
        BreachAndAssault.INSTANCE.execute(brawler, squad, sim);

        assertFalse(Paths.isEmpty(sim.world().path(brawler)),
                "Form must author recall movement even when combat posture would hold");
        int destinationX = Paths.destX(sim.world().path(brawler));
        int destinationY = Paths.destY(sim.world().path(brawler));
        float dx = destinationX + 0.5f - sim.world().x(lead);
        float dy = destinationY + 0.5f - sim.world().y(lead);
        assertTrue(dx * dx + dy * dy
                        <= BreachAndAssault.MAX_SUPPORT_LEAD
                        * BreachAndAssault.MAX_SUPPORT_LEAD + 0.01f,
                "recall destination returns inside the production lead bound");
        assertEquals(missionZone, sim.getZoneGraph().zoneIdAt(
                destinationX, destinationY),
                "recall movement remains inside mission-owned ground");
    }

    @Test
    void balancedLrmChassisClosesToDirectBandButHoldsAgainstACloseThreat() {
        BattleSimulation sim = openSimulation(64, 18);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long balanced = spawn(sim, squadId, MechVariant.SIROCCO,
                MechRole.BALANCED, 10, 8);
        long contact = sim.spawn(new EntitySpec(
                "contact", Faction.DEFENDER, UnitType.MARINE, 42, 8));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = balanced;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = 10.5f;
        squad.centroidY = 8.5f;
        SquadBeliefTestAccess.observeDirect(squad, contact,
                42, 8, sim.getSimTickIndex());
        sim.world().setTargetId(balanced, contact);

        EngageAtCurrentBand.INSTANCE.execute(balanced, squad, sim);

        int[] approach = sim.world().path(balanced);
        assertTrue(Paths.cellCount(approach) > 0,
                "being inside LRM range must not satisfy Balanced direct-range posture");
        float dx = Paths.destX(approach) + 0.5f - sim.world().x(contact);
        float dy = Paths.destY(approach) + 0.5f - sim.world().y(contact);
        assertTrue(dx * dx + dy * dy <= Math.pow(
                sim.world().mechLoadout(balanced).mediumDirectRange(), 2));

        sim.world().setCellPos(contact, 12, 8);
        sim.world().setTargetId(balanced, contact);
        EngageAtCurrentBand.INSTANCE.execute(balanced, squad, sim);

        assertTrue(Paths.isEmpty(sim.world().path(balanced)),
                "Balanced stands and answers a close threat instead of following its old approach path");
    }

    @Test
    void assignedBalancedMechFightsCurrentContactThenResumesMission() {
        BattleSimulation sim = openSimulation(32, 18);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        spawn(sim, squadId, MechVariant.BULWARK, MechRole.BALANCED, 8, 8);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.assignedObjective = ObjectiveAssignment.rushObjective(
                squadId, 7, ObjectiveAssignment.UNSCOPED, 24, 8);

        WorldState contact = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
        assertSame(MechAssignedObjectiveGoal.INSTANCE,
                Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS,
                        contact, squad, sim));
        assertSame(ExecuteMechDoctrine.INSTANCE,
                MechAssignedObjectiveGoal.INSTANCE.customPlan(squad, sim)
                        .currentStep().action);

        assertSame(MechAssignedObjectiveGoal.INSTANCE,
                Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS,
                        WorldState.EMPTY, squad, sim),
                "the assignment-aware doctrine dispatcher resumes authored movement when contact clears");
    }

    @Test
    void balancedContactInterruptionYieldsToWithdrawalAndSurvival() {
        BattleSimulation sim = openSimulation(32, 18);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        spawn(sim, squadId, MechVariant.BULWARK, MechRole.BALANCED, 8, 8);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.assignedObjective = ObjectiveAssignment.withdraw(squadId, 2, 8);
        WorldState contact = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);

        assertSame(WithdrawAssignedGoal.INSTANCE,
                Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS,
                        contact, squad, sim));

        squad.assignedObjective = ObjectiveAssignment.rushObjective(
                squadId, 7, ObjectiveAssignment.UNSCOPED, 24, 8);
        WorldState brokenContact = contact.with(Predicate.MORALE_BROKEN, true);
        assertSame(MechSurviveContact.INSTANCE,
                Goal.pickMostRelevant(GoapMechBehavior.MECH_GOALS,
                        brokenContact, squad, sim));
    }

    private static void assertAction(MechRole role, Action expected) {
        assertSame(expected, ExecuteMechDoctrine.actionFor(role));
    }

    private static int brawlerDestination(MechLanceOrder order) {
        BattleSimulation sim = openSimulation(52, 16);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long brawler = spawn(sim, squadId, MechVariant.HOUND,
                MechRole.ASSAULT, 10, 8);
        spawn(sim, squadId, MechVariant.BULWARK,
                MechRole.BALANCED, 11, 8);
        long contact = sim.spawn(new EntitySpec(
                "lance-order-contact", Faction.DEFENDER,
                UnitType.MARINE, 42, 8));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = brawler;
        squad.aliveMembers = 2;
        squad.originalSize = 2;
        squad.centroidX = 10.5f;
        squad.centroidY = 8.5f;
        squad.applyLanceOrder(order);
        SquadBeliefTestAccess.observeDirect(squad, contact,
                42, 8, sim.getSimTickIndex());

        BreachAndAssault.INSTANCE.execute(brawler, squad, sim);

        assertFalse(Paths.isEmpty(sim.world().path(brawler)));
        return Paths.destX(sim.world().path(brawler));
    }

    private static long spawn(BattleSimulation sim, int squadId,
                              MechVariant variant, MechRole role,
                              int x, int y) {
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                variant.id + "-" + role.name(), Faction.MARINE,
                UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(role));
        return mech;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
