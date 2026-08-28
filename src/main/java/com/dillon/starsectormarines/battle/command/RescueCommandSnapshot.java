package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Rescue-specific Marine corridor picture shared by UI, dumps, and traces. */
public record RescueCommandSnapshot(
        int tick,
        Faction perspective,
        String phase,
        String payloadId,
        String payloadName,
        int shelterCellX,
        int shelterCellY,
        int cohortCellX,
        int cohortCellY,
        int corridorGuideCellX,
        int corridorGuideCellY,
        int liftCellX,
        int liftCellY,
        int initialCivilians,
        int activeCivilians,
        int boardedCivilians,
        int lostCivilians,
        float progress,
        boolean escortPresent,
        int controllingSquadId,
        int knownPressureContacts,
        boolean complete,
        boolean failed,
        ExtractionPayloadObjective.Failure failure,
        List<SquadIntent> squadIntents) {

    public enum Role {
        COHORT_ESCORT,
        LEAD_SCREEN,
        LEFT_SCREEN,
        RIGHT_SCREEN,
        REAR_SCREEN,
        SHELTER_GUARD,
        PICKUP_GUARD,
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
            boolean localContact,
            boolean locallySlowed) { }

    public RescueCommandSnapshot {
        squadIntents = List.copyOf(squadIntents);
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
