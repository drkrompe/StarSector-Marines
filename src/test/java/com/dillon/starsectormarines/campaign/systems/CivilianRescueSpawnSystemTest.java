package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilianRescueSpawnSystemTest {

    @Test
    void firstEpochFreezesSizeDerivedEventSnapshot() {
        CampaignState state = stateWithMarkets("small", "large");
        TestMarkets markets = new TestMarkets().add("small", 3).add("large", 6);
        CivilianRescueSpawnSystem system = new CivilianRescueSpawnSystem(markets);

        system.tick(state, 29);
        assertEquals(0, state.eventCount);

        system.tick(state, 30);

        assertEquals(1, state.eventCount);
        int row = 0;
        int size = markets.eligibleSize(state.marketRegistry.get(
                state.eventMarketId[row]));
        int tier = size - 2;
        assertEquals(0L, state.eventTriggerKey[row]);
        assertEquals(30, state.eventCreatedTick[row]);
        assertEquals(33, state.eventDeadlineTick[row]);
        assertEquals(25 * tier, state.eventSuppliesRequired[row]);
        assertEquals(15 * tier, state.eventFuelRequired[row]);
        assertEquals(100 * tier * tier, state.eventCiviliansAtRisk[row]);
    }

    @Test
    void marketSelectionIgnoresRegistryInsertionOrder() {
        TestMarkets markets = new TestMarkets()
                .add("alpha", 4).add("beta", 5).add("gamma", 6);
        CampaignState forward = stateWithMarkets("alpha", "beta", "gamma");
        CampaignState reverse = stateWithMarkets("gamma", "beta", "alpha");

        new CivilianRescueSpawnSystem(markets).tick(forward, 75);
        new CivilianRescueSpawnSystem(markets).tick(reverse, 75);

        assertEquals(selectedMarket(forward), selectedMarket(reverse));
        assertEquals(1L, forward.eventTriggerKey[0]);
        assertEquals(1L, reverse.eventTriggerKey[0]);
    }

    @Test
    void epochIdentityPreventsRepeatAfterEarlyRefusal() {
        CampaignState state = stateWithMarkets("market");
        CivilianRescueSpawnSystem system = new CivilianRescueSpawnSystem(
                new TestMarkets().add("market", 4));

        system.tick(state, 30);
        long first = state.eventId[0];
        assertEquals(CivilianRescueEvent.Result.REFUSED,
                CivilianRescueEvent.refuse(state, first, 30));

        system.tick(state, 31);
        system.tick(state, 74);
        assertEquals(1, state.eventCount);

        system.tick(state, 75);
        assertEquals(2, state.eventCount);
        assertEquals(1L, state.eventTriggerKey[1]);
    }

    @Test
    void pendingOrCommittedRescueBlocksLaterEpoch() {
        CampaignState state = stateWithMarkets("market");
        CivilianRescueSpawnSystem system = new CivilianRescueSpawnSystem(
                new TestMarkets().add("market", 4));
        system.tick(state, 30);

        system.tick(state, 75);
        assertEquals(1, state.eventCount);

        state.eventState[0] = CampaignEventState.COMMITTED.toByte();
        system.tick(state, 120);
        assertEquals(1, state.eventCount);
    }

    @Test
    void lateFirstTickCreatesOnlyCurrentEpoch() {
        CampaignState state = stateWithMarkets("market");
        CivilianRescueSpawnSystem system = new CivilianRescueSpawnSystem(
                new TestMarkets().add("market", 4));

        system.tick(state, 200);
        system.tick(state, 200);

        assertEquals(1, state.eventCount);
        assertEquals(3L, state.eventTriggerKey[0]);
        assertEquals(200, state.eventCreatedTick[0]);
    }

    @Test
    void ineligibleMarketsDoNotProduceAnEvent() {
        CampaignState state = stateWithMarkets("hidden", "tiny", "missing");
        TestMarkets markets = new TestMarkets()
                .add("hidden", -1).add("tiny", 2);

        new CivilianRescueSpawnSystem(markets).tick(state, 30);

        assertEquals(0, state.eventCount);
    }

    private static CampaignState stateWithMarkets(String... marketIds) {
        CampaignState state = new CampaignState();
        for (String marketId : marketIds) state.marketRegistry.intern(marketId);
        return state;
    }

    private static String selectedMarket(CampaignState state) {
        return state.marketRegistry.get(state.eventMarketId[0]);
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
