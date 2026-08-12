package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GarrisonDefenseResolutionTest {

    @Test
    void victoryReturnsSurvivingGarrisonToActiveAndConsumesPayload() {
        CampaignState state = pending(80, 77L);

        GarrisonDefenseResolution.Result result = GarrisonDefenseResolution.apply(
                state, state.contractId[0], 77L, 7, false, true);

        assertEquals(GarrisonDefenseResolution.Result.DEFENSE_WON, result);
        assertEquals(73, state.contractMarinesCommitted[0]);
        assertEquals(ContractState.ACTIVE, ContractState.fromByte(state.contractState[0]));
        assertEquals(77L, state.contractDefenseEventKey[0]);
        assertEquals(-1, state.contractDefenseTriggeredTick[0]);
        assertEquals(GarrisonDefenseTriggerType.NONE,
                GarrisonDefenseTriggerType.fromByte(state.contractDefenseTriggerType[0]));
        assertEquals(-1L, state.contractDefenseAttackerHouseId[0]);
        assertEquals(-1, state.contractDefenseAttackerFactionId[0]);
        assertNull(GarrisonDefensePayload.from(state, state.contractId[0]));
    }

    @Test
    void defeatWipeOrCaptainLossFailsAssignment() {
        CampaignState defeated = pending(80, 77L);
        assertEquals(GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED,
                GarrisonDefenseResolution.apply(defeated, defeated.contractId[0],
                        77L, 4, false, false));
        assertEquals(ContractState.FAILED,
                ContractState.fromByte(defeated.contractState[0]));

        CampaignState wiped = pending(10, 78L);
        assertEquals(GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED,
                GarrisonDefenseResolution.apply(wiped, wiped.contractId[0],
                        78L, 99, false, true));
        assertEquals(0, wiped.contractMarinesCommitted[0]);

        CampaignState captainLost = pending(80, 79L);
        assertEquals(GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED,
                GarrisonDefenseResolution.apply(captainLost, captainLost.contractId[0],
                        79L, 2, true, true));
    }

    @Test
    void staleAndDuplicateResultsCannotMutateAssignment() {
        CampaignState state = pending(80, 77L);

        assertNull(GarrisonDefenseResolution.apply(state, state.contractId[0],
                76L, 5, false, true));
        assertNull(GarrisonDefenseResolution.apply(state, state.contractId[0],
                77L, -1, false, true));
        assertEquals(80, state.contractMarinesCommitted[0]);

        assertEquals(GarrisonDefenseResolution.Result.DEFENSE_WON,
                GarrisonDefenseResolution.apply(state, state.contractId[0],
                        77L, 5, false, true));
        assertNull(GarrisonDefenseResolution.apply(state, state.contractId[0],
                77L, 5, false, true));
        assertEquals(75, state.contractMarinesCommitted[0]);
    }

    private static CampaignState pending(int marines, long eventKey) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                ContractState.IN_PROGRESS, 10, 100, -1, (byte) 0,
                -1, 7, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = marines;
        state.contractDefenseEventKey[0] = eventKey;
        state.contractDefenseTriggeredTick[0] = 42;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();
        state.contractDefenseAttackerHouseId[0] = 2L;
        state.contractDefenseAttackerFactionId[0] = 5;
        return state;
    }
}
