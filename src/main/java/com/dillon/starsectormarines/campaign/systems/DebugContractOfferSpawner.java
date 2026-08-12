package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.Random;

/** Pure forcing function used by debug intel to create production-shaped offers. */
@DebugOnly
public final class DebugContractOfferSpawner {

    private DebugContractOfferSpawner() {}

    public static long spawn(CampaignState state, int patronRow,
                             ContractType type, int day) {
        if (state == null || patronRow < 0 || patronRow >= state.houseCount) return -1L;
        if (HouseStatus.fromByte(state.houseStatus[patronRow]) != HouseStatus.ACTIVE) return -1L;
        HouseRank rank = HouseRank.fromByte(state.houseRank[patronRow]);
        ContractOfferTemplate template = ContractOfferTemplate.forType(rank, type);
        if (template == null) return -1L;

        long patronId = state.houseId[patronRow];
        if (hasOpenOffer(state, patronId)) return -1L;
        long targetId = type.isStationing() ? -1L : pickTarget(state, patronRow);
        if (!type.isStationing() && targetId < 0L) return -1L;

        return state.addContract(patronId, targetId, -1L,
                type, ContractState.OFFERED, day, -1, -1,
                template.phasesTotal, -1,
                state.houseMarketId[patronRow], -1,
                template.payout, 0,
                template.salvageBaseline, template.salvageBaseline, (byte) 100);
    }

    private static boolean hasOpenOffer(CampaignState state, long patronId) {
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractPatronHouseId[i] == patronId
                    && ContractState.fromByte(state.contractState[i]) == ContractState.OFFERED) {
                return true;
            }
        }
        return false;
    }

    private static long pickTarget(CampaignState state, int patronRow) {
        int candidates = 0;
        for (int i = 0; i < state.houseCount; i++) {
            if (isTarget(state, patronRow, i)) candidates++;
        }
        if (candidates == 0) return -1L;
        int pick = new Random(state.houseId[patronRow]).nextInt(candidates);
        for (int i = 0; i < state.houseCount; i++) {
            if (!isTarget(state, patronRow, i)) continue;
            if (pick-- == 0) return state.houseId[i];
        }
        return -1L;
    }

    private static boolean isTarget(CampaignState state, int patronRow, int candidateRow) {
        return candidateRow != patronRow
                && HouseRank.fromByte(state.houseRank[candidateRow]) != HouseRank.TIER_4
                && HouseStatus.fromByte(state.houseStatus[candidateRow]) == HouseStatus.ACTIVE;
    }
}
