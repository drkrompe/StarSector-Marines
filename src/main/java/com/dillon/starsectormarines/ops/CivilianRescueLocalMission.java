package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;

/** Resolves the one committed rescue mission visible at a local market. */
public final class CivilianRescueLocalMission {

    private CivilianRescueLocalMission() {}

    public static Mission find(CampaignState state, String marketId,
                               String planetName, String factionId) {
        int row = committedRow(state, marketId);
        if (row < 0) return null;
        return CivilianRescueMissionFactory.create(state,
                state.eventId[row], state.eventMarketId[row],
                planetName, factionId);
    }

    /** Newest matching committed row; terminal, pending, and remote rows are excluded. */
    public static int committedRow(CampaignState state, String marketId) {
        if (state == null || marketId == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.CIVILIAN_RESCUE) continue;
            if (CampaignEventState.fromByte(state.eventState[row])
                    != CampaignEventState.COMMITTED) continue;
            String eventMarket =
                    state.marketRegistry.get(state.eventMarketId[row]);
            if (marketId.equals(eventMarket)) return row;
        }
        return -1;
    }
}
