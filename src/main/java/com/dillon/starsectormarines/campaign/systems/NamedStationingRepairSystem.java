package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Status;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Reconciles named stationing bindings after both persisted graphs are available. */
public final class NamedStationingRepairSystem implements CampaignSystem {

    @Override
    public String name() {
        return "NamedStationingRepair";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script != null) repair(state, script.roster());
    }

    public static int repair(CampaignState state, MarineRoster roster) {
        if (state == null || roster == null) return 0;
        int repairs = 0;
        Set<Long> inspected = new HashSet<>();
        for (MarineSquad squad : roster.squads()) {
            long contractId = squad.stationingContractId();
            if (contractId <= 0L || !inspected.add(contractId)) continue;
            int row = state.contractIndex(contractId);
            if (row < 0 || !ContractType.fromByte(state.contractType[row]).isStationing()) {
                repairs += roster.releaseStationing(contractId);
                continue;
            }

            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            String captainId = state.captainRegistry.get(state.contractCaptainId[row]);
            MarineCaptain captain = roster.byId(captainId);
            if (!mayOwnNamedPersonnel(contractState) || captain == null
                    || captain.status() == Status.INJURED || captain.status() == Status.KIA) {
                repairs += settleInvalidBinding(state, row, contractId, roster, captain);
                continue;
            }

            int living = roster.stationedLivingCount(contractId);
            if (state.contractMarinesCommitted[row] != living) {
                state.contractMarinesCommitted[row] = living;
                repairs++;
            }
            if (captain.status() == Status.ACTIVE) {
                captain.setStatus(Status.GARRISONED);
                repairs++;
            }
        }
        return repairs;
    }

    private static boolean mayOwnNamedPersonnel(ContractState state) {
        return state == ContractState.ACTIVE || state == ContractState.IN_PROGRESS
                || state == ContractState.DEFAULTED || state == ContractState.COMPLETED;
    }

    private static int settleInvalidBinding(CampaignState state, int row, long contractId,
                                            MarineRoster roster, MarineCaptain captain) {
        int repairs = roster.releaseStationing(contractId);
        if (captain != null && captain.status() == Status.GARRISONED) {
            captain.setStatus(Status.ACTIVE);
            repairs++;
        }
        if (state.contractMarinesCommitted[row] != 0) {
            state.contractMarinesCommitted[row] = 0;
            repairs++;
        }
        if (state.contractCaptainId[row] != -1) {
            state.contractCaptainId[row] = -1;
            repairs++;
        }
        return repairs;
    }
}
