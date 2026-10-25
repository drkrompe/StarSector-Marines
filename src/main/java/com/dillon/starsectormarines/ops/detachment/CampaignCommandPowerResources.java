package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Live player-cargo adapter for command-power costs paid during a battle. */
public final class CampaignCommandPowerResources implements CommandPowerResources {

    /** Supplies this battle has taken from cargo, so an erased attempt can return them. */
    private int spent;

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
        spent += amount;
        return true;
    }

    /** Returns every supply this battle spent; the battle must not spend through it again. */
    public void refundSpent() {
        CargoAPI cargo = cargo();
        if (cargo == null || spent <= 0) return;
        cargo.addSupplies(spent);
        spent = 0;
    }

    private static CargoAPI cargo() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet == null ? null : fleet.getCargo();
    }
}
