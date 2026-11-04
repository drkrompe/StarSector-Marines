package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/** The saved start's directions; progress is read from the real variant and company. */
public final class CompanyFoundingIntel extends BaseIntelPlugin {
    @Override public String getName() {
        var destination = CerberusCompanyStart.destination();
        return CerberusCompanyStart.conversionRequired()
                ? "Second Wind: " + (destination == null ? Strings.get("openingCompanyFoundingIntelFindAWorkingSpaceport") : "Reach " + destination.getName())
                : Strings.get("openingCompanyFoundingIntelCastleAHomeForTheCompany");
    }
    @Override public String getIcon() { return CompanyManagementGuide.portrait(); }
    @Override public SectorEntityToken getMapLocation(SectorMapAPI map) { return CerberusCompanyStart.destination(); }
    @Override public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0);
        info.addPara(Strings.get("openingCompanyFoundingIntelCerberusConversionAndCompanyPreparation"), 4);
    }
    @Override public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addImage(CompanyManagementGuide.portrait(), 96, 96, 0);
        info.addPara(Strings.get("openingCompanyFoundingIntelCastleCompanyOfficer"), 8);
        info.addPara(Strings.get("openingCompanyFoundingIntelSecondWindCarriedYourCrewOffHarker"), 8);
        var destination = CerberusCompanyStart.destination();
        if (destination != null)
            info.addPara(Strings.format("openingCompanyFoundingIntelCastleSContactOnJangalaIsNessa", destination.getName()), 8);
        info.addPara(CerberusCompanyStart.instructions(), 8);
        int reserved = CompanyYard.reservedCredits(CerberusCompanyStart.startingShip());
        if (reserved > 0) info.addPara(Strings.format("openingCompanyFoundingIntelCastleSConversionReserveCreditsHeldOutside", reserved), 8);
    }
}
