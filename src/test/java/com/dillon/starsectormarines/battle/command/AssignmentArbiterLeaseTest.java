package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lease is bounded external authority that hands control back, and every
 * question here is about the handing back rather than the taking. Taking a
 * squad is what any assignment writer does; what makes this a lease is that the
 * directive it displaced is <em>held</em> and put back, so an order can end
 * without leaving the squad unowned for a tick.
 */
class AssignmentArbiterLeaseTest {

    private static final String PLAYER = "player";
    private static final String COMMANDER = "test-command";

    @Test
    void aLeaseShelvesTheIncumbentAndOwnsTheSquadWhileItStands() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        arbiter.assignExternal(squad, mission, CommandAuthority.MISSION_COMMAND,
                COMMANDER, "zone push", 0);

        ObjectiveAssignment order = ObjectiveAssignment.attackMove(squad.id, 4, 4);
        CommandDirective leased = arbiter.lease(squad, order, PLAYER,
                "attack move to 4,4", 10, 100);

        assertEquals(CommandAuthority.PLAYER_INTERVENTION, leased.authority());
        assertEquals(PLAYER, leased.issuer());
        assertEquals(100, leased.leaseUntilTick());
        assertEquals("leased over " + COMMANDER, leased.dispositionReason());
        assertSame(leased, arbiter.activeDirective(squad.id));
        assertEquals(order, squad.assignedObjective);
        assertEquals(CommandAuthority.PLAYER_INTERVENTION, squad.assignedAuthority,
                "the mirror has to move with the field, or a reader cannot tell"
                        + " a player's order from a commander's of the same kind");
        assertEquals(mission, arbiter.shelvedDirective(squad.id).assignment(),
                "the displaced directive is held, not discarded");
    }

    @Test
    void endingALeaseRestoresWhatItShelved() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        arbiter.assignExternal(squad, mission, CommandAuthority.MISSION_COMMAND,
                COMMANDER, "zone push", 0);
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);

        assertTrue(arbiter.endLease(squad, PLAYER, "arrived", 50));

        CommandDirective resumed = arbiter.activeDirective(squad.id);
        assertEquals(COMMANDER, resumed.issuer());
        assertEquals(CommandAuthority.MISSION_COMMAND, resumed.authority());
        assertEquals(CommandDirective.Status.ACTIVE, resumed.status());
        assertEquals("resumed after lease: arrived", resumed.dispositionReason());
        assertEquals(mission, squad.assignedObjective);
        assertEquals(CommandAuthority.MISSION_COMMAND, squad.assignedAuthority);
        assertNull(arbiter.shelvedDirective(squad.id),
                "the shelf is empty once what was on it has been put back");
        assertFalse(arbiter.endLease(squad, PLAYER, "arrived again", 51),
                "there is no second lease to end");
    }

    @Test
    void reissuingKeepsTheOriginalMissionOnTheShelf() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        arbiter.assignExternal(squad, mission, CommandAuthority.MISSION_COMMAND,
                COMMANDER, "zone push", 0);
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);

        ObjectiveAssignment second = ObjectiveAssignment.attackMove(squad.id, 6, 6);
        CommandDirective reissued = arbiter.lease(squad, second, PLAYER,
                "attack move to 6,6", 20, 110);

        assertEquals("leased over " + PLAYER, reissued.dispositionReason());
        assertEquals(second, squad.assignedObjective);
        assertEquals(mission, arbiter.shelvedDirective(squad.id).assignment(),
                "a player changing their mind must not shelve their own previous"
                        + " order as the mission underneath");

        arbiter.endLease(squad, PLAYER, "arrived", 60);
        assertEquals(mission, squad.assignedObjective);
    }

    @Test
    void onlyAnExpiredLeaseExpires() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment mission = ObjectiveAssignment.clearZone(squad.id, 7);
        arbiter.assignExternal(squad, mission, CommandAuthority.MISSION_COMMAND,
                COMMANDER, "zone push", 0);
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);

        assertFalse(arbiter.expireLease(squad, 100),
                "the bound is the last tick the lease may stand, not the first"
                        + " tick it may not");
        assertTrue(arbiter.expireLease(squad, 101));
        assertEquals(mission, squad.assignedObjective);
        assertEquals("resumed after lease: lease expired",
                arbiter.activeDirective(squad.id).dispositionReason());

        arbiter.lease(squad, ObjectiveAssignment.defendArea(squad.id, 4, 4, 20),
                PLAYER, "defend area", 200, -1);
        assertFalse(arbiter.expireLease(squad, 100_000),
                "an unbounded lease ends when its holder says so and not before");
    }

    @Test
    void aCommanderWriteDuringALeaseIsShelvedRatherThanLost() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.clearZone(squad.id, 7),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "zone push", 0);
        ObjectiveAssignment order = ObjectiveAssignment.attackMove(squad.id, 4, 4);
        arbiter.lease(squad, order, PLAYER, "attack move to 4,4", 10, 100);

        ObjectiveAssignment rethink = ObjectiveAssignment.clearZone(squad.id, 9);
        squad.assignedObjective = rethink;
        arbiter.synchronizeCompatibilityAssignments(sim, Map.of());

        assertEquals(order, squad.assignedObjective,
                "the lease still owns what the squad executes");
        assertEquals(CommandAuthority.PLAYER_INTERVENTION, squad.assignedAuthority);
        assertEquals(rethink, arbiter.shelvedDirective(squad.id).assignment(),
                "a squad hands back to what the commander wants now, not to the"
                        + " intent that stood when the player clicked");

        arbiter.endLease(squad, PLAYER, "arrived", 50);
        assertEquals(rethink, squad.assignedObjective);
    }

    @Test
    void aCommandProposalDuringALeaseIsRejectedNamingTheLease() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.clearZone(squad.id, 7),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "zone push", 0);
        ObjectiveAssignment order = ObjectiveAssignment.attackMove(squad.id, 4, 4);
        arbiter.lease(squad, order, PLAYER, "attack move to 4,4", 0, 100);

        CommandDirective refused = commit(arbiter, sim, CommandProposal.assign(
                ObjectiveAssignment.clearZone(squad.id, 9),
                CommandAuthority.MISSION_COMMAND, "retask"));

        assertEquals(CommandDirective.Status.REJECTED, refused.status());
        assertEquals("leased by player until tick 100", refused.dispositionReason(),
                "the tick the commander may have the squad back is the fact a"
                        + " reader of the rejection wants");
        assertEquals(order, squad.assignedObjective);
    }

    @Test
    void endingALeaseOnAnUnownedSquadLeavesItUnassigned() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);

        assertEquals("leased an unowned squad",
                arbiter.activeDirective(squad.id).dispositionReason());
        assertNull(arbiter.shelvedDirective(squad.id));

        assertTrue(arbiter.endLease(squad, PLAYER, "arrived", 50));
        assertNull(arbiter.activeDirective(squad.id));
        assertNull(squad.assignedObjective);
        assertNull(squad.assignedAuthority);
    }

    /**
     * The same rule at handback rather than at pulse cadence. An order can end
     * between two command pulses, and a hard withdrawal written straight onto a
     * leased squad in that window was once restored away — leaving the squad
     * resuming the task it held before anybody asked it to leave.
     */
    @Test
    void aWriteBetweenPulsesIsAdoptedAtHandbackRatherThanRestoredAway() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.clearZone(squad.id, 7),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "zone push", 0);
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);

        ObjectiveAssignment pullOut = ObjectiveAssignment.withdraw(squad.id, 0, 5);
        squad.assignedObjective = pullOut;
        arbiter.endLease(squad, PLAYER, "withdrawing", 20);

        assertEquals(pullOut, squad.assignedObjective);
        assertEquals(pullOut, arbiter.activeDirective(squad.id).assignment());
        assertNull(arbiter.shelvedDirective(squad.id));
    }

    /**
     * A hard withdrawal is the one order a lease does not outlast. Everything
     * else the player may issue is a place to be, and a bounded interval of the
     * commander not getting its way about that is what a lease is for.
     */
    @Test
    void aWithdrawalProposalIsNotBlockedByALease() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.clearZone(squad.id, 7),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "zone push", 0);
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 0, 100);

        ObjectiveAssignment pullOut = ObjectiveAssignment.withdraw(squad.id, 1, 1);
        CommandDirective committed = commit(arbiter, sim,
                CommandProposal.assign(pullOut, CommandAuthority.MISSION_COMMAND,
                        "target secured"));

        assertEquals(CommandDirective.Status.ACTIVE, committed.status());
        assertEquals(pullOut, squad.assignedObjective);
        assertEquals(CommandAuthority.MISSION_COMMAND, squad.assignedAuthority);
        assertNull(arbiter.shelvedDirective(squad.id),
                "a shelf held for a handback that will never come is a mission"
                        + " waiting to be restored over the squad's new owner");
    }

    /**
     * An assignment that arrived as a direct write is adopted onto the shelf,
     * so a squad whose mission was never registered still has one to resume.
     * Most scenes and half the missions still write that field directly, and a
     * lease that quietly ate their order would look exactly like the order path
     * working.
     */
    @Test
    void anUnregisteredMissionIsAdoptedOntoTheShelf() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment mission = ObjectiveAssignment.attackMove(squad.id, 8, 8);
        squad.assignedObjective = mission;

        arbiter.lease(squad, ObjectiveAssignment.attackMove(squad.id, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);
        assertEquals(mission, arbiter.shelvedDirective(squad.id).assignment());

        arbiter.endLease(squad, PLAYER, "arrived", 50);
        assertEquals(mission, squad.assignedObjective);
    }

    @Test
    void aDeadSquadClearsItsLeaseAndItsShelf() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.clearZone(squadId, 7),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "zone push", 0);
        arbiter.lease(squad, ObjectiveAssignment.attackMove(squadId, 4, 4),
                PLAYER, "attack move to 4,4", 10, 100);
        assertNotNull(arbiter.shelvedDirective(squadId));

        arbiter.synchronizeCompatibilityAssignments(sim, Map.of());

        assertNull(arbiter.activeDirective(squadId));
        assertNull(arbiter.shelvedDirective(squadId),
                "a shelf outliving its squad would restore a mission onto"
                        + " whichever squad reused the id");
        assertNull(squad.assignedObjective);
        assertNull(squad.assignedAuthority);
    }

    private static CommandDirective commit(AssignmentArbiter arbiter,
                                           BattleSimulation sim,
                                           CommandProposal proposal) {
        CommandPlan<String> plan = new CommandPlan<>(Faction.MARINE, COMMANDER,
                "ADVANCE", sim.getSimTickIndex(), -1, 1, 0, List.of(),
                List.of(proposal), "detail");
        return arbiter.commit(plan, sim, CommandTopology.freeze(sim))
                .directiveFor(proposal.squadId());
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(10, 10));
    }

    private static Squad addSquad(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("unit-" + squadId, Faction.MARINE,
                UnitType.MARINE, 2, 2).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = 2;
        squad.centroidY = 2;
        return squad;
    }
}
