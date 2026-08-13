package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilWarOfferAcceptance;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.Status;

import java.util.ArrayList;
import java.util.List;

/** Validates and atomically records one stationing personnel commitment. */
public final class StationingAssignmentService {

    interface MarineStore {
        int available();
        boolean remove(int count);
    }

    private StationingAssignmentService() {}

    /** Accepts a new named assignment without consuming anonymous marine cargo. */
    public static boolean acceptNamed(CampaignState state, long contractId,
                                      MarineRoster roster, MarineCaptain captain,
                                      Iterable<String> squadIds,
                                      int requestedMonths, int day) {
        if (roster == null || captain == null || roster.byId(captain.id()) == null) {
            return false;
        }
        if (squadIds == null) return false;
        List<String> selected = new ArrayList<>();
        for (String squadId : squadIds) selected.add(squadId);
        int living = roster.livingCount(selected);
        StationingContractTerms terms = eligibleTerms(
                state, contractId, captain, living, requestedMonths);
        if (terms == null || !roster.bindStationing(contractId, captain.id(), selected)) {
            return false;
        }
        activate(state, contractId, captain, terms, day);
        return true;
    }

    /** Retained only to prove the pre-named acceptance behavior for compatibility tests. */
    static boolean acceptLegacy(CampaignState state, long contractId,
                                MarineCaptain captain, int marineCount,
                                int requestedMonths, int day,
                                MarineStore store) {
        if (state == null || store == null || captain == null
                || captain.status() != Status.ACTIVE) return false;
        StationingContractTerms terms = eligibleTerms(
                state, contractId, captain, marineCount, requestedMonths);
        if (terms == null || store.available() < terms.committedMarines
                || !store.remove(terms.committedMarines)) {
            return false;
        }

        activate(state, contractId, captain, terms, day);
        return true;
    }

    private static StationingContractTerms eligibleTerms(
            CampaignState state, long contractId, MarineCaptain captain,
            int marineCount, int requestedMonths) {
        if (state == null || captain == null || captain.status() != Status.ACTIVE) return null;
        int row = state.contractIndex(contractId);
        if (row < 0 || ContractState.fromByte(state.contractState[row]) != ContractState.OFFERED) {
            return null;
        }
        if (!ContractEligibility.contractAcceptable(state, contractId)) return null;
        if (CivilWarOfferAcceptance.isParticipation(state, contractId)
                && !CivilWarOfferAcceptance.canAccept(state, contractId)) {
            return null;
        }
        ContractType type = ContractType.fromByte(state.contractType[row]);
        if (!type.isStationing()) return null;
        int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
        if (patronRow < 0
                || HouseStatus.fromByte(state.houseStatus[patronRow]) != HouseStatus.ACTIVE) {
            return null;
        }
        HouseRank rank = HouseRank.fromByte(state.houseRank[patronRow]);
        return StationingContractTerms.create(
                type, rank, marineCount, requestedMonths);
    }

    private static void activate(CampaignState state, long contractId,
                                 MarineCaptain captain,
                                 StationingContractTerms terms, int day) {
        int row = state.contractIndex(contractId);
        ContractType type = terms.type;

        state.contractState[row] = ContractState.ACTIVE.toByte();
        state.contractAcceptedTick[row] = day;
        state.contractExpiresTick[row] = day + terms.termDays;
        state.contractOfferExpiresTick[row] = -1;
        state.contractPhasesTotal[row] = 0;
        state.contractCaptainId[row] = state.captainRegistry.intern(captain.id());
        state.contractBasePayout[row] = 0;
        state.contractRetainerPerMonth[row] = terms.monthlyRetainer;
        state.contractMarinesCommitted[row] = terms.committedMarines;
        state.contractLastRetainerTick[row] = day;
        state.contractLastTrainingTick[row] = type == ContractType.CADRE ? day : -1;
        state.contractLastDefaultCheckTick[row] = day;
        state.contractNextIncidentTick[row] = type == ContractType.CADRE
                ? StationingIncidentSystem.nextIncidentDay(contractId, day) : -1;
        state.contractIncidentPending[row] = 0;
        state.contractIncidentType[row] = StationingIncidentType.NONE.toByte();
        state.contractDefenseEventKey[row] = 0L;
        state.contractDefenseTriggeredTick[row] = -1;
        state.contractDefenseTriggerType[row] = GarrisonDefenseTriggerType.NONE.toByte();
        state.contractDefenseAttackerHouseId[row] = -1L;
        state.contractDefenseAttackerFactionId[row] = -1;
        state.contractSalvageBaseline[row] = terms.salvageBaseline;
        state.contractSalvageNegotiated[row] = terms.salvageBaseline;
        state.contractCashMultiplier[row] = 100;
        captain.setStatus(Status.GARRISONED);
        captain.commendations().add("Day " + day + ": Assigned to "
                + type.name().toLowerCase() + " duty.");
        if (CivilWarOfferAcceptance.isParticipation(state, contractId)) {
            CivilWarOfferAcceptance.onStationingAccepted(state, contractId);
        }
    }

}
