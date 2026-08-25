package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestDefenderCommandTest {

    private static final int W = 30;
    private static final int H = 60;

    @Test
    void unseenMarineDoesNotCreateThreatOrMobilization() {
        BattleSimulation sim = openSim();
        Squad left = addDefender(sim, "left", 5, 50, UnitRole.PATROL);
        addDefender(sim, "right", 25, 50, UnitRole.PATROL);
        sim.spawn(new EntitySpec("hidden", Faction.MARINE, UnitType.MARINE,
                25, 5).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command();
        command.tick(sim);

        assertEquals(0, command.frontSnapshot().track(2).knownHostileContacts());
        assertEquals(0f, command.frontSnapshot().track(2).knownHostilePressure(), 0.0001f);
        assertNull(left.assignedObjective);
    }

    @Test
    void firstContactMobilizesOnePatrolAndRetainsOneReserve() {
        BattleSimulation sim = openSim();
        Squad reporter = addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        Squad responder = addDefender(sim, "responder", 5, 48, UnitRole.PATROL);
        Squad reserve = addDefender(sim, "reserve", 5, 56, UnitRole.PATROL);
        sim.getGrid().setWalkable(5, 12, false);
        long contact = sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.postShot(new ShotEvent(contact, 5.5f, 14.5f, 5.5f, 10.5f,
                false, Faction.MARINE, 0.1f));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(reporter.hasBelievedContacts());
        assertEquals(BeliefSource.AUDIO,
                reporter.believedContacts().get(0).source());
        assertTrue(!responder.hasBelievedContacts());

        ConquestDefenderCommand command = command();
        command.tick(sim);

        assertEquals(1, command.frontSnapshot().track(0).knownHostileContacts());
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_LOCAL_CONTACT,
                command.frontSnapshot().directiveFor(reporter.id).reason());
        assertEquals(AssignmentKind.DEFEND_TRACK, responder.assignedObjective.kind());
        assertNotEquals(14, responder.assignedObjective.targetCellY(),
                "the order is a coarse defensive rally, not the observed enemy cell");
        assertNull(reserve.assignedObjective,
                "one otherwise-free mobile squad remains in reserve");
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_RESERVE_HOLD,
                command.frontSnapshot().directiveFor(reserve.id).reason());
    }

    @Test
    void framePlanningDoesNotMutateLiveSquadBeforeArbitration() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        Squad responder = addDefender(sim, "responder", 5, 48, UnitRole.PATROL);
        sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command();
        CommandTopology topology = CommandTopology.freeze(sim);
        ConquestCommandFrame frame = command.freeze(sim, topology,
                new CommandAssignmentSnapshot(Map.of()));
        CommandPlan<ConquestFrontSnapshot> plan = command.plan(frame);

        assertNull(responder.assignedObjective,
                "planning must leave the live squad unchanged until arbiter commit");
        CommandProposal response = plan.proposals().stream()
                .filter(proposal -> proposal.squadId() == responder.id)
                .findFirst().orElseThrow();
        assertEquals(CommandProposal.Action.ASSIGN, response.action());
        assertEquals(AssignmentKind.DEFEND_TRACK, response.assignment().kind());
    }

    @Test
    void garrisonNeverLeavesItsNodeForTrackContact() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        addDefender(sim, "response", 5, 48, UnitRole.PATROL);
        Squad garrison = addDefender(sim, "garrison", 5, 54, UnitRole.GARRISON);
        sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command();
        command.tick(sim);

        assertNull(garrison.assignedObjective);
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_GARRISON_HOLD,
                command.frontSnapshot().directiveFor(garrison.id).reason());
    }

    @Test
    void explicitMustHoldAssignmentOutranksSoftTrackResponse() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        Squad mustHold = addDefender(sim, "must-hold", 5, 48, UnitRole.PATROL);
        TacticalNode node = new TacticalNode(TacticalNode.Kind.GUARDPOST,
                5, 48, 4, 47, 6, 49, Faction.DEFENDER, 50, 2);
        mustHold.assignedObjective = ObjectiveAssignment.holdNode(mustHold.id, node);
        sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command();
        command.tick(sim);

        assertEquals(AssignmentKind.HOLD_NODE, mustHold.assignedObjective.kind());
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED,
                command.frontSnapshot().directiveFor(mustHold.id).reason());
    }

    @Test
    void higherAuthorityTrackOrderIsRetainedInsteadOfRewritten() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        Squad protectedSquad = addDefender(sim, "protected", 5, 48, UnitRole.PATROL);
        addDefender(sim, "response", 5, 56, UnitRole.PATROL);
        sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        ObjectiveAssignment protectedOrder = ObjectiveAssignment.defendTrack(
                protectedSquad.id, 5, 50);
        CommanderService service = new CommanderService();
        service.assignments().assignExternal(protectedSquad, protectedOrder,
                CommandAuthority.PLAYER_INTERVENTION, "test-player",
                "player-set defensive position", sim.getSimTickIndex());
        ConquestDefenderCommand command = command();
        service.setCommander(Faction.DEFENDER, command);
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertEquals(protectedOrder, protectedSquad.assignedObjective);
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_EXTERNAL_ASSIGNMENT_PRESERVED,
                command.frontSnapshot().directiveFor(protectedSquad.id).reason());
        assertEquals(CommandDirective.Status.RETAINED,
                service.snapshot(Faction.DEFENDER).directiveFor(protectedSquad.id).status());
    }

    @Test
    void expiredFactionContactReleasesCommandOwnedRally() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        Squad responder = addDefender(sim, "responder", 5, 48, UnitRole.PATROL);
        sim.spawn(new EntitySpec("contact", Faction.MARINE,
                UnitType.MARINE, 5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);
        ConquestDefenderCommand command = command();
        command.tick(sim);
        assertEquals(AssignmentKind.DEFEND_TRACK, responder.assignedObjective.kind());

        for (int x = 0; x < W; x++) sim.getGrid().setWalkable(x, 12, false);
        int expiryTicks = (int) Math.ceil(Squad.BELIEF_LIFETIME_SECONDS
                / BattleSimulation.TICK_DT) + 20;
        for (int i = 0; i < expiryTicks; i++) sim.advance(BattleSimulation.TICK_DT);
        command.tick(sim);

        assertNull(responder.assignedObjective);
        assertEquals(0, command.frontSnapshot().track(0).knownHostileContacts());
        assertNull(command.frontSnapshot().directiveFor(responder.id).assignmentKind());
    }

    @Test
    void patrolDeliveredAfterSetupIsNotAbsorbedIntoStartingReserve() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        addDefender(sim, "starting-response", 5, 48, UnitRole.PATROL);
        ConquestDefenderCommand command = command();
        command.captureStartingForce(sim);
        Squad deliveredLater = addDefender(sim, "later", 5, 30, UnitRole.PATROL);
        sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        command.tick(sim);

        assertNull(deliveredLater.assignedObjective);
        assertNull(command.frontSnapshot().directiveFor(deliveredLater.id));
    }

    @Test
    void sealedPatrolDoesNotConsumeReachableAdjacentResponseSlot() {
        BattleSimulation sim = openSim();
        addDefender(sim, "reporter", 5, 10, UnitRole.PATROL);
        Squad sealedReserve = addDefender(sim, "sealed", 5, 48, UnitRole.PATROL);
        Squad reachableReserve = addDefender(sim, "reachable", 15, 48, UnitRole.PATROL);
        Squad nonAdjacentReserve = addDefender(sim, "far-track", 25, 48, UnitRole.PATROL);
        for (int x = 3; x <= 7; x++) {
            sim.getGrid().setWalkable(x, 46, false);
            sim.getGrid().setWalkable(x, 50, false);
        }
        for (int y = 46; y <= 50; y++) {
            sim.getGrid().setWalkable(3, y, false);
            sim.getGrid().setWalkable(7, y, false);
        }
        sim.spawn(new EntitySpec("contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command();
        command.tick(sim);

        assertNull(sealedReserve.assignedObjective);
        assertEquals(AssignmentKind.DEFEND_TRACK, reachableReserve.assignedObjective.kind());
        assertEquals(ConquestFrontSnapshot.AssignmentReason.DEFENDER_ADJACENT_TRACK_RESPONSE,
                command.frontSnapshot().directiveFor(reachableReserve.id).reason());
        assertNull(nonAdjacentReserve.assignedObjective);
    }

    @Test
    void twoThreatTracksReceiveRespondersBeforeAnyTrackDoubles() {
        BattleSimulation sim = openSim();
        addDefender(sim, "left-reporter", 5, 10, UnitRole.PATROL);
        addDefender(sim, "right-reporter", 25, 10, UnitRole.PATROL);
        addDefender(sim, "left-response", 5, 48, UnitRole.PATROL);
        addDefender(sim, "right-response", 25, 48, UnitRole.PATROL);
        addDefender(sim, "center-reserve", 15, 56, UnitRole.PATROL);
        sim.spawn(new EntitySpec("left-contact", Faction.MARINE, UnitType.MARINE,
                5, 14).moveSpeed(0f).health(10_000f));
        sim.spawn(new EntitySpec("right-contact", Faction.MARINE, UnitType.MARINE,
                25, 14).moveSpeed(0f).health(10_000f));
        sim.advance(BattleSimulation.TICK_DT);

        ConquestDefenderCommand command = command();
        command.tick(sim);

        assertEquals(1, responders(command, 0));
        assertEquals(1, responders(command, 2));
    }

    private static int responders(ConquestDefenderCommand command, int track) {
        int result = 0;
        for (ConquestFrontSnapshot.SquadDirective directive
                : command.frontSnapshot().directives()) {
            if (directive.effectiveTrack() == track
                    && directive.assignmentKind() == AssignmentKind.DEFEND_TRACK) result++;
        }
        return result;
    }

    private static ConquestDefenderCommand command() {
        return new ConquestDefenderCommand(new ConquestTrackLayout(
                TraversalAxis.SOUTH_TO_NORTH, W, H));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad addDefender(BattleSimulation sim, String name,
                                     int x, int y, UnitRole role) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec(name, Faction.DEFENDER,
                UnitType.MILITIA, x, y).role(role).squad(squadId).moveSpeed(0f));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }
}
