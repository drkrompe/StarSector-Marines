package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.StationingIncidentSystem;

/** Exactly-once writeback policy for a battle or choice resolving a Cadre incident. */
public final class StationingIncidentResolution {

    public enum Result {
        INCIDENT_RESOLVED,
        ASSIGNMENT_FAILED
    }

    private StationingIncidentResolution() {}

    public static Result apply(CampaignState state, long contractId,
                               int expectedDueDay, StationingIncidentType expectedType,
                               int marinesLost, boolean captainLost, int day) {
        StationingIncidentPayload payload = StationingIncidentPayload.from(state, contractId);
        if (payload == null || payload.dueDay != expectedDueDay
                || payload.type != expectedType || marinesLost < 0) {
            return null;
        }
        int row = state.contractIndex(contractId);
        int committed = state.contractMarinesCommitted[row];
        int losses = Math.min(committed, marinesLost);
        int survivors = committed - losses;
        state.contractMarinesCommitted[row] = survivors;
        state.contractIncidentPending[row] = 0;
        state.contractIncidentType[row] = StationingIncidentType.NONE.toByte();

        if (captainLost || survivors == 0) {
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
