package com.dillon.starsectormarines.campaign;

/** The sole exactly-once mutation seam for hidden moral-compass choices. */
public final class MoralChoiceRecorder {

    public enum Result {
        RECORDED,
        ALREADY_RECORDED,
        INVALID
    }

    private static final int MIN_AXIS = -100;
    private static final int MAX_AXIS = 100;

    private MoralChoiceRecorder() {}

    public static Result record(CampaignState state, MoralChoiceSource source,
                                long sourceId, int mercyDelta,
                                int integrityDelta, int stewardshipDelta,
                                int institutionalismDelta, int happenedTick,
                                int recordedTick) {
        if (state == null || source == null || source == MoralChoiceSource.NONE
                || sourceId < 0L || happenedTick < 0
                || recordedTick < happenedTick
                || !validDelta(mercyDelta) || !validDelta(integrityDelta)
                || !validDelta(stewardshipDelta)
                || !validDelta(institutionalismDelta)
                || (mercyDelta == 0 && integrityDelta == 0
                    && stewardshipDelta == 0 && institutionalismDelta == 0)) {
            return Result.INVALID;
        }
        if (hasSource(state, source, sourceId)) {
            return Result.ALREADY_RECORDED;
        }

        int nextMercy = clamp(state.moralMercy, mercyDelta);
        int nextIntegrity = clamp(state.moralIntegrity, integrityDelta);
        int nextStewardship = clamp(state.moralStewardship, stewardshipDelta);
        int nextInstitutionalism = clamp(
                state.moralInstitutionalism, institutionalismDelta);
        short appliedMercy = (short) (nextMercy - state.moralMercy);
        short appliedIntegrity = (short) (nextIntegrity - state.moralIntegrity);
        short appliedStewardship = (short) (
                nextStewardship - state.moralStewardship);
        short appliedInstitutionalism = (short) (
                nextInstitutionalism - state.moralInstitutionalism);

        state.moralMercy = nextMercy;
        state.moralIntegrity = nextIntegrity;
        state.moralStewardship = nextStewardship;
        state.moralInstitutionalism = nextInstitutionalism;
        state.appendMoralChoice(source, sourceId, appliedMercy,
                appliedIntegrity, appliedStewardship, appliedInstitutionalism,
                happenedTick, recordedTick);
        return Result.RECORDED;
    }

    public static boolean hasSource(CampaignState state, MoralChoiceSource source,
                                    long sourceId) {
        if (state == null || source == null || source == MoralChoiceSource.NONE
                || sourceId < 0L) {
            return false;
        }
        for (int row = 0; row < state.moralChoiceCount; row++) {
            if (MoralChoiceSource.fromByte(state.moralChoiceSourceType[row]) == source
                    && state.moralChoiceSourceId[row] == sourceId) {
                return true;
            }
        }
        return false;
    }

    private static boolean validDelta(int delta) {
        return delta >= MIN_AXIS && delta <= MAX_AXIS;
    }

    private static int clamp(int current, int delta) {
        return Math.max(MIN_AXIS, Math.min(MAX_AXIS, current + delta));
    }
}
