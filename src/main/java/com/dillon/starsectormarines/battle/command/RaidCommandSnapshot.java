package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Mission-specific Raid state published for dumps, overlays, and traces. */
public record RaidCommandSnapshot(
        int tick,
        Faction perspective,
        String phase,
        String targetId,
        String targetName,
        int targetCellX,
        int targetCellY,
        int targetZoneId,
        int egressCellX,
        int egressCellY,
        float serviceProgress,
        float serviceDuration,
        boolean targetSecured,
        boolean alarmActive,
        int alarmRaisedTick,
        List<SquadIntent> squadIntents) {

    public record SquadIntent(int squadId, String role, String reason,
                              AssignmentKind assignmentKind,
                              int targetCellX, int targetCellY) { }

    public RaidCommandSnapshot {
        squadIntents = List.copyOf(squadIntents);
    }
}
