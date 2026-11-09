package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsRouteTest {

    @Test
    void anUnsupportedSiteAlwaysGetsTheSalvageTeam() {
        for (RuinsExpeditionPreference preference : RuinsExpeditionPreference.values()) {
            assertEquals(RuinsRoute.STANDARD, RuinsRoute.choose(preference, false));
        }
    }

    @Test
    void aSupportedSiteFollowsThePreference() {
        assertEquals(RuinsRoute.FORK, RuinsRoute.choose(RuinsExpeditionPreference.ASK, true));
        assertEquals(RuinsRoute.FORK, RuinsRoute.choose(null, true));
        assertEquals(RuinsRoute.MARINE_OPERATIONS,
                RuinsRoute.choose(RuinsExpeditionPreference.MARINE_OPERATIONS, true));
        assertEquals(RuinsRoute.STANDARD, RuinsRoute.choose(RuinsExpeditionPreference.STANDARD, true));
    }

    @Test
    void onlyGroundRuinsOnAnUncolonizedWorldAreSupported() {
        assertTrue(RuinsRoute.supported(RuinsGrade.VAST, false, true));
        assertFalse(RuinsRoute.supported(null, false, true));
        assertFalse(RuinsRoute.supported(RuinsGrade.VAST, true, true), "gas-giant platforms");
        assertFalse(RuinsRoute.supported(RuinsGrade.VAST, false, false), "a colony's ruins");
    }

    @Test
    void theGradeIsTheLargestRuinsConditionPresent() {
        assertNull(RuinsGrade.of(List.of("hot", "no_atmosphere")));
        assertEquals(RuinsGrade.SCATTERED, RuinsGrade.of(List.of("hot", "ruins_scattered")));
        assertEquals(RuinsGrade.VAST, RuinsGrade.of(List.of("ruins_vast", "ruins_scattered")));
    }

    @Test
    void anUnreadableSavedPreferenceAsks() {
        assertEquals(RuinsExpeditionPreference.ASK, RuinsExpeditionPreference.of(-1));
        assertEquals(RuinsExpeditionPreference.ASK, RuinsExpeditionPreference.of(99));
    }
}
