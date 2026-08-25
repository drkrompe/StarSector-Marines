package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAssessmentComposerTest {

    @AfterEach
    void resetVoice() {
        CommsOfficerVoice.loadForTest(null);
    }

    @Test
    void assessmentUsesCompanyFactsAndIsStableForTheDay() {
        inject("{rating}|{ready}/{strength}|{squads}|{wounded}|{stationed}");

        String first = CompanyAssessmentComposer.render(OfficerMood.SEASONED, 40,
                CompanyRating.ESTABLISHED, 31, 36, 3, 2, 12);
        String second = CompanyAssessmentComposer.render(OfficerMood.SEASONED, 40,
                CompanyRating.ESTABLISHED, 31, 36, 3, 2, 12);

        assertEquals(first, second);
        assertEquals("ESTABLISHED|31/36|3|2|12", first);
        assertFalse(first.contains("{"));
    }

    @Test
    void nullPresentationBandsFallBackSafely() {
        inject("{rating} {ready}");

        String rendered = CompanyAssessmentComposer.render(null, 1, null,
                0, 0, 0, 0, 0);

        assertTrue(rendered.startsWith("UNPROVEN"));
    }

    private static void inject(String assessment) {
        Map<OfficerMood, CommsOfficerVoice.Frame> frames =
                new EnumMap<>(OfficerMood.class);
        for (OfficerMood mood : OfficerMood.values()) {
            frames.put(mood, new CommsOfficerVoice.Frame(
                    new String[] { "prefix" }, new String[] { "suffix" },
                    new CommsOfficerVoice.Summary(
                            new String[] { "overview" }, new String[] { "client" }),
                    new String[] { assessment }));
        }
        CommsOfficerVoice.loadForTest(frames);
    }
}
