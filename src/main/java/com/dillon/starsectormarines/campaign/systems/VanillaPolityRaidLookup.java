package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;

/** Reads the live sector through the shared raid readers. */
public final class VanillaPolityRaidLookup implements PolityRaidLookup {

    @Override
    public String attackerFactionId(CampaignState state, long eventKey) {
        if (state == null || eventKey == 0L) return null;
        return PolityRaidLookup.attackerIn(
                state, VanillaRaidGarrisonSystem.readLiveThreats(state), eventKey);
    }
}
