package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;

/** Pure lookup for the one open stationing offer a patron may expose locally. */
public final class StationingOfferLookup {

    private StationingOfferLookup() {}

    public static long find(CampaignState state, long patronId, int marketId) {
        if (state == null || patronId < 0L || marketId < 0) return -1L;
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractPatronHouseId[i] != patronId
                    || state.contractMarketId[i] != marketId
                    || ContractState.fromByte(state.contractState[i]) != ContractState.OFFERED) {
                continue;
            }
            ContractType type = ContractType.fromByte(state.contractType[i]);
            if (type.isStationing()) return state.contractId[i];
        }
        return -1L;
    }
}
