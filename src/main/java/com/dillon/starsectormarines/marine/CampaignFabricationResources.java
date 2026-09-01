package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Live player-fleet cargo and vanilla commodity presentation for the workshops. */
public final class CampaignFabricationResources implements FabricationResources {

    @Override
    public int available(String commodityId) {
        CargoAPI cargo = cargo();
        return cargo == null ? 0 : Math.max(0,
                (int) Math.floor(cargo.getCommodityQuantity(commodityId)));
    }

    @Override
    public String commodityName(String commodityId) {
        return CampaignCommodityPresentation.INSTANCE.commodityName(commodityId);
    }

    @Override
    public String commodityIcon(String commodityId) {
        return CampaignCommodityPresentation.INSTANCE.commodityIcon(commodityId);
    }

    @Override
    public boolean spend(FabricationCost cost) {
        CargoAPI cargo = cargo();
        if (cargo == null || !canAfford(cost)) return false;
        for (FabricationCost.Line line : cost.lines()) {
            cargo.removeCommodity(line.commodityId(), line.quantity());
        }
        return true;
    }

    private static CargoAPI cargo() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                ? Global.getSector().getPlayerFleet().getCargo() : null;
    }
}
