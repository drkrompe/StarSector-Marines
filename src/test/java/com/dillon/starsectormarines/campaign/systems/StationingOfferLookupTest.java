package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationingOfferLookupTest {

    @Test
    void findsOnlyMatchingLocalOfferedStationingRow() {
        CampaignState state = new CampaignState();
        state.addContract(7L, -1L, -1L, ContractType.STRIKE, ContractState.OFFERED,
                0, -1, 5, (byte) 1, -1, 3, -1,
                25_000, 0, (byte) 60, (byte) 60, (byte) 100);
        long expected = state.addContract(7L, -1L, -1L, ContractType.GARRISON,
                ContractState.OFFERED, 0, -1, 5, (byte) 0, -1, 3, -1,
                0, 0, (byte) 25, (byte) 25, (byte) 100);

        assertEquals(expected, StationingOfferLookup.find(state, 7L, 3));
        assertEquals(-1L, StationingOfferLookup.find(state, 8L, 3));
        assertEquals(-1L, StationingOfferLookup.find(state, 7L, 4));
    }

    @Test
    void ignoresActiveAndTerminalStationingRows() {
        CampaignState state = new CampaignState();
        state.addContract(7L, -1L, -1L, ContractType.CADRE, ContractState.ACTIVE,
                0, 30, -1, (byte) 0, -1, 3, -1,
                0, 500, (byte) 5, (byte) 5, (byte) 100);

        assertEquals(-1L, StationingOfferLookup.find(state, 7L, 3));
        assertEquals(-1L, StationingOfferLookup.find(null, 7L, 3));
    }

    @Test
    void findsOnlyMatchingLocalActiveStationingRowForManagement() {
        CampaignState state = new CampaignState();
        long expected = state.addContract(7L, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 0, 30, -1, (byte) 0, 2, 3, -1,
                0, 500, (byte) 5, (byte) 5, (byte) 100);
        state.addContract(7L, -1L, -1L, ContractType.GARRISON,
                ContractState.COMPLETED, 0, 30, -1, (byte) 0, -1, 3, -1,
                0, 500, (byte) 25, (byte) 25, (byte) 100);

        assertEquals(expected, StationingOfferLookup.findActive(state, 7L, 3));
        assertEquals(-1L, StationingOfferLookup.findActive(state, 7L, 4));
        assertEquals(-1L, StationingOfferLookup.findActive(state, 8L, 3));
    }

    @Test
    void pendingDefenseRemainsVisibleForManagement() {
        CampaignState state = new CampaignState();
        long expected = state.addContract(7L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 0, 30, -1, (byte) 0, 2, 3, -1,
                0, 500, (byte) 25, (byte) 25, (byte) 100);

        assertEquals(expected, StationingOfferLookup.findActive(state, 7L, 3));
    }
}
