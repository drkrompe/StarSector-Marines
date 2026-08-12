package com.dillon.starsectormarines.campaign;

/** Exactly-once writeback policy for a battle resolving a Garrison defense. */
public final class GarrisonDefenseResolution {

    public enum Result {
        DEFENSE_WON,
        ASSIGNMENT_FAILED
    }

    private GarrisonDefenseResolution() {}

    public static Result apply(CampaignState state, long contractId,
                               long expectedEventKey, int marinesLost,
                               boolean captainUnavailable, boolean victory) {
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(state, contractId);
        if (payload == null || payload.eventKey != expectedEventKey || marinesLost < 0) {
            return null;
        }
        int row = state.contractIndex(contractId);
        int committed = state.contractMarinesCommitted[row];
        int losses = Math.min(committed, marinesLost);
        int survivors = committed - losses;
        state.contractMarinesCommitted[row] = survivors;
        clearPendingDefense(state, row);

        if (!victory || captainUnavailable || survivors == 0) {
            state.contractState[row] = ContractState.FAILED.toByte();
            return Result.ASSIGNMENT_FAILED;
        }

        state.contractState[row] = ContractState.ACTIVE.toByte();
        return Result.DEFENSE_WON;
    }

    private static void clearPendingDefense(CampaignState state, int row) {
        // Keep contractDefenseEventKey as the consumed-event watermark so the
        // campaign producer cannot immediately re-arm this same raid.
        state.contractDefenseTriggeredTick[row] = -1;
        state.contractDefenseTriggerType[row] = GarrisonDefenseTriggerType.NONE.toByte();
        state.contractDefenseAttackerHouseId[row] = -1L;
        state.contractDefenseAttackerFactionId[row] = -1;
    }
}
