package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Status;

import java.util.LinkedHashSet;

/** Explicit terminal resolution when a named assignment has no ACTIVE battle seats. */
public final class StationingNoForceResolution {

    public enum Result {
        INCIDENT_FAILED,
        DEFENSE_FAILED
    }

    private StationingNoForceResolution() {}

    public static Result apply(CampaignState state, StationingIncidentPayload payload,
                               MarineRoster roster, int day) {
        if (state == null || !valid(payload != null ? payload.contractId : -1L,
                payload != null ? payload.fireteamIds : null,
                payload != null ? payload.activeSeats : -1, roster)) return null;
        MarineCaptain captain = assignedCaptain(state, payload.contractId, roster);
        StationingIncidentResolution.Result result = StationingIncidentResolution.apply(
                state, payload.contractId, payload.dueDay, payload.type,
                0, false, false, day, roster,
                new LinkedHashSet<>(payload.fireteamIds));
        if (result != StationingIncidentResolution.Result.ASSIGNMENT_FAILED) return null;
        restoreUnengagedCaptain(captain, day);
        return Result.INCIDENT_FAILED;
    }

    public static Result apply(CampaignState state, GarrisonDefensePayload payload,
                               MarineRoster roster, int day) {
        if (state == null || !valid(payload != null ? payload.contractId : -1L,
                payload != null ? payload.fireteamIds : null,
                payload != null ? payload.activeSeats : -1, roster)) return null;
        MarineCaptain captain = assignedCaptain(state, payload.contractId, roster);
        int row = state.contractIndex(payload.contractId);
        long patronId = row >= 0 ? state.contractPatronHouseId[row] : -1L;
        GarrisonDefenseResolution.Result result = GarrisonDefenseResolution.apply(
                state, payload.contractId, payload.eventKey, 0,
                false, false, roster, new LinkedHashSet<>(payload.fireteamIds));
        if (result != GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED) return null;
        restoreUnengagedCaptain(captain, day);
        ContractReputation.failed(state, patronId, -1, day);
        return Result.DEFENSE_FAILED;
    }

    private static boolean valid(long contractId, Iterable<String> fireteamIds,
                                 int activeSeats, MarineRoster roster) {
        if (contractId <= 0L || roster == null || activeSeats != 0
                || fireteamIds == null) return false;
        return fireteamIds.iterator().hasNext()
                && !roster.squadsStationedOn(contractId).isEmpty();
    }

    private static MarineCaptain assignedCaptain(CampaignState state, long contractId,
                                                  MarineRoster roster) {
        if (state == null) return null;
        int row = state.contractIndex(contractId);
        if (row < 0 || state.contractCaptainId[row] < 0) return null;
        return roster.byId(state.captainRegistry.get(state.contractCaptainId[row]));
    }

    private static void restoreUnengagedCaptain(MarineCaptain captain, int day) {
        if (captain == null || captain.status() != Status.GARRISONED) return;
        captain.setStatus(Status.ACTIVE);
        captain.commendations().add("Day " + day
                + ": Stationing assignment ended without a battle-ready detachment.");
    }
}
