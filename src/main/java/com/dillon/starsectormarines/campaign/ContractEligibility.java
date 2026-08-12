package com.dillon.starsectormarines.campaign;

/** Shared MRB and house-reputation gate for new patron work. */
public final class ContractEligibility {

    public static final int MIN_HOUSE_REP = -25;
    public static final int TIER_2_MRB_REQUIRED = 5;
    public static final int TIER_3_MRB_REQUIRED = 20;

    private ContractEligibility() {}

    public static boolean patronEligible(CampaignState state, long patronId) {
        if (state == null) return false;
        int patronRow = state.houseIndex(patronId);
        if (patronRow < 0
                || HouseStatus.fromByte(state.houseStatus[patronRow]) != HouseStatus.ACTIVE) {
            return false;
        }
        if (state.debugBypassHouseGating) return true;
        HouseRank rank = HouseRank.fromByte(state.houseRank[patronRow]);
        if (rank == HouseRank.TIER_4 || state.playerMrbRep < requiredMrb(rank)) return false;
        int repRow = state.repIndex(patronId);
        int houseRep = repRow >= 0 ? state.repValue[repRow] : 0;
        return houseRep >= MIN_HOUSE_REP;
    }

    public static boolean contractAcceptable(CampaignState state, long contractId) {
        if (state == null) return false;
        int row = state.contractIndex(contractId);
        if (row < 0) return false;
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState != ContractState.OFFERED) return true;
        if (ContractType.fromByte(state.contractType[row]) == ContractType.EXTRACTION) return true;
        return patronEligible(state, state.contractPatronHouseId[row]);
    }

    public static int requiredMrb(HouseRank rank) {
        if (rank == null) return Integer.MAX_VALUE;
        switch (rank) {
            case TIER_3: return TIER_3_MRB_REQUIRED;
            case TIER_2: return TIER_2_MRB_REQUIRED;
            case TIER_1: return Integer.MIN_VALUE;
            case TIER_4:
            default:     return Integer.MAX_VALUE;
        }
    }
}
