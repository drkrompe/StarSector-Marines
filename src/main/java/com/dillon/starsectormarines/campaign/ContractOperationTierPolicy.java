package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.OperationTier;

/** Campaign authority for the scale persisted on one mission-mode contract. */
public final class ContractOperationTierPolicy {

    private ContractOperationTierPolicy() {}

    public static OperationTier select(HouseRank patronRank, ContractType type) {
        if (patronRank == null || type == null || !type.isMissionMode()
                || patronRank == HouseRank.TIER_4) {
            return null;
        }
        OperationTier tier = switch (patronRank) {
            case TIER_1 -> OperationTier.FIRST_CONTRACT;
            case TIER_2 -> OperationTier.ESTABLISHED;
            case TIER_3 -> OperationTier.VETERAN;
            case TIER_4 -> null;
        };
        return type == ContractType.PLANETARY_ASSAULT
                ? OperationTier.clampTo(tier, OperationTier.REINFORCED)
                : tier;
    }

    /** Legacy-safe lookup when a contract row did not persist the new column. */
    public static OperationTier select(CampaignState state, long patronHouseId,
                                       ContractType type) {
        if (state == null) return null;
        int patronRow = state.houseIndex(patronHouseId);
        HouseRank rank = patronRow >= 0
                ? HouseRank.fromByte(state.houseRank[patronRow]) : null;
        return select(rank, type);
    }
}
