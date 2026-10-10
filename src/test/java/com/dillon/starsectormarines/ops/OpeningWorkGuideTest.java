package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningWorkGuideTest {
    @Test void aFreshCompanyGetsDirectionsAndPreparationWithoutCreatingPersonnel() {
        MarineRoster roster = new MarineRoster();
        OpeningWorkGuide guide = OpeningWorkGuide.from(roster);
        assertTrue(guide.visible());
        assertEquals(OpeningOperationKind.RELIEF, guide.kind());
        assertTrue(guide.preparation().contains("first squad"));
        assertTrue(roster.squads().isEmpty());
        assertFalse(roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId));
    }

    @Test void guidanceAdvancesOnlyWithTheExistingCompletionFacts() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(12);
        assertFalse(OpeningWorkGuide.from(roster).preparation().contains("first squad"));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        OpeningWorkGuide followup = OpeningWorkGuide.from(roster);
        assertTrue(followup.visible());
        assertEquals(OpeningOperationKind.COUNTERATTACK, followup.kind());
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertFalse(OpeningWorkGuide.from(roster).visible());
        assertFalse(OpeningWorkGuide.from(null).visible());
    }
}
