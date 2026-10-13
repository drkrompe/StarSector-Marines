package com.dillon.starsectormarines.battle.salvage;

/** Field cargo, distinct from ammunition resupply and dropped infantry kit. */
public enum SalvageResource {
    METALS("metals", "Metals", 40),
    MACHINERY("heavy_machinery", "Heavy machinery", 8),
    SUPPLIES("supplies", "Supplies", 12);

    public final String commodityId;
    public final String displayName;
    public final int cacheQuantity;

    SalvageResource(String commodityId, String displayName, int cacheQuantity) {
        this.commodityId = commodityId;
        this.displayName = displayName;
        this.cacheQuantity = cacheQuantity;
    }
}
