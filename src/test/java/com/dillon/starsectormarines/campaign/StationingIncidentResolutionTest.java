package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationingIncidentResolutionTest {

    @Test
    void resolutionDebitsStationedMarinesAndSchedulesNextIncident() {
        CampaignState state = pending(80, 42, 200, StationingIncidentType.LIVE_FIRE_RAID);

        StationingIncidentResolution.Result result = StationingIncidentResolution.apply(
                state, state.contractId[0], 42, StationingIncidentType.LIVE_FIRE_RAID,
                7, false, 45);

        assertEquals(StationingIncidentResolution.Result.INCIDENT_RESOLVED, result);
        assertEquals(73, state.contractMarinesCommitted[0]);
        assertEquals(0, state.contractIncidentPending[0]);
        assertEquals(StationingIncidentType.NONE,
                StationingIncidentType.fromByte(state.contractIncidentType[0]));
        assertTrue(state.contractNextIncidentTick[0] >= 69);
        assertTrue(state.contractNextIncidentTick[0] <= 81);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void duplicateAndStaleResultsCannotMutateAssignment() {
        CampaignState state = pending(80, 42, 200, StationingIncidentType.DEFECTOR_LEAD);

        assertNull(StationingIncidentResolution.apply(state, state.contractId[0],
                41, StationingIncidentType.DEFECTOR_LEAD, 5, false, 45));
        assertNull(StationingIncidentResolution.apply(state, state.contractId[0],
                42, StationingIncidentType.FACTORY_ACCIDENT, 5, false, 45));
        assertEquals(80, state.contractMarinesCommitted[0]);

        assertEquals(StationingIncidentResolution.Result.INCIDENT_RESOLVED,
                StationingIncidentResolution.apply(state, state.contractId[0],
                        42, StationingIncidentType.DEFECTOR_LEAD, 5, false, 45));
        assertNull(StationingIncidentResolution.apply(state, state.contractId[0],
                42, StationingIncidentType.DEFECTOR_LEAD, 5, false, 45));
        assertEquals(75, state.contractMarinesCommitted[0]);
    }

    @Test
    void detachmentWipeOrCaptainLossFailsAssignment() {
        CampaignState wiped = pending(10, 42, 200, StationingIncidentType.FACTORY_ACCIDENT);
        assertEquals(StationingIncidentResolution.Result.ASSIGNMENT_FAILED,
                StationingIncidentResolution.apply(wiped, wiped.contractId[0],
                        42, StationingIncidentType.FACTORY_ACCIDENT, 99, false, 45));
        assertEquals(0, wiped.contractMarinesCommitted[0]);
        assertEquals(ContractState.FAILED, ContractState.fromByte(wiped.contractState[0]));

        CampaignState captainLost = pending(80, 42, 200,
                StationingIncidentType.LIVE_FIRE_RAID);
        assertEquals(StationingIncidentResolution.Result.ASSIGNMENT_FAILED,
                StationingIncidentResolution.apply(captainLost, captainLost.contractId[0],
                        42, StationingIncidentType.LIVE_FIRE_RAID, 2, true, 45));
        assertEquals(78, captainLost.contractMarinesCommitted[0]);
        assertEquals(ContractState.FAILED,
                ContractState.fromByte(captainLost.contractState[0]));
    }

    @Test
    void noNewIncidentIsScheduledBeyondTerm() {
        CampaignState state = pending(80, 42, 60, StationingIncidentType.DEFECTOR_LEAD);

        StationingIncidentResolution.apply(state, state.contractId[0], 42,
                StationingIncidentType.DEFECTOR_LEAD, 0, false, 45);

        assertEquals(Integer.MAX_VALUE, state.contractNextIncidentTick[0]);
    }

    private static CampaignState pending(int marines, int dueDay, int expires,
                                         StationingIncidentType type) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.CADRE, ContractState.ACTIVE,
                10, expires, -1, (byte) 0, -1, 1, -1,
                0, 1_000, (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = marines;
        state.contractNextIncidentTick[0] = dueDay;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = type.toByte();
        return state;
    }
}
