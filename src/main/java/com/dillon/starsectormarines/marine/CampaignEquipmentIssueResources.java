package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/** Live player-fleet cargo authority for Fleet Armory equipment transactions. */
public final class CampaignEquipmentIssueResources implements EquipmentIssueResources {

    @Override
    public EquipmentTemplateCost available() {
        CargoAPI cargo = cargo();
        return cargo == null ? EquipmentTemplateCost.ZERO : new EquipmentTemplateCost(
                floor(cargo.getSupplies()),
                floor(cargo.getCommodityQuantity(Commodities.HAND_WEAPONS)),
                floor(cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY)),
                floor(cargo.getCommodityQuantity(Commodities.FOOD)));
    }

    @Override
    public boolean spend(EquipmentTemplateCost cost) {
        CargoAPI cargo = cargo();
        if (cargo == null || !canAfford(cost)) return false;
        cargo.removeSupplies(cost.supplies());
        cargo.removeCommodity(Commodities.HAND_WEAPONS, cost.heavyArmaments());
        cargo.removeCommodity(Commodities.HEAVY_MACHINERY, cost.heavyMachinery());
        cargo.removeCommodity(Commodities.FOOD, cost.food());
        return true;
    }

    private static int floor(float amount) {
        return Math.max(0, (int) Math.floor(amount));
    }

    private static CargoAPI cargo() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                ? Global.getSector().getPlayerFleet().getCargo() : null;
    }
}
