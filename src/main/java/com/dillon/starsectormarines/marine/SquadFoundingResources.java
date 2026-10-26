package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CommodityPresentation;

/**
 * External authority for a squad-founding preview and atomic commit: the marines in player cargo,
 * the company's credits, and whether the fleet is docked at a civilized port to buy the rest.
 */
public interface SquadFoundingResources extends CommodityPresentation {

    SquadFoundingResources NONE = new SquadFoundingResources() {
        @Override public int available(String commodityId) { return 0; }
        @Override public long credits() { return 0; }
        @Override public boolean atMarket() { return false; }
        @Override public String commodityName(String commodityId) { return commodityId; }
        @Override public String commodityIcon(String commodityId) { return ""; }
        @Override public boolean spend(SquadFoundingCost cost) { return false; }
    };

    int available(String commodityId);

    /** The credits this founding may draw. */
    long credits();

    /** Whether the fleet is docked at a civilized port, where the squad's stores can be bought. */
    boolean atMarket();

    /** Rechecks and consumes the complete bill, marines and credits, or consumes nothing. */
    boolean spend(SquadFoundingCost cost);

    default boolean canAfford(SquadFoundingCost cost) {
        if (cost == null || credits() < cost.credits()) return false;
        for (SquadFoundingCost.Line line : cost.lines()) {
            if (available(line.commodityId()) < line.quantity()) return false;
        }
        return true;
    }

    /** Whether a squad can be founded here and now: docked, with the marines and the price. */
    default boolean canBuy(SquadFoundingCost cost) {
        return atMarket() && canAfford(cost);
    }
}
