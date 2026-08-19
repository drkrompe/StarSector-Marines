package com.dillon.starsectormarines.campaign;

/** Applies source-frozen political, reputation, and moral asylum effects. */
public final class DefectorAsylumConsequences {

    public enum Result {
        APPLIED,
        ALREADY_APPLIED,
        NEUTRAL,
        NOT_READY,
        INVALID
    }

    private DefectorAsylumConsequences() {}

    public static Result apply(CampaignState state, int eventRow, int day) {
        if (!validRow(state, eventRow) || day < 0) return Result.INVALID;

        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[eventRow]);
        if (eventState == CampaignEventState.EXPIRED) return Result.NEUTRAL;
        if (eventState != CampaignEventState.REFUSED
                && eventState != CampaignEventState.RESOLVED) {
            return Result.NOT_READY;
        }

        long eventId = state.eventId[eventRow];
        if (eventId < 0L) return Result.INVALID;
        if (MoralChoiceRecorder.hasSource(state,
                MoralChoiceSource.DEFECTOR_ASYLUM, eventId)) {
            return Result.ALREADY_APPLIED;
        }

        if (eventState == CampaignEventState.REFUSED) {
            int happened = state.eventDecisionTick[eventRow];
            if (!validTicks(happened, day)) return Result.INVALID;
            return record(state, eventId, -5, 0, 0, happened, day);
        }

        DefectorAsylumOutcome outcome = DefectorAsylumOutcome.fromByte(
                state.eventDefectorOutcome[eventRow]);
        int happened = state.eventResolvedTick[eventRow];
        if (outcome == DefectorAsylumOutcome.NONE
                || !validTicks(happened, day)
                || !validFrozenHouses(state, eventRow)) {
            return Result.INVALID;
        }

        if (outcome == DefectorAsylumOutcome.PROTECTED) {
            adjustActiveSourceProgress(state, eventRow, -20);
            adjustReputation(state, state.eventTargetHouseId[eventRow], 5);
            return record(state, eventId, 0, 20, 10, happened, day);
        }

        adjustActiveSourceProgress(state, eventRow, 20);
        adjustReputation(state, state.eventActorHouseId[eventRow], 5);
        adjustReputation(state, state.eventTargetHouseId[eventRow], -10);
        return record(state, eventId, 0, -25, -10, happened, day);
    }

    private static Result record(CampaignState state, long eventId,
                                 int mercy, int integrity, int stewardship,
                                 int happened, int day) {
        MoralChoiceRecorder.Result result = MoralChoiceRecorder.record(state,
                MoralChoiceSource.DEFECTOR_ASYLUM, eventId,
                mercy, integrity, stewardship, 0, happened, day);
        if (result == MoralChoiceRecorder.Result.RECORDED) {
            return Result.APPLIED;
        }
        return result == MoralChoiceRecorder.Result.ALREADY_RECORDED
                ? Result.ALREADY_APPLIED : Result.INVALID;
    }

    private static void adjustActiveSourceProgress(CampaignState state,
                                                   int eventRow, int delta) {
        int chainRow = state.chainIndex(state.eventSourceChainId[eventRow]);
        if (chainRow < 0
                || ChainState.fromByte(state.chainState[chainRow])
                    != ChainState.ACTIVE
                || state.chainActorHouseId[chainRow]
                    != state.eventActorHouseId[eventRow]
                || state.chainTarget[chainRow]
                    != state.eventTargetHouseId[eventRow]
                || state.chainMarketId[chainRow]
                    != state.eventMarketId[eventRow]) {
            return;
        }
        int progress = state.chainProgress[chainRow];
        state.chainProgress[chainRow] = (short) Math.max(0,
                Math.min(Short.MAX_VALUE, progress + delta));
    }

    private static void adjustReputation(CampaignState state, long houseId,
                                         int delta) {
        int row = state.ensureRepRow(houseId);
        state.repValue[row] = Math.max(-100,
                Math.min(100, state.repValue[row] + delta));
    }

    private static boolean validRow(CampaignState state, int row) {
        return state != null && row >= 0 && row < state.eventCount
                && CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.DEFECTOR_ASYLUM;
    }

    private static boolean validFrozenHouses(CampaignState state, int row) {
        long actor = state.eventActorHouseId[row];
        long target = state.eventTargetHouseId[row];
        return actor >= 0L && target >= 0L && actor != target
                && state.houseIndex(actor) >= 0
                && state.houseIndex(target) >= 0;
    }

    private static boolean validTicks(int happened, int recorded) {
        return happened >= 0 && recorded >= happened;
    }
}
