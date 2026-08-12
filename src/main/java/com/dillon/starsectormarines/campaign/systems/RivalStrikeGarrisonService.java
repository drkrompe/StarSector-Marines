package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;

/** Arms defenders when a player-backed rival Strike actually launches. */
public final class RivalStrikeGarrisonService {

    private static final long EVENT_NAMESPACE = 0x5253564C5354524BL;

    private RivalStrikeGarrisonService() {}

    public static int armForContractLaunch(CampaignState state, long contractId, int day) {
        if (state == null || contractId <= 0L) return 0;
        int contractRow = state.contractIndex(contractId);
        if (contractRow < 0
                || ContractType.fromByte(state.contractType[contractRow]) != ContractType.STRIKE
                || ContractState.fromByte(state.contractState[contractRow]).isTerminal()) {
            return 0;
        }

        long attackerHouseId = state.contractPatronHouseId[contractRow];
        long targetHouseId = state.contractTargetHouseId[contractRow];
        if (attackerHouseId < 0L || targetHouseId < 0L
                || attackerHouseId == targetHouseId) {
            return 0;
        }
        int attackerRow = state.houseIndex(attackerHouseId);
        int targetRow = state.houseIndex(targetHouseId);
        if (attackerRow < 0 || targetRow < 0) return 0;

        int targetMarketId = state.houseMarketId[targetRow];
        int attackerFactionId = state.houseFactionId[attackerRow];
        return GarrisonDefenseTrigger.arm(state, eventKey(contractId), targetMarketId,
                GarrisonDefenseTriggerType.RIVAL_STRIKE, attackerHouseId,
                attackerFactionId, day);
    }

    static long eventKey(long contractId) {
        long key = contractId ^ EVENT_NAMESPACE;
        return key != 0L ? key : Long.MIN_VALUE;
    }
}
