package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Published Marine picture of the two Silent Colony expedition branches. */
public record SilentColonyCommandSnapshot(
        int tick,
        int influenceTick,
        Faction perspective,
        Phase phase,
        ExtractionObjectiveFacts survivors,
        ExtractionObjectiveFacts archive,
        int archiveZoneId,
        int knownPressureContacts,
        int archiveBranchSquads,
        int survivorBranchSquads,
        List<SquadIntent> squadIntents) {

    public enum Phase {
        DIVIDED_EXPEDITION,
        ARCHIVE_RECOVERY_ONLY,
        SURVIVOR_ESCORT_ONLY,
        EXPEDITION_COMPLETE
    }

    public enum Role {
        ARCHIVE_RECOVERY,
        SURVIVOR_ESCORT,
        EXTERNAL,
        STRANDED,
        RELEASED
    }

    public enum Reason {
        INITIAL_ROUTE_AND_STRENGTH,
        ARCHIVE_BRANCH_LOSS_REBALANCE,
        SURVIVOR_BRANCH_LOSS_REBALANCE,
        ARCHIVE_COMPLETE_REJOIN,
        SURVIVORS_GONE_REINFORCE_ARCHIVE,
        RECOVER_SEALED_ARCHIVE,
        REACH_COLONY_SURVIVORS,
        ESCORT_COLONY_SURVIVORS,
        EXTERNAL_OWNERSHIP_PRESERVED,
        TARGET_UNREACHABLE,
        EXPEDITION_OBJECTIVES_COMPLETE
    }

    public record SquadIntent(
            int squadId,
            Role role,
            Reason membershipReason,
            Reason assignmentReason,
            AssignmentKind assignmentKind,
            int targetCellX,
            int targetCellY,
            boolean localContact) { }

    public SilentColonyCommandSnapshot {
        squadIntents = List.copyOf(squadIntents);
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
