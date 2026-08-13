package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.StationingIncidentSystem;
import com.dillon.starsectormarines.marine.MarineRoster;

import java.util.HashSet;
import java.util.Set;

/** Exactly-once writeback policy for a battle or choice resolving a Cadre incident. */
public final class StationingIncidentResolution {

    public enum Result {
        INCIDENT_RESOLVED,
        ASSIGNMENT_FAILED
    }

    private StationingIncidentResolution() {}

    public static Result apply(CampaignState state, long contractId,
                               int expectedDueDay, StationingIncidentType expectedType,
                               int marinesLost, boolean captainUnavailable, int day) {
        return apply(state, contractId, expectedDueDay, expectedType, marinesLost,
                captainUnavailable, true, day, null, null);
    }

    public static Result apply(CampaignState state, long contractId,
                               int expectedDueDay, StationingIncidentType expectedType,
                               int marinesLost, boolean captainUnavailable,
                               boolean responseSucceeded, int day,
                               MarineRoster roster, Set<String> deployedFireteamIds) {
        if (roster == null && deployedFireteamIds != null
                && !deployedFireteamIds.isEmpty()) return null;
        StationingIncidentPayload payload = StationingIncidentPayload.from(
                state, contractId, roster);
        if (payload == null || payload.dueDay != expectedDueDay
                || payload.type != expectedType || marinesLost < 0) {
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

        boolean failed = !responseSucceeded || captainUnavailable || survivors == 0;
        if (named && failed) {
            if (roster.releaseStationing(contractId) <= 0) return null;
            survivors = 0;
        }
        state.contractMarinesCommitted[row] = survivors;
        state.contractIncidentPending[row] = 0;
        state.contractIncidentType[row] = StationingIncidentType.NONE.toByte();

        if (failed) {
            state.contractState[row] = ContractState.FAILED.toByte();
            state.contractNextIncidentTick[row] = -1;
            return Result.ASSIGNMENT_FAILED;
        }

        int next = StationingIncidentSystem.nextIncidentDay(contractId, day);
        int expires = state.contractExpiresTick[row];
        state.contractNextIncidentTick[row] = expires >= 0 && next >= expires
                ? Integer.MAX_VALUE : next;
        return Result.INCIDENT_RESOLVED;
    }
}
