package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import com.dillon.starsectormarines.campaign.CampaignState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsRouteTest {

    @Test
    void onlyASupportedSiteWithTheForkOnAsks() {
        assertEquals(RuinsRoute.FORK, RuinsRoute.choose(true, true));
        assertEquals(RuinsRoute.STANDARD, RuinsRoute.choose(false, true));
        assertEquals(RuinsRoute.STANDARD, RuinsRoute.choose(true, false));
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
    void aSaveAsksUntilTheSettingIsTurnedOff() {
        CampaignState state = new CampaignState();
        assertTrue(RuinsForkSetting.asks(state));
        RuinsForkSetting.setAsks(state, false);
        assertFalse(RuinsForkSetting.asks(state));
        state.ruinsExpeditionPreference = 1;
        assertTrue(RuinsForkSetting.asks(state), "a retired always-send-the-company answer asks");
    }
}
