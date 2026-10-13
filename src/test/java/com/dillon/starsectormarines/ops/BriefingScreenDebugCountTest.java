package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BriefingScreenDebugCountTest {

    @Test
    void coldStartOpeningRecordingOnlyDescribesAnUndeliveredRig() {
        for (boolean powered : new boolean[]{false, true}) {
            ShepherdRecoveryProgress progress = new ShepherdRecoveryProgress();
            String claim = progress.missionId();
            assertTrue(progress.begin(claim, "Asharu"));
            assertEquals(18000, progress.settle(claim, "Asharu", true, 18000, 0, true));
            Mission fresh = ShepherdRecoveryCampaign.offer(progress, "Asharu");
            assertTrue(BriefingScreen.recordedBriefingApplies(fresh, progress));
            assertTrue(progress.begin(fresh.id, "Asharu"));
            assertEquals(0, progress.settleColdStart(fresh.id, "Asharu", false, 0, 0,
                    true, powered, false, false));
            Mission retry = ShepherdRecoveryCampaign.offer(progress, "Asharu");
            assertFalse(BriefingScreen.recordedBriefingApplies(retry, progress),
                    "A recorded escort order must not override the readable remaining-work briefing");
            assertFalse(retry.flavor.isBlank(), "The remaining work stays available in the written briefing");
            Mission debug = Mission.builder().id(retry.id).source(MissionSource.DEBUG).build();
            assertTrue(BriefingScreen.recordedBriefingApplies(debug, progress),
                    "A detached debug opening must not inherit campaign milestones");
        }
    }

    @Test
    void countButtonsCrossFormerLimitsAndSaturateOnlyAtIntegerBounds() {
        assertEquals(50, BriefingScreen.adjustDebugCount(40, 10));
        assertEquals(110, BriefingScreen.adjustDebugCount(100, 10));
        assertEquals(0, BriefingScreen.adjustDebugCount(0, -10));
        assertEquals(Integer.MAX_VALUE,
                BriefingScreen.adjustDebugCount(Integer.MAX_VALUE, 10));
    }
}
