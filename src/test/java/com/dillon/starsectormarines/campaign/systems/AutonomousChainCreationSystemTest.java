package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutonomousChainCreationSystemTest {

    @Test
    void monthlyPassBindsActorToStrongestActiveLocalRival() {
        CampaignState state = new CampaignState();
        long actor = house(state, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        long tiedLowerId = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long tiedHigherId = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long offMarket = house(state, 2, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long dormant = house(state, 1, HouseRank.TIER_1, HouseStatus.DORMANT);
        state.addStake(actor, 1, 7, (short) 40);
        state.addStake(tiedLowerId, 1, 7, (short) 80);
        state.addStake(tiedHigherId, 1, 7, (short) 80);
        state.addStake(offMarket, 2, 7, (short) 200);
        state.addStake(dormant, 1, 7, (short) 200);
        consolidate(state, actor, 7);

        AutonomousChainCreationSystem system = new AutonomousChainCreationSystem();
        system.tick(state, 29);
        assertEquals(0, state.chainCount);
        system.tick(state, 30);

        assertEquals(1, state.chainCount);
        assertEquals(-1L, state.chainPatron[0]);
        assertEquals(actor, state.chainActorHouseId[0]);
        assertEquals(tiedLowerId, state.chainTarget[0]);
        assertEquals(1, state.chainMarketId[0]);
        assertEquals(7, state.chainIndustryId[0]);
        assertEquals(HouseRank.TIER_2.toByte(), state.chainTier[0]);
        assertEquals(ChainArchetype.CONSOLIDATE_STAKE,
                ChainArchetype.fromByte(state.chainArchetype[0]));
        assertEquals(AutonomousChainCreationSystem.CHAIN_THRESHOLD,
                state.chainThreshold[0]);
        assertEquals(AutonomousChainCreationSystem.DISCOVERY_RISK,
                state.chainDiscoveryRisk[0]);
        assertEquals(30, state.chainInitiatedTick[0]);
    }

    @Test
    void activeChainBlocksAnotherButTerminalChainDoesNot() {
        CampaignState state = new CampaignState();
        long actor = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long rival = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        state.addStake(actor, 1, 7, (short) 40);
        state.addStake(rival, 1, 7, (short) 80);
        consolidate(state, actor, 7);
        state.addChain(actor, rival, (byte) 0, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 10, (byte) 1, 1);

        AutonomousChainCreationSystem system = new AutonomousChainCreationSystem();
        system.tick(state, 30);
        assertEquals(1, state.chainCount);

        state.chainState[0] = ChainState.RESOLVED.toByte();
        system.tick(state, 60);

        assertEquals(2, state.chainCount);
        assertEquals(-1L, state.chainPatron[1]);
        assertEquals(actor, state.chainActorHouseId[1]);
    }

    @Test
    void ineligibleOrUncontestedHouseCreatesNothing() {
        CampaignState state = new CampaignState();
        long noAmbition = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long dormant = house(state, 1, HouseRank.TIER_1, HouseStatus.DORMANT);
        long tombstoned = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long uncontested = house(state, 2, HouseRank.TIER_1, HouseStatus.ACTIVE);
        state.addStake(noAmbition, 1, 7, (short) 40);
        state.addStake(dormant, 1, 7, (short) 40);
        state.addStake(tombstoned, 1, 7, (short) 0);
        state.addStake(uncontested, 2, 9, (short) 40);
        consolidate(state, dormant, 7);
        consolidate(state, tombstoned, 7);
        consolidate(state, uncontested, 9);

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(0, state.chainCount);
    }

    @Test
    void throneClaimBelowHandoffCapCreatesNoChain() {
        CampaignState state = new CampaignState();
        long claimant = house(state, 1, HouseRank.TIER_3, HouseStatus.ACTIVE);
        long rival = house(state, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        state.addStake(claimant, 1, 7, (short) 40);
        state.addStake(rival, 1, 7, (short) 80);
        int claimantRow = state.houseIndex(claimant);
        state.houseAmbition[claimantRow] = HouseAmbition.CLAIM_THRONE.toByte();
        state.houseAmbitionTarget[claimantRow] = state.houseFactionId[claimantRow];

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(0, state.chainCount);
    }

    @Test
    void cappedThroneClaimCreatesCivilWarAgainstStrongestFactionRival() {
        CampaignState state = new CampaignState();
        long claimant = house(state, 1, 1, HouseRank.TIER_3, HouseStatus.ACTIVE);
        long tiedLowerId = house(state, 2, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        long tiedHigherId = house(state, 3, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        long foreign = house(state, 1, 2, HouseRank.TIER_3, HouseStatus.ACTIVE);
        int claimantRow = state.houseIndex(claimant);
        state.housePower[state.houseIndex(tiedLowerId)] = 500;
        state.housePower[state.houseIndex(tiedHigherId)] = 500;
        state.housePower[state.houseIndex(foreign)] = 2_000;
        state.housePromotionProgress[claimantRow] = 1000;
        state.houseAmbition[claimantRow] = HouseAmbition.CLAIM_THRONE.toByte();
        state.houseAmbitionTarget[claimantRow] = state.houseFactionId[claimantRow];

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(1, state.chainCount);
        assertEquals(claimant, state.chainActorHouseId[0]);
        assertEquals(tiedLowerId, state.chainTarget[0]);
        assertEquals(1, state.chainMarketId[0]);
        assertEquals(-1, state.chainIndustryId[0]);
        assertEquals(ChainArchetype.CIVIL_WAR,
                ChainArchetype.fromByte(state.chainArchetype[0]));
        assertEquals(AutonomousChainCreationSystem.CIVIL_WAR_CHAIN_THRESHOLD,
                state.chainThreshold[0]);
        assertEquals(128, state.chainDiscoveryRisk[0] & 0xFF);
    }

    @Test
    void invalidFactionTargetOrMissingRivalBlocksCivilWar() {
        CampaignState state = new CampaignState();
        long invalidTarget = house(state, 1, 1, HouseRank.TIER_3, HouseStatus.ACTIVE);
        long alone = house(state, 2, 2, HouseRank.TIER_3, HouseStatus.ACTIVE);
        int invalidRow = state.houseIndex(invalidTarget);
        int aloneRow = state.houseIndex(alone);
        state.housePromotionProgress[invalidRow] = 1000;
        state.houseAmbition[invalidRow] = HouseAmbition.CLAIM_THRONE.toByte();
        state.houseAmbitionTarget[invalidRow] = 99L;
        state.housePromotionProgress[aloneRow] = 1000;
        state.houseAmbition[aloneRow] = HouseAmbition.CLAIM_THRONE.toByte();
        state.houseAmbitionTarget[aloneRow] = state.houseFactionId[aloneRow];

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(0, state.chainCount);
    }

    @Test
    void preparedHandoffPreventsAnotherCivilWarForClaimant() {
        CampaignState state = new CampaignState();
        long claimant = house(state, 1, 1, HouseRank.TIER_3, HouseStatus.ACTIVE);
        house(state, 2, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        int claimantRow = state.houseIndex(claimant);
        state.housePromotionProgress[claimantRow] = 1000;
        state.houseAmbition[claimantRow] = HouseAmbition.CLAIM_THRONE.toByte();
        state.houseAmbitionTarget[claimantRow] = state.houseFactionId[claimantRow];
        state.prepareThroneClaim(77L, claimant, 1, 2, 1, 20);

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(0, state.chainCount);
        assertEquals(1, state.throneClaimCount);
    }

    @Test
    void promotionChainChoosesStrongestSameFactionRivalAndLowestIndustryTie() {
        CampaignState state = new CampaignState();
        long actor = house(state, 1, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        long sameFaction = house(state, 1, 1, HouseRank.TIER_2, HouseStatus.ACTIVE);
        long foreign = house(state, 1, 2, HouseRank.TIER_2, HouseStatus.ACTIVE);
        state.addStake(actor, 1, 9, (short) 40);
        state.addStake(actor, 1, 7, (short) 40);
        state.addStake(sameFaction, 1, 9, (short) 90);
        state.addStake(sameFaction, 1, 7, (short) 90);
        state.addStake(foreign, 1, 7, (short) 200);
        promote(state, actor, HouseRank.TIER_3);

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(1, state.chainCount);
        assertEquals(actor, state.chainActorHouseId[0]);
        assertEquals(sameFaction, state.chainTarget[0]);
        assertEquals(1, state.chainMarketId[0]);
        assertEquals(7, state.chainIndustryId[0]);
        assertEquals(ChainArchetype.PROMOTE,
                ChainArchetype.fromByte(state.chainArchetype[0]));
        assertEquals(AutonomousChainCreationSystem.PROMOTION_CHAIN_THRESHOLD,
                state.chainThreshold[0]);
        assertEquals(AutonomousChainCreationSystem.PROMOTION_DISCOVERY_RISK,
                state.chainDiscoveryRisk[0]);
    }

    @Test
    void majorityOrInvalidPromotionIntentCreatesNoChain() {
        CampaignState state = new CampaignState();
        long majority = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long rival = house(state, 1, HouseRank.TIER_1, HouseStatus.ACTIVE);
        long invalid = house(state, 2, HouseRank.TIER_2, HouseStatus.ACTIVE);
        state.addStake(majority, 1, 7, (short) 101);
        state.addStake(rival, 1, 7, (short) 99);
        state.addStake(invalid, 2, 9, (short) 40);
        state.addStake(rival, 2, 9, (short) 80);
        promote(state, majority, HouseRank.TIER_2);
        promote(state, invalid, HouseRank.TIER_2);

        new AutonomousChainCreationSystem().tick(state, 30);

        assertEquals(0, state.chainCount);
    }

    private static void consolidate(CampaignState state, long houseId, int industryId) {
        int row = state.houseIndex(houseId);
        state.houseAmbition[row] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
        state.houseAmbitionTarget[row] = industryId;
    }

    private static void promote(CampaignState state, long houseId, HouseRank targetRank) {
        int row = state.houseIndex(houseId);
        state.houseAmbition[row] = HouseAmbition.PROMOTE.toByte();
        state.houseAmbitionTarget[row] = targetRank.ordinal();
    }

    private static long house(CampaignState state, int market, HouseRank rank,
                              HouseStatus status) {
        return house(state, market, 1, rank, status);
    }

    private static long house(CampaignState state, int market, int faction,
                              HouseRank rank, HouseStatus status) {
        return state.addHouse(market, faction, HouseFlavor.FEUDAL, rank, status,
                PatronArchetype.NEWCOMER, "House");
    }
}
