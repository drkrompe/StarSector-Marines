package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.dillon.starsectormarines.marine.CampaignFabricationResources;
import java.util.Map;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/** The saved start's directions; progress is read from the real variant and company. */
public final class CompanyFoundingIntel extends BaseIntelPlugin {
    @Override public String getName() {
        var destination = CerberusCompanyStart.destination();
        return CerberusCompanyStart.conversionRequired()
                ? "Second Wind: " + (destination == null ? "Find a working spaceport" : "Reach " + destination.getName())
                : "Castle: A home for the company";
    }
    @Override public String getIcon() { return CompanyManagementGuide.portrait(); }
    @Override public SectorEntityToken getMapLocation(SectorMapAPI map) { return CerberusCompanyStart.destination(); }
    @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0);
        info.addPara("Cerberus conversion and company preparation", 4);
    }
    @Override public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addImage(CompanyManagementGuide.portrait(), 96, 96, 0);
        info.addPara("CASTLE / COMPANY OFFICER", 8);
        info.addPara("Second Wind carried your crew off Harker's Claim, an independent mining asteroid abandoned by its operator. "
                + "The departure roster still has Castle's additions in the margin. Everyone on it is counting on this ship lasting "
                + "long enough to earn a living.", 8);
        var destination = CerberusCompanyStart.destination();
        if (destination != null && "jangala".equals(destination.getId()))
            info.addPara("Castle's contact on Jangala is Nessa Venn, a yard forewoman from her old ore runs. "
                    + "Nessa has agreed to take on the conversion and hold your prepaid stores.", 8);
        info.addPara(CerberusCompanyStart.instructions(), 8);
        int reserved = CompanyYard.reservedCredits(CerberusCompanyStart.startingShip());
        if (reserved > 0) info.addPara("Castle's conversion reserve: " + reserved
                + " credits, held outside the fleet account for the original Second Wind only.", 8);
        var depot = CerberusCompanyStart.depot();
        if (depot != null) {
            info.addPara("Prepaid depot stock remaining (available only while docked at its planet):", 8);
            if (depot.protectedStock()) info.addPara("Shuttle parts and first-squad stores are separately earmarked. "
                    + "Other workshop jobs cannot draw these supplies.", 8);
            var presentation = new CampaignFabricationResources();
            depot.stock().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    info.addPara(presentation.commodityName(e.getKey()) + ": " + e.getValue(), 3));
        }
    }
}
