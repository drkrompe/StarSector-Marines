package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateStationedStrengthColumnsTest {

    @Test
    void legacySaveBackfillsStationedStrengthColumns() throws Exception {
        CampaignState state = new CampaignState();
        state.stationedStrengthContractId = null;
        state.stationedStrengthMarketId = null;

        readResolve(state);

        assertNotNull(state.stationedStrengthContractId);
        assertNotNull(state.stationedStrengthMarketId);
        assertEquals(state.stationedStrengthContractId.length,
                state.stationedStrengthMarketId.length);
        assertEquals(0, state.stationedStrengthCount);
        assertEquals(-1L, state.stationedStrengthContractId[0]);
        assertEquals(-1, state.stationedStrengthMarketId[0]);

        // The load is only safe if the table works afterwards, not merely non-null.
        state.recordStationedStrength(42L, 3);
        assertEquals(1, state.stationedStrengthCount);
        assertEquals(0, state.stationedStrengthRow(42L));
        assertEquals(3, state.stationedStrengthMarketId[0]);
    }

    @Test
    void countCannotOutrunTheArraysItAddresses() throws Exception {
        CampaignState state = new CampaignState();
        state.stationedStrengthContractId = null;
        state.stationedStrengthMarketId = null;
        state.stationedStrengthCount = 7;

        readResolve(state);

        assertEquals(0, state.stationedStrengthCount);
        assertEquals(-1, state.stationedStrengthRow(42L));
    }

    private static void readResolve(CampaignState state) throws Exception {
        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);
    }
}
