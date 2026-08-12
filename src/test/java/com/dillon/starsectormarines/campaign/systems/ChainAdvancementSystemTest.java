package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.StakeLedger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChainAdvancementSystemTest {

    @Test
    void autonomousChainAdvancesOncePerDistinctDayWhilePlayerChainDoesNot() {
        Fixture fixture = new Fixture();
        long autonomous = fixture.autonomousChain((short) 10);
        long player = fixture.state.addChain(fixture.actor, fixture.target, (byte) 0,
                ChainArchetype.CONSOLIDATE_STAKE, (short) 10, (byte) 1, 1);
        ChainAdvancementSystem system = new ChainAdvancementSystem();

        system.tick(fixture.state, 20);
        system.tick(fixture.state, 20);
        system.tick(fixture.state, 21);

        int autonomousRow = fixture.state.chainIndex(autonomous);
        int playerRow = fixture.state.chainIndex(player);
        assertEquals(2, fixture.state.chainProgress[autonomousRow]);
        assertEquals(21, fixture.state.chainLastAdvanceTick[autonomousRow]);
        assertEquals(0, fixture.state.chainProgress[playerRow]);
        assertEquals(-1, fixture.state.chainLastAdvanceTick[playerRow]);
    }

    @Test
    void thresholdResolutionMovesStakeAndPromotionProgressExactlyOnce() {
        Fixture fixture = new Fixture();
        long chain = fixture.autonomousChain((short) 2);
        int chainRow = fixture.state.chainIndex(chain);
        fixture.state.chainProgress[chainRow] = 1;
        int actorRow = fixture.state.houseIndex(fixture.actor);
        fixture.state.housePromotionProgress[actorRow] = 80;
        ChainAdvancementSystem system = new ChainAdvancementSystem();

        system.tick(fixture.state, 20);

        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(20, fixture.state.chainResolvedTick[chainRow]);
        assertEquals(90, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(60, StakeLedger.shareOf(fixture.state, fixture.target, 1, 7));
        assertEquals(HouseRank.TIER_2,
                HouseRank.fromByte(fixture.state.houseRank[actorRow]));
        assertEquals(10, fixture.state.housePromotionProgress[actorRow]);

        system.tick(fixture.state, 21);

        assertEquals(90, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(60, StakeLedger.shareOf(fixture.state, fixture.target, 1, 7));
        assertEquals(10, fixture.state.housePromotionProgress[actorRow]);
        assertEquals(20, fixture.state.chainResolvedTick[chainRow]);
    }

    @Test
    void inactiveParticipantFailsChainWithoutPoliticalEffects() {
        Fixture fixture = new Fixture();
        long chain = fixture.autonomousChain((short) 1);
        int chainRow = fixture.state.chainIndex(chain);
        fixture.state.houseStatus[fixture.state.houseIndex(fixture.target)] =
                HouseStatus.DORMANT.toByte();

        new ChainAdvancementSystem().tick(fixture.state, 20);

        assertEquals(ChainState.FAILED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(20, fixture.state.chainResolvedTick[chainRow]);
        assertEquals(50, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(100, StakeLedger.shareOf(fixture.state, fixture.target, 1, 7));
        assertEquals(0, fixture.state.housePromotionProgress[
                fixture.state.houseIndex(fixture.actor)]);
    }

    @Test
    void legacyActorlessAutonomousChainRemainsInert() {
        Fixture fixture = new Fixture();
        long chain = fixture.autonomousChain((short) 1);
        int row = fixture.state.chainIndex(chain);
        fixture.state.chainActorHouseId[row] = -1L;

        new ChainAdvancementSystem().tick(fixture.state, 20);

        assertEquals(ChainState.ACTIVE, ChainState.fromByte(fixture.state.chainState[row]));
        assertEquals(0, fixture.state.chainProgress[row]);
        assertEquals(-1, fixture.state.chainLastAdvanceTick[row]);
        assertEquals(-1, fixture.state.chainResolvedTick[row]);
    }

    @Test
    void promotionResolutionMovesProgressSuppressionAndSmallerStakeExactlyOnce() {
        Fixture fixture = new Fixture();
        long chain = fixture.autonomousChain(ChainArchetype.PROMOTE, (short) 1);
        int chainRow = fixture.state.chainIndex(chain);
        int actorRow = fixture.state.houseIndex(fixture.actor);
        int targetRow = fixture.state.houseIndex(fixture.target);
        fixture.state.housePromotionProgress[actorRow] = 75;
        fixture.state.housePromotionProgress[targetRow] = 20;
        ChainAdvancementSystem system = new ChainAdvancementSystem();

        system.tick(fixture.state, 20);

        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(70, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(80, StakeLedger.shareOf(fixture.state, fixture.target, 1, 7));
        assertEquals(HouseRank.TIER_2,
                HouseRank.fromByte(fixture.state.houseRank[actorRow]));
        assertEquals(65, fixture.state.housePromotionProgress[actorRow]);
        assertEquals(0, fixture.state.housePromotionProgress[targetRow]);

        system.tick(fixture.state, 21);
        assertEquals(70, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(65, fixture.state.housePromotionProgress[actorRow]);
        assertEquals(20, fixture.state.chainResolvedTick[chainRow]);
    }

    @Test
    void promotionReachedElsewhereResolvesWithoutReplayingPayload() {
        Fixture fixture = new Fixture();
        long chain = fixture.autonomousChain(ChainArchetype.PROMOTE, (short) 10);
        int chainRow = fixture.state.chainIndex(chain);
        int actorRow = fixture.state.houseIndex(fixture.actor);
        fixture.state.houseRank[actorRow] = HouseRank.TIER_2.toByte();
        fixture.state.housePromotionProgress[actorRow] = 5;

        new ChainAdvancementSystem().tick(fixture.state, 20);

        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(50, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(100, StakeLedger.shareOf(fixture.state, fixture.target, 1, 7));
        assertEquals(5, fixture.state.housePromotionProgress[actorRow]);
        assertEquals(0, fixture.state.chainProgress[chainRow]);
    }

    @Test
    void crossFactionPromotionChainFailsWithoutPoliticalEffects() {
        Fixture fixture = new Fixture();
        long chain = fixture.autonomousChain(ChainArchetype.PROMOTE, (short) 1);
        int chainRow = fixture.state.chainIndex(chain);
        fixture.state.houseFactionId[fixture.state.houseIndex(fixture.target)] = 2;

        new ChainAdvancementSystem().tick(fixture.state, 20);

        assertEquals(ChainState.FAILED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(50, StakeLedger.shareOf(fixture.state, fixture.actor, 1, 7));
        assertEquals(0, fixture.state.housePromotionProgress[
                fixture.state.houseIndex(fixture.actor)]);
    }

    @Test
    void civilWarResolutionPreparesHandoffWithoutApplyingFactionFlip() {
        Fixture fixture = new Fixture();
        int actorRow = fixture.state.houseIndex(fixture.actor);
        fixture.state.houseRank[actorRow] = HouseRank.TIER_3.toByte();
        fixture.state.houseRank[fixture.state.houseIndex(fixture.target)] =
                HouseRank.TIER_3.toByte();
        fixture.state.housePromotionProgress[actorRow] = 1000;
        long chain = fixture.state.addAutonomousChain(fixture.actor, fixture.target,
                1, -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 1, (byte) 128, 1);
        int chainRow = fixture.state.chainIndex(chain);
        ChainAdvancementSystem system = new ChainAdvancementSystem();

        system.tick(fixture.state, 20);

        assertEquals(ChainState.RESOLVED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(1, fixture.state.throneClaimCount);
        assertEquals(chain, fixture.state.throneClaimSourceChainId[0]);
        assertEquals(fixture.actor, fixture.state.throneClaimHouseId[0]);
        assertEquals(1, fixture.state.throneClaimSourceFactionId[0]);
        assertEquals(1, fixture.state.throneClaimMarketId[0]);
        assertEquals(ChainAdvancementSystem.CLAIMANT_FACTION_ID,
                fixture.state.factionRegistry.get(
                    fixture.state.throneClaimResultFactionId[0]));
        assertEquals(HouseRank.TIER_3,
                HouseRank.fromByte(fixture.state.houseRank[actorRow]));
        assertEquals(1000, fixture.state.housePromotionProgress[actorRow]);

        system.tick(fixture.state, 21);
        assertEquals(1, fixture.state.throneClaimCount);
    }

    @Test
    void crossFactionCivilWarFailsWithoutPreparingHandoff() {
        Fixture fixture = new Fixture();
        int actorRow = fixture.state.houseIndex(fixture.actor);
        fixture.state.houseRank[actorRow] = HouseRank.TIER_3.toByte();
        fixture.state.houseFactionId[fixture.state.houseIndex(fixture.target)] = 2;
        long chain = fixture.state.addAutonomousChain(fixture.actor, fixture.target,
                1, -1, HouseRank.TIER_3.toByte(), ChainArchetype.CIVIL_WAR,
                (short) 1, (byte) 128, 1);
        int chainRow = fixture.state.chainIndex(chain);

        new ChainAdvancementSystem().tick(fixture.state, 20);

        assertEquals(ChainState.FAILED,
                ChainState.fromByte(fixture.state.chainState[chainRow]));
        assertEquals(0, fixture.state.throneClaimCount);
        assertEquals(HouseRank.TIER_3,
                HouseRank.fromByte(fixture.state.houseRank[actorRow]));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final long actor = house(state, "Actor");
        final long target = house(state, "Target");

        Fixture() {
            state.addStake(actor, 1, 7, (short) 50);
            state.addStake(target, 1, 7, (short) 100);
        }

        long autonomousChain(short threshold) {
            return autonomousChain(ChainArchetype.CONSOLIDATE_STAKE, threshold);
        }

        long autonomousChain(ChainArchetype archetype, short threshold) {
            return state.addAutonomousChain(actor, target, 1, 7, (byte) 0,
                    archetype, threshold, (byte) 1, 1);
        }

        private static long house(CampaignState state, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
