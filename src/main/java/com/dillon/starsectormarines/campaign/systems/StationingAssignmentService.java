package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Status;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

/** Validates and atomically records one stationing personnel commitment. */
public final class StationingAssignmentService {

    interface MarineStore {
        int available();
        boolean remove(int count);
    }

    private StationingAssignmentService() {}

    public static boolean accept(CampaignState state, long contractId,
                                 MarineCaptain captain, int marineCount,
                                 int requestedMonths, int day) {
        return accept(state, contractId, captain, marineCount, requestedMonths, day,
                new MarineStore() {
                    @Override
                    public int available() {
                        return availablePlayerMarines();
                    }

                    @Override
                    public boolean remove(int count) {
                        return removePlayerMarines(count);
                    }
                });
    }

    static boolean accept(CampaignState state, long contractId,
                          MarineCaptain captain, int marineCount,
                          int requestedMonths, int day,
                          MarineStore store) {
        if (state == null || store == null || captain == null
                || captain.status() != Status.ACTIVE) {
            return false;
        }
        int row = state.contractIndex(contractId);
        if (row < 0 || ContractState.fromByte(state.contractState[row]) != ContractState.OFFERED) {
            return false;
        }
        if (!ContractEligibility.contractAcceptable(state, contractId)) return false;
        ContractType type = ContractType.fromByte(state.contractType[row]);
        if (!type.isStationing()) return false;
        int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
        if (patronRow < 0
                || HouseStatus.fromByte(state.houseStatus[patronRow]) != HouseStatus.ACTIVE) {
            return false;
        }
        HouseRank rank = HouseRank.fromByte(state.houseRank[patronRow]);
        StationingContractTerms terms = StationingContractTerms.create(
                type, rank, marineCount, requestedMonths);
        if (terms == null || store.available() < terms.committedMarines
                || !store.remove(terms.committedMarines)) {
            return false;
        }

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
        state.contractSalvageBaseline[row] = terms.salvageBaseline;
        state.contractSalvageNegotiated[row] = terms.salvageBaseline;
        state.contractCashMultiplier[row] = 100;
        captain.setStatus(Status.GARRISONED);
        captain.commendations().add("Day " + day + ": Assigned to "
                + type.name().toLowerCase() + " duty.");
        return true;
    }

    private static int availablePlayerMarines() {
        CampaignFleetAPI fleet = playerFleet();
        return fleet != null && fleet.getCargo() != null ? fleet.getCargo().getMarines() : 0;
    }

    private static boolean removePlayerMarines(int count) {
        CampaignFleetAPI fleet = playerFleet();
        if (fleet == null || fleet.getCargo() == null || fleet.getCargo().getMarines() < count) {
            return false;
        }
        fleet.getCargo().removeMarines(count);
        return true;
    }

    private static CampaignFleetAPI playerFleet() {
        return Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
    }
}
