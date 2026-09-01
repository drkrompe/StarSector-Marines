package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CommodityPresentation;

/** External player-cargo authority used by Mech Lab previews and atomic commits. */
public interface MechFabricationResources extends CommodityPresentation {

    MechFabricationResources NONE = new MechFabricationResources() {
        @Override public int available(String commodityId) { return 0; }
        @Override public String commodityName(String commodityId) { return commodityId; }
        @Override public String commodityIcon(String commodityId) { return ""; }
        @Override public boolean spend(MechFabricationCost cost) { return false; }
    };

    int available(String commodityId);

    /** Rechecks and consumes the whole cost, or consumes nothing. */
    boolean spend(MechFabricationCost cost);

    default boolean canAfford(MechFabricationCost cost) {
        if (cost == null) return false;
        for (MechFabricationCost.Line line : cost.lines()) {
            if (available(line.commodityId()) < line.quantity()) return false;
        }
        return true;
    }
}
