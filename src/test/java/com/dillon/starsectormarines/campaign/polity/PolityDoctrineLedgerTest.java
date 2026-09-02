package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolityDoctrineLedgerTest {

    /** A save from before the polity had a doctrine has spent nothing. */
    @Test
    void aLegacySaveReadsAsNothingSpent() {
        assertEquals(PolityDoctrine.NONE, PolityDoctrineLedger.read(new CampaignState()));
        assertEquals(PolityDoctrine.NONE, PolityDoctrineLedger.read(null));
    }

    @Test
    void whatIsWrittenIsWhatIsRead() {
        CampaignState state = new CampaignState();

        PolityDoctrineLedger.write(state, PolityDoctrine.of(1, 2, 0));

        assertEquals(PolityDoctrine.of(1, 2, 0), PolityDoctrineLedger.read(state));
        assertEquals(1, state.polityDoctrineQuality);
        assertEquals(2, state.polityDoctrineNumbers);
        assertEquals(0, state.polityDoctrineHeavySupport);
    }

    @Test
    void writingNothingSpendsNothing() {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(0, 2, 1));

        PolityDoctrineLedger.write(state, null);

        assertEquals(PolityDoctrine.NONE, PolityDoctrineLedger.read(state));
    }

    /**
     * An overspent save loads as a legal allocation rather than refusing to load: the
     * point ceiling can move, and a save is not a place to throw from.
     */
    @Test
    void anOverspentSaveIsClampedOnRead() {
        CampaignState state = new CampaignState();
        state.polityDoctrineQuality = 9;
        state.polityDoctrineNumbers = 9;
        state.polityDoctrineHeavySupport = 9;

        PolityDoctrine doctrine = PolityDoctrineLedger.read(state);

        assertEquals(PolityDoctrine.POINTS,
                doctrine.quality() + doctrine.numbers() + doctrine.heavySupport());
        assertEquals(PolityDoctrine.of(2, 1, 0), doctrine,
                "the overspend comes back off heavy support first, then numbers");
    }

    @Test
    void aNegativeAxisIsClampedRatherThanRefunded() {
        CampaignState state = new CampaignState();
        state.polityDoctrineQuality = -4;
        state.polityDoctrineNumbers = 2;

        assertEquals(PolityDoctrine.of(0, 2, 0), PolityDoctrineLedger.read(state));
    }
}
