package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable view of a pending Cadre incident and its stationed detachment. */
public final class StationingIncidentPayload {

    public final long contractId;
    public final StationingIncidentType type;
    public final int dueDay;
    public final int marketId;
    public final String captainId;
    public final int committedMarines;
    public final List<String> fireteamIds;
    public final int activeSeats;

    private StationingIncidentPayload(long contractId, StationingIncidentType type,
                                      int dueDay, int marketId, String captainId,
                                      int committedMarines,
                                      List<String> fireteamIds, int activeSeats) {
        this.contractId = contractId;
        this.type = type;
        this.dueDay = dueDay;
        this.marketId = marketId;
        this.captainId = captainId;
        this.committedMarines = committedMarines;
        this.fireteamIds = Collections.unmodifiableList(new ArrayList<>(fireteamIds));
        this.activeSeats = activeSeats;
    }

    public static StationingIncidentPayload from(CampaignState state, long contractId) {
        return from(state, contractId, null);
    }

    public static StationingIncidentPayload from(CampaignState state, long contractId,
                                                  MarineRoster roster) {
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
        List<String> fireteamIds = new ArrayList<>();
        if (roster != null) {
            for (MarineSquad squad : roster.squadsStationedOn(contractId)) {
                fireteamIds.add(squad.id());
            }
        }
        int activeSeats = fireteamIds.isEmpty()
                ? state.contractMarinesCommitted[row]
                : roster.stationedActiveCount(contractId);
        return new StationingIncidentPayload(contractId, type,
                state.contractNextIncidentTick[row], state.contractMarketId[row],
                captainId, state.contractMarinesCommitted[row], fireteamIds, activeSeats);
    }
}
