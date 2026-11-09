package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidRouteTest {

    @Test
    void onlyASupportedRaidWithTheForkOnAsks() {
        assertEquals(RaidRoute.FORK, RaidRoute.choose(true, true));
        assertEquals(RaidRoute.STANDARD, RaidRoute.choose(false, true));
        assertEquals(RaidRoute.STANDARD, RaidRoute.choose(true, false));
    }

    @Test
    void aSaveAsksUntilTheSettingIsTurnedOff() {
        CampaignState state = new CampaignState();
        assertTrue(RaidForkSetting.asks(state));
        RaidForkSetting.setAsks(state, false);
        assertFalse(RaidForkSetting.asks(state));
        RaidForkSetting.setAsks(state, true);
        assertTrue(RaidForkSetting.asks(state));
        state.raidResolutionPreference = 1;
        assertTrue(RaidForkSetting.asks(state), "a retired always-lead answer asks");
    }
}
