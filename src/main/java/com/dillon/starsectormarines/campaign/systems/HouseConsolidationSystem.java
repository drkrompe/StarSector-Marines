package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/** Transitions politically exhausted houses to stable-id DORMANT tombstones. */
public final class HouseConsolidationSystem implements CampaignSystem {

    @Override
    public String name() {
        return "HouseConsolidation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.STAKES,
                CampaignTable.CHAINS, CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.CHAINS,
                CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int houseRow = 0; houseRow < state.houseCount; houseRow++) {
            if (HouseStatus.fromByte(state.houseStatus[houseRow]) != HouseStatus.ACTIVE) {
                continue;
            }
            long houseId = state.houseId[houseRow];
            boolean hasHistory = false;
            boolean hasPositiveStake = false;
            for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
                if (state.stakeHouseId[stakeRow] != houseId) continue;
                hasHistory = true;
                if (state.stakeShare[stakeRow] > 0) {
                    hasPositiveStake = true;
                    break;
                }
            }
            if (hasHistory && !hasPositiveStake) {
                state.houseStatus[houseRow] = HouseStatus.DORMANT.toByte();
                terminatePoliticalWork(state, houseId, day);
            }
        }
    }

    private static void terminatePoliticalWork(CampaignState state, long houseId, int day) {
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (ChainState.fromByte(state.chainState[chainRow]) != ChainState.ACTIVE
                    || (state.chainActorHouseId[chainRow] != houseId
                        && state.chainTarget[chainRow] != houseId)) {
                continue;
            }
            state.chainState[chainRow] = ChainState.FAILED.toByte();
            state.chainResolvedTick[chainRow] = day;
        }

        for (int contractRow = 0; contractRow < state.contractCount; contractRow++) {
            if (state.contractPatronHouseId[contractRow] != houseId
                    || ContractType.fromByte(state.contractType[contractRow])
                        == ContractType.EXTRACTION) {
                continue;
            }
            ContractState contractState = ContractState.fromByte(
                    state.contractState[contractRow]);
            if (contractState == ContractState.OFFERED) {
                state.contractState[contractRow] = ContractState.EXPIRED.toByte();
            } else if (contractState == ContractState.ACTIVE
                    || contractState == ContractState.IN_PROGRESS) {
                state.contractState[contractRow] = ContractState.DEFAULTED.toByte();
            }
        }
    }
}
