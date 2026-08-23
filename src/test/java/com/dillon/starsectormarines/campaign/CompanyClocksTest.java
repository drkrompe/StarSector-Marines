package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyClocksTest {

    private static final int DAY = 100;

    // ---------- what counts as a clock ----------

    @Test
    void aLapsingOfferIsNeverAClock() {
        // The valence split this pane is built on: obligations bite, opportunities
        // lapse. A missed offer costs nothing but the job, and its only action is to
        // fly somewhere else — which is the contract board's, not this pane's.
        // See c11-the-contract-board.md.
        CampaignState state = new CampaignState();
        state.addContract(7L, -1L, -1L, ContractType.GARRISON, ContractState.OFFERED,
                DAY, -1, DAY + 3, (byte) 0, -1, 12, -1, 0, 5_000,
                (byte) 25, (byte) 25, (byte) 100);

        assertTrue(CompanyClocks.rows(state, null, DAY).isEmpty());
    }

    @Test
    void onlyRunningAssignmentsHaveATermClock() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY + 20);
        addStationing(state, ContractType.CADRE, ContractState.IN_PROGRESS, DAY + 40);
        // Already resolved one way or another — nothing is still counting down.
        addStationing(state, ContractType.GARRISON, ContractState.COMPLETED, DAY + 5);
        addStationing(state, ContractType.CADRE, ContractState.DEFAULTED, DAY + 5);
        addStationing(state, ContractType.GARRISON, ContractState.FAILED, DAY + 5);

        List<CompanyClocks.Entry> rows = CompanyClocks.rows(state, null, DAY);

        assertEquals(2, rows.size());
        for (CompanyClocks.Entry row : rows) {
            assertEquals(CompanyClocks.Kind.TERM_ENDING, row.kind);
        }
    }

    @Test
    void aStationingRowWithNoTermSetContributesNothing() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, -1);

        assertTrue(CompanyClocks.rows(state, null, DAY).isEmpty());
    }

    @Test
    void missionModeContractsHaveNoClockHere() {
        CampaignState state = new CampaignState();
        state.addContract(7L, 8L, -1L, ContractType.STRIKE, ContractState.IN_PROGRESS,
                DAY, DAY + 10, -1, (byte) 1, -1, 12, -1, 25_000, 0,
                (byte) 60, (byte) 60, (byte) 100);

        assertTrue(CompanyClocks.rows(state, null, DAY).isEmpty());
    }

    // ---------- ordering ----------

    @Test
    void rowsRunSoonestFirst() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY + 30);
        addStationing(state, ContractType.CADRE, ContractState.ACTIVE, DAY + 2);
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY + 11);

        List<CompanyClocks.Entry> rows = CompanyClocks.rows(state, null, DAY);

        assertEquals(3, rows.size());
        assertEquals(2, rows.get(0).daysRemaining(DAY));
        assertEquals(11, rows.get(1).daysRemaining(DAY));
        assertEquals(30, rows.get(2).daysRemaining(DAY));
    }

    @Test
    void equalDeadlinesBreakOnContractIdSoTheListCannotReshuffle() {
        CampaignState state = new CampaignState();
        long first = addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY + 9);
        long second = addStationing(state, ContractType.CADRE, ContractState.ACTIVE, DAY + 9);

        List<CompanyClocks.Entry> rows = CompanyClocks.rows(state, null, DAY);

        assertTrue(first < second);
        assertEquals(first, rows.get(0).contractId);
        assertEquals(second, rows.get(1).contractId);
    }

    @Test
    void anOverdueClockReadsAsDueRatherThanNegative() {
        CampaignState state = new CampaignState();
        // The lapse system runs on day boundaries; a frame read can land after one.
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY - 4);

        assertEquals(0, CompanyClocks.rows(state, null, DAY).get(0).daysRemaining(DAY));
    }

    // ---------- responses ----------

    @Test
    void aPendingResponseIsItsOwnClockAndCarriesItsNotice() {
        CampaignState state = new CampaignState();
        long contractId = addStationing(state, ContractType.GARRISON,
                ContractState.IN_PROGRESS, DAY + 40);
        armGarrisonDefense(state, contractId, DAY + 3);

        List<CompanyClocks.Entry> rows = CompanyClocks.rows(state, null, DAY);
        CompanyClocks.Entry response = rows.get(0);

        assertEquals(CompanyClocks.Kind.RESPONSE, response.kind);
        assertEquals(3, response.daysRemaining(DAY));
        // Respond routes through the notice, exactly as the event popup's Deploy does.
        assertNotNull(response.notice);
        assertEquals(contractId, response.notice.contractId);
    }

    @Test
    void oneContractCanOwnTwoClocksAndTheTermKnowsTheResponseWillFailIt() {
        CampaignState state = new CampaignState();
        long contractId = addStationing(state, ContractType.GARRISON,
                ContractState.IN_PROGRESS, DAY + 12);
        armGarrisonDefense(state, contractId, DAY + 2);

        List<CompanyClocks.Entry> rows = CompanyClocks.rows(state, null, DAY);

        assertEquals(2, rows.size());
        assertEquals(CompanyClocks.Kind.RESPONSE, rows.get(0).kind);
        assertEquals(CompanyClocks.Kind.TERM_ENDING, rows.get(1).kind);
        // G31's invariant: a term boundary reached with a response still owing fails the
        // contract rather than completing it. The row says so before it happens.
        assertTrue(rows.get(1).failsOnExpiry);
        assertFalse(rows.get(0).failsOnExpiry);
        assertNull(rows.get(1).notice);
    }

    @Test
    void aTermWithNoResponseOwingIsNotMarkedAsFailing() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY + 12);

        assertFalse(CompanyClocks.rows(state, null, DAY).get(0).failsOnExpiry);
    }

    // ---------- naming ----------

    @Test
    void theEmployerIsNamedFromTheHouseRegistry() {
        CampaignState state = new CampaignState();
        long house = state.addHouse(0, 0, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.ESTABLISHED, "Kazeron");
        state.addContract(house, -1L, -1L, ContractType.GARRISON, ContractState.ACTIVE,
                DAY, DAY + 8, -1, (byte) 0, -1, 12, -1, 0, 5_000,
                (byte) 25, (byte) 25, (byte) 100);

        assertEquals("Kazeron", CompanyClocks.rows(state, null, DAY).get(0).patronName);
    }

    @Test
    void anUnknownEmployerIsLeftUnnamedRatherThanRenderedAsAnId() {
        CampaignState state = new CampaignState();
        addStationing(state, ContractType.GARRISON, ContractState.ACTIVE, DAY + 8);

        assertNull(CompanyClocks.rows(state, null, DAY).get(0).patronName);
    }

    // ---------- edges ----------

    @Test
    void anEmptyOrAbsentStateHasNoClocks() {
        assertTrue(CompanyClocks.rows(new CampaignState(), null, DAY).isEmpty());
        assertTrue(CompanyClocks.rows(null, null, DAY).isEmpty());
    }

    // ---------- fixtures ----------

    private static long addStationing(CampaignState state, ContractType type,
                                      ContractState contractState, int expiresTick) {
        return state.addContract(-1L, -1L, -1L, type, contractState, DAY - 10,
                expiresTick, -1, (byte) 0, -1, 12, -1, 0, 5_000,
                (byte) 25, (byte) 25, (byte) 100);
    }

    /** The four fields {@code GarrisonDefensePayload.from} requires to see a live event. */
    private static void armGarrisonDefense(CampaignState state, long contractId,
                                           int deadlineDay) {
        int row = state.contractIndex(contractId);
        state.contractDefenseEventKey[row] = 4242L;
        state.contractDefenseTriggerType[row] =
                GarrisonDefenseTriggerType.RIVAL_STRIKE.toByte();
        state.contractDefenseTriggeredTick[row] = DAY;
        state.contractResponseDeadlineTick[row] = deadlineDay;
    }
}
