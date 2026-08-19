package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingmakerTestamentTest {

    @Test
    void revealIsOneWayAndTouchesOnlyDeliveryState() {
        CampaignState state = new CampaignState();
        state.moralMercy = 15;
        state.moralIntegrity = -25;
        state.moralStewardship = 10;
        state.moralInstitutionalism = -20;
        long testament = state.sealKingmakerTestament(7L, 8L, 9L, 10L,
                1, 2, 3, (short) 60,
                15, -25, 10, -20, 4, 90);
        int row = state.kingmakerTestamentIndex(testament);

        assertEquals(KingmakerTestament.Result.REVEALED,
                KingmakerTestament.reveal(state, testament));
        assertEquals(KingmakerTestament.Result.ALREADY_REVEALED,
                KingmakerTestament.reveal(state, testament));

        assertEquals(KingmakerTestamentState.REVEALED,
                KingmakerTestamentState.fromByte(
                        state.kingmakerTestamentState[row]));
        assertEquals(1, state.kingmakerTestamentCount);
        assertEquals(15, state.moralMercy);
        assertEquals(-25, state.moralIntegrity);
        assertEquals(10, state.moralStewardship);
        assertEquals(-20, state.moralInstitutionalism);
        assertEquals(0, state.houseCount);
        assertEquals(0, state.chainCount);
        assertEquals(0, state.eventCount);
        assertEquals(0, state.contractCount);
        assertEquals(0, state.repCount);
    }

    @Test
    void missingAndNonSealedRowsAreRejected() {
        CampaignState state = new CampaignState();
        long testament = state.sealKingmakerTestament(7L, 8L, 9L, 10L,
                1, 2, 3, (short) 60,
                0, 0, 0, -20, 1, 90);
        int row = state.kingmakerTestamentIndex(testament);
        state.kingmakerTestamentState[row] =
                KingmakerTestamentState.NONE.toByte();

        assertEquals(KingmakerTestament.Result.INVALID,
                KingmakerTestament.reveal(state, testament));
        assertEquals(KingmakerTestament.Result.INVALID,
                KingmakerTestament.reveal(state, 999L));
        assertEquals(KingmakerTestament.Result.INVALID,
                KingmakerTestament.reveal(null, testament));
    }
}
