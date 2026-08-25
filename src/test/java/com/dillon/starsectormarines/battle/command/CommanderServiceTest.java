package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommanderServiceTest {

    @Test
    void freezesEveryPerspectiveBeforeEitherStrategyPlans() {
        BattleSimulation sim = openSim();
        Squad marine = addSquad(sim, Faction.MARINE, 2, 2);
        Squad defender = addSquad(sim, Faction.DEFENDER, 7, 7);
        Set<Faction> frozen = new HashSet<>();
        List<String> events = new ArrayList<>();
        CommanderService service = new CommanderService();
        RecordingCommand marineCommand = new RecordingCommand(
                Faction.MARINE, frozen, events, marine.id, null);
        RecordingCommand defenderCommand = new RecordingCommand(
                Faction.DEFENDER, frozen, events, defender.id, marine);
        service.setCommander(Faction.MARINE, marineCommand);
        service.setCommander(Faction.DEFENDER, defenderCommand);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertEquals(List.of("freeze-MARINE", "freeze-DEFENDER",
                "plan-MARINE", "plan-DEFENDER"), events);
        assertEquals(AssignmentKind.SUPPORT, marine.assignedObjective.kind());
        assertEquals(AssignmentKind.SUPPORT, defender.assignedObjective.kind());
        assertEquals(CommandDirective.Status.ACTIVE,
                service.snapshot(Faction.MARINE).directiveFor(marine.id).status());

        service.setCommander(Faction.MARINE, null);
        assertNull(service.snapshot(Faction.MARINE),
                "removing a commander must not leave stale diagnostics");
    }

    @Test
    void frameCopiesOnlyOwnForceAndDoesNotTrackLaterMutation() {
        BattleSimulation sim = openSim();
        Squad marine = addSquad(sim, Faction.MARINE, 2, 2);
        addSquad(sim, Faction.DEFENDER, 7, 7);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE,
                CommandTopology.freeze(sim), arbiter.snapshot());

        marine.centroidX = 9f;
        marine.assignedObjective = ObjectiveAssignment.support(marine.id);

        assertEquals(1, frame.squads().size());
        assertEquals(Faction.MARINE, frame.squads().get(0).faction());
        assertEquals(2f, frame.squads().get(0).centroidX());
        assertNull(frame.squads().get(0).assignment());
    }

    @Test
    void frameDeepCopiesNodeBearingAssignments() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        TacticalNode node = new TacticalNode(TacticalNode.Kind.GUARDPOST,
                2, 2, 1, 1, 3, 3, Faction.MARINE, 50, 1);
        node.setCompoundBounds(1, 1, 3, 3);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.holdNode(squad.id, node),
                CommandAuthority.GARRISON, "test-garrison", "fixture", 1);

        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE,
                CommandTopology.freeze(sim), arbiter.snapshot());
        node.setCompoundBounds(0, 0, 9, 9);

        TacticalNode assignmentNode = frame.squad(squad.id).assignment().targetNode();
        TacticalNode directiveNode = frame.squad(squad.id).directive()
                .assignment().targetNode();
        assertNotSame(node, assignmentNode);
        assertNotSame(node, directiveNode);
        assertEquals(1, assignmentNode.compoundLeft());
        assertEquals(3, directiveNode.compoundRight());
    }

    @Test
    void frameLedgerContainsOnlyItsOwnPerspective() {
        BattleSimulation sim = openSim();
        Squad marine = addSquad(sim, Faction.MARINE, 2, 2);
        Squad defender = addSquad(sim, Faction.DEFENDER, 7, 7);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(marine, ObjectiveAssignment.support(marine.id),
                CommandAuthority.PLAYER_INTERVENTION, "marine-owner",
                "marine order", 1);
        arbiter.assignExternal(defender, ObjectiveAssignment.support(defender.id),
                CommandAuthority.REINFORCEMENT, "defender-owner",
                "defender order", 1);

        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE,
                CommandTopology.freeze(sim), arbiter.snapshot());

        assertEquals(Set.of(marine.id), frame.assignments().directives().keySet());
        assertEquals("marine-owner",
                frame.assignments().directiveFor(marine.id).issuer());
        assertNull(frame.assignments().directiveFor(defender.id),
                "an opposing command directive is not legal frame input");
    }

    @Test
    void higherExternalOwnershipRejectsMissionReplacementWithReason() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment hold = ObjectiveAssignment.holdNode(squad.id, null);
        arbiter.assignExternal(squad, hold, CommandAuthority.GARRISON,
                "compound-garrison", "born holding", 4);
        CommandPlan<String> plan = new CommandPlan<>(Faction.MARINE,
                "test-command", "ADVANCE", 5, -1, 1, 0, List.of(),
                List.of(CommandProposal.assign(
                        ObjectiveAssignment.support(squad.id),
                        CommandAuthority.MISSION_COMMAND, "advance")), "detail");

        CommanderSnapshot<String> snapshot = arbiter.commit(plan, sim,
                CommandTopology.freeze(sim));

        CommandDirective result = snapshot.directiveFor(squad.id);
        assertEquals(CommandDirective.Status.REJECTED, result.status());
        assertTrue(result.dispositionReason().contains("compound-garrison"));
        assertEquals(hold, squad.assignedObjective);
        assertFalse(result.ownsAssignment());
    }

    @Test
    void ownershipOnlyClaimExcludesSquadWithoutInventingAnAssignment() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.claimExternal(squad, CommandAuthority.REINFORCEMENT,
                "reinforcement", "counterattack", 4);
        CommandPlan<String> plan = new CommandPlan<>(Faction.MARINE,
                "test-command", "ADVANCE", 5, -1, 1, 0, List.of(),
                List.of(CommandProposal.assign(
                        ObjectiveAssignment.support(squad.id),
                        CommandAuthority.MISSION_COMMAND, "advance")), "detail");

        CommandDirective result = arbiter.commit(plan, sim,
                CommandTopology.freeze(sim)).directiveFor(squad.id);

        CommandDirective owner = arbiter.activeDirective(squad.id);
        assertNull(squad.assignedObjective);
        assertEquals(CommandDirective.Status.REJECTED, result.status());
        assertTrue(result.dispositionReason().contains("reinforcement"));
        assertTrue(owner.ownsSquad());
        assertFalse(owner.ownsAssignment());
    }

    @Test
    void handoffRequiresTheIncumbentIssuerAndTransfersAtomically() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.claimExternal(squad, CommandAuthority.REINFORCEMENT,
                "reinforcement", "arrival", 4);
        ObjectiveAssignment next = ObjectiveAssignment.support(squad.id);

        assertFalse(arbiter.handoff(squad, "wrong-owner",
                CommandAuthority.MISSION_COMMAND, "test-command", next,
                "join mission pool", 5));
        assertNull(squad.assignedObjective);
        assertTrue(arbiter.handoff(squad, "reinforcement",
                CommandAuthority.MISSION_COMMAND, "test-command", next,
                "join mission pool", 5));

        assertEquals(next, squad.assignedObjective);
        assertEquals("test-command", arbiter.activeDirective(squad.id).issuer());
        assertEquals("handed off from reinforcement",
                arbiter.activeDirective(squad.id).dispositionReason());
    }

    @Test
    void weakerBirthClaimCannotDisplaceSpecializedOwnership() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment hold = ObjectiveAssignment.holdNode(squad.id, null);
        arbiter.assignExternal(squad, hold, CommandAuthority.GARRISON,
                "compound-garrison", "born holding", 4);

        arbiter.claimExternal(squad, CommandAuthority.REINFORCEMENT,
                "reinforcement", "late claim", 5);

        assertEquals(hold, squad.assignedObjective);
        assertEquals("compound-garrison",
                arbiter.activeDirective(squad.id).issuer());
        assertEquals(4, arbiter.activeDirective(squad.id).issuedTick());
    }

    @Test
    void wipedSquadLosesItsAssignmentAndLedgerOwnership() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.support(squad.id),
                CommandAuthority.REINFORCEMENT, "reinforcement", "arrival", 1);

        TestUnits.kill(sim, squad.leaderId);
        squad.aliveMembers = 0;
        arbiter.synchronizeCompatibilityAssignments(sim, Map.of());

        assertNull(squad.assignedObjective);
        assertNull(arbiter.activeDirective(squad.id));
    }

    private static final class RecordingCommand
            implements AutonomousMissionCommand<CommandFrame, String> {
        private final Faction faction;
        private final Set<Faction> frozen;
        private final List<String> events;
        private final int squadId;
        private final Squad mustRemainUnassigned;
        private CommanderSnapshot<String> snapshot;

        private RecordingCommand(Faction faction, Set<Faction> frozen,
                                 List<String> events, int squadId,
                                 Squad mustRemainUnassigned) {
            this.faction = faction;
            this.frozen = frozen;
            this.events = events;
            this.squadId = squadId;
            this.mustRemainUnassigned = mustRemainUnassigned;
        }

        @Override public Faction faction() { return faction; }
        @Override public String strategyId() { return "recording-" + faction; }

        @Override
        public CommandFrame freeze(BattleView sim, CommandTopology topology,
                                   CommandAssignmentSnapshot assignments) {
            events.add("freeze-" + faction);
            frozen.add(faction);
            return CommandFrame.freeze(sim, faction, topology, assignments);
        }

        @Override
        public CommandPlan<String> plan(CommandFrame frame) {
            assertEquals(Set.of(Faction.MARINE, Faction.DEFENDER), frozen);
            if (mustRemainUnassigned != null) {
                assertNull(mustRemainUnassigned.assignedObjective,
                        "all strategies must plan before the first commit");
            }
            events.add("plan-" + faction);
            return new CommandPlan<>(faction, strategyId(),
                    "ADVANCE", frame.tick(),
                    frame.influence() != null ? frame.influence().updatedTick() : -1,
                    1, 0, List.of(),
                    List.of(CommandProposal.assign(
                            ObjectiveAssignment.support(squadId),
                            CommandAuthority.MISSION_COMMAND, "test advance")),
                    "detail");
        }

        @Override
        public void publish(CommanderSnapshot<String> snapshot) {
            this.snapshot = snapshot;
        }
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(10, 10));
    }

    private static Squad addSquad(BattleSimulation sim, Faction faction,
                                  int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("unit-" + squadId, faction,
                UnitType.MARINE, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }
}
