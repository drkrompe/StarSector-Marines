package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.Mission;

/** Freezes and validates contract-wide Planetary Assault negotiation terms. */
public final class PlanetaryAssaultTerms {

    private PlanetaryAssaultTerms() {}

    public static boolean lockForDeployment(CampaignState state, Mission mission) {
        if (state == null || mission == null || mission.contractId < 0L) return false;
        int row = state.contractIndex(mission.contractId);
        if (row < 0 || ContractType.fromByte(state.contractType[row])
                != ContractType.PLANETARY_ASSAULT) {
            return false;
        }
        int baseline = mission.contractSalvageBaseline & 0xFF;
        int negotiated = mission.contractSalvageNegotiated & 0xFF;
        int cashMultiplier = mission.cashMultiplier & 0xFF;
        if (baseline != (state.contractSalvageBaseline[row] & 0xFF)
                || negotiated > baseline || cashMultiplier < 100
                || cashMultiplier != 100 + (baseline - negotiated) / 2) {
            return false;
        }

        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState == ContractState.OFFERED) {
            state.contractSalvageNegotiated[row] = (byte) negotiated;
            state.contractCashMultiplier[row] = (byte) cashMultiplier;
            return true;
        }
        return contractState == ContractState.IN_PROGRESS
                && (state.contractSalvageNegotiated[row] & 0xFF) == negotiated
                && (state.contractCashMultiplier[row] & 0xFF) == cashMultiplier;
    }

    public static boolean negotiationOpen(CampaignState state, long contractId) {
        if (state == null || contractId < 0L) return true;
        int row = state.contractIndex(contractId);
        return row < 0 || ContractState.fromByte(state.contractState[row])
                == ContractState.OFFERED;
    }
}
