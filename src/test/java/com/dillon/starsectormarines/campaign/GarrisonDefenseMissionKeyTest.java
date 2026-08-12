package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GarrisonDefenseMissionKeyTest {

    @Test
    void roundTripsPendingDefenseIdentity() {
        CampaignState state = pending();
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(
                state, state.contractId[0]);

        GarrisonDefenseMissionKey key = GarrisonDefenseMissionKey.parse(
                GarrisonDefenseMissionKey.encode(payload));

        assertEquals(state.contractId[0], key.contractId);
        assertEquals(-77L, key.eventKey);
    }

    @Test
    void rejectsMalformedAndZeroKeys() {
        assertNull(GarrisonDefenseMissionKey.parse(null));
        assertNull(GarrisonDefenseMissionKey.parse("contract:1"));
        assertNull(GarrisonDefenseMissionKey.parse("garrison-defense:1:x"));
        assertNull(GarrisonDefenseMissionKey.parse("garrison-defense:1:0"));
        assertNull(GarrisonDefenseMissionKey.parse("garrison-defense:1:2:3"));
    }

    private static CampaignState pending() {
        CampaignState state = new CampaignState();
        int captain = state.captainRegistry.intern("captain-1");
        state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                captain, 7, -1, 0, 1_000,
                (byte) 25, (byte) 15, (byte) 105);
        state.contractMarinesCommitted[0] = 80;
        state.contractDefenseEventKey[0] = -77L;
        state.contractDefenseTriggeredTick[0] = 42;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        return state;
    }
}
