package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;

/** Pure lookup for stationing offers and active assignments exposed locally. */
public final class StationingOfferLookup {

    private StationingOfferLookup() {}

    public static long find(CampaignState state, long patronId, int marketId) {
        return findInState(state, patronId, marketId, ContractState.OFFERED);
    }

    public static long findActive(CampaignState state, long patronId, int marketId) {
        return findInState(state, patronId, marketId, ContractState.ACTIVE);
    }

    private static long findInState(CampaignState state, long patronId, int marketId,
                                    ContractState expectedState) {
        if (state == null || patronId < 0L || marketId < 0) return -1L;
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractPatronHouseId[i] != patronId
                    || state.contractMarketId[i] != marketId
                    || ContractState.fromByte(state.contractState[i]) != expectedState) {
                continue;
            }
            ContractType type = ContractType.fromByte(state.contractType[i]);
            if (type.isStationing()) return state.contractId[i];
        }
        return -1L;
    }
}
