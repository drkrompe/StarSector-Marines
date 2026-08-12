package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;

import java.util.Random;

/** Pure rank-gated type and economy shape for one standard contract offer. */
public final class ContractOfferTemplate {

    private static final float ESCORT_CHANCE = 0.35f;
    private static final int STRIKE_BASE_PAYOUT = 25_000;
    private static final int ESCORT_BASE_PAYOUT = 30_000;

    public final ContractType type;
    public final int payout;
    public final byte salvageBaseline;

    private ContractOfferTemplate(ContractType type, int payout, int salvageBaseline) {
        this.type = type;
        this.payout = payout;
        this.salvageBaseline = (byte) salvageBaseline;
    }

    public static ContractOfferTemplate roll(HouseRank rank, Random random) {
        if (rank == null || random == null || rank == HouseRank.TIER_4) return null;
        ContractType type = rank == HouseRank.TIER_1 || random.nextFloat() >= ESCORT_CHANCE
                ? ContractType.STRIKE
                : ContractType.ESCORT;
        int basePayout = type == ContractType.ESCORT
                ? ESCORT_BASE_PAYOUT
                : STRIKE_BASE_PAYOUT;
        int payout = Math.round(basePayout * tierMultiplier(rank));
        int salvage = type == ContractType.ESCORT ? 10 : 60;
        return new ContractOfferTemplate(type, payout, salvage);
    }

    private static float tierMultiplier(HouseRank rank) {
        switch (rank) {
            case TIER_2: return 1.5f;
            case TIER_3: return 3f;
            case TIER_1:
            default:     return 1f;
        }
    }
}
