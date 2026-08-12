package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;

/** Shared idempotent boundary for rival, vanilla-raid, and political-flip producers. */
public final class GarrisonDefenseTrigger {

    private GarrisonDefenseTrigger() {}

    public static int arm(CampaignState state, long eventKey, int marketId,
                          GarrisonDefenseTriggerType triggerType,
                          long attackerHouseId, int attackerFactionId, int day) {
        if (state == null || eventKey == 0L || marketId < 0 || triggerType == null
                || triggerType == GarrisonDefenseTriggerType.NONE) {
            return 0;
        }
        int armed = 0;
        for (int row = 0; row < state.contractCount; row++) {
            if (ContractType.fromByte(state.contractType[row]) != ContractType.GARRISON
                    || ContractState.fromByte(state.contractState[row]) != ContractState.ACTIVE
                    || state.contractMarketId[row] != marketId
                    || state.contractDefenseEventKey[row] == eventKey) {
                continue;
            }
            long patronId = state.contractPatronHouseId[row];
            if (triggerType == GarrisonDefenseTriggerType.RIVAL_STRIKE
                    && (attackerHouseId < 0L || attackerHouseId == patronId)) {
                continue;
            }
            if (triggerType == GarrisonDefenseTriggerType.INTERNAL_FLIP) {
                int patronRow = state.houseIndex(patronId);
                if (attackerFactionId < 0 || patronRow < 0
                        || state.houseFactionId[patronRow] == attackerFactionId) {
                    continue;
                }
            }
            state.contractState[row] = ContractState.IN_PROGRESS.toByte();
            state.contractDefenseEventKey[row] = eventKey;
            state.contractDefenseTriggeredTick[row] = day;
            state.contractDefenseTriggerType[row] = triggerType.toByte();
            state.contractDefenseAttackerHouseId[row] = attackerHouseId;
            state.contractDefenseAttackerFactionId[row] = attackerFactionId;
            armed++;
        }
        return armed;
    }
}
