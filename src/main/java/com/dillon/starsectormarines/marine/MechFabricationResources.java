package com.dillon.starsectormarines.marine;

/** External player-cargo authority used by Mech Lab previews and atomic commits. */
public interface MechFabricationResources {

    MechFabricationResources NONE = new MechFabricationResources() {
        @Override public int available(String commodityId) { return 0; }
        @Override public String commodityName(String commodityId) { return commodityId; }
        @Override public String commodityIcon(String commodityId) { return ""; }
        @Override public boolean spend(MechFabricationCost cost) { return false; }
    };

    int available(String commodityId);

    String commodityName(String commodityId);

    /** Base-game commodity icon path from the live commodity specification. */
    String commodityIcon(String commodityId);

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
