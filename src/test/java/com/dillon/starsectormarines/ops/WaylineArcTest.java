package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WaylineArcTest {
    private HeadlessStrings strings;
    /** Reads the shipped copy, so what these tests pin is the text the player sees. */
    @BeforeEach void shippedCopy() throws Exception { strings = new HeadlessStrings(Path.of("mod")); }
    @AfterEach void restoreSettings() { strings.close(); }

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
        assertEquals("", WaylineArc.debrief(loss), "A defeat is a retry; Zoey says nothing");
        MissionOutcome recurring = outcome(MissionSource.RECURRING, true, 0, 0);
        MissionResolver.recordStoryCompletion(roster, recurring);
        assertEquals(WaylineArc.Stage.RELIEF, WaylineArc.stage(roster));
        assertEquals("", WaylineArc.debrief(recurring));
        MissionResolver.recordStoryCompletion(roster, outcome(MissionSource.STORY, true, 0, 0));
        assertEquals(WaylineArc.Stage.DEPOT, WaylineArc.stage(roster));
    }

    private static MissionOutcome outcome(MissionSource source, boolean victory, int alliedLosses, int losses) {
        return MissionOutcome.builder().missionId(OpeningOperationKind.RELIEF.missionId)
                .missionSource(source).victory(victory).alliesEngaged(12).alliesLost(alliedLosses)
                .marinesLost(losses).build();
    }
}
