package com.dillon.starsectormarines.campaign;

/** Validated terminal effect for a completed threatened-house intervention. */
public final class ChainIntervention {

    private ChainIntervention() {}

    /**
     * Fails the active autonomous chain opposed by a completed contract.
     * Returns true only when this call applied the terminal transition.
     */
    public static boolean stopOpposedChain(CampaignState state, int contractRow, int day) {
        if (state == null || contractRow < 0 || contractRow >= state.contractCount
                || ContractState.fromByte(state.contractState[contractRow])
                    != ContractState.COMPLETED) {
            return false;
        }
        long opposedChainId = state.contractOpposedChainId[contractRow];
        int chainRow = state.chainIndex(opposedChainId);
        if (opposedChainId < 0L || chainRow < 0
                || state.chainPatron[chainRow] != -1L
                || ChainState.fromByte(state.chainState[chainRow]) != ChainState.ACTIVE
                || state.contractPatronHouseId[contractRow] != state.chainTarget[chainRow]
                || state.contractTargetHouseId[contractRow]
                    != state.chainActorHouseId[chainRow]) {
            return false;
        }

        state.chainState[chainRow] = ChainState.FAILED.toByte();
        state.chainResolvedTick[chainRow] = day;
        return true;
    }
}
