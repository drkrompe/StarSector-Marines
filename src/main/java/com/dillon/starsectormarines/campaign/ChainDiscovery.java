package com.dillon.starsectormarines.campaign;

/** Read-only queries over discovered active political chains. */
public final class ChainDiscovery {

    private ChainDiscovery() {}

    /**
     * Returns the row of the discovered autonomous threat currently targeting
     * {@code targetHouseId}, or {@code -1}. Earliest discovery wins; ties use
     * stable chain id so a future counter-contract generator is deterministic.
     */
    public static int findActiveThreatAgainst(CampaignState state, long targetHouseId) {
        if (state == null || targetHouseId < 0L) return -1;
        int bestRow = -1;
        for (int row = 0; row < state.chainCount; row++) {
            if (state.chainTarget[row] != targetHouseId || !isDiscoveredActive(state, row)) {
                continue;
            }
            if (bestRow < 0
                    || state.chainDiscoveredTick[row] < state.chainDiscoveredTick[bestRow]
                    || (state.chainDiscoveredTick[row] == state.chainDiscoveredTick[bestRow]
                        && state.chainId[row] < state.chainId[bestRow])) {
                bestRow = row;
            }
        }
        return bestRow;
    }

    /** True only for a learned, still-active autonomous chain row. */
    public static boolean isDiscoveredActive(CampaignState state, int chainRow) {
        return state != null
                && chainRow >= 0
                && chainRow < state.chainCount
                && state.chainPatron[chainRow] == -1L
                && state.chainDiscoveredTick[chainRow] >= 0
                && ChainState.fromByte(state.chainState[chainRow]) == ChainState.ACTIVE;
    }
}
