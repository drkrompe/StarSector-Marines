package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractEligibilityTest {

    @Test
    void mrbUnlocksTiersWhileTierOneRemainsRecoveryFloor() {
        assertTrue(eligible(HouseRank.TIER_1, -100, 0));
        assertFalse(eligible(HouseRank.TIER_2, 4, 0));
        assertTrue(eligible(HouseRank.TIER_2, 5, 0));
        assertFalse(eligible(HouseRank.TIER_3, 19, 0));
        assertTrue(eligible(HouseRank.TIER_3, 20, 0));
        assertFalse(eligible(HouseRank.TIER_4, 10_000, 100));
    }

    @Test
    void burnedHouseAndInactivePatronBlockNewWork() {
        CampaignState state = state(HouseRank.TIER_1);
        int repRow = state.ensureRepRow(state.houseId[0]);
        state.repValue[repRow] = -26;
        assertFalse(ContractEligibility.patronEligible(state, state.houseId[0]));

        state.debugBypassHouseGating = true;
        assertTrue(ContractEligibility.patronEligible(state, state.houseId[0]));

        state.houseStatus[0] = HouseStatus.DORMANT.toByte();
        assertFalse(ContractEligibility.patronEligible(state, state.houseId[0]));
        assertFalse(ContractEligibility.patronEligible(state, 999L));
    }

    @Test
    void recoveryAndExistingObligationsBypassNewWorkGate() {
        CampaignState state = state(HouseRank.TIER_3);
        long recovery = state.addContract(state.houseId[0], -1L, -1L,
                ContractType.EXTRACTION, ContractState.OFFERED,
                1, -1, -1, (byte) 1, -1, 1, -1,
                1_000, 0, (byte) 25, (byte) 25, (byte) 100);
        long active = state.addContract(state.houseId[0], 2L, -1L,
                ContractType.PLANETARY_ASSAULT, ContractState.IN_PROGRESS,
                1, -1, -1, (byte) 4, -1, 1, -1,
                180_000, 0, (byte) 80, (byte) 80, (byte) 100);

        assertTrue(ContractEligibility.contractAcceptable(state, recovery));
        assertTrue(ContractEligibility.contractAcceptable(state, active));
    }

    private static boolean eligible(HouseRank rank, int mrb, int rep) {
        CampaignState state = state(rank);
        state.playerMrbRep = mrb;
        int repRow = state.ensureRepRow(state.houseId[0]);
        state.repValue[repRow] = rep;
        return ContractEligibility.patronEligible(state, state.houseId[0]);
    }

    private static CampaignState state(HouseRank rank) {
        CampaignState state = new CampaignState();
        state.addHouse(1, 1, HouseFlavor.CORPORATE, rank, HouseStatus.ACTIVE,
                PatronArchetype.ESTABLISHED, "Patron");
        return state;
    }
}
