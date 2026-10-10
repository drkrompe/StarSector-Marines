package com.dillon.starsectormarines.ops;

/**
 * Stable identities for the Independent brokers' militia-support work and its
 * authored battle setups. Recurring low-tier contracts rather than one-shot
 * story rungs — see {@code OpeningOperationStory}.
 */
public enum OpeningOperationKind {
    RELIEF("story_opening_relief", "Hold Until Relieved", 6_000),
    COUNTERATTACK("story_opening_counterattack", "Take Back the Depot", 9_000);

    public final String missionId;

    public final String displayName;
    public final int payout;

    OpeningOperationKind(String missionId, String displayName, int payout) {
        this.missionId = missionId;
        this.displayName = displayName;
        this.payout = payout;
    }

    public static OpeningOperationKind fromMission(Mission mission) {
        if (mission == null || mission.source != MissionSource.STORY) return null;
        for (OpeningOperationKind kind : values()) {
            if (kind.missionId.equals(mission.id)) return kind;
        }
        return null;
    }
}
