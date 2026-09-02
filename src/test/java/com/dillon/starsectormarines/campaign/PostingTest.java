package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recognition rules for a row whose employer is nobody ({@code contracts-nouns.md}). */
class PostingTest {

    private static final String MARKET = "jangala";

    @Test
    void aGarrisonRowWithNoEmployerIsAPosting() {
        CampaignState state = new CampaignState();
        garrison(state, Posting.NO_PATRON, ContractState.ACTIVE, MARKET);

        assertTrue(Posting.isPosting(state, 0));
    }

    @Test
    void anEmployedGarrisonAndEveryOtherWorkKindIsNot() {
        CampaignState employed = new CampaignState();
        garrison(employed, 7L, ContractState.ACTIVE, MARKET);
        assertFalse(Posting.isPosting(employed, 0), "a patron makes it a contract");

        for (ContractType type : List.of(ContractType.CADRE, ContractType.STRIKE,
                ContractType.ESCORT, ContractType.PLANETARY_ASSAULT,
                ContractType.EXTRACTION)) {
            CampaignState state = new CampaignState();
            state.addContract(Posting.NO_PATRON, -1L, -1L, type, ContractState.ACTIVE,
                    0, -1, -1, (byte) 0, -1, state.marketRegistry.intern(MARKET), -1,
                    0, 0, (byte) 0, (byte) 0, (byte) 100);
            assertFalse(Posting.isPosting(state, 0), "type " + type);
        }
    }

    /** House id 0 is a legal house here, so the sentinel has to be tested as negative. */
    @Test
    void houseZeroIsAnEmployerAndNotTheSentinel() {
        CampaignState state = new CampaignState();
        garrison(state, 0L, ContractState.ACTIVE, MARKET);

        assertFalse(Posting.isPosting(state, 0));
    }

    @Test
    void anOutOfRangeRowIsNotAPosting() {
        CampaignState state = new CampaignState();
        garrison(state, Posting.NO_PATRON, ContractState.ACTIVE, MARKET);

        assertFalse(Posting.isPosting(state, -1));
        assertFalse(Posting.isPosting(state, 1));
        assertFalse(Posting.isPosting(null, 0));
    }

    @Test
    void activeRowAtFindsThePostingStandingOnThatMarket() {
        CampaignState state = new CampaignState();
        int elsewhere = state.marketRegistry.intern("elsewhere");
        int here = state.marketRegistry.intern(MARKET);
        garrison(state, Posting.NO_PATRON, ContractState.ACTIVE, "elsewhere");
        garrison(state, 7L, ContractState.ACTIVE, MARKET);
        garrison(state, Posting.NO_PATRON, ContractState.ACTIVE, MARKET);

        assertEquals(2, Posting.activeRowAt(state, here));
        assertEquals(0, Posting.activeRowAt(state, elsewhere));
        assertEquals(-1, Posting.activeRowAt(state, -1));
        assertEquals(-1, Posting.activeRowAt(null, here));
    }

    /** A defence arms the row into IN_PROGRESS; the posting is still standing. */
    @Test
    void aPostingUnderAnArmedDefenceIsStillTheLiveRow() {
        CampaignState state = new CampaignState();
        garrison(state, Posting.NO_PATRON, ContractState.IN_PROGRESS, MARKET);

        assertEquals(0, Posting.activeRowAt(state, state.marketRegistry.intern(MARKET)));
    }

    @Test
    void aTerminalPostingIsNotFound() {
        for (ContractState ended : List.of(ContractState.COMPLETED, ContractState.FAILED,
                ContractState.ABANDONED, ContractState.DEFAULTED, ContractState.EXPIRED)) {
            CampaignState state = new CampaignState();
            garrison(state, Posting.NO_PATRON, ended, MARKET);

            assertEquals(-1, Posting.activeRowAt(state,
                    state.marketRegistry.intern(MARKET)), "state " + ended);
            assertTrue(Posting.isPosting(state, 0),
                    "a settled posting is still recognisably one: " + ended);
        }
    }

    @Test
    void isPostingIdResolvesThroughTheContractIndex() {
        CampaignState state = new CampaignState();
        long postingId = garrison(state, Posting.NO_PATRON, ContractState.ACTIVE, MARKET);
        long contractId = garrison(state, 7L, ContractState.ACTIVE, MARKET);

        assertTrue(Posting.isPostingId(state, postingId));
        assertFalse(Posting.isPostingId(state, contractId));
        assertFalse(Posting.isPostingId(state, 9_999L));
    }

    private static long garrison(CampaignState state, long patronId,
                                 ContractState contractState, String marketId) {
        return state.addContract(patronId, -1L, -1L, ContractType.GARRISON, contractState,
                0, -1, -1, (byte) 0, -1, state.marketRegistry.intern(marketId), -1,
                0, 0, (byte) 0, (byte) 0, (byte) 100);
    }
}
