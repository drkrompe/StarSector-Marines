package com.dillon.starsectormarines.campaign.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidRouteTest {

    @Test
    void anUnsupportedRaidIsAlwaysStandard() {
        for (RaidResolutionPreference preference : RaidResolutionPreference.values()) {
            assertEquals(RaidRoute.STANDARD, RaidRoute.choose(preference, false));
        }
    }

    @Test
    void aSupportedRaidFollowsThePreference() {
        assertEquals(RaidRoute.FORK, RaidRoute.choose(RaidResolutionPreference.ASK, true));
        assertEquals(RaidRoute.FORK, RaidRoute.choose(null, true));
        assertEquals(RaidRoute.MARINE_OPERATIONS, RaidRoute.choose(RaidResolutionPreference.MARINE_OPERATIONS, true));
        assertEquals(RaidRoute.STANDARD, RaidRoute.choose(RaidResolutionPreference.STANDARD, true));
    }

    @Test
    void anUnreadableSavedPreferenceAsks() {
        assertEquals(RaidResolutionPreference.ASK, RaidResolutionPreference.of(-1));
        assertEquals(RaidResolutionPreference.ASK, RaidResolutionPreference.of(99));
    }
}
