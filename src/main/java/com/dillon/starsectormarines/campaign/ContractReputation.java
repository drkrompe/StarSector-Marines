package com.dillon.starsectormarines.campaign;

/** Central policy for house and MRB reputation changes from contract outcomes. */
public final class ContractReputation {

    public static final int ABANDONED_HOUSE_DELTA = -15;
    public static final int ABANDONED_MRB_DELTA = -10;
    public static final int EMPLOYER_BREACH_HOUSE_DELTA = -10;
    public static final int FAILED_MRB_DELTA = -1;

    private ContractReputation() {}

    public static void completed(CampaignState state, long patronId, int houseDelta, int day) {
        apply(state, patronId, houseDelta, completionMrbDelta(state, patronId), day,
                true, false);
    }

    public static void failed(CampaignState state, long patronId, int houseDelta, int day) {
        apply(state, patronId, houseDelta, FAILED_MRB_DELTA, day,
                false, true);
    }

    public static void abandoned(CampaignState state, long patronId, int day) {
        apply(state, patronId, ABANDONED_HOUSE_DELTA, ABANDONED_MRB_DELTA, day,
                false, true);
    }

    public static void employerBreached(CampaignState state, long patronId, int day) {
        apply(state, patronId, EMPLOYER_BREACH_HOUSE_DELTA, 0, day,
                false, false);
    }

    static int completionMrbDelta(CampaignState state, long patronId) {
        int patronRow = state != null ? state.houseIndex(patronId) : -1;
        HouseRank rank = patronRow >= 0
                ? HouseRank.fromByte(state.houseRank[patronRow]) : HouseRank.TIER_1;
        switch (rank) {
            case TIER_4: return 20;
            case TIER_3: return 10;
            case TIER_2: return 3;
            case TIER_1:
            default:     return 1;
        }
    }

    private static void apply(CampaignState state, long patronId,
                              int houseDelta, int mrbDelta, int day,
                              boolean completed, boolean failed) {
        if (state == null) return;
        int repRow = state.ensureRepRow(patronId);
        state.repValue[repRow] = Math.max(-100,
                Math.min(100, state.repValue[repRow] + houseDelta));
        state.repLastContractTick[repRow] = day;
        if (completed) {
            state.repContractsCompleted[repRow] = increment(state.repContractsCompleted[repRow]);
        }
        if (failed) {
            state.repContractsFailed[repRow] = increment(state.repContractsFailed[repRow]);
        }
        long mrb = (long) state.playerMrbRep + mrbDelta;
        state.playerMrbRep = (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, mrb));
    }

    private static short increment(short value) {
        int next = (value & 0xFFFF) + 1;
        return (short) Math.min(65535, next);
    }
}
