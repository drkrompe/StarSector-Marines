package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineRoster;

import java.util.HashSet;
import java.util.Set;

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
        return apply(state, contractId, expectedEventKey, marinesLost,
                captainUnavailable, victory, null, null);
    }

    public static Result apply(CampaignState state, long contractId,
                               long expectedEventKey, int marinesLost,
                               boolean captainUnavailable, boolean victory,
                               MarineRoster roster, Set<String> deployedFireteamIds) {
        if (roster == null && deployedFireteamIds != null
                && !deployedFireteamIds.isEmpty()) return null;
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(state, contractId, roster);
        if (payload == null || payload.eventKey != expectedEventKey || marinesLost < 0) {
            return null;
        }
        boolean named = !payload.fireteamIds.isEmpty();
        if (named && (deployedFireteamIds == null
                || !new HashSet<>(payload.fireteamIds).equals(
                        new HashSet<>(deployedFireteamIds)))) {
            return null;
        }
        int row = state.contractIndex(contractId);
        int survivors;
        if (named) {
            survivors = roster.stationedLivingCount(contractId);
        } else {
            int committed = state.contractMarinesCommitted[row];
            survivors = committed - Math.min(committed, marinesLost);
        }

        boolean failed = !victory || captainUnavailable || survivors == 0;
        if (named && failed) {
            if (roster.releaseStationing(contractId) <= 0) return null;
            survivors = 0;
        }
        state.contractMarinesCommitted[row] = survivors;
        clearPendingDefense(state, row);

        if (failed) {
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
