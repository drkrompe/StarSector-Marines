package com.dillon.starsectormarines.ops;

/**
 * Stable identities for the Independent brokers' militia-support work and its
 * authored battle setups. Recurring low-tier contracts rather than one-shot
 * story rungs — see {@code OpeningOperationStory}.
 */
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
