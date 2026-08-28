package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;

/** Perspective-safe projection of one Extraction-family payload objective. */
public record ExtractionObjectiveFacts(
        String payloadId,
        String payloadName,
        ExtractionPayloadObjective.Kind kind,
        String phase,
        int sourceCellX,
        int sourceCellY,
        int egressCellX,
        int egressCellY,
        int payloadCellX,
        int payloadCellY,
        int initialElements,
        int activeElements,
        int boardedElements,
        int lostElements,
        float progress,
        boolean alarmActive,
        int alarmRaisedTick,
        int controllingSquadId,
        boolean escortPresent,
        boolean complete,
        boolean failed,
        ExtractionPayloadObjective.Failure failure) { }
