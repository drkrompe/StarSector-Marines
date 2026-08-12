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

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final long actor = house(state, "Actor");
        final long target = house(state, "Target");

        Fixture() {
            state.addStake(actor, 1, 7, (short) 50);
            state.addStake(target, 1, 7, (short) 100);
        }

        long autonomousChain(short threshold) {
            return state.addAutonomousChain(actor, target, 1, 7, (byte) 0,
                    ChainArchetype.CONSOLIDATE_STAKE, threshold, (byte) 1, 1);
        }

        private static long house(CampaignState state, String name) {
            return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                    HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
        }
    }
}
