package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;

/** Stable story and recurring identities sharing the opening tactical setups. */
public enum OpeningOperationKind {
    RELIEF("story_opening_relief", "openingKindReliefName", "repeatable_militia_relief", "openingKindReliefRecurringName", 6_000),
    COUNTERATTACK("story_opening_counterattack", "openingKindCounterattackName", "repeatable_depot_clearance", "openingKindCounterattackRecurringName", 9_000);

    public final String missionId;

    private final String displayNameKey;
    public final String recurringId;
    private final String recurringNameKey;
    public final int payout;

    OpeningOperationKind(String missionId, String displayNameKey, String recurringId, String recurringNameKey, int payout) {
        this.missionId = missionId;
        this.displayNameKey = displayNameKey;
        this.recurringId = recurringId;
        this.recurringNameKey = recurringNameKey;
        this.payout = payout;
    }

    public String displayName() { return Strings.get(displayNameKey); }

    public String recurringName() { return Strings.get(recurringNameKey); }

    public static OpeningOperationKind fromMission(Mission mission) {
        if (mission == null) return null;
        for (OpeningOperationKind kind : values()) {
            if (mission.source == MissionSource.STORY && kind.missionId.equals(mission.id)) return kind;
            if (mission.source == MissionSource.RECURRING && kind.recurringId.equals(mission.id)) return kind;
        }
        return null;
    }
}
