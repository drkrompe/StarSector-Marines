package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainDiscoveryTest {

    @Test
    void queryChoosesEarliestDiscoveryThenLowestStableChainId() {
        CampaignState state = new CampaignState();
        long actor = house(state, "Actor");
        long target = house(state, "Target");
        int later = chain(state, actor, target, 20);
        int tiedLowerId = chain(state, actor, target, 10);
        chain(state, actor, target, 10);

        int found = ChainDiscovery.findActiveThreatAgainst(state, target);

        assertEquals(tiedLowerId, found);
        assertTrue(ChainDiscovery.isDiscoveredActive(state, found));
        assertTrue(state.chainId[tiedLowerId] < state.chainId[later] ||
                state.chainDiscoveredTick[tiedLowerId] < state.chainDiscoveredTick[later]);
    }

    @Test
    void queryExcludesUnknownTerminalPlayerBackedAndOtherTargetRows() {
        CampaignState state = new CampaignState();
        long actor = house(state, "Actor");
        long target = house(state, "Target");
        long other = house(state, "Other");
        int unknown = chain(state, actor, target, -1);
        int terminal = chain(state, actor, target, 10);
        state.chainState[terminal] = ChainState.RESOLVED.toByte();
        int playerBacked = chain(state, actor, target, 10);
        state.chainPatron[playerBacked] = actor;
        chain(state, actor, other, 10);

        assertEquals(-1, ChainDiscovery.findActiveThreatAgainst(state, target));
        assertFalse(ChainDiscovery.isDiscoveredActive(state, unknown));
        assertFalse(ChainDiscovery.isDiscoveredActive(state, terminal));
        assertFalse(ChainDiscovery.isDiscoveredActive(state, playerBacked));
    }

    private static int chain(CampaignState state, long actor, long target, int discovered) {
        long id = state.addAutonomousChain(actor, target, 1, 7, (byte) 0,
                ChainArchetype.CONSOLIDATE_STAKE, (short) 45, (byte) 32, 1);
        int row = state.chainIndex(id);
        state.chainDiscoveredTick[row] = discovered;
        return row;
    }

    private static long house(CampaignState state, String name) {
        return state.addHouse(1, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
