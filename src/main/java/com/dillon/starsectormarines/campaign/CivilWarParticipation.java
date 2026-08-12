package com.dillon.starsectormarines.campaign;

/** Validates and applies one completed civil-war participation contract. */
public final class CivilWarParticipation {

    public enum Result {
        APPLIED,
        ALREADY_APPLIED,
        NOT_READY,
        NOT_PARTICIPATION,
        INVALID,
        ALLEGIANCE_CONFLICT,
        CHAIN_TERMINAL
    }

    private CivilWarParticipation() {}

    public static Result applyCompleted(CampaignState state, long contractId, int day) {
        if (state == null) return Result.INVALID;
        int contractRow = state.contractIndex(contractId);
        if (contractRow < 0) return Result.INVALID;
        CivilWarBand band = CivilWarBand.fromByte(state.contractCivilWarBand[contractRow]);
        if (band == CivilWarBand.NONE) return Result.NOT_PARTICIPATION;
        if (state.contractCivilWarContributionAppliedTick[contractRow] >= 0) {
            return Result.ALREADY_APPLIED;
        }
        if (ContractState.fromByte(state.contractState[contractRow])
                != ContractState.COMPLETED) {
            return Result.NOT_READY;
        }

        Lineage lineage = lineage(state, contractRow);
        if (lineage == null) return Result.INVALID;
        int chainRow = state.chainIndex(lineage.chainId);
        if (!validChainAndParties(state, contractRow, chainRow, lineage.allegiance)
                || !validContractType(state, contractRow, band, lineage.allegiance)) {
            return Result.INVALID;
        }
        if (ChainState.fromByte(state.chainState[chainRow]) != ChainState.ACTIVE) {
            return Result.CHAIN_TERMINAL;
        }

        CivilWarAllegiance existing = CivilWarAllegiance.fromByte(
                state.chainPlayerAllegiance[chainRow]);
        if (existing != CivilWarAllegiance.NONE && existing != lineage.allegiance) {
            return Result.ALLEGIANCE_CONFLICT;
        }
        if (existing == CivilWarAllegiance.NONE) {
            state.chainPlayerAllegiance[chainRow] = lineage.allegiance.toByte();
        }

        int threshold = Math.max(0, state.chainThreshold[chainRow]);
        int progress = Math.max(0, state.chainProgress[chainRow]);
        if (band == CivilWarBand.OPEN_CONFLICT) {
            if (lineage.allegiance == CivilWarAllegiance.CLAIMANT) {
                state.chainProgress[chainRow] = (short) Math.min(Short.MAX_VALUE, threshold);
            } else {
                state.chainState[chainRow] = ChainState.FAILED.toByte();
                state.chainResolvedTick[chainRow] = day;
            }
        } else {
            int signedWeight = lineage.allegiance == CivilWarAllegiance.CLAIMANT
                    ? band.contributionWeight : -band.contributionWeight;
            int adjusted = Math.max(0, Math.min(threshold, progress + signedWeight));
            state.chainProgress[chainRow] = (short) adjusted;
        }

        int contribution = (state.chainPlayerContribution[chainRow] & 0xFFFF)
                + band.contributionWeight;
        state.chainPlayerContribution[chainRow] =
                (short) Math.min(Short.MAX_VALUE, contribution);
        state.chainPlayerLastContributionTick[chainRow] = day;
        state.contractCivilWarContributionAppliedTick[contractRow] = day;
        return Result.APPLIED;
    }

    private static Lineage lineage(CampaignState state, int contractRow) {
        long parent = state.contractChainId[contractRow];
        long opposed = state.contractOpposedChainId[contractRow];
        if (parent >= 0L && opposed < 0L) {
            return new Lineage(parent, CivilWarAllegiance.CLAIMANT);
        }
        if (opposed >= 0L && parent < 0L) {
            return new Lineage(opposed, CivilWarAllegiance.INCUMBENT);
        }
        return null;
    }

    private static boolean validChainAndParties(CampaignState state, int contractRow,
                                                int chainRow,
                                                CivilWarAllegiance allegiance) {
        if (chainRow < 0
                || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    != ChainArchetype.CIVIL_WAR
                || state.contractMarketId[contractRow] != state.chainMarketId[chainRow]) {
            return false;
        }
        long expectedPatron = allegiance == CivilWarAllegiance.CLAIMANT
                ? state.chainActorHouseId[chainRow] : state.chainTarget[chainRow];
        long expectedTarget = allegiance == CivilWarAllegiance.CLAIMANT
                ? state.chainTarget[chainRow] : state.chainActorHouseId[chainRow];
        return state.contractPatronHouseId[contractRow] == expectedPatron
                && state.contractTargetHouseId[contractRow] == expectedTarget;
    }

    private static boolean validContractType(CampaignState state, int contractRow,
                                             CivilWarBand band,
                                             CivilWarAllegiance allegiance) {
        ContractType type = ContractType.fromByte(state.contractType[contractRow]);
        switch (band) {
            case COALITION_BUILDING:
                return type == (allegiance == CivilWarAllegiance.CLAIMANT
                        ? ContractType.ESCORT : ContractType.STRIKE);
            case MOBILIZATION:
                return type == (allegiance == CivilWarAllegiance.CLAIMANT
                        ? ContractType.CADRE : ContractType.GARRISON);
            case OPEN_CONFLICT:
                return type == ContractType.PLANETARY_ASSAULT;
            case NONE:
            default:
                return false;
        }
    }

    private static final class Lineage {
        final long chainId;
        final CivilWarAllegiance allegiance;

        Lineage(long chainId, CivilWarAllegiance allegiance) {
            this.chainId = chainId;
            this.allegiance = allegiance;
        }
    }
}
