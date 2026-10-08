package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConquestGroupCommandTest {
    private static final CommandGroupIdentity OFFICER =
            new CommandGroupIdentity("officer:1", "1", "Captain Vale", true);

    @Test void keepsUsefulTaskAndRetasksWhenItEnds() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var first = command.plan(0, List.of(member(1, 0, 4), member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertEquals(4, first.decisions().get(2).assignment().targetZoneId());
        var retained = command.plan(75, List.of(member(1, 0, 7), member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertEquals(4, retained.decisions().get(1).assignment().targetZoneId());
        assertEquals("GROUP_TASK_PRESERVED", retained.groups().get(0).reason());
        var completed = command.plan(150, List.of(member(1, 0, 7), member(2, 4, 7)),
                (state, target) -> target.targetZoneId() == 7, CommandSquadState::centroidX);
        assertEquals(7, completed.decisions().get(2).assignment().targetZoneId());
        assertEquals("GROUP_TASK_SELECTED", completed.groups().get(0).reason());
    }

    @Test void boundsTheLeadButTimeoutCannotRearmUntilTheBodyRegroups() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        List<ConquestGroupCommand.Member> stretched =
                List.of(member(1, 0, 4), member(2, 3, 4), member(3, 35, 4));
        var waiting = command.plan(0, stretched, (state, target) -> true,
                CommandSquadState::centroidX);
        assertEquals("GROUP_BOUNDED_RALLY", waiting.decisions().get(3).reason());
        assertEquals(35, waiting.decisions().get(3).assignment().targetCellX());
        assertTrue(waiting.groups().get(0).rallying());
        for (int tick : new int[]{300, 375, 1200}) {
            var released = command.plan(tick, stretched, (state, target) -> true,
                    CommandSquadState::centroidX);
            assertEquals(AssignmentKind.CLEAR_ZONE, released.decisions().get(3).assignment().kind());
            assertFalse(released.groups().get(0).rallying());
        }
        command.invalidateZoneTargets();
        assertFalse(command.plan(1225,
                List.of(member(1, 0, 7), member(2, 3, 7), member(3, 35, 7)),
                (state, target) -> true, CommandSquadState::centroidX).groups().get(0).rallying());
        command.plan(1275, List.of(member(1, 30, 4), member(2, 32, 4), member(3, 35, 4)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertTrue(command.plan(1350, stretched, (state, target) -> true,
                CommandSquadState::centroidX).groups().get(0).rallying());
    }

    @Test void missingRosterAndDistantLateArrivalsDoNotBlockTheLandedBody() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var result = command.plan(0, List.of(member(1, 0, 4), member(2, 65, 4), member(3, 70, 4)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertFalse(result.groups().get(0).rallying());
        assertEquals(List.of(1, 2, 3), result.groups().get(0).squadIds());
    }

    @Test void holdsRemainTaskedWhileTheSameGroupAssaults() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var hold = new ConquestGroupCommand.Member(state(1, 0, false, OFFICER),
                ObjectiveAssignment.defendArea(1, 0, 0, 20), false);
        var result = command.plan(0, List.of(hold, member(2, 5, 4)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertFalse(result.decisions().containsKey(1));
        assertEquals("MIXED", result.groups().get(0).task());
        assertEquals(4, result.decisions().get(2).assignment().targetZoneId());
    }

    @Test void contactAndCasualtyStateNeverCauseAQuietGroupWait() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var contact = new ConquestGroupCommand.Member(state(3, 35, true, OFFICER),
                ObjectiveAssignment.clearZone(3, 4), true);
        var result = command.plan(0, List.of(member(1, 0, 4), member(2, 3, 4), contact),
                (state, target) -> true, CommandSquadState::centroidX);
        assertFalse(result.groups().get(0).rallying());
        assertEquals(AssignmentKind.CLEAR_ZONE, result.decisions().get(3).assignment().kind());
    }

    @Test void actingNcoAndLegacyFixturesKeepExistingAutonomy() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var nco = new ConquestGroupCommand.Member(state(1, 0, false,
                new CommandGroupIdentity("nco:1", null, "Acting NCO", false)),
                ObjectiveAssignment.clearZone(1, 4), true);
        var legacy = new ConquestGroupCommand.Member(state(2, 35, false, null),
                ObjectiveAssignment.clearZone(2, 7), true);
        var result = command.plan(0, List.of(nco, legacy), (state, target) -> true,
                CommandSquadState::centroidX);
        assertTrue(result.decisions().isEmpty());
        assertEquals(1, result.groups().size());
        assertEquals("ACTING_NCO_AUTONOMY", result.groups().get(0).reason());
    }

    @Test void supportDoesNotIncreaseTheCaptureQuota() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var capture = new ConquestGroupCommand.Member(state(1, 0, false, OFFICER),
                ObjectiveAssignment.secureCompound(1, 4, null), true);
        var result = command.plan(0, List.of(capture, member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertFalse(result.decisions().containsKey(1));
        assertEquals(AssignmentKind.CLEAR_ZONE, result.decisions().get(2).assignment().kind());
        assertEquals(4, result.decisions().get(2).assignment().targetZoneId());
    }

    @Test void retainedCompoundIntentRebindsBeforeSupportIsPublished() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var capture = new ConquestGroupCommand.Member(state(1, 0, false, OFFICER),
                ObjectiveAssignment.secureCompound(1, 4, null), true);
        command.plan(0, List.of(capture, member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX);
        var rebound = command.plan(75, List.of(member(1, 0, 7), member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX,
                target -> ObjectiveAssignment.secureCompound(target.squadId(), 9, null),
                state -> true);
        assertEquals(9, rebound.decisions().get(2).assignment().targetZoneId());
        assertEquals(9, rebound.groups().get(0).targetZoneId());
    }

    @Test void topologyPublicationChangeInvalidatesTransientClearZoneIdentity() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        command.plan(0, List.of(member(1, 0, 4), member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX);
        command.invalidateZoneTargets();
        // The old numeric address still looks legal, but it no longer names the same place.
        var retasked = command.plan(75, List.of(member(1, 0, 7), member(2, 4, 7)),
                (state, target) -> true, CommandSquadState::centroidX);
        assertEquals(7, retasked.decisions().get(2).assignment().targetZoneId());
        assertEquals("GROUP_TASK_SELECTED", retasked.groups().get(0).reason());
    }

    @Test void illegalAnchorCannotReceiveARallyOrder() {
        ConquestGroupCommand command = new ConquestGroupCommand();
        var result = command.plan(0, List.of(member(1, 0, 4), member(2, 3, 4), member(3, 35, 4)),
                (state, target) -> true, CommandSquadState::centroidX,
                target -> target, state -> state.squadId() != 3);
        assertEquals(AssignmentKind.CLEAR_ZONE, result.decisions().get(3).assignment().kind());
        assertFalse(result.groups().get(0).rallying());
    }

    private static ConquestGroupCommand.Member member(int id, int x, int zone) {
        return new ConquestGroupCommand.Member(state(id, x, false, OFFICER),
                ObjectiveAssignment.clearZone(id, zone), true);
    }

    private static CommandSquadState state(int id, int x, boolean contact,
                                           CommandGroupIdentity identity) {
        return new CommandSquadState(id, Faction.MARINE, 6, x, 0, x, 0,
                0, UnitRole.COMBATANT, contact, false, false, null, null, null,
                null, null, 0, 0, -1, 0, 0, null, null, null, 0,
                new int[0], new int[0], new int[0], identity);
    }
}
