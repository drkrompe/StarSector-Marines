package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import java.util.Map;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/** The saved start's directions; progress is read from the real variant and company. */
public final class CompanyFoundingIntel extends BaseIntelPlugin {
    @Override public String getName() { return "Castle: A home for the company"; }
    @Override public String getIcon() { return CompanyManagementGuide.portrait(); }
    @Override public SectorEntityToken getMapLocation(SectorMapAPI map) { return CerberusCompanyStart.destination(); }
    @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0);
        info.addPara("Cerberus conversion and company preparation", 4);
    }
    @Override public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addImage(CompanyManagementGuide.portrait(), 96, 96, 0);
        info.addPara("CASTLE / COMPANY OFFICER", 8);
        info.addPara(CerberusCompanyStart.instructions(), 8);
        var depot = CerberusCompanyStart.depot();
        if (depot != null) {
            info.addPara("Prepaid depot stock remaining (available only while docked at its planet):", 8);
            var presentation = new CampaignFabricationResources();
            depot.stock().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    info.addPara(presentation.commodityName(e.getKey()) + ": " + e.getValue(), 3));
        }
    }
}
