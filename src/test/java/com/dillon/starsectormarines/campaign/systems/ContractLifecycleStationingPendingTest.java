package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The G31 backstop: a stationing term can never be laundered into a successful
 * completion while the player still owes a response.
 */
class ContractLifecycleStationingPendingTest {

    private static final int EXPIRES = 100;

    @Test
    void pendingGarrisonDefenseAtExpiryFailsInsteadOfCompleting() {
        CampaignState state = stationing(ContractType.GARRISON,
                ContractState.IN_PROGRESS);
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggeredTick[0] = EXPIRES - 2;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.VANILLA_RAID.toByte();

        new ContractLifecycleSystem().tick(state, EXPIRES);

        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void pendingCadreIncidentAtExpiryFailsInsteadOfCompleting() {
        CampaignState state = stationing(ContractType.CADRE, ContractState.ACTIVE);
        state.contractNextIncidentTick[0] = EXPIRES - 2;
        state.contractIncidentPending[0] = 1;
        state.contractIncidentType[0] = StationingIncidentType.FACTORY_ACCIDENT.toByte();

        new ContractLifecycleSystem().tick(state, EXPIRES);

        assertEquals(ContractState.FAILED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void quietStationingTermStillCompletesAtExpiry() {
        CampaignState state = stationing(ContractType.GARRISON, ContractState.ACTIVE);

        new ContractLifecycleSystem().tick(state, EXPIRES);

        assertEquals(ContractState.COMPLETED, ContractState.fromByte(state.contractState[0]));
    }

    @Test
    void consumedDefenseWatermarkDoesNotBlockCompletion() {
        CampaignState state = stationing(ContractType.GARRISON, ContractState.ACTIVE);
        // Event key is deliberately retained after a resolved defense as the re-arm
        // watermark; only a live trigger type means a response is still owed.
        state.contractDefenseEventKey[0] = 77L;
        state.contractDefenseTriggeredTick[0] = -1;
        state.contractDefenseTriggerType[0] = GarrisonDefenseTriggerType.NONE.toByte();

        new ContractLifecycleSystem().tick(state, EXPIRES);

        assertEquals(ContractState.COMPLETED, ContractState.fromByte(state.contractState[0]));
    }

    private static CampaignState stationing(ContractType type, ContractState contractState) {
        CampaignState state = new CampaignState();
        state.addContract(1L, -1L, -1L, type, contractState, 10, EXPIRES, -1,
                (byte) 0, -1, 12, -1, 0, 1_000,
                (byte) 25, (byte) 25, (byte) 100);
        state.contractMarinesCommitted[0] = 12;
        return state;
    }
}
