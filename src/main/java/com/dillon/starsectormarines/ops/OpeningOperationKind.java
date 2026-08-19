package com.dillon.starsectormarines.ops;

/** Stable identities for the green-company story ladder and its battle setups. */
public enum OpeningOperationKind {
    RELIEF("story_opening_relief"),
    COUNTERATTACK("story_opening_counterattack");

    public final String missionId;

    OpeningOperationKind(String missionId) {
        this.missionId = missionId;
    }

    public static OpeningOperationKind fromMission(Mission mission) {
        if (mission == null || mission.source != MissionSource.STORY) return null;
        for (OpeningOperationKind kind : values()) {
            if (kind.missionId.equals(mission.id)) return kind;
        }
        return null;
    }
}
