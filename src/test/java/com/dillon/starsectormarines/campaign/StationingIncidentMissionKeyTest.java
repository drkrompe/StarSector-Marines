package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StationingIncidentMissionKeyTest {

    @Test
    void roundTripsPendingIncidentIdentity() {
        CampaignState state = pending();
        StationingIncidentPayload payload = StationingIncidentPayload.from(
                state, state.contractId[0]);

        StationingIncidentMissionKey key = StationingIncidentMissionKey.parse(
                StationingIncidentMissionKey.encode(payload));

        assertEquals(state.contractId[0], key.contractId);
        assertEquals(42, key.dueDay);
        assertEquals(StationingIncidentType.LIVE_FIRE_RAID, key.type);
    }

    @Test
    void rejectsMalformedAndNoneKeys() {
        assertNull(StationingIncidentMissionKey.parse(null));
        assertNull(StationingIncidentMissionKey.parse("contract:1"));
        assertNull(StationingIncidentMissionKey.parse("cadre-incident:1:x:LIVE_FIRE_RAID"));
        assertNull(StationingIncidentMissionKey.parse("cadre-incident:1:42:NONE"));
    }

    private static CampaignState pending() {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        state.addContract(1L, -1L, -1L, ContractType.CADRE, ContractState.ACTIVE,
                10, 100, -1, (byte) 0, captain, 1, -1,
                0, 1_000, (byte) 5, (byte) 5, (byte) 100);
        state.contractMarinesCommitted[0] = 80;
        state.contractNextIncidentTick[0] = 42;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.LIVE_FIRE_RAID.toByte();
        return state;
    }
}
