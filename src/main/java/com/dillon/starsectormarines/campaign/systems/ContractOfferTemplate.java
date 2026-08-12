package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;

import java.util.Random;

/** Pure rank-gated type and economy shape for one standard contract offer. */
public final class ContractOfferTemplate {

    private static final float ESCORT_CHANCE = 0.35f;
    private static final float STATIONING_CHANCE = 0.20f;
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
        ContractType type;
        if (rank == HouseRank.TIER_1) {
            type = ContractType.STRIKE;
        } else if (random.nextFloat() < STATIONING_CHANCE) {
            type = random.nextBoolean() ? ContractType.GARRISON : ContractType.CADRE;
        } else {
            type = random.nextFloat() < ESCORT_CHANCE
                    ? ContractType.ESCORT
                    : ContractType.STRIKE;
        }
        return forType(rank, type);
    }

    public static ContractOfferTemplate forType(HouseRank rank, ContractType type) {
        if (rank == null || type == null || rank == HouseRank.TIER_4) return null;
        if ((type == ContractType.ESCORT || type.isStationing())
                && rank == HouseRank.TIER_1) {
            return null;
        }
        if (type.isStationing()) {
            return new ContractOfferTemplate(type, 0,
                    type == ContractType.GARRISON ? 25 : 5);
        }
        if (type != ContractType.STRIKE && type != ContractType.ESCORT) return null;
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
