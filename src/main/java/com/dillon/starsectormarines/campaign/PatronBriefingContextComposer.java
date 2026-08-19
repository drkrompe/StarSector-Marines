package com.dillon.starsectormarines.campaign;

/** Chooses the most relevant factual history layer for a patron briefing. */
public final class PatronBriefingContextComposer {

    private PatronBriefingContextComposer() {}

    public static String compose(CampaignState state, long patronHouseId,
                                 int marketId, long currentContractId,
                                 int currentOfferDay,
                                 String patronDisplayName) {
        String direct = PatronMemoryComposer.compose(state, patronHouseId,
                currentContractId, patronDisplayName);
        if (direct != null) return direct;
        return PatronLocalEchoComposer.compose(state, patronHouseId, marketId,
                currentContractId, currentOfferDay);
    }
}
