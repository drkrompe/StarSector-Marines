package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every branch of the polity defence's record, including a replay of the same outcome. */
class PolityDefenceResolutionTest {

    private static PolityDefenceMissionKey key(int marketSlot, long eventKey) {
        PolityDefenceMissionKey parsed = PolityDefenceMissionKey.parse(
                PolityDefenceMissionKey.encode(marketSlot, eventKey));
        assertNotNull(parsed);
        return parsed;
    }

    @Test
    void aWonDefenceIsRecordedAsWon() {
        CampaignState state = new CampaignState();

        assertEquals(PolityDefenceResolution.Result.RESOLVED_WON,
                PolityDefenceResolution.apply(state, key(3, 77L), true, 120));

        int row = state.polityDefenceRow(77L, 3);
        assertEquals(0, row);
        assertEquals(1, state.polityDefenceWon[row]);
        assertEquals(120, state.polityDefenceDay[row]);
    }

    @Test
    void aLostDefenceIsRecordedAsLost() {
        CampaignState state = new CampaignState();

        assertEquals(PolityDefenceResolution.Result.RESOLVED_LOST,
                PolityDefenceResolution.apply(state, key(3, 77L), false, 120));

        assertEquals(0, state.polityDefenceWon[state.polityDefenceRow(77L, 3)]);
    }

    @Test
    void aReplayedResolutionSettlesNothingAndWritesNothing() {
        CampaignState state = new CampaignState();
        PolityDefenceMissionKey key = key(3, 77L);
        PolityDefenceResolution.apply(state, key, true, 120);

        assertEquals(PolityDefenceResolution.Result.ALREADY_SETTLED,
                PolityDefenceResolution.apply(state, key, true, 130));
        // A replay that flips the verdict must not rewrite the row either.
        assertEquals(PolityDefenceResolution.Result.ALREADY_SETTLED,
                PolityDefenceResolution.apply(state, key, false, 140));

        assertEquals(1, state.polityDefenceCount);
        assertEquals(1, state.polityDefenceWon[0]);
        assertEquals(120, state.polityDefenceDay[0]);
    }

    @Test
    void theSameRaidAtAnotherMarketIsItsOwnDefence() {
        CampaignState state = new CampaignState();
        PolityDefenceResolution.apply(state, key(3, 77L), true, 120);

        assertEquals(PolityDefenceResolution.Result.RESOLVED_LOST,
                PolityDefenceResolution.apply(state, key(4, 77L), false, 121));
        assertEquals(2, state.polityDefenceCount);
    }

    @Test
    void nothingToSettleIsRefused() {
        assertEquals(PolityDefenceResolution.Result.REFUSED,
                PolityDefenceResolution.apply(null, key(3, 77L), true, 120));
        assertEquals(PolityDefenceResolution.Result.REFUSED,
                PolityDefenceResolution.apply(new CampaignState(), null, true, 120));
        // A key that names no market cannot be recorded, so it settles nothing either.
        CampaignState state = new CampaignState();
        assertEquals(PolityDefenceResolution.Result.REFUSED,
                PolityDefenceResolution.apply(state, key(-1, 77L), true, 120));
        assertEquals(0, state.polityDefenceCount);
    }
}
