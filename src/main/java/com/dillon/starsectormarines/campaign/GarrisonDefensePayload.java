package com.dillon.starsectormarines.campaign;

/** Immutable view of one pending Garrison defense and its stationed detachment. */
public final class GarrisonDefensePayload {

    public final long contractId;
    public final long eventKey;
    public final GarrisonDefenseTriggerType triggerType;
    public final int triggeredDay;
    public final int marketId;
    public final long attackerHouseId;
    public final int attackerFactionId;
    public final String captainId;
    public final int committedMarines;
    public final byte salvageBaseline;
    public final byte salvageNegotiated;

    private GarrisonDefensePayload(long contractId, long eventKey,
                                   GarrisonDefenseTriggerType triggerType,
                                   int triggeredDay, int marketId,
                                   long attackerHouseId, int attackerFactionId,
                                   String captainId, int committedMarines,
                                   byte salvageBaseline, byte salvageNegotiated) {
        this.contractId = contractId;
        this.eventKey = eventKey;
        this.triggerType = triggerType;
        this.triggeredDay = triggeredDay;
        this.marketId = marketId;
        this.attackerHouseId = attackerHouseId;
        this.attackerFactionId = attackerFactionId;
        this.captainId = captainId;
        this.committedMarines = committedMarines;
        this.salvageBaseline = salvageBaseline;
        this.salvageNegotiated = salvageNegotiated;
    }

    public static GarrisonDefensePayload from(CampaignState state, long contractId) {
        if (state == null) return null;
        int row = state.contractIndex(contractId);
        if (row < 0 || ContractType.fromByte(state.contractType[row]) != ContractType.GARRISON
                || ContractState.fromByte(state.contractState[row]) != ContractState.IN_PROGRESS
                || state.contractDefenseEventKey[row] == 0L) {
            return null;
        }
        GarrisonDefenseTriggerType type = GarrisonDefenseTriggerType.fromByte(
                state.contractDefenseTriggerType[row]);
        if (type == GarrisonDefenseTriggerType.NONE) return null;
        int captainSlot = state.contractCaptainId[row];
        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        return new GarrisonDefensePayload(contractId, state.contractDefenseEventKey[row],
                type, state.contractDefenseTriggeredTick[row], state.contractMarketId[row],
                state.contractDefenseAttackerHouseId[row],
                state.contractDefenseAttackerFactionId[row], captainId,
                state.contractMarinesCommitted[row],
                state.contractSalvageBaseline[row], state.contractSalvageNegotiated[row]);
    }
}
