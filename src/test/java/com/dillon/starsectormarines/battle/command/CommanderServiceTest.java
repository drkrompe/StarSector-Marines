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
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommanderServiceTest {

    @Test
    void autonomousStrategiesExposeNoLiveBattleViewContract() {
        assertFalse(MissionCommand.class.isAssignableFrom(
                AutonomousMissionCommand.class));
        for (Class<?> type : List.of(AutonomousMissionCommand.class,
                ConquestCommand.class, ConquestDefenderCommand.class)) {
            for (var method : type.getDeclaredMethods()) {
                assertFalse(method.getReturnType() == BattleView.class
                                || List.of(method.getParameterTypes())
                                .contains(BattleView.class),
                        type.getSimpleName() + "." + method.getName()
                                + " must not expose BattleView");
            }
        }
    }

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
        service.setAutonomousCommander(Faction.MARINE, marineCommand,
                recordingDisclosure(Faction.MARINE, frozen, events));
        service.setAutonomousCommander(Faction.DEFENDER, defenderCommand,
                recordingDisclosure(Faction.DEFENDER, frozen, events));

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
    void registrationOrderDoesNotChangePairedCommandPulse() {
        PairedResult marineFirst = runPairedPulse(false);
        PairedResult defenderFirst = runPairedPulse(true);

        assertEquals(marineFirst, defenderFirst);
    }

    @Test
    void pairedConquestCommandsPublishEmptySnapshotsForEmptyPools() {
        BattleSimulation sim = openSim();
        ConquestTrackLayout tracks = new ConquestTrackLayout(
                TraversalAxis.SOUTH_TO_NORTH, 10, 10);
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE,
                new ConquestCommand(tracks), ConquestCommandDisclosure.INSTANCE);
        service.setAutonomousCommander(Faction.DEFENDER,
                new ConquestDefenderCommand(tracks),
                ConquestCommandDisclosure.INSTANCE);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        for (Faction faction : List.of(Faction.MARINE, Faction.DEFENDER)) {
            CommanderSnapshot<?> snapshot = service.snapshot(faction);
            assertNotNull(snapshot);
            assertEquals(0, snapshot.commandPoolSize());
            assertEquals(0, snapshot.reserveCount());
            assertTrue(snapshot.directives().isEmpty());
            assertEquals(faction, snapshot.perspective());
        }
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

    @Test
    void malformedTargetsAreRejectedWithoutDisturbingTheIncumbent() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE, 2, 2);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        ObjectiveAssignment incumbent = ObjectiveAssignment.support(squad.id);
        arbiter.assignExternal(squad, incumbent, CommandAuthority.MISSION_COMMAND,
                "test-command", "valid incumbent", 1);
        List<ObjectiveAssignment> malformed = List.of(
                ObjectiveAssignment.clearZone(squad.id, -1),
                ObjectiveAssignment.secureCompound(squad.id, -1, null),
                ObjectiveAssignment.defendTrack(squad.id, -1, -1),
                ObjectiveAssignment.sweepSector(squad.id, -1, -1),
                ObjectiveAssignment.escort(squad.id, -1, -1),
                ObjectiveAssignment.rushObjective(squad.id, -1, -1),
                ObjectiveAssignment.holdNode(squad.id, null),
                new ObjectiveAssignment(squad.id, null, -1, null,
                        -1, -1, -1),
                ObjectiveAssignment.defendTrack(squad.id, 99, 99));

        for (ObjectiveAssignment assignment : malformed) {
            CommandPlan<String> plan = new CommandPlan<>(Faction.MARINE,
                    "test-command", "ADVANCE", 2, -1, 1, 0, List.of(),
                    List.of(CommandProposal.assign(assignment,
                            CommandAuthority.MISSION_COMMAND, "malformed")),
                    "detail");
            CommandDirective result = arbiter.commit(plan, sim,
                    CommandTopology.freeze(sim)).directiveFor(squad.id);

            assertEquals(CommandDirective.Status.REJECTED, result.status(),
                    assignment.toString());
            assertEquals(incumbent, squad.assignedObjective);
            assertEquals(incumbent,
                    arbiter.activeDirective(squad.id).assignment());
        }
    }

    private static PairedResult runPairedPulse(boolean reverseRegistration) {
        BattleSimulation sim = openSim();
        Squad marine = addSquad(sim, Faction.MARINE, 2, 2);
        Squad defender = addSquad(sim, Faction.DEFENDER, 7, 7);
        Set<Faction> frozen = new HashSet<>();
        List<String> events = new ArrayList<>();
        RecordingCommand marineCommand = new RecordingCommand(
                Faction.MARINE, frozen, events, marine.id, null);
        RecordingCommand defenderCommand = new RecordingCommand(
                Faction.DEFENDER, frozen, events, defender.id, marine);
        CommanderService service = new CommanderService();
        if (reverseRegistration) {
            service.setAutonomousCommander(Faction.DEFENDER, defenderCommand,
                    recordingDisclosure(Faction.DEFENDER, frozen, events));
            service.setAutonomousCommander(Faction.MARINE, marineCommand,
                    recordingDisclosure(Faction.MARINE, frozen, events));
        } else {
            service.setAutonomousCommander(Faction.MARINE, marineCommand,
                    recordingDisclosure(Faction.MARINE, frozen, events));
            service.setAutonomousCommander(Faction.DEFENDER, defenderCommand,
                    recordingDisclosure(Faction.DEFENDER, frozen, events));
        }

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
        return new PairedResult(marine.assignedObjective,
                defender.assignedObjective, service.snapshot(Faction.MARINE),
                service.snapshot(Faction.DEFENDER), List.copyOf(events));
    }

    private record PairedResult(ObjectiveAssignment marineAssignment,
                                ObjectiveAssignment defenderAssignment,
                                CommanderSnapshot<?> marineSnapshot,
                                CommanderSnapshot<?> defenderSnapshot,
                                List<String> events) { }

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

    private static CommandFrameDisclosure<CommandFrame> recordingDisclosure(
            Faction faction, Set<Faction> frozen, List<String> events) {
        return (sim, perspective, topology, assignments) -> {
            assertEquals(faction, perspective);
            events.add("freeze-" + faction);
            frozen.add(faction);
            return CommandFrame.freeze(sim, faction, topology, assignments);
        };
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
