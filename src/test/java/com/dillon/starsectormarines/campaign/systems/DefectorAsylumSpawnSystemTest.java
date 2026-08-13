package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefectorAsylumSpawnSystemTest {

    @Test
    void firstEpochSelectsStableEligibleChainAndFreezesTierTerms() {
        Fixture fixture = new Fixture();
        fixture.chain(50, ChainState.ACTIVE, (byte) 0);
        fixture.chain(54, ChainState.ACTIVE, (byte) 1);
        fixture.chain(-1, ChainState.ACTIVE, (byte) 2);
        DefectorAsylumSpawnSystem system = new DefectorAsylumSpawnSystem();

        system.tick(fixture.state, 59);
        assertEquals(0, fixture.state.eventCount);

        int expected = DefectorAsylumSpawnSystem.selectChain(
                fixture.state, 0, 60);
        system.tick(fixture.state, 60);

        assertEquals(1, fixture.state.eventCount);
        int row = 0;
        assertEquals(CampaignEventType.DEFECTOR_ASYLUM,
                CampaignEventType.fromByte(fixture.state.eventType[row]));
        assertEquals(fixture.state.chainId[expected],
                fixture.state.eventSourceChainId[row]);
        assertEquals(fixture.actor, fixture.state.eventActorHouseId[row]);
        assertEquals(fixture.target, fixture.state.eventTargetHouseId[row]);
        assertEquals(fixture.market, fixture.state.eventMarketId[row]);
        int tier = (fixture.state.chainTier[expected] & 0xFF) + 1;
        assertEquals(10 * tier, fixture.state.eventSuppliesRequired[row]);
        assertEquals(5 * tier, fixture.state.eventFuelRequired[row]);
        assertEquals(20_000 * tier, fixture.state.eventCreditsOffered[row]);
        assertEquals(0L, fixture.state.eventTriggerKey[row]);
    }

    @Test
    void discoveryMustBeFiveDaysOldAndChainMustRemainActive() {
        Fixture fixture = new Fixture();
        fixture.chain(56, ChainState.ACTIVE, (byte) 0);
        fixture.chain(40, ChainState.RESOLVED, (byte) 0);
        DefectorAsylumSpawnSystem system = new DefectorAsylumSpawnSystem();

        system.tick(fixture.state, 60);
        assertEquals(0, fixture.state.eventCount);

        system.tick(fixture.state, 61);
        assertEquals(1, fixture.state.eventCount);
    }

    @Test
    void epochAndSourceIdentityPreventReplayButPermitLaterUnusedSource() {
        Fixture fixture = new Fixture();
        fixture.chain(10, ChainState.ACTIVE, (byte) 0);
        fixture.chain(20, ChainState.ACTIVE, (byte) 1);
        DefectorAsylumSpawnSystem system = new DefectorAsylumSpawnSystem();

        system.tick(fixture.state, 60);
        long firstSource = fixture.state.eventSourceChainId[0];
        DefectorAsylumEvent.refuse(
                fixture.state, fixture.state.eventId[0], 60);

        system.tick(fixture.state, 61);
        system.tick(fixture.state, 119);
        assertEquals(1, fixture.state.eventCount);

        system.tick(fixture.state, 120);
        assertEquals(2, fixture.state.eventCount);
        assertEquals(1L, fixture.state.eventTriggerKey[1]);
        long secondSource = fixture.state.eventSourceChainId[1];
        assertNotEquals(firstSource, secondSource);
    }

    @Test
    void openRescueBlocksDefectorAndOpenDefectorBlocksRescue() {
        Fixture defectorBlocked = new Fixture();
        defectorBlocked.chain(10, ChainState.ACTIVE, (byte) 0);
        CivilianRescueSpawnSystem rescue = new CivilianRescueSpawnSystem(
                new TestMarkets().add("market", 4));
        rescue.tick(defectorBlocked.state, 30);

        new DefectorAsylumSpawnSystem().tick(defectorBlocked.state, 60);
        assertEquals(1, defectorBlocked.state.eventCount);
        assertEquals(CampaignEventType.CIVILIAN_RESCUE,
                CampaignEventType.fromByte(defectorBlocked.state.eventType[0]));

        Fixture rescueBlocked = new Fixture();
        int chainRow = rescueBlocked.chain(10, ChainState.ACTIVE, (byte) 0);
        long event = DefectorAsylumEvent.prepare(rescueBlocked.state, 0L,
                rescueBlocked.state.chainId[chainRow], rescueBlocked.actor,
                rescueBlocked.target, rescueBlocked.market,
                30, 33, 10, 5, 20_000);
        assertEquals(CampaignEventState.PENDING_CHOICE,
                CampaignEventState.fromByte(rescueBlocked.state.eventState[
                        rescueBlocked.state.eventIndex(event)]));

        rescue.tick(rescueBlocked.state, 30);
        assertEquals(1, rescueBlocked.state.eventCount);
        assertEquals(CampaignEventType.DEFECTOR_ASYLUM,
                CampaignEventType.fromByte(rescueBlocked.state.eventType[0]));
    }

    @Test
    void selectionScoreIsDeterministicAndEpochSensitive() {
        long first = DefectorAsylumSpawnSystem.selectionScore(2, 77L);

        assertEquals(first, DefectorAsylumSpawnSystem.selectionScore(2, 77L));
        assertNotEquals(first, DefectorAsylumSpawnSystem.selectionScore(3, 77L));
        assertEquals(2L, DefectorAsylumSpawnSystem.triggerKey(2));
    }

    @Test
    void physicalChainRowOrderCannotChangeSelectedIdentity() {
        Fixture fixture = new Fixture();
        int firstRow = fixture.chain(10, ChainState.ACTIVE, (byte) 0);
        int secondRow = fixture.chain(20, ChainState.ACTIVE, (byte) 2);
        int selected = DefectorAsylumSpawnSystem.selectChain(
                fixture.state, 3, 200);
        long expectedId = fixture.state.chainId[selected];

        swap(fixture.state.chainId, firstRow, secondRow);
        swap(fixture.state.chainActorHouseId, firstRow, secondRow);
        swap(fixture.state.chainTarget, firstRow, secondRow);
        swap(fixture.state.chainMarketId, firstRow, secondRow);
        swap(fixture.state.chainTier, firstRow, secondRow);
        swap(fixture.state.chainState, firstRow, secondRow);
        swap(fixture.state.chainDiscoveredTick, firstRow, secondRow);

        int reordered = DefectorAsylumSpawnSystem.selectChain(
                fixture.state, 3, 200);
        assertEquals(expectedId, fixture.state.chainId[reordered]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long actor = house(state, market, "Actor");
        final long target = house(state, market, "Target");

        int chain(int discoveredDay, ChainState chainState, byte tier) {
            long id = state.addAutonomousChain(actor, target, market, 7, tier,
                    ChainArchetype.CONSOLIDATE_STAKE,
                    (short) 45, (byte) 32, 1);
            int row = state.chainIndex(id);
            state.chainDiscoveredTick[row] = discoveredDay;
            state.chainState[row] = chainState.toByte();
            return row;
        }
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }

    private static void swap(long[] values, int first, int second) {
        long value = values[first];
        values[first] = values[second];
        values[second] = value;
    }

    private static void swap(int[] values, int first, int second) {
        int value = values[first];
        values[first] = values[second];
        values[second] = value;
    }

    private static void swap(byte[] values, int first, int second) {
        byte value = values[first];
        values[first] = values[second];
        values[second] = value;
    }

    private static final class TestMarkets
            implements CivilianRescueSpawnSystem.MarketSource {
        private final Map<String, Integer> sizes = new HashMap<>();

        TestMarkets add(String marketId, int size) {
            sizes.put(marketId, size);
            return this;
        }

        @Override
        public int eligibleSize(String marketId) {
            Integer size = sizes.get(marketId);
            return size != null ? size : -1;
        }
    }
}
