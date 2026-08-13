package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Status;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import java.util.EnumSet;

/** Returns completed stationing personnel exactly once. */
public final class StationingReleaseSystem implements CampaignSystem {

    interface PersonnelStore {
        MarineCaptain captain(String id);
        boolean addMarines(int count);
        default boolean hasNamedAssignment(long contractId) { return false; }
        default boolean releaseNamedAssignment(long contractId) { return false; }
    }

    private final PersonnelStore store;

    public StationingReleaseSystem() {
        this(new LivePersonnelStore());
    }

    StationingReleaseSystem(PersonnelStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "StationingRelease";
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
        for (int i = 0; i < state.contractCount; i++) {
            ContractType type = ContractType.fromByte(state.contractType[i]);
            if (!type.isStationing()
                    || ContractState.fromByte(state.contractState[i]) != ContractState.COMPLETED) {
                continue;
            }
            int marines = state.contractMarinesCommitted[i];
            int captainSlot = state.contractCaptainId[i];
            long contractId = state.contractId[i];
            boolean named = store.hasNamedAssignment(contractId);
            if (marines <= 0 && captainSlot < 0 && !named) continue;

            String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
            MarineCaptain captain = captainId != null ? store.captain(captainId) : null;
            if (captainSlot >= 0 && captain == null) continue;
            if (named) {
                if (!store.releaseNamedAssignment(contractId)) continue;
            } else if (marines > 0 && !store.addMarines(marines)) {
                continue;
            }

            if (captain != null && captain.status() == Status.GARRISONED) {
                captain.setStatus(Status.ACTIVE);
                captain.commendations().add("Day " + day + ": Returned from "
                        + type.name().toLowerCase() + " duty.");
            }
            state.contractMarinesCommitted[i] = 0;
            state.contractCaptainId[i] = -1;
        }
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
