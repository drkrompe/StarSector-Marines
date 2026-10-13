package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.i18n.Strings;
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
        if (destination != null && "jangala".equals(destination.getId()))
            info.addPara(Strings.get("openingCompanyFoundingIntelCastleSContactOnJangalaIsNessa"), 8);
        info.addPara(CerberusCompanyStart.instructions(), 8);
        int reserved = CompanyYard.reservedCredits(CerberusCompanyStart.startingShip());
        if (reserved > 0) info.addPara(Strings.format("openingCompanyFoundingIntelCastleSConversionReserveCreditsHeldOutside", reserved), 8);
        var depot = CerberusCompanyStart.depot();
        if (depot != null) {
            info.addPara(Strings.get("openingCompanyFoundingIntelPrepaidDepotStockRemainingAvailableOnlyWhile"), 8);
            if (depot.protectedStock()) info.addPara(Strings.get("openingCompanyFoundingIntelShuttlePartsAndFirstSquadStoresAre"), 8);
            var presentation = new CampaignFabricationResources();
            depot.stock().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    info.addPara(presentation.commodityName(e.getKey()) + ": " + e.getValue(), 3));
        }
    }
}
