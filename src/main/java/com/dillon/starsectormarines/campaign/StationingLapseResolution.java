package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Status;

import java.util.LinkedHashSet;

/**
 * Terminal resolution for a pending stationing response the player never answered.
 *
 * <p>Distinct from {@link StationingNoForceResolution}, which covers an assignment
 * that <em>could not</em> field a battle-ready detachment. Here the detachment may be
 * perfectly healthy — the deadline simply passed. Both funnel into the same shipped
 * writeback policies ({@link GarrisonDefenseResolution} /
 * {@link StationingIncidentResolution}) so a lapse can never invent its own personnel
 * or contract mutation.
 */
public final class StationingLapseResolution {

    public enum Result {
        DEFENSE_LAPSED,
        INCIDENT_LAPSED
    }

    private StationingLapseResolution() {}

    public static Result apply(CampaignState state, GarrisonDefensePayload payload,
                               MarineRoster roster, int day) {
        if (state == null || payload == null) return null;
        MarineCaptain captain = assignedCaptain(state, payload.contractId, roster);
        GarrisonDefenseResolution.Result result = GarrisonDefenseResolution.apply(
                state, payload.contractId, payload.eventKey, 0, false, false,
                roster, deployed(payload.fireteamIds, roster));
        if (result != GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED) return null;
        restoreStrandedCaptain(captain, day);
        ContractReputation.lapsedForContract(state, payload.contractId, day);
        return Result.DEFENSE_LAPSED;
    }

    public static Result apply(CampaignState state, StationingIncidentPayload payload,
                               MarineRoster roster, int day) {
        if (state == null || payload == null) return null;
        MarineCaptain captain = assignedCaptain(state, payload.contractId, roster);
        StationingIncidentResolution.Result result = StationingIncidentResolution.apply(
                state, payload.contractId, payload.dueDay, payload.type,
                0, false, false, day, roster, deployed(payload.fireteamIds, roster));
        if (result != StationingIncidentResolution.Result.ASSIGNMENT_FAILED) return null;
        restoreStrandedCaptain(captain, day);
        ContractReputation.lapsedForContract(state, payload.contractId, day);
        return Result.INCIDENT_LAPSED;
    }

    /**
     * The shipped resolutions match the deployed set against the payload's own
     * fireteams, and reject a non-empty set with no roster. An unnamed (legacy,
     * marine-count) assignment has no fireteams and passes {@code null} through.
     */
    private static LinkedHashSet<String> deployed(Iterable<String> fireteamIds,
                                                  MarineRoster roster) {
        if (roster == null || fireteamIds == null) return null;
        LinkedHashSet<String> deployed = new LinkedHashSet<>();
        for (String id : fireteamIds) {
            deployed.add(id);
        }
        return deployed.isEmpty() ? null : deployed;
    }

    private static MarineCaptain assignedCaptain(CampaignState state, long contractId,
                                                 MarineRoster roster) {
        if (roster == null) return null;
        int row = state.contractIndex(contractId);
        if (row < 0 || state.contractCaptainId[row] < 0) return null;
        return roster.byId(state.captainRegistry.get(state.contractCaptainId[row]));
    }

    /**
     * No battle ran, so nothing else will move the captain off {@code GARRISONED}.
     * They come home with the assignment's failure on their record.
     */
    private static void restoreStrandedCaptain(MarineCaptain captain, int day) {
        if (captain == null || captain.status() != Status.GARRISONED) return;
        captain.setStatus(Status.ACTIVE);
        captain.commendations().add("Day " + day
                + ": Recalled after a stationing assignment went unanswered.");
    }
}
