package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;

/** Live commodity names and icons supplied by Starsector's commodity registry. */
public final class CampaignCommodityPresentation implements CommodityPresentation {

    public static final CampaignCommodityPresentation INSTANCE =
            new CampaignCommodityPresentation();

    private CampaignCommodityPresentation() { }

    @Override
    public String commodityName(String commodityId) {
        CommoditySpecAPI spec = spec(commodityId);
        return spec != null && spec.getName() != null
                ? spec.getName() : CommodityPresentation.super.commodityName(commodityId);
    }

    @Override
    public String commodityIcon(String commodityId) {
        CommoditySpecAPI spec = spec(commodityId);
        return spec != null && spec.getIconName() != null ? spec.getIconName() : "";
    }

    private static CommoditySpecAPI spec(String commodityId) {
        SettingsAPI settings = Global.getSettings();
        return settings != null && commodityId != null
                ? settings.getCommoditySpec(commodityId) : null;
    }
}
