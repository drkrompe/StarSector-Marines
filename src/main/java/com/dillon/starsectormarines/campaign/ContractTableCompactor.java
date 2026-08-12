package com.dillon.starsectormarines.campaign;

/** Debug/maintenance compaction that preserves every contracts[] SoA column. */
public final class ContractTableCompactor {

    private ContractTableCompactor() {}

    public static int removeTerminal(CampaignState state) {
        if (state == null) return 0;
        int originalCount = state.contractCount;
        int write = 0;
        state.contractIndexById.clear();
        for (int read = 0; read < originalCount; read++) {
            ContractState contractState = ContractState.fromByte(state.contractState[read]);
            if (contractState.isTerminal()) continue;
            if (write != read) copyRow(state, read, write);
            state.contractIndexById.put(state.contractId[write], write);
            write++;
        }
        state.contractCount = write;
        return originalCount - write;
    }

    private static void copyRow(CampaignState state, int from, int to) {
        state.contractId[to] = state.contractId[from];
        state.contractPatronHouseId[to] = state.contractPatronHouseId[from];
        state.contractTargetHouseId[to] = state.contractTargetHouseId[from];
        state.contractChainId[to] = state.contractChainId[from];
        state.contractType[to] = state.contractType[from];
        state.contractState[to] = state.contractState[from];
        state.contractAcceptedTick[to] = state.contractAcceptedTick[from];
        state.contractExpiresTick[to] = state.contractExpiresTick[from];
        state.contractOfferExpiresTick[to] = state.contractOfferExpiresTick[from];
        state.contractPhasesTotal[to] = state.contractPhasesTotal[from];
        state.contractPhasesDone[to] = state.contractPhasesDone[from];
        state.contractCaptainId[to] = state.contractCaptainId[from];
        state.contractMarketId[to] = state.contractMarketId[from];
        state.contractIndustryId[to] = state.contractIndustryId[from];
        state.contractBasePayout[to] = state.contractBasePayout[from];
        state.contractRetainerPerMonth[to] = state.contractRetainerPerMonth[from];
        state.contractMarinesCommitted[to] = state.contractMarinesCommitted[from];
        state.contractLastRetainerTick[to] = state.contractLastRetainerTick[from];
        state.contractLastTrainingTick[to] = state.contractLastTrainingTick[from];
        state.contractSalvageBaseline[to] = state.contractSalvageBaseline[from];
        state.contractSalvageNegotiated[to] = state.contractSalvageNegotiated[from];
        state.contractCashMultiplier[to] = state.contractCashMultiplier[from];
    }
}
