package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MarineRoster;

/** The depot's first successful recovery settles before its chapter-completion fact. */
public final class WaylineHoundAward {
    private WaylineHoundAward() {}

    public static boolean eligible(MissionOutcome outcome) {
        return outcome != null && outcome.victory && outcome.waylineHound != null
                && outcome.missionSource == MissionSource.STORY
                && OpeningOperationKind.COUNTERATTACK.missionId.equals(outcome.missionId);
    }

    static boolean settle(MarineRoster roster, MissionOutcome outcome) {
        if (roster == null || !eligible(outcome) || roster.hasCompletedStory(outcome.missionId)) return false;
        var fit = WaylineHound.deployment();
        CampaignMech mech = CampaignMech.fromDeployment(WaylineHound.ID, fit);
        return roster.mechBay().receiveRecoveredMech(mech);
    }
}
