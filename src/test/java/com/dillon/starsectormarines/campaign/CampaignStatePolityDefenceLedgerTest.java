package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The small write-once table behind a polity defence's exactly-once settlement. */
class CampaignStatePolityDefenceLedgerTest {

    @Test
    void recordsOneDefenceAndFindsItAgain() {
        CampaignState state = new CampaignState();

        int row = state.recordPolityDefence(77L, 3, true, 120);

        assertEquals(0, row);
        assertEquals(1, state.polityDefenceCount);
        assertEquals(0, state.polityDefenceRow(77L, 3));
        assertEquals(1, state.polityDefenceWon[row]);
        assertEquals(120, state.polityDefenceDay[row]);
        assertTrue(state.hasPolityDefence(77L));
    }

    @Test
    void theSamePairIsRefusedRatherThanRewritten() {
        CampaignState state = new CampaignState();
        state.recordPolityDefence(77L, 3, true, 120);

        assertEquals(-1, state.recordPolityDefence(77L, 3, false, 130));

        assertEquals(1, state.polityDefenceCount);
        assertEquals(1, state.polityDefenceWon[0], "the first verdict stands");
        assertEquals(120, state.polityDefenceDay[0]);
    }

    @Test
    void anotherRaidAtTheSameMarketIsItsOwnRow() {
        CampaignState state = new CampaignState();
        state.recordPolityDefence(77L, 3, true, 120);

        assertEquals(1, state.recordPolityDefence(78L, 3, false, 140));
        assertEquals(2, state.polityDefenceCount);
        assertTrue(state.hasPolityDefence(78L));
        assertEquals(0, state.polityDefenceWon[1]);
    }

    @Test
    void anUnrecordedRaidAndAnUnnameablePairAreNotOnTheLedger() {
        CampaignState state = new CampaignState();
        state.recordPolityDefence(77L, 3, true, 120);

        assertFalse(state.hasPolityDefence(78L));
        assertFalse(state.hasPolityDefence(0L));
        assertEquals(-1, state.polityDefenceRow(77L, 4));
        assertEquals(-1, state.recordPolityDefence(0L, 3, true, 120));
        assertEquals(-1, state.recordPolityDefence(77L, -1, true, 120));
        assertEquals(1, state.polityDefenceCount);
    }

    @Test
    void theTableGrowsPastItsInitialCapacity() {
        CampaignState state = new CampaignState();
        for (int i = 1; i <= 40; i++) state.recordPolityDefence(i, i, i % 2 == 0, i);

        assertEquals(40, state.polityDefenceCount);
        assertEquals(39, state.polityDefenceRow(40L, 40));
        assertTrue(state.hasPolityDefence(17L));
    }

    @Test
    void legacySaveBackfillsThePolityDefenceColumns() throws Exception {
        CampaignState state = new CampaignState();
        state.polityDefenceEventKey = null;
        state.polityDefenceMarketId = null;
        state.polityDefenceWon = null;
        state.polityDefenceDay = null;
        state.polityDefenceCount = 9;

        readResolve(state);

        assertNotNull(state.polityDefenceEventKey);
        assertNotNull(state.polityDefenceMarketId);
        assertNotNull(state.polityDefenceWon);
        assertNotNull(state.polityDefenceDay);
        assertEquals(0, state.polityDefenceCount, "a count cannot outrun its arrays");
        assertEquals(-1, state.polityDefenceMarketId[0]);
        assertEquals(-1, state.polityDefenceDay[0]);

        // Non-null is not the same as usable: the table has to work after the load.
        assertEquals(0, state.recordPolityDefence(77L, 3, true, 120));
        assertTrue(state.hasPolityDefence(77L));
    }

    private static void readResolve(CampaignState state) throws Exception {
        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);
    }
}
