package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractReputation;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Status;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

/** Atomically withdraws an idle stationing assignment and returns its personnel. */
public final class StationingWithdrawalService {

    interface PersonnelStore {
        MarineCaptain captain(String id);
        boolean addMarines(int count);
        default boolean hasNamedAssignment(long contractId) { return false; }
        default boolean releaseNamedAssignment(long contractId) { return false; }
    }

    private StationingWithdrawalService() {}

    public static boolean withdraw(CampaignState state, long contractId, int day) {
        return withdraw(state, contractId, day, new LivePersonnelStore());
    }

    static boolean withdraw(CampaignState state, long contractId, int day,
                            PersonnelStore store) {
        if (state == null || store == null) return false;
        int row = state.contractIndex(contractId);
        if (row < 0
                || ContractState.fromByte(state.contractState[row]) != ContractState.ACTIVE
                || !ContractType.fromByte(state.contractType[row]).isStationing()) {
            return false;
        }

        int marines = state.contractMarinesCommitted[row];
        int captainSlot = state.contractCaptainId[row];
        boolean named = store.hasNamedAssignment(contractId);
        if (marines <= 0 && captainSlot < 0 && !named) return false;
        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        MarineCaptain captain = captainId != null ? store.captain(captainId) : null;
        if (captainSlot >= 0 && captain == null) return false;
        if (named) {
            if (!store.releaseNamedAssignment(contractId)) return false;
        } else if (marines > 0 && !store.addMarines(marines)) {
            return false;
        }

        ContractType type = ContractType.fromByte(state.contractType[row]);
        if (captain != null && captain.status() == Status.GARRISONED) {
            captain.setStatus(Status.ACTIVE);
            captain.commendations().add("Day " + day + ": Withdrawn early from "
                    + type.name().toLowerCase() + " duty.");
        }
        state.contractMarinesCommitted[row] = 0;
        state.contractCaptainId[row] = -1;
        state.contractState[row] = ContractState.ABANDONED.toByte();
        ContractReputation.abandonedForContract(state, contractId, day);
        return true;
    }

    private static final class LivePersonnelStore implements PersonnelStore {
        @Override
        public MarineCaptain captain(String id) {
            MarineRosterScript roster = MarineRosterScript.getInstance();
            return roster != null ? roster.roster().byId(id) : null;
        }

        @Override
        public boolean addMarines(int count) {
            if (Global.getSector() == null) return false;
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet == null || fleet.getCargo() == null) return false;
            fleet.getCargo().addMarines(count);
            return true;
        }

        @Override
        public boolean hasNamedAssignment(long contractId) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            return script != null
                    && !script.roster().squadsStationedOn(contractId).isEmpty();
        }

        @Override
        public boolean releaseNamedAssignment(long contractId) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            return script != null && script.roster().releaseStationing(contractId) > 0;
        }
    }
}
