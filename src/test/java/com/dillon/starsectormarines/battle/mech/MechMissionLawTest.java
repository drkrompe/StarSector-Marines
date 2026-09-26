package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AttackMoveGoal;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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

import java.util.function.IntFunction;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechMissionLawTest {

    @Test
    void attackMoveFightsOnForwardProgressAndResumesExactCellAfterContact() {
        BattleSimulation sim = openSimulation(64, 14);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long brawler = spawn(sim, squadId, MechRole.ASSAULT, 10, 7);
        Squad squad = finishSquad(sim, squadId, brawler, 1, 10, 7);
        squad.assignedObjective = ObjectiveAssignment.attackMove(squadId, 50, 7);
        long contact = sim.spawn(new EntitySpec(
                "route-contact", Faction.DEFENDER, UnitType.MARINE, 40, 7)
                .health(10_000f));
        SquadBeliefTestAccess.observeDirect(squad, contact,
                40, 7, sim.getSimTickIndex());

        GoapMechBehavior.replanIfNeeded(squad, sim);
        assertSame(AttackMoveGoal.INSTANCE, squad.currentGoal);
        assertSame(ExecuteMechDoctrine.INSTANCE,
                squad.currentPlan.currentStep().action);
        GoapMechBehavior.INSTANCE.update(brawler, sim);
        int localDest = Paths.destX(sim.world().path(brawler));
        assertTrue(localDest >= 30 && localDest < 50,
                "ATTACK_MOVE may fight contact that lies along its forward progress");

        sim.world().setCellPos(brawler, localDest, 7);
        sim.clearPath(brawler);
        sim.world().setCellPos(contact, 2, 2);
        SquadBeliefTestAccess.forgetAll(squad, sim.getSimTickIndex());
        GoapMechBehavior.INSTANCE.update(brawler, sim);
        assertEquals(50, Paths.destX(sim.world().path(brawler)),
                "when contact clears, ATTACK_MOVE resumes its exact destination");
    }

    @Test
    void freeReignRushMustReachExactCellBeforeUsingItsLocalTacticalLeash() {
        BattleSimulation sim = openSimulation(64, 14);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long brawler = spawn(sim, squadId, MechRole.ASSAULT, 10, 7);
        Squad squad = finishSquad(sim, squadId, brawler, 1, 10, 7);
        squad.applyLanceOrder(MechLanceOrder.FREE_REIGN);
        squad.assignedObjective = ObjectiveAssignment.rushObjective(
                squadId, 3, ObjectiveAssignment.UNSCOPED, 50, 7);
        long contact = sim.spawn(new EntitySpec(
                "objective-contact", Faction.DEFENDER, UnitType.MARINE,
                40, 7).health(10_000f));
        SquadBeliefTestAccess.observeDirect(squad, contact,
                40, 7, sim.getSimTickIndex());

        GoapMechBehavior.replanIfNeeded(squad, sim);
        GoapMechBehavior.INSTANCE.update(brawler, sim);
        assertEquals(50, Paths.destX(sim.world().path(brawler)),
                "RUSH must service the interaction cell before target standoff is legal");

        sim.world().setCellPos(brawler, 50, 7);
        sim.clearPath(brawler);
        GoapMechBehavior.INSTANCE.update(brawler, sim);
        assertTrue(Paths.destX(sim.world().path(brawler)) < 50,
                "after arrival, the local ten-cell doctrine leash becomes available");
    }

    @Test
    void reissuedSameCellAfterCommandGapMustBeServicedAgain() {
        BattleSimulation sim = openSimulation(64, 14);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long brawler = spawn(sim, squadId, MechRole.ASSAULT, 50, 7);
        Squad squad = finishSquad(sim, squadId, brawler, 1, 50, 7);
        squad.assignedObjective = ObjectiveAssignment.rushObjective(
                squadId, 3, ObjectiveAssignment.UNSCOPED, 50, 7);
        long contact = sim.spawn(new EntitySpec(
                "same-cell-contact", Faction.DEFENDER, UnitType.MARINE,
                42, 7).health(10_000f));
        SquadBeliefTestAccess.observeDirect(squad, contact,
                42, 7, sim.getSimTickIndex());

        ExecuteMechDoctrine.INSTANCE.execute(brawler, squad, sim);
        assertTrue(sim.world().mechLoadout(brawler).assignmentBoundaryReached);

        squad.assignedObjective = null;
        ExecuteMechDoctrine.INSTANCE.execute(brawler, squad, sim);
        assertFalse(sim.world().mechLoadout(brawler).assignmentBoundaryReached,
                "observing command release invalidates the old arrival latch");

        squad.assignedObjective = ObjectiveAssignment.rushObjective(
                squadId, 3, ObjectiveAssignment.UNSCOPED, 50, 7);
        sim.world().setCellPos(brawler, 10, 7);
        sim.clearPath(brawler);
        ExecuteMechDoctrine.INSTANCE.execute(brawler, squad, sim);

        assertEquals(50, Paths.destX(sim.world().path(brawler)),
                "the reissued order must reach its cell before regaining local freedom");
        assertFalse(sim.world().mechLoadout(brawler).assignmentBoundaryReached);
    }

    @Test
    void exactRushDefendAndAdvanceRemainRepresentedForEveryMixedLanceMember() {
        assertMixedExactCommand(id -> ObjectiveAssignment.rushObjective(
                id, 7, ObjectiveAssignment.UNSCOPED, 50, 7));
        assertMixedExactCommand(id -> ObjectiveAssignment.defendTrack(id, 50, 7));
        assertMixedExactCommand(id -> ObjectiveAssignment.advanceTrack(id, 50, 7));
        TacticalNode node = new TacticalNode(TacticalNode.Kind.GATE,
                51, 7, 50, 6, 52, 8, Faction.MARINE, 70, 2,
                List.of(new TacticalNode.StandPosition(50, 7)));
        assertMixedExactCommand(id -> ObjectiveAssignment.holdNode(id, node));
    }

    @Test
    void zoneCommandMovesEveryMixedLanceMemberWithinTheCommandBoundary() {
        BattleSimulation sim = twoRoomSimulation();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long tank = spawn(sim, squadId, MechRole.ARMORED_SUPPORT, 2, 5);
        long balanced = spawn(sim, squadId, MechRole.BALANCED, 3, 5);
        Squad squad = finishSquad(sim, squadId, balanced, 2, 2, 5);
        int targetZone = sim.getZoneGraph().zoneIdAt(9, 5);
        squad.assignedObjective = ObjectiveAssignment.clearZone(squadId, targetZone);

        GoapMechBehavior.replanIfNeeded(squad, sim);
        assertSame(MechAssignedObjectiveGoal.INSTANCE, squad.currentGoal);
        GoapMechBehavior.INSTANCE.update(tank, sim);
        GoapMechBehavior.INSTANCE.update(balanced, sim);

        assertFalse(Paths.isEmpty(sim.world().path(tank)));
        assertFalse(Paths.isEmpty(sim.world().path(balanced)));
        assertEquals(targetZone, sim.getZoneGraph().zoneIdAt(
                Paths.destX(sim.world().path(tank)),
                Paths.destY(sim.world().path(tank))));
        assertEquals(targetZone, sim.getZoneGraph().zoneIdAt(
                Paths.destX(sim.world().path(balanced)),
                Paths.destY(sim.world().path(balanced))));
    }

    @Test
    void mobileDoctrinesCannotReverseAttackMoveProgressForRemoteContact() {
        for (MechRole role : new MechRole[]{
                MechRole.ASSAULT, MechRole.BALANCED, MechRole.LR_SUPPORT}) {
            BattleSimulation sim = openSimulation(64, 14);
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
            long mech = spawn(sim, squadId, role, 20, 7);
            Squad squad = finishSquad(sim, squadId, mech, 1, 20, 7);
            squad.assignedObjective = ObjectiveAssignment.attackMove(
                    squadId, 55, 7);
            long remote = sim.spawn(new EntitySpec(
                    "remote-" + role, Faction.DEFENDER, UnitType.MARINE,
                    5, 7));
            SquadBeliefTestAccess.observeDirect(squad, remote,
                    5, 7, sim.getSimTickIndex());

            GoapMechBehavior.replanIfNeeded(squad, sim);
            assertSame(AttackMoveGoal.INSTANCE, squad.currentGoal);
            GoapMechBehavior.INSTANCE.update(mech, sim);

            assertFalse(Paths.isEmpty(sim.world().path(mech)), role.name());
            assertTrue(Paths.destX(sim.world().path(mech)) > 20,
                    role + " must keep moving forward rather than chase the remote rear contact");
        }
    }

    private static void assertMixedExactCommand(
            IntFunction<ObjectiveAssignment> assignment) {
        BattleSimulation sim = openSimulation(64, 14);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long tank = spawn(sim, squadId, MechRole.ARMORED_SUPPORT, 8, 7);
        long balanced = spawn(sim, squadId, MechRole.BALANCED, 9, 7);
        Squad squad = finishSquad(sim, squadId, balanced, 2, 8, 7);
        squad.assignedObjective = assignment.apply(squadId);

        GoapMechBehavior.replanIfNeeded(squad, sim);
        assertSame(MechAssignedObjectiveGoal.INSTANCE, squad.currentGoal);
        GoapMechBehavior.INSTANCE.update(tank, sim);
        GoapMechBehavior.INSTANCE.update(balanced, sim);

        assertEquals(50, Paths.destX(sim.world().path(tank)),
                "a Tank with a legal same-lance anchor still obeys the exact command");
        assertEquals(50, Paths.destX(sim.world().path(balanced)),
                "the other member keeps the exact command represented");
    }

    private static long spawn(BattleSimulation sim, int squadId,
                              MechRole role, int x, int y) {
        MechVariant variant = switch (role) {
            case ASSAULT -> MechVariant.HOUND;
            case LR_SUPPORT -> MechVariant.SIROCCO;
            default -> MechVariant.BULWARK;
        };
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                role.name(), Faction.MARINE, UnitType.HEAVY_MECH, x, y)
                .squad(squadId)));
        sim.world().attachMechLoadout(mech, variant.createLoadout(role));
        return mech;
    }

    private static Squad finishSquad(BattleSimulation sim, int squadId,
                                     long leader, int alive, int x, int y) {
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = leader;
        squad.aliveMembers = alive;
        squad.originalSize = alive;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation twoRoomSimulation() {
        int width = 12;
        int height = 10;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x != 6) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(6, 5);
        grid.setDoorway(6, 5, true);
        // The mission law needs a passage the Bulwark's 1.2-cell body can use.
        grid.setWalkableFloor(6, 6);
        grid.setDoorway(6, 6, true);
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
