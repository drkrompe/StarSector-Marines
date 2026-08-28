package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Generic Extraction corridor picture published to all diagnostic surfaces. */
public record ExtractionCommandSnapshot(
        int tick,
        Faction perspective,
        String phase,
        String payloadId,
        String payloadName,
        int sourceCellX,
        int sourceCellY,
        int payloadCellX,
        int payloadCellY,
        int corridorGuideCellX,
        int corridorGuideCellY,
        int egressCellX,
        int egressCellY,
        float progress,
        boolean escortPresent,
        int controllingSquadId,
        boolean complete,
        boolean failed,
        ExtractionPayloadObjective.Failure failure,
        List<SquadIntent> squadIntents) {

    public enum Role {
        PAYLOAD_ELEMENT,
        CLOSE_ESCORT,
        LEAD_SCREEN,
        LEFT_SCREEN,
        RIGHT_SCREEN,
        REAR_SCREEN,
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

    public ExtractionCommandSnapshot {
        squadIntents = List.copyOf(squadIntents);
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
