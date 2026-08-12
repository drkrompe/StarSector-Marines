package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractReputationTest {

    @Test
    void completionMrbRewardScalesByPatronTier() {
        assertCompletionReward(HouseRank.TIER_1, 1);
        assertCompletionReward(HouseRank.TIER_2, 3);
        assertCompletionReward(HouseRank.TIER_3, 10);
        assertCompletionReward(HouseRank.TIER_4, 20);
    }

    @Test
    void failureCostsMrbAndTracksFailedContract() {
        CampaignState state = state(HouseRank.TIER_2);

        ContractReputation.failed(state, state.houseId[0], -2, 30);

        assertEquals(-2, state.repValue[0]);
        assertEquals(-1, state.playerMrbRep);
        assertEquals(1, state.repContractsFailed[0] & 0xFFFF);
        assertEquals(30, state.repLastContractTick[0]);
    }

    @Test
    void abandonmentIsSevereWhileEmployerBreachIsMrbNeutral() {
        CampaignState abandoned = state(HouseRank.TIER_2);
        ContractReputation.abandoned(abandoned, abandoned.houseId[0], 40);
        assertEquals(-15, abandoned.repValue[0]);
        assertEquals(-10, abandoned.playerMrbRep);
        assertEquals(1, abandoned.repContractsFailed[0] & 0xFFFF);

        CampaignState breached = state(HouseRank.TIER_2);
        ContractReputation.employerBreached(breached, breached.houseId[0], 40);
        assertEquals(-10, breached.repValue[0]);
        assertEquals(0, breached.playerMrbRep);
        assertEquals(0, breached.repContractsFailed[0] & 0xFFFF);
    }

    @Test
    void houseRepAndCountersClampWithoutWrapping() {
        CampaignState state = state(HouseRank.TIER_1);
        int repRow = state.ensureRepRow(state.houseId[0]);
        state.repValue[repRow] = 100;
        state.repContractsCompleted[repRow] = (short) 0xFFFF;

        ContractReputation.completed(state, state.houseId[0], 5, 50);

        assertEquals(100, state.repValue[repRow]);
        assertEquals(65535, state.repContractsCompleted[repRow] & 0xFFFF);
    }

    private static void assertCompletionReward(HouseRank rank, int expectedMrb) {
        CampaignState state = state(rank);
        ContractReputation.completed(state, state.houseId[0], 1, 20);
        assertEquals(1, state.repValue[0]);
        assertEquals(expectedMrb, state.playerMrbRep);
        assertEquals(1, state.repContractsCompleted[0] & 0xFFFF);
    }

    private static CampaignState state(HouseRank rank) {
        CampaignState state = new CampaignState();
        state.addHouse(1, 1, HouseFlavor.CORPORATE, rank, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Patron");
        return state;
    }
}
