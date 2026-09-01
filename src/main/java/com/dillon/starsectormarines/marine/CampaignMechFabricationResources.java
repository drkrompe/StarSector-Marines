package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;

import java.util.Locale;

/** Live player-fleet cargo and vanilla commodity presentation for the Mech Lab. */
public final class CampaignMechFabricationResources implements MechFabricationResources {

    @Override
    public int available(String commodityId) {
        CargoAPI cargo = cargo();
        return cargo == null ? 0 : Math.max(0,
                (int) Math.floor(cargo.getCommodityQuantity(commodityId)));
    }

    @Override
    public String commodityName(String commodityId) {
        CommoditySpecAPI spec = spec(commodityId);
        return spec != null ? spec.getName() : commodityId.replace('_', ' ')
                .toUpperCase(Locale.ROOT);
    }

    @Override
    public String commodityIcon(String commodityId) {
        CommoditySpecAPI spec = spec(commodityId);
        return spec != null && spec.getIconName() != null ? spec.getIconName() : "";
    }

    @Override
    public boolean spend(MechFabricationCost cost) {
        CargoAPI cargo = cargo();
        if (cargo == null || !canAfford(cost)) return false;
        for (MechFabricationCost.Line line : cost.lines()) {
            cargo.removeCommodity(line.commodityId(), line.quantity());
        }
        return true;
    }

    private static CommoditySpecAPI spec(String commodityId) {
        SettingsAPI settings = Global.getSettings();
        return settings != null ? settings.getCommoditySpec(commodityId) : null;
    }

    private static CargoAPI cargo() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                ? Global.getSector().getPlayerFleet().getCargo() : null;
    }
}
