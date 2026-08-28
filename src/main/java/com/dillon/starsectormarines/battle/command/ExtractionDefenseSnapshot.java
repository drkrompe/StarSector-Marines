package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Identity-free defender picture for generic Extraction source interdiction. */
public record ExtractionDefenseSnapshot(
        int tick,
        Faction perspective,
        Phase phase,
        String payloadId,
        String payloadName,
        int sourceCellX,
        int sourceCellY,
        boolean alarmActive,
        int alarmRaisedTick,
        boolean complete,
        boolean failed,
        ExtractionPayloadObjective.Failure failure,
        int knownContactCount,
        int freshestContactTick,
        int mobilePool,
        int reserveCount,
        List<SquadIntent> squadIntents) {

    public enum Phase {
        ROUTINE_SECURITY,
        ALARM_INTERDICTION,
        TERMINAL
    }

    public enum Role {
        SOURCE_GUARD,
        ALARM_RESPONDER,
        INTERDICTION,
        RESERVE,
        AUTHORED_POST,
        EXTERNAL,
        STRANDED,
        RELEASED
    }

    public record SquadIntent(
            int squadId,
            Role role,
            String reason,
            AssignmentKind assignmentKind,
            int targetCellX,
            int targetCellY,
            boolean localContact) { }

    public ExtractionDefenseSnapshot {
        squadIntents = List.copyOf(squadIntents);
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
