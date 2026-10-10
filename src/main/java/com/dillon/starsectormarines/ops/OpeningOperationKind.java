package com.dillon.starsectormarines.ops;

/** Stable story and recurring identities sharing the opening tactical setups. */
public enum OpeningOperationKind {
    RELIEF("story_opening_relief", "Hold Until Relieved", "repeatable_militia_relief", "Militia Relief", 6_000),
    COUNTERATTACK("story_opening_counterattack", "Take Back the Depot", "repeatable_depot_clearance", "Depot Clearance", 9_000);

    public final String missionId;

    public final String displayName;
    public final String recurringId;
    public final String recurringName;
    public final int payout;

    OpeningOperationKind(String missionId, String displayName, String recurringId, String recurringName, int payout) {
        this.missionId = missionId;
        this.displayName = displayName;
        this.recurringId = recurringId;
        this.recurringName = recurringName;
        this.payout = payout;
    }

    public static OpeningOperationKind fromMission(Mission mission) {
        if (mission == null) return null;
        for (OpeningOperationKind kind : values()) {
            if (mission.source == MissionSource.STORY && kind.missionId.equals(mission.id)) return kind;
            if (mission.source == MissionSource.RECURRING && kind.recurringId.equals(mission.id)) return kind;
        }
        return null;
    }
}
