package com.dillon.starsectormarines.campaign;

/** Immutable view of a pending Cadre incident and its stationed detachment. */
public final class StationingIncidentPayload {

    public final long contractId;
    public final StationingIncidentType type;
    public final int dueDay;
    public final int marketId;
    public final String captainId;
    public final int committedMarines;

    private StationingIncidentPayload(long contractId, StationingIncidentType type,
                                      int dueDay, int marketId, String captainId,
                                      int committedMarines) {
        this.contractId = contractId;
        this.type = type;
        this.dueDay = dueDay;
        this.marketId = marketId;
        this.captainId = captainId;
        this.committedMarines = committedMarines;
    }

    public static StationingIncidentPayload from(CampaignState state, long contractId) {
        if (state == null) return null;
        int row = state.contractIndex(contractId);
        if (row < 0 || ContractType.fromByte(state.contractType[row]) != ContractType.CADRE
                || state.contractIncidentPending[row] == 0) {
            return null;
        }
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState != ContractState.ACTIVE
                && contractState != ContractState.IN_PROGRESS) {
            return null;
        }
        StationingIncidentType type = StationingIncidentType.fromByte(
                state.contractIncidentType[row]);
        if (type == StationingIncidentType.NONE) return null;
        int captainSlot = state.contractCaptainId[row];
        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        return new StationingIncidentPayload(contractId, type,
                state.contractNextIncidentTick[row], state.contractMarketId[row],
                captainId, state.contractMarinesCommitted[row]);
    }
}
