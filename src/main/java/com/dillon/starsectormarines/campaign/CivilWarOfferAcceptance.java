package com.dillon.starsectormarines.campaign;

/** Validates one civil-war offer choice and withdraws the opposing side. */
public final class CivilWarOfferAcceptance {

    private CivilWarOfferAcceptance() {}

    public static boolean isParticipation(CampaignState state, long contractId) {
        if (state == null) return false;
        int row = state.contractIndex(contractId);
        return row >= 0 && CivilWarBand.fromByte(
                state.contractCivilWarBand[row]) != CivilWarBand.NONE;
    }

    public static boolean isOfferedParticipation(CampaignState state,
                                                 long contractId) {
        if (!isParticipation(state, contractId)) return false;
        int row = state.contractIndex(contractId);
        return ContractState.fromByte(state.contractState[row])
                == ContractState.OFFERED;
    }

    public static boolean canAccept(CampaignState state, long contractId) {
        if (state == null) return false;
        int row = state.contractIndex(contractId);
        if (row < 0 || ContractState.fromByte(state.contractState[row])
                != ContractState.OFFERED) {
            return false;
        }
        Participation participation = participation(state, row);
        if (participation == null) return false;
        int chainRow = state.chainIndex(participation.chainId);
        if (!validOffer(state, row, chainRow, participation)) return false;

        CivilWarAllegiance locked = CivilWarAllegiance.fromByte(
                state.chainPlayerAllegiance[chainRow]);
        if (locked != CivilWarAllegiance.NONE
                && locked != participation.allegiance) {
            return false;
        }
        return !hasOpposingCommitment(state, row, participation);
    }

    /** Activates a mission-mode offer at deployment time. */
    public static boolean acceptMission(CampaignState state, long contractId, int day) {
        if (!canAccept(state, contractId)) return false;
        int row = state.contractIndex(contractId);
        state.contractState[row] = ContractState.ACTIVE.toByte();
        state.contractAcceptedTick[row] = day;
        state.contractOfferExpiresTick[row] = -1;
        withdrawOpposingOffers(state, row);
        return true;
    }

    /** Completes the shared side effects after stationing terms are activated. */
    public static void onStationingAccepted(CampaignState state, long contractId) {
        if (state == null) return;
        int row = state.contractIndex(contractId);
        if (row < 0 || ContractState.fromByte(state.contractState[row])
                != ContractState.ACTIVE) {
            return;
        }
        withdrawOpposingOffers(state, row);
    }

    private static void withdrawOpposingOffers(CampaignState state, int acceptedRow) {
        Participation accepted = participation(state, acceptedRow);
        if (accepted == null) return;
        for (int row = 0; row < state.contractCount; row++) {
            if (row == acceptedRow || ContractState.fromByte(state.contractState[row])
                    != ContractState.OFFERED) {
                continue;
            }
            Participation candidate = participation(state, row);
            if (candidate != null && candidate.chainId == accepted.chainId
                    && candidate.allegiance != accepted.allegiance) {
                state.contractState[row] = ContractState.EXPIRED.toByte();
            }
        }
    }

    private static boolean hasOpposingCommitment(CampaignState state, int offeredRow,
                                                  Participation offered) {
        for (int row = 0; row < state.contractCount; row++) {
            if (row == offeredRow) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                continue;
            }
            Participation existing = participation(state, row);
            if (existing != null && existing.chainId == offered.chainId
                    && existing.allegiance != offered.allegiance) {
                return true;
            }
        }
        return false;
    }

    private static boolean validOffer(CampaignState state, int contractRow,
                                      int chainRow, Participation participation) {
        if (chainRow < 0
                || ChainState.fromByte(state.chainState[chainRow]) != ChainState.ACTIVE
                || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    != ChainArchetype.CIVIL_WAR
                || CivilWarBand.forProgress(state.chainProgress[chainRow])
                    != participation.band
                || state.contractMarketId[contractRow] != state.chainMarketId[chainRow]) {
            return false;
        }
        long claimant = state.chainActorHouseId[chainRow];
        long incumbent = state.chainTarget[chainRow];
        long expectedPatron = participation.allegiance == CivilWarAllegiance.CLAIMANT
                ? claimant : incumbent;
        long expectedTarget = participation.allegiance == CivilWarAllegiance.CLAIMANT
                ? incumbent : claimant;
        return state.contractPatronHouseId[contractRow] == expectedPatron
                && state.contractTargetHouseId[contractRow] == expectedTarget
                && validType(state, contractRow, participation);
    }

    private static boolean validType(CampaignState state, int row,
                                     Participation participation) {
        ContractType type = ContractType.fromByte(state.contractType[row]);
        switch (participation.band) {
            case COALITION_BUILDING:
                return type == (participation.allegiance == CivilWarAllegiance.CLAIMANT
                        ? ContractType.ESCORT : ContractType.STRIKE);
            case MOBILIZATION:
                return type == (participation.allegiance == CivilWarAllegiance.CLAIMANT
                        ? ContractType.CADRE : ContractType.GARRISON);
            case OPEN_CONFLICT:
                return type == ContractType.PLANETARY_ASSAULT;
            case NONE:
            default:
                return false;
        }
    }

    private static Participation participation(CampaignState state, int row) {
        CivilWarBand band = CivilWarBand.fromByte(state.contractCivilWarBand[row]);
        if (band == CivilWarBand.NONE) return null;
        long parent = state.contractChainId[row];
        long opposed = state.contractOpposedChainId[row];
        if (parent >= 0L && opposed < 0L) {
            return new Participation(parent, CivilWarAllegiance.CLAIMANT, band);
        }
        if (opposed >= 0L && parent < 0L) {
            return new Participation(opposed, CivilWarAllegiance.INCUMBENT, band);
        }
        return null;
    }

    private static final class Participation {
        final long chainId;
        final CivilWarAllegiance allegiance;
        final CivilWarBand band;

        Participation(long chainId, CivilWarAllegiance allegiance,
                      CivilWarBand band) {
            this.chainId = chainId;
            this.allegiance = allegiance;
            this.band = band;
        }
    }
}
