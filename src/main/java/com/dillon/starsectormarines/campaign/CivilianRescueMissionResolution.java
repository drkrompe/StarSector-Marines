package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.dillon.starsectormarines.ops.MissionSource;

/** Validates one explicit evacuation report before resolving its event row. */
public final class CivilianRescueMissionResolution {

    public enum Result {
        RESOLVED,
        NO_REPORT,
        ALREADY_TERMINAL,
        NOT_COMMITTED,
        INVALID
    }

    private CivilianRescueMissionResolution() {}

    public static Result apply(CampaignState state, MissionOutcome outcome,
                               int day) {
        if (state == null || outcome == null || day < 0
                || outcome.missionSource != MissionSource.CAMPAIGN_EVENT
                || outcome.contractId != -1L
                || outcome.payoutBase != 0 || outcome.payoutEarned != 0
                || outcome.salvageEntitlement != 0
                || outcome.targetIndustryId != null
                || outcome.campaignEventId <= 0L
                || outcome.campaignEventMarketId < 0
                || outcome.civiliansAtRisk <= 0) {
            return Result.INVALID;
        }
        CivilianRescueMissionKey key = CivilianRescueMissionKey.parse(
                outcome.missionId);
        if (key == null || key.eventId != outcome.campaignEventId) {
            return Result.INVALID;
        }

        int row = state.eventIndex(outcome.campaignEventId);
        if (row < 0 || CampaignEventType.fromByte(state.eventType[row])
                != CampaignEventType.CIVILIAN_RESCUE
                || state.eventMarketId[row] != outcome.campaignEventMarketId
                || state.eventCiviliansAtRisk[row] != outcome.civiliansAtRisk) {
            return Result.INVALID;
        }

        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.COMMITTED) {
            return Result.NOT_COMMITTED;
        }
        if (outcome.civiliansRescued == -1) return Result.NO_REPORT;
        if (outcome.civiliansRescued < -1) return Result.INVALID;
        if (outcome.civiliansRescued > outcome.civiliansAtRisk) {
            return Result.INVALID;
        }

        CivilianRescueEvent.Result resolved = CivilianRescueEvent.resolve(
                state, outcome.campaignEventId, outcome.civiliansRescued, day);
        if (resolved == CivilianRescueEvent.Result.RESOLVED) {
            return Result.RESOLVED;
        }
        if (resolved == CivilianRescueEvent.Result.ALREADY_TERMINAL) {
            return Result.ALREADY_TERMINAL;
        }
        return resolved == CivilianRescueEvent.Result.NOT_READY
                ? Result.NOT_COMMITTED : Result.INVALID;
    }
}
