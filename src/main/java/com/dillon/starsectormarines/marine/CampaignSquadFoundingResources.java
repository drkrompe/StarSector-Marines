package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.fs.starfarer.api.campaign.CargoAPI;

/** Live player-fleet marines and credits, and vanilla commodity presentation, for squad founding. */
public final class CampaignSquadFoundingResources implements SquadFoundingResources {

    @Override
    public int available(String commodityId) {
        CargoAPI cargo = cargo();
        return cargo == null ? 0 : Math.max(0, (int) Math.floor(cargo.getCommodityQuantity(commodityId)));
    }

    @Override
    public long credits() {
        CargoAPI cargo = cargo();
        return cargo == null || cargo.getCredits() == null ? 0 : Math.max(0, (long) Math.floor(cargo.getCredits().get()));
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
    public boolean spend(SquadFoundingCost cost) {
        // Both halves are checked before either is drawn, so a short purse takes no marines.
        if (!canAfford(cost)) return false;
        CargoAPI cargo = cargo();
        for (SquadFoundingCost.Line line : cost.lines()) cargo.removeCommodity(line.commodityId(), line.quantity());
        if (cost.credits() > 0) cargo.getCredits().subtract(cost.credits());
        return true;
    }

    private static CargoAPI cargo() {
        return CampaignBackend.playerFleet() == null ? null : CampaignBackend.playerFleet().getCargo();
    }
}
