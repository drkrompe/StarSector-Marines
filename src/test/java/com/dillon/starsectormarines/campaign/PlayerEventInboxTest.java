package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.StationingLapseSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEventInboxTest {

    private static final int DAY = 40;

    @Test
    void aPendingGarrisonDefenseBecomesANotice() {
        CampaignState state = new CampaignState();
        long contractId = addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, DAY, 900L);

        List<PlayerEventNotice> notices = PlayerEventInbox.pending(state, null, DAY);

        assertEquals(1, notices.size());
        PlayerEventNotice notice = notices.get(0);
        assertEquals(PlayerEventNotice.Kind.GARRISON_DEFENSE, notice.kind);
        assertEquals(contractId, notice.contractId);
        assertEquals(900L, notice.sourceKey);
        assertEquals(12, notice.marketId);
        assertEquals(DAY, notice.triggeredDay);
        assertEquals("garrisonDefensePending", notice.headerKey);
        assertEquals("garrisonDefenseVanillaRaid", notice.labelKey);
    }

    @Test
    void aPendingCadreIncidentBecomesANoticeIdentifiedByItsDueDay() {
        CampaignState state = new CampaignState();
        addCadre(state, 1L, 7, 500);
        armIncident(state, 0, DAY);

        List<PlayerEventNotice> notices = PlayerEventInbox.pending(state, null, DAY);

        assertEquals(1, notices.size());
        PlayerEventNotice notice = notices.get(0);
        assertEquals(PlayerEventNotice.Kind.CADRE_INCIDENT, notice.kind);
        assertEquals(DAY, notice.sourceKey);
        assertEquals("stationingIncidentPending", notice.headerKey);
        assertEquals("stationingIncidentLiveFireRaid", notice.labelKey);
    }

    @Test
    void noticesAreOrderedByDeadlineThenContractId() {
        CampaignState state = new CampaignState();
        // Cadre gets the longer window, so arming it first still leaves it last.
        addCadre(state, 1L, 7, 500);
        armIncident(state, 0, DAY);
        long garrison = addGarrison(state, 2L, 12, 500);
        armDefense(state, 1, DAY, 900L);

        List<PlayerEventNotice> notices = PlayerEventInbox.pending(state, null, DAY);

        assertEquals(2, notices.size());
        assertEquals(garrison, notices.get(0).contractId);
        assertTrue(notices.get(0).deadlineDay < notices.get(1).deadlineDay);
    }

    @Test
    void anArmedDeadlineIsPreferredOverAProjectedOne() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, DAY, 900L);
        state.contractResponseDeadlineTick[0] = DAY + 3;

        assertEquals(DAY + 3, PlayerEventInbox.pending(state, null, DAY).get(0).deadlineDay);
    }

    @Test
    void anUnarmedDeadlineIsProjectedFromTheSameWindowTheLapseSystemUses() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, DAY, 900L);

        assertEquals(-1, state.contractResponseDeadlineTick[0]);
        assertEquals(DAY + StationingLapseSystem.GARRISON_RESPONSE_DAYS,
                PlayerEventInbox.pending(state, null, DAY).get(0).deadlineDay);
    }

    @Test
    void projectedDeadlinesAreClampedToTheRemainingTerm() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, DAY + 2);
        armDefense(state, 0, DAY, 900L);

        assertEquals(DAY + 2, PlayerEventInbox.pending(state, null, DAY).get(0).deadlineDay);
    }

    @Test
    void aResolvedOrTerminalRowProducesNothing() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, DAY, 900L);
        assertEquals(1, PlayerEventInbox.pending(state, null, DAY).size());

        state.contractDefenseEventKey[0] = 0L;
        assertTrue(PlayerEventInbox.pending(state, null, DAY).isEmpty());
    }

    @Test
    void missionModeContractsAreNeverInTheInbox() {
        CampaignState state = new CampaignState();
        state.addContract(1L, 2L, -1L, ContractType.STRIKE,
                ContractState.IN_PROGRESS, 10, 500, -1, (byte) 1,
                -1, 12, -1, 25_000, 0, (byte) 60, (byte) 60, (byte) 100);

        assertTrue(PlayerEventInbox.pending(state, null, DAY).isEmpty());
        assertNull(PlayerEventInbox.nextToPresent(state, null, DAY));
    }

    @Test
    void anEmptyStateHasAnEmptyInbox() {
        assertTrue(PlayerEventInbox.pending(new CampaignState(), null, DAY).isEmpty());
        assertTrue(PlayerEventInbox.pending(null, null, DAY).isEmpty());
        assertNull(PlayerEventInbox.nextToPresent(new CampaignState(), null, DAY));
    }

    @Test
    void nextToPresentFollowsTheSameUrgencyOrdering() {
        CampaignState state = new CampaignState();
        addCadre(state, 1L, 7, 500);
        armIncident(state, 0, DAY);
        long garrison = addGarrison(state, 2L, 12, 500);
        armDefense(state, 1, DAY, 900L);

        PlayerEventNotice first = PlayerEventInbox.nextToPresent(state, null, DAY);
        assertEquals(garrison, first.contractId);
    }

    @Test
    void daysRemainingNeverGoesNegative() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, DAY, 900L);
        PlayerEventNotice notice = PlayerEventInbox.pending(state, null, DAY).get(0);

        assertEquals(StationingLapseSystem.GARRISON_RESPONSE_DAYS, notice.daysRemaining(DAY));
        assertEquals(0, notice.daysRemaining(notice.deadlineDay + 50));
    }

    @Test
    void payloadDetailsAreCarriedThroughForTheCard() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        state.contractMarinesCommitted[0] = 24;
        armDefense(state, 0, DAY, 900L);

        PlayerEventNotice notice = PlayerEventInbox.pending(state, null, DAY).get(0);

        assertEquals(24, notice.committedMarines);
        // No roster in a headless projection: the committed count stands in for seats.
        assertEquals(24, notice.activeSeats);
        assertNull(notice.captainId);
    }

    @Test
    void twoEnumerationsOfTheSameStateAgree() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, DAY, 900L);

        PlayerEventNotice a = PlayerEventInbox.nextToPresent(state, null, DAY);
        PlayerEventNotice b = PlayerEventInbox.nextToPresent(state, null, DAY);

        assertEquals(a.contractId, b.contractId);
        assertEquals(a.sourceKey, b.sourceKey);
        assertEquals(a.deadlineDay, b.deadlineDay);
        // Distinct instances — the inbox stores nothing.
        assertTrue(a != b);
        assertSame(PlayerEventNotice.Kind.GARRISON_DEFENSE, b.kind);
    }

    static long addGarrison(CampaignState state, long patronId, int marketId, int expires) {
        long id = state.addContract(patronId, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, expires, -1, (byte) 0,
                -1, marketId, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[state.contractIndex(id)] = 40;
        return id;
    }

    static long addCadre(CampaignState state, long patronId, int marketId, int expires) {
        long id = state.addContract(patronId, -1L, -1L, ContractType.CADRE,
                ContractState.ACTIVE, 10, expires, -1, (byte) 0,
                -1, marketId, -1, 0, 400,
                (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[state.contractIndex(id)] = 6;
        return id;
    }

    static void armDefense(CampaignState state, int row, int triggeredDay, long eventKey) {
        state.contractDefenseEventKey[row] = eventKey;
        state.contractDefenseTriggeredTick[row] = triggeredDay;
        state.contractDefenseTriggerType[row] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerHouseId[row] = 2L;
        state.contractDefenseAttackerFactionId[row] = 5;
    }

    static void armIncident(CampaignState state, int row, int dueDay) {
        state.contractNextIncidentTick[row] = dueDay;
        state.contractIncidentPending[row] = 1;
        state.contractIncidentType[row] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
    }
}
