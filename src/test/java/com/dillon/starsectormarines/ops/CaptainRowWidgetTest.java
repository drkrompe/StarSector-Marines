package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Trait;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CaptainRowWidgetTest {

    @Test
    void detailsShowRankAloneBeforeOutlookResolves() {
        MarineCaptain captain = captain();

        assertEquals("Corporal", CaptainRowWidget.detailsText(captain));
    }

    @Test
    void detailsShowReadableOutlookWithoutMoralArithmetic() {
        MarineCaptain captain = captain();
        captain.resolveMoralOutlook(Trait.IDEALIST, 120);

        String details = CaptainRowWidget.detailsText(captain);

        assertEquals("Corporal · Idealist", details);
        assertFalse(details.contains("mercy"));
        assertFalse(details.contains("+"));
        assertFalse(details.matches(".*\\d.*"));
    }

    @Test
    void allTraitIdentitiesHaveReadableNames() {
        for (Trait trait : Trait.values()) {
            assertFalse(trait.displayName().isBlank(), trait.name());
            assertFalse(trait.displayName().contains("_"), trait.name());
        }
    }

    private static MarineCaptain captain() {
        return new MarineCaptain("Display Captain", null, Rank.CORPORAL, 0f);
    }
}
