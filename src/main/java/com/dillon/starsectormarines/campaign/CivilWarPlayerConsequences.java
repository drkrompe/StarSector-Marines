package com.dillon.starsectormarines.campaign;

/** Applies terminal civil-war house-reputation effects from persisted attribution. */
public final class CivilWarPlayerConsequences {

    public enum Result {
        APPLIED,
        ALREADY_HANDLED,
        NOT_READY,
        NOT_APPLICABLE
    }

    private CivilWarPlayerConsequences() {}

    public static Result applyClaimant(CampaignState state, int claimRow, int day) {
        if (state == null || claimRow < 0 || claimRow >= state.throneClaimCount) {
            return Result.NOT_APPLICABLE;
        }
        CivilWarPlayerConsequenceState consequenceState =
                CivilWarPlayerConsequenceState.fromByte(
                        state.throneClaimPlayerConsequenceState[claimRow]);
        if (consequenceState != CivilWarPlayerConsequenceState.PENDING) {
            return Result.ALREADY_HANDLED;
        }
        ThroneClaimState claimState = ThroneClaimState.fromByte(
                state.throneClaimState[claimRow]);
        if (claimState == ThroneClaimState.PREPARED) return Result.NOT_READY;
        if (claimState != ThroneClaimState.APPLIED) {
            return closeClaim(state, claimRow);
        }

        int chainRow = state.chainIndex(
                state.throneClaimSourceChainId[claimRow]);
        int contribution = state.throneClaimPlayerContribution[claimRow] & 0xFFFF;
        long supported = state.throneClaimHouseId[claimRow];
        if (chainRow < 0
                || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    != ChainArchetype.CIVIL_WAR
                || ChainState.fromByte(state.chainState[chainRow]) != ChainState.RESOLVED
                || CivilWarAllegiance.fromByte(
                    state.throneClaimPlayerAllegiance[claimRow])
                    != CivilWarAllegiance.CLAIMANT
                || contribution <= 0
                || state.throneClaimPlayerLastContributionTick[claimRow] < 0
                || state.chainActorHouseId[chainRow] != supported) {
            return closeClaim(state, claimRow);
        }
        long opposed = state.chainTarget[chainRow];
        if (!validHouses(state, supported, opposed)) {
            return closeClaim(state, claimRow);
        }

        applyReputation(state, supported, opposed, contribution);
        state.throneClaimPlayerConsequenceState[claimRow] =
                CivilWarPlayerConsequenceState.APPLIED.toByte();
        state.throneClaimPlayerConsequenceAppliedTick[claimRow] = day;
        return Result.APPLIED;
    }

    public static Result applyIncumbent(CampaignState state, int chainRow, int day) {
        if (state == null || chainRow < 0 || chainRow >= state.chainCount) {
            return Result.NOT_APPLICABLE;
        }
        CivilWarPlayerConsequenceState consequenceState =
                CivilWarPlayerConsequenceState.fromByte(
                        state.chainPlayerConsequenceState[chainRow]);
        if (consequenceState != CivilWarPlayerConsequenceState.PENDING) {
            return Result.ALREADY_HANDLED;
        }
        if (ChainArchetype.fromByte(state.chainArchetype[chainRow])
                != ChainArchetype.CIVIL_WAR) {
            return closeChain(state, chainRow);
        }
        ChainState chainState = ChainState.fromByte(state.chainState[chainRow]);
        if (chainState == ChainState.ACTIVE) return Result.NOT_READY;

        int contribution = state.chainPlayerContribution[chainRow] & 0xFFFF;
        int contributionDay = state.chainPlayerLastContributionTick[chainRow];
        if (chainState != ChainState.FAILED
                || CivilWarAllegiance.fromByte(state.chainPlayerAllegiance[chainRow])
                    != CivilWarAllegiance.INCUMBENT
                || contribution <= 0 || contributionDay < 0
                || state.chainResolvedTick[chainRow] != contributionDay) {
            return closeChain(state, chainRow);
        }
        long supported = state.chainTarget[chainRow];
        long opposed = state.chainActorHouseId[chainRow];
        if (!validHouses(state, supported, opposed)) {
            return closeChain(state, chainRow);
        }

        applyReputation(state, supported, opposed, contribution);
        state.chainPlayerConsequenceState[chainRow] =
                CivilWarPlayerConsequenceState.APPLIED.toByte();
        state.chainPlayerConsequenceAppliedTick[chainRow] = day;
        return Result.APPLIED;
    }

    static int supportedDelta(int contribution) {
        if (contribution >= 60) return 15;
        if (contribution >= 30) return 10;
        return contribution > 0 ? 5 : 0;
    }

    static int opposedDelta(int contribution) {
        if (contribution >= 60) return -25;
        if (contribution >= 30) return -15;
        return contribution > 0 ? -8 : 0;
    }

    private static void applyReputation(CampaignState state, long supported,
                                        long opposed, int contribution) {
        adjust(state, supported, supportedDelta(contribution));
        adjust(state, opposed, opposedDelta(contribution));
    }

    private static void adjust(CampaignState state, long houseId, int delta) {
        int row = state.ensureRepRow(houseId);
        state.repValue[row] = Math.max(-100,
                Math.min(100, state.repValue[row] + delta));
    }

    private static boolean validHouses(CampaignState state, long supported,
                                       long opposed) {
        return supported >= 0L && opposed >= 0L && supported != opposed
                && state.houseIndex(supported) >= 0
                && state.houseIndex(opposed) >= 0;
    }

    private static Result closeClaim(CampaignState state, int row) {
        state.throneClaimPlayerConsequenceState[row] =
                CivilWarPlayerConsequenceState.NOT_APPLICABLE.toByte();
        return Result.NOT_APPLICABLE;
    }

    private static Result closeChain(CampaignState state, int row) {
        state.chainPlayerConsequenceState[row] =
                CivilWarPlayerConsequenceState.NOT_APPLICABLE.toByte();
        return Result.NOT_APPLICABLE;
    }
}
