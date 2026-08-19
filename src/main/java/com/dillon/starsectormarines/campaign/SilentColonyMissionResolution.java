package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;

/** Strict, lineage-bound writeback for one completed Silent Colony mission. */
public final class SilentColonyMissionResolution {

    public enum Result {
        RESOLVED,
        NO_REPORT,
        ALREADY_TERMINAL,
        NOT_COMMITTED,
        INVALID
    }

    private SilentColonyMissionResolution() {}

    public static Result apply(CampaignState state, MissionOutcome outcome,
                               int day) {
        if (!validEnvelope(state, outcome, day)) return Result.INVALID;
        SilentColonyMissionKey key = SilentColonyMissionKey.parse(
                outcome.missionId);
        if (key == null || key.eventId != outcome.campaignEventId) {
            return Result.INVALID;
        }

        int row = state.eventIndex(outcome.campaignEventId);
        if (!matchesFrozenEvent(state, row, outcome)) return Result.INVALID;

        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState.isTerminal()) return Result.ALREADY_TERMINAL;
        if (eventState != CampaignEventState.COMMITTED) {
            return Result.NOT_COMMITTED;
        }
        if (outcome.civiliansRescued < -1) return Result.INVALID;
        if (outcome.civiliansRescued == -1
                || outcome.colonyArchiveOutcome
                    == AbandonedColonyArchiveOutcome.NONE) {
            return Result.NO_REPORT;
        }
        if (!validExplicitReport(outcome)) return Result.INVALID;

        SilentColonyEvent.Result resolved = SilentColonyEvent.resolve(
                state, outcome.campaignEventId, outcome.civiliansRescued,
                outcome.colonyArchiveOutcome
                        == AbandonedColonyArchiveOutcome.RECOVERED,
                day);
        if (resolved == SilentColonyEvent.Result.RESOLVED) {
            state.addChronicleSilentColony(outcome.campaignEventId,
                    outcome.campaignEventMarketId,
                    outcome.civiliansAtRisk, outcome.civiliansRescued,
                    outcome.colonyArchiveOutcome, day, day);
            return Result.RESOLVED;
        }
        if (resolved == SilentColonyEvent.Result.ALREADY_TERMINAL) {
            return Result.ALREADY_TERMINAL;
        }
        return resolved == SilentColonyEvent.Result.NOT_READY
                ? Result.NOT_COMMITTED : Result.INVALID;
    }

    private static boolean validEnvelope(CampaignState state,
                                         MissionOutcome outcome, int day) {
        return state != null && outcome != null && day >= 0
                && outcome.missionSource == MissionSource.CAMPAIGN_EVENT
                && outcome.missionType == MissionType.EXTRACTION
                && outcome.contractId == -1L
                && outcome.payoutBase == 0 && outcome.payoutEarned == 0
                && outcome.salvageEntitlement == 0
                && outcome.targetIndustryId == null
                && outcome.campaignEventId > 0L
                && outcome.campaignEventMarketId >= 0
                && outcome.campaignEventThreatSeed >= 0L
                && outcome.civiliansAtRisk > 0;
    }

    private static boolean matchesFrozenEvent(CampaignState state, int row,
                                               MissionOutcome outcome) {
        return row >= 0
                && CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.SILENT_COLONY
                && state.marketRegistry.get(state.eventMarketId[row]) != null
                && state.eventTriggerKey[row] >= 0L
                && state.eventSuppliesRequired[row] > 0
                && state.eventFuelRequired[row] > 0
                && state.eventMarketId[row] == outcome.campaignEventMarketId
                && state.eventCiviliansAtRisk[row] == outcome.civiliansAtRisk
                && state.eventColonyThreatSeed[row]
                    == outcome.campaignEventThreatSeed;
    }

    private static boolean validExplicitReport(MissionOutcome outcome) {
        return outcome.civiliansRescued >= 0
                && outcome.civiliansRescued <= outcome.civiliansAtRisk
                && outcome.evacuationRepresentatives
                    == outcome.civiliansAtRisk
                && outcome.representativesEvacuated
                    == outcome.civiliansRescued
                && (outcome.colonyArchiveOutcome
                        == AbandonedColonyArchiveOutcome.LOST
                    || outcome.colonyArchiveOutcome
                        == AbandonedColonyArchiveOutcome.RECOVERED);
    }
}
