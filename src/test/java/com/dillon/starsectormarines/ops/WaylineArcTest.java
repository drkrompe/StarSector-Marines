package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaylineArcTest {
    @Test void oldVictoryFactsAdvanceWithoutNewMeetingFlags() {
        MarineRoster roster = new MarineRoster();
        assertEquals(WaylineArc.Stage.RELIEF, WaylineArc.stage(roster));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertEquals(WaylineArc.Stage.DEPOT, WaylineArc.stage(roster));
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertEquals(WaylineArc.Stage.COMPLETE, WaylineArc.stage(roster));
        // Legacy final victories are authoritative even if the earlier key is absent.
        MarineRoster legacy = new MarineRoster();
        legacy.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertEquals(WaylineArc.Stage.COMPLETE, WaylineArc.stage(legacy));
    }

    @Test void defeatIsRetryableAndRecurringWorkCannotWriteStoryProgress() {
        MarineRoster roster = new MarineRoster();
        MissionOutcome loss = outcome(MissionSource.STORY, false, 0, 0);
        MissionResolver.recordStoryCompletion(roster, loss);
        assertEquals(WaylineArc.Stage.RELIEF, WaylineArc.stage(roster));
        assertTrue(WaylineArc.debrief(loss).contains("try again"));
        MissionOutcome recurring = outcome(MissionSource.RECURRING, true, 0, 0);
        MissionResolver.recordStoryCompletion(roster, recurring);
        assertEquals(WaylineArc.Stage.RELIEF, WaylineArc.stage(roster));
        assertEquals("", WaylineArc.debrief(recurring));
        MissionResolver.recordStoryCompletion(roster, outcome(MissionSource.STORY, true, 0, 0));
        assertEquals(WaylineArc.Stage.DEPOT, WaylineArc.stage(roster));
    }

    @Test void casualtyAcknowledgementUsesOutcomeAndDoesNotClaimSurvivors() {
        String clean = WaylineArc.debrief(outcome(MissionSource.STORY, true, 0, 0));
        assertFalse(clean.contains("losses"));
        assertTrue(clean.contains("traffic logs"));
        String costly = WaylineArc.debrief(outcome(MissionSource.STORY, true, 12, 2));
        assertTrue(costly.contains("None of our militia came home"));
        assertTrue(costly.contains("company's losses"));
        assertTrue(WaylineArc.debrief(outcome(MissionSource.STORY, true, 3, 0)).contains("families"));
    }

    private static MissionOutcome outcome(MissionSource source, boolean victory, int alliedLosses, int losses) {
        return MissionOutcome.builder().missionId(OpeningOperationKind.RELIEF.missionId)
                .missionSource(source).victory(victory).alliesEngaged(12).alliesLost(alliedLosses)
                .marinesLost(losses).build();
    }
}
