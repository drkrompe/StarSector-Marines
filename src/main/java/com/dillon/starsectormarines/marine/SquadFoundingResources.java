package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CommodityPresentation;

/**
 * External authority for a squad-founding preview and atomic commit: the marines in player cargo
 * and the company's credits. Founding needs no port.
 */
public interface SquadFoundingResources extends CommodityPresentation {

    SquadFoundingResources NONE = new SquadFoundingResources() {
        @Override public int available(String commodityId) { return 0; }
        @Override public long credits() { return 0; }
        @Override public String commodityName(String commodityId) { return commodityId; }
        @Override public String commodityIcon(String commodityId) { return ""; }
        @Override public boolean spend(SquadFoundingCost cost) { return false; }
    };

    int available(String commodityId);

    /** The credits this founding may draw. */
    long credits();

    /** Rechecks and consumes the complete bill, marines and credits, or consumes nothing. */
    boolean spend(SquadFoundingCost cost);

    default boolean canAfford(SquadFoundingCost cost) {
        if (cost == null || credits() < cost.credits()) return false;
        for (SquadFoundingCost.Line line : cost.lines()) {
            if (available(line.commodityId()) < line.quantity()) return false;
        }
        return true;
    }
}
