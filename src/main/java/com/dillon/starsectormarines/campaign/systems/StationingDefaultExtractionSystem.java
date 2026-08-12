package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;

import java.util.EnumSet;

/** Creates one recovery contract for each defaulted stationing assignment. */
public final class StationingDefaultExtractionSystem implements CampaignSystem {

    @Override
    public String name() {
        return "StationingDefaultExtraction";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        int parentsAtStart = state.contractCount;
        for (int row = 0; row < parentsAtStart; row++) {
            if (ContractState.fromByte(state.contractState[row]) != ContractState.DEFAULTED) continue;
            if (!ContractType.fromByte(state.contractType[row]).isStationing()) continue;
            if (state.contractMarinesCommitted[row] <= 0 && state.contractCaptainId[row] < 0) continue;

            long parentId = state.contractId[row];
            if (hasFollowup(state, parentId)) continue;

            long extractionId = state.addContract(
                    state.contractPatronHouseId[row], -1L, -1L,
                    ContractType.EXTRACTION, ContractState.OFFERED,
                    day, -1, -1, (byte) 1, -1,
                    state.contractMarketId[row], state.contractIndustryId[row],
                    state.contractRetainerPerMonth[row], 0,
                    state.contractSalvageBaseline[row], state.contractSalvageNegotiated[row],
                    (byte) 100);
            int extractionRow = state.contractIndex(extractionId);
            state.contractSourceContractId[extractionRow] = parentId;
        }
    }

    private static boolean hasFollowup(CampaignState state, long parentId) {
        for (int row = 0; row < state.contractCount; row++) {
            if (state.contractSourceContractId[row] == parentId
                    && ContractType.fromByte(state.contractType[row]) == ContractType.EXTRACTION) {
                return true;
            }
        }
        return false;
    }
}
