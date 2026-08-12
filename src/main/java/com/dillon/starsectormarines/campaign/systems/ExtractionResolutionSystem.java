package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractReputation;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Status;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import java.util.EnumSet;

/** Applies the personnel and employer-reputation consequences of extraction. */
public final class ExtractionResolutionSystem implements CampaignSystem {

    static final int FAILED_EXTRACTION_INJURY_DAYS = 45;

    interface PersonnelStore {
        MarineCaptain captain(String id);
        boolean addMarines(int count);
    }

    private final PersonnelStore store;

    public ExtractionResolutionSystem() {
        this(new LivePersonnelStore());
    }

    ExtractionResolutionSystem(PersonnelStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "ExtractionResolution";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS, CampaignTable.PLAYER_REP);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (int row = 0; row < state.contractCount; row++) {
            if (ContractType.fromByte(state.contractType[row]) != ContractType.EXTRACTION) continue;
            ContractState result = ContractState.fromByte(state.contractState[row]);
            if (result != ContractState.COMPLETED && result != ContractState.FAILED) continue;

            int parentRow = state.contractIndex(state.contractSourceContractId[row]);
            if (parentRow < 0
                    || !ContractType.fromByte(state.contractType[parentRow]).isStationing()
                    || ContractState.fromByte(state.contractState[parentRow]) != ContractState.DEFAULTED) {
                continue;
            }
            resolve(state, parentRow, result == ContractState.COMPLETED, day);
        }
    }

    private void resolve(CampaignState state, int parentRow, boolean success, int day) {
        int marines = state.contractMarinesCommitted[parentRow];
        int captainSlot = state.contractCaptainId[parentRow];
        if (marines <= 0 && captainSlot < 0) return;

        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        MarineCaptain captain = captainId != null ? store.captain(captainId) : null;
        if (captainSlot >= 0 && captain == null) return;
        if (success && marines > 0 && !store.addMarines(marines)) return;

        if (captain != null && captain.status() == Status.GARRISONED) {
            if (success) {
                captain.setStatus(Status.ACTIVE);
                captain.commendations().add("Day " + day
                        + ": Recovered after employer default.");
            } else {
                captain.setStatus(Status.INJURED);
                captain.setInjuredUntilDay(day + FAILED_EXTRACTION_INJURY_DAYS);
                captain.commendations().add("Day " + day
                        + ": Injured during failed extraction after employer default.");
            }
        }

        state.contractMarinesCommitted[parentRow] = 0;
        state.contractCaptainId[parentRow] = -1;
        ContractReputation.employerBreached(
                state, state.contractPatronHouseId[parentRow], day);
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
    }
}
