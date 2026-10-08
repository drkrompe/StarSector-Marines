package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BriefingComparisonTest {
    @Test
    void previewsShowValidCommandGroupsWithoutInventedLaneAssignments() {
        Map<String, Object> props = BriefingViewModel.campaignPreviewProps(true);
        List<?> cards = (List<?>) props.get("taskForceRows");
        assertEquals(4, cards.size());
        for (Object value : cards) {
            BriefingViewModel.ForceCard card = (BriefingViewModel.ForceCard) value;
            assertTrue(card.name().startsWith("Colonel"));
            assertFalse(card.detail().contains("lane"));
        }
        BriefingViewModel.ForceCard nco = (BriefingViewModel.ForceCard)
                ((List<?>) BriefingViewModel.actingNcoPreviewProps().get("taskForceRows")).get(0);
        assertFalse(nco.namedOfficer());
    }

    @Test
    void scaleAndRiskRemainIndependent() {
        Map<String, Object> props = BriefingViewModel.baseProps();
        BriefingViewModel.putForceComparison(props, OperationTier.FULL_STRENGTH, RiskLevel.LOW, 12, 24);
        assertEquals(5, lit(props, "scaleIndicators"));
        assertEquals(1, lit(props, "riskIndicators"));
        assertEquals(5, lit(props, "forceSegments"));
        assertEquals("Below recommended strength", props.get("forceAssessment"));
    }

    @Test
    void meterCapsAtRecommendationWithoutHidingExcessPersonnel() {
        Map<String, Object> props = BriefingViewModel.baseProps();
        BriefingViewModel.putForceComparison(props, OperationTier.FIRST_CONTRACT, RiskLevel.HIGH, 36, 12);
        assertEquals(10, lit(props, "forceSegments"));
        assertEquals("36", props.get("forceSelected"));
        assertEquals("12 recommended", props.get("forceRecommended"));
        assertEquals("Recommendation met", props.get("forceAssessment"));
    }

    @Test
    void emptySelectionDoesNotLightAnySegments() {
        Map<String, Object> props = BriefingViewModel.baseProps();
        BriefingViewModel.putForceComparison(props, OperationTier.FIRST_CONTRACT, RiskLevel.LOW, 0, 12);
        assertEquals(0, lit(props, "forceSegments"));
    }

    private static long lit(Map<String, Object> props, String name) {
        return ((List<?>) props.get(name)).stream().map(BriefingViewModel.Indicator.class::cast)
                .filter(pip -> pip.classes().contains(" lit")).count();
    }
}
