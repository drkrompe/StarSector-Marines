package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Live player-cargo adapter for command-power costs paid during a battle. */
public final class CampaignCommandPowerResources implements CommandPowerResources {

    @Override
    public int availableSupplies() {
        CargoAPI cargo = cargo();
        return cargo == null ? 0 : (int) Math.floor(cargo.getSupplies());
    }

    @Override
    public boolean spendSupplies(int amount) {
        if (amount <= 0) return true;
        CargoAPI cargo = cargo();
        if (cargo == null || cargo.getSupplies() < amount) return false;
        cargo.removeSupplies(amount);
        return true;
    }

    private static CargoAPI cargo() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet == null ? null : fleet.getCargo();
    }
}
