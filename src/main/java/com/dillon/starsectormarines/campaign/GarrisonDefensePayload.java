package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable view of one pending Garrison defense and its stationed detachment. */
public final class GarrisonDefensePayload {

    public final long contractId;
    public final long eventKey;
    public final GarrisonDefenseTriggerType triggerType;
    public final int triggeredDay;
    public final int marketId;
    public final long attackerHouseId;
    public final int attackerFactionId;
    /** Attacker ground strength in vanilla's raid-strength units; 0 when unestimated. */
    public final float attackerStrength;
    /** Attacking faction id as vanilla knows it, or null when the slot is unset. */
    public final String attackerFactionKey;
    public final String captainId;
    public final int committedMarines;
    public final List<String> fireteamIds;
    public final int activeSeats;
    public final byte salvageBaseline;
    public final byte salvageNegotiated;

    private GarrisonDefensePayload(long contractId, long eventKey,
                                   GarrisonDefenseTriggerType triggerType,
                                   int triggeredDay, int marketId,
                                   long attackerHouseId, int attackerFactionId,
                                   float attackerStrength, String attackerFactionKey,
                                   String captainId, int committedMarines,
                                   List<String> fireteamIds, int activeSeats,
                                   byte salvageBaseline, byte salvageNegotiated) {
        this.contractId = contractId;
        this.eventKey = eventKey;
        this.triggerType = triggerType;
        this.triggeredDay = triggeredDay;
        this.marketId = marketId;
        this.attackerHouseId = attackerHouseId;
        this.attackerFactionId = attackerFactionId;
        this.attackerStrength = attackerStrength;
        this.attackerFactionKey = attackerFactionKey;
        this.captainId = captainId;
        this.committedMarines = committedMarines;
        this.fireteamIds = Collections.unmodifiableList(new ArrayList<>(fireteamIds));
        this.activeSeats = activeSeats;
        this.salvageBaseline = salvageBaseline;
        this.salvageNegotiated = salvageNegotiated;
    }

    public static GarrisonDefensePayload from(CampaignState state, long contractId) {
        return from(state, contractId, null);
    }

    public static GarrisonDefensePayload from(CampaignState state, long contractId,
                                              MarineRoster roster) {
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
        List<String> fireteamIds = new ArrayList<>();
        if (roster != null) {
            for (MarineSquad squad : roster.squadsStationedOn(contractId)) {
                fireteamIds.add(squad.id());
            }
        }
        int activeSeats = fireteamIds.isEmpty()
                ? state.contractMarinesCommitted[row]
                : roster.stationedActiveCount(contractId);
        int attackerSlot = state.contractDefenseAttackerFactionId[row];
        String attackerFactionKey = attackerSlot >= 0
                ? state.factionRegistry.get(attackerSlot) : null;
        return new GarrisonDefensePayload(contractId, state.contractDefenseEventKey[row],
                type, state.contractDefenseTriggeredTick[row], state.contractMarketId[row],
                state.contractDefenseAttackerHouseId[row], attackerSlot,
                state.contractDefenseAttackerStrength[row], attackerFactionKey, captainId,
                state.contractMarinesCommitted[row], fireteamIds, activeSeats,
                state.contractSalvageBaseline[row], state.contractSalvageNegotiated[row]);
    }
}
