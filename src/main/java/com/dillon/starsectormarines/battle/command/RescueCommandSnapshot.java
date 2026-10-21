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
        List<SquadIntent> squadIntents,
        HivePicture hive) {

    public enum Role {
        COHORT_ESCORT,
        LEAD_SCREEN,
        LEFT_SCREEN,
        RIGHT_SCREEN,
        REAR_SCREEN,
        SHELTER_GUARD,
        PICKUP_GUARD,
        /** Sent against a swarm bore by the hive posture ({@link StrikeDetachment}). */
        STRIKE_DETACHMENT,
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

    /**
     * The hive posture's side of the picture: the gate as read this pulse, the
     * detachment and its bore, and every strike decision made so far.
     *
     * @param gate      {@link ReadinessGate#describe()} with its inputs, or {@code null} before any pulse read it
     * @param targetKey the bore the detachment is on, or {@code null}
     */
    public record HivePicture(
            HivePosture posture,
            String gate,
            String targetKey,
            int targetCellX,
            int targetCellY,
            List<Integer> detachment,
            List<StrikeDetachment.Decision> decisions) {
        public static final HivePicture NONE = new HivePicture(HivePosture.DEFAULT, null, null, -1, -1,
                List.of(), List.of());

        public HivePicture {
            detachment = List.copyOf(detachment);
            decisions = List.copyOf(decisions);
        }
    }

    public RescueCommandSnapshot {
        squadIntents = List.copyOf(squadIntents);
        hive = hive == null ? HivePicture.NONE : hive;
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
