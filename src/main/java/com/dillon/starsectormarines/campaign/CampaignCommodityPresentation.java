package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;

/**
 * Live commodity names and icons supplied by Starsector's commodity registry, falling back to the
 * special-item registry for a bill line that is an item rather than a commodity.
 */
public final class CampaignCommodityPresentation implements CommodityPresentation {

    public static final CampaignCommodityPresentation INSTANCE =
            new CampaignCommodityPresentation();

    private CampaignCommodityPresentation() { }

    @Override
    public String commodityName(String commodityId) {
        CommoditySpecAPI spec = spec(commodityId);
        if (spec != null && spec.getName() != null) return spec.getName();
        SpecialItemSpecAPI item = item(commodityId);
        return item != null && item.getName() != null ? item.getName() : CommodityPresentation.super.commodityName(commodityId);
    }

    @Override
    public String commodityIcon(String commodityId) {
        CommoditySpecAPI spec = spec(commodityId);
        if (spec != null && spec.getIconName() != null) return spec.getIconName();
        SpecialItemSpecAPI item = item(commodityId);
        return item != null && item.getIconName() != null ? item.getIconName() : "";
    }

    private static CommoditySpecAPI spec(String commodityId) {
        SettingsAPI settings = Global.getSettings();
        try {
            return settings != null && commodityId != null ? settings.getCommoditySpec(commodityId) : null;
        } catch (RuntimeException notACommodity) {
            return null;
        }
    }

    private static SpecialItemSpecAPI item(String itemId) {
        SettingsAPI settings = Global.getSettings();
        try {
            return settings != null && itemId != null ? settings.getSpecialItemSpec(itemId) : null;
        } catch (RuntimeException notAnItem) {
            return null;
        }
    }
}
