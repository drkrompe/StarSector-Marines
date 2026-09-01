package com.dillon.starsectormarines.campaign;

/** Read-only presentation metadata for a Starsector commodity. */
public interface CommodityPresentation {

    CommodityPresentation NONE = new CommodityPresentation() { };

    default String commodityName(String commodityId) {
        return commodityId == null ? "" : commodityId.replace('_', ' ');
    }

    /** Base-game commodity icon path from the live commodity specification. */
    default String commodityIcon(String commodityId) {
        return "";
    }
}
