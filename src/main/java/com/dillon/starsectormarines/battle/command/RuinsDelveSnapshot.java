package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * The delve commander's picture, published to diagnostics and the squad-activity rows.
 *
 * @param stage        where the delve is
 * @param layer        the layers the lead has cleared, {@code 0} to {@code layers}
 * @param waypointX    the cell the company's body holds or moves to, or {@code -1}
 * @param leadSquadId  the delve's lead element, or {@code -1}
 * @param detourCacheId the cache a detour is recovering, or {@code null}
 */
public record RuinsDelveSnapshot(
        int tick,
        Faction perspective,
        Stage stage,
        String phase,
        int layer,
        int layers,
        int waypointX,
        int waypointY,
        int leadSquadId,
        String detourCacheId,
        List<SquadIntent> squadIntents) {

    public enum Stage { DELVE, BREACH, HAUL, DONE }

    public enum Role {
        DELVE_LEAD,
        DELVE_BODY,
        CACHE_DETOUR,
        VAULT_HOLD,
        VAULT_SCREEN,
        COURT_HOLD,
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

    public record SquadIntent(int squadId, Role role, String reason, AssignmentKind assignmentKind,
                              int targetCellX, int targetCellY, boolean localContact) { }

    public RuinsDelveSnapshot {
        squadIntents = List.copyOf(squadIntents);
    }

    public SquadIntent intentFor(int squadId) {
        for (SquadIntent intent : squadIntents) {
            if (intent.squadId() == squadId) return intent;
        }
        return null;
    }
}
