package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseAmbitionSystemTest {

    @Test
    void noneHouseConsolidatesItsStrongestHomeIndustry() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 40);
        state.addStake(house, 1, 9, (short) 80);

        new HouseAmbitionSystem().tick(state, 20);

        int row = state.houseIndex(house);
        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(9L, state.houseAmbitionTarget[row]);
    }

    @Test
    void tiesChooseLowestIndustrySlotDeterministically() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 9, (short) 60);
        state.addStake(house, 1, 7, (short) 60);

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(7L, state.houseAmbitionTarget[state.houseIndex(house)]);
    }

    @Test
    void offMarketAndTombstonedStakesDoNotCreateAmbition() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 2, 7, (short) 100);
        state.addStake(house, 1, 9, (short) 0);

        new HouseAmbitionSystem().tick(state, 20);

        int row = state.houseIndex(house);
        assertEquals(HouseAmbition.NONE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(-1L, state.houseAmbitionTarget[row]);
    }

    @Test
    void existingAmbitionAndInactiveHouseRemainUntouched() {
        CampaignState state = new CampaignState();
        long active = house(state, 1, HouseStatus.ACTIVE);
        long dormant = house(state, 1, HouseStatus.DORMANT);
        state.addStake(active, 1, 7, (short) 100);
        state.addStake(dormant, 1, 9, (short) 100);
        int activeRow = state.houseIndex(active);
        state.houseAmbition[activeRow] = HouseAmbition.PROMOTE.toByte();
        state.houseAmbitionTarget[activeRow] = 123L;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.PROMOTE,
                HouseAmbition.fromByte(state.houseAmbition[activeRow]));
        assertEquals(123L, state.houseAmbitionTarget[activeRow]);
        assertEquals(HouseAmbition.NONE, HouseAmbition.fromByte(
                state.houseAmbition[state.houseIndex(dormant)]));
    }

    @Test
    void invalidConsolidationTargetRetargetsStrongestSurvivingHomeStake() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        state.addStake(house, 1, 9, (short) 60);
        int row = state.houseIndex(house);
        state.houseAmbition[row] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
        state.houseAmbitionTarget[row] = 7L;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(9L, state.houseAmbitionTarget[row]);
    }

    @Test
    void invalidConsolidationWithoutReplacementClearsToNone() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 0);
        state.addStake(house, 2, 9, (short) 60);
        int row = state.houseIndex(house);
        state.houseAmbition[row] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
        state.houseAmbitionTarget[row] = 7L;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.NONE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(-1L, state.houseAmbitionTarget[row]);
    }

    @Test
    void dormantHouseClearsConsolidationButOtherInactiveNarrativeStateSurvives() {
        CampaignState state = new CampaignState();
        long dormant = house(state, 1, HouseStatus.DORMANT);
        long deposed = house(state, 1, HouseStatus.DEPOSED);
        int dormantRow = state.houseIndex(dormant);
        int deposedRow = state.houseIndex(deposed);
        state.houseAmbition[dormantRow] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
        state.houseAmbitionTarget[dormantRow] = 7L;
        state.houseAmbition[deposedRow] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
        state.houseAmbitionTarget[deposedRow] = 9L;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.NONE,
                HouseAmbition.fromByte(state.houseAmbition[dormantRow]));
        assertEquals(-1L, state.houseAmbitionTarget[dormantRow]);
        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[deposedRow]));
        assertEquals(9L, state.houseAmbitionTarget[deposedRow]);
    }

    @Test
    void monthlyReviewTurnsEstablishedNearThresholdHouseTowardPromotion() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 100);
        int row = state.houseIndex(house);
        state.housePromotionProgress[row] = 75;
        state.housePower[row] = 100;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.PROMOTE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(HouseRank.TIER_2.ordinal(), state.houseAmbitionTarget[row]);
        assertEquals(20, state.houseLastAmbitionReviewTick[row]);
    }

    @Test
    void promotionIntentRequiresBothProgressAndPower() {
        CampaignState state = new CampaignState();
        long weak = house(state, 1, HouseStatus.ACTIVE);
        long unready = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(weak, 1, 7, (short) 100);
        state.addStake(unready, 1, 9, (short) 100);
        int weakRow = state.houseIndex(weak);
        int unreadyRow = state.houseIndex(unready);
        state.housePromotionProgress[weakRow] = 75;
        state.housePower[weakRow] = 99;
        state.housePromotionProgress[unreadyRow] = 74;
        state.housePower[unreadyRow] = 100;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[weakRow]));
        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[unreadyRow]));
    }

    @Test
    void ineligibleHouseWaitsForNextPersistedReview() {
        CampaignState state = new CampaignState();
        long house = house(state, 1, HouseStatus.ACTIVE);
        state.addStake(house, 1, 7, (short) 100);
        int row = state.houseIndex(house);
        state.housePromotionProgress[row] = 74;
        state.housePower[row] = 100;
        HouseAmbitionSystem system = new HouseAmbitionSystem();
        system.tick(state, 20);

        state.housePromotionProgress[row] = 75;
        system.tick(state, 49);
        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(20, state.houseLastAmbitionReviewTick[row]);

        system.tick(state, 50);
        assertEquals(HouseAmbition.PROMOTE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(50, state.houseLastAmbitionReviewTick[row]);
    }

    @Test
    void tierThreeClaimTargetsPersistedFactionIdentityAtEndgameThreshold() {
        CampaignState state = new CampaignState();
        long house = state.addHouse(1, 17, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Claimant");
        state.addStake(house, 1, 7, (short) 100);
        int row = state.houseIndex(house);
        state.housePromotionProgress[row] = 750;
        state.housePower[row] = 1000;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.CLAIM_THRONE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(17L, state.houseAmbitionTarget[row]);
        assertEquals(HouseRank.TIER_3, HouseRank.fromByte(state.houseRank[row]));
    }

    @Test
    void throneClaimRequiresBothEndgameProgressAndPower() {
        CampaignState state = new CampaignState();
        long weak = state.addHouse(1, 17, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Weak");
        long unready = state.addHouse(1, 17, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Unready");
        state.addStake(weak, 1, 7, (short) 100);
        state.addStake(unready, 1, 9, (short) 100);
        int weakRow = state.houseIndex(weak);
        int unreadyRow = state.houseIndex(unready);
        state.housePromotionProgress[weakRow] = 750;
        state.housePower[weakRow] = 999;
        state.housePromotionProgress[unreadyRow] = 749;
        state.housePower[unreadyRow] = 1000;

        new HouseAmbitionSystem().tick(state, 20);

        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[weakRow]));
        assertEquals(HouseAmbition.CONSOLIDATE_STAKE,
                HouseAmbition.fromByte(state.houseAmbition[unreadyRow]));
    }

    @Test
    void reachedSystemPromotionTargetReevaluatesOnlyOnCadence() {
        CampaignState state = new CampaignState();
        long house = state.addHouse(1, 17, HouseFlavor.FEUDAL, HouseRank.TIER_3,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, "Promoted");
        state.addStake(house, 1, 7, (short) 100);
        int row = state.houseIndex(house);
        state.houseAmbition[row] = HouseAmbition.PROMOTE.toByte();
        state.houseAmbitionTarget[row] = HouseRank.TIER_3.ordinal();
        state.houseLastAmbitionReviewTick[row] = 20;
        state.housePromotionProgress[row] = 750;
        state.housePower[row] = 1000;
        HouseAmbitionSystem system = new HouseAmbitionSystem();

        system.tick(state, 49);
        assertEquals(HouseAmbition.PROMOTE,
                HouseAmbition.fromByte(state.houseAmbition[row]));

        system.tick(state, 50);
        assertEquals(HouseAmbition.CLAIM_THRONE,
                HouseAmbition.fromByte(state.houseAmbition[row]));
        assertEquals(17L, state.houseAmbitionTarget[row]);
    }

    private static long house(CampaignState state, int market, HouseStatus status) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                status, PatronArchetype.NEWCOMER, "House");
    }
}
