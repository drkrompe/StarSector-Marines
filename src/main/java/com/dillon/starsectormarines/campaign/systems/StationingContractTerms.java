package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;

/** Pure economy and duration terms for one Garrison or Cadre commitment. */
public final class StationingContractTerms {

    public final ContractType type;
    public final int committedMarines;
    public final int termDays;
    public final int monthlyRetainer;
    public final byte salvageBaseline;

    private StationingContractTerms(ContractType type, int committedMarines,
                                    int termDays, int monthlyRetainer,
                                    int salvageBaseline) {
        this.type = type;
        this.committedMarines = committedMarines;
        this.termDays = termDays;
        this.monthlyRetainer = monthlyRetainer;
        this.salvageBaseline = (byte) salvageBaseline;
    }

    public static StationingContractTerms create(ContractType type, HouseRank rank,
                                                  int marineCount, int requestedMonths) {
        if (type == null || !type.isStationing() || rank == null
                || rank == HouseRank.TIER_4 || marineCount <= 0) {
            return null;
        }
        int months = Math.max(1, Math.min(requestedMonths, maxMonths(rank)));
        float modeMultiplier = type == ContractType.GARRISON ? 1.10f : 0.40f;
        long base = Math.round(marineCount * 20d * modeMultiplier * tierMultiplier(rank));
        int retainer = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, base));
        int salvage = type == ContractType.GARRISON ? 25 : 5;
        return new StationingContractTerms(type, marineCount, months * 30,
                retainer, salvage);
    }

    private static int maxMonths(HouseRank rank) {
        switch (rank) {
            case TIER_2: return 3;
            case TIER_3: return 6;
            case TIER_1:
            default:     return 1;
        }
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
