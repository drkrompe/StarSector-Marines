package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.EnumSet;

/**
 * Tick phase 4: roll garrison-contract payment defaults.
 *
 * <p>Per <code>economy.md</code>: an active garrison contract has a per-tick
 * default chance scaled by patron strength, mission count since payment,
 * etc. A default spawns an extraction mission against the patron's location
 * (the player can go collect, fight through the defaulting client).
 *
 * <p>Stub — garrison contracts aren't modeled in {@link CampaignState} yet.
 */
public final class GarrisonDefaultSystem implements CampaignSystem {

    @Override
    public String name() {
        return "GarrisonDefault";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.PLAYER_REP, CampaignTable.HOUSES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.PLAYER_REP);
    }

    @Override
    public void tick(CampaignState state, int day) {
        // Pending: walk player garrison contracts (once they're a table),
        // roll default chance, mutate reputation + spawn extraction intel.
    }
}
