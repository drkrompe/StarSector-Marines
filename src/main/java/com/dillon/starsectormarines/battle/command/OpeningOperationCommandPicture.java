package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.OpeningOperationKind;

import java.util.List;

/** Scenario-specific explanation layered over the common command snapshot. */
public record OpeningOperationCommandPicture(
        int tick,
        int influenceTick,
        Faction perspective,
        OpeningOperationKind kind,
        Phase phase,
        String placeId,
        String placeName,
        int placeCellX,
        int placeCellY,
        int placeZoneId,
        List<SquadIntent> squadIntents) {

    public enum Phase {
        PRESERVE_RELIEF_ANCHOR,
        ASSAULT_RELIEF_ANCHOR,
        SECURE_BANDIT_DEPOT,
        HOLD_DISPATCH,
        RETAKE_DISPATCH,
        DEFEND_BANDIT_DEPOT
    }

    public enum Role {
        AUTHORED_POST,
        PRESERVE_ELEMENT,
        ASSAULT_ELEMENT,
        SECURE_ELEMENT,
        DEPOT_GUARD,
        EXTERNAL,
        UNASSIGNED
    }

    public enum Reason {
        AUTHORED_POST_PRESERVED,
        EXTERNAL_OWNERSHIP_PRESERVED,
        RELIEF_ANCHOR_PRESERVE,
        RELIEF_ANCHOR_ASSAULT,
        BANDIT_DEPOT_SECURE,
        BANDIT_DEPOT_DEFEND,
        DEPOT_WAIT_FOR_COMPANY,
        DEPOT_SCREEN_COMPANY,
        DEPOT_APPROACH_GATE,
        DEPOT_CROSS_YARD,
        DEPOT_GATE_GUARD,
        DEPOT_YARD_GUARD,
        DEPOT_REAR_GUARD,
        DEPOT_RESERVE_COMMITTED,
        DEPOT_FALLBACK,
        DEPOT_HOLD,
        DEPOT_RETAKE,
        OBJECTIVE_UNREACHABLE
    }

    public record SquadIntent(
            int squadId,
            Role role,
            Reason reason,
            AssignmentKind assignmentKind,
            int targetCellX,
            int targetCellY) { }

    public OpeningOperationCommandPicture {
        squadIntents = List.copyOf(squadIntents);
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
