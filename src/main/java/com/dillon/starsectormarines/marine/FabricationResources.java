package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CommodityPresentation;

/**
 * External player-cargo authority used by workshop previews and atomic commits.
 *
 * <p>Every room that spends materials spends the player's, so this is one
 * authority the Mech Lab and the Boat Deck share rather than two views of the
 * same hold that can disagree about what is in it.
 */
public interface FabricationResources extends CommodityPresentation {

    FabricationResources NONE = new FabricationResources() {
        @Override public int available(String commodityId) { return 0; }
        @Override public String commodityName(String commodityId) { return commodityId; }
        @Override public String commodityIcon(String commodityId) { return ""; }
        @Override public boolean spend(FabricationCost cost) { return false; }
    };

    int available(String commodityId);

    /** Current installed-workshop price; both previews and commits ask here. */
    default FabricationCost quote(FabricationCost base) { return base; }

    /** Rechecks and consumes the whole cost, or consumes nothing. */
    boolean spend(FabricationCost cost);

    /** Puts salvaged materials back in the hold; false where there is no hold to take them. */
    default boolean credit(FabricationCost salvage) { return false; }

    /** How much the ship's machine shop gets back from a recycled spare now; previews and commits both ask. */
    default Reclamation reclamation() { return Reclamation.NONE; }

    default boolean canAfford(FabricationCost cost) {
        if (cost == null) return false;
        for (FabricationCost.Line line : cost.lines()) {
            if (available(line.commodityId()) < line.quantity()) return false;
        }
        return true;
    }
}
