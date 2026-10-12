package com.dillon.starsectormarines.campaign.start;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;
import java.util.UUID;

/** Planet-side order, with ownership, access and funds rechecked at commitment. */
public final class CompanyYard {
    private CompanyYard() { }
    public static boolean accessible(MarketAPI market) {
        return MercenaryCompanyConversion.planetaryYard(market) && Global.getSector() != null
                && market.getFaction() != null && !market.getFaction().isHostileTo(Global.getSector().getPlayerFaction());
    }
    public static MarketAPI dockedMarket() {
        if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) return null;
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog == null || dialog.getInteractionTarget() == null || !(dialog.getInteractionTarget() instanceof PlanetAPI)) return null;
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        return accessible(market) ? market : null;
    }
    public static String refusal(boolean owned, boolean compatible, boolean mothballed,
                                 boolean installed, boolean docked, float credits) {
        if (!owned || !compatible) return "Select an owned Cerberus for the conversion.";
        if (mothballed) return "Reactivate the Cerberus before ordering yard work.";
        if (installed) return "This ship already has the Mercenary Company Conversion.";
        if (!docked) return "Visit an accessible planet with a working spaceport to install the conversion.";
        if (credits < MercenaryCompanyConversion.CREDIT_COST) return "The conversion requires 5,000 credits.";
        return null;
    }
    public static String refusal(FleetMemberAPI member, MarketAPI market) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return "No player fleet.";
        var fleet = Global.getSector().getPlayerFleet();
        return refusal(fleet.getFleetData().getMembersListCopy().contains(member),
                MercenaryCompanyConversion.compatible(member), member != null && member.isMothballed(),
                MercenaryCompanyConversion.installed(member), accessible(market) && market == dockedMarket(),
                fleet.getCargo().getCredits().get());
    }
    public static String install(FleetMemberAPI member, MarketAPI market) {
        String refused = refusal(member, market);
        if (refused != null) return refused;
        ShipVariantAPI refit = member.getVariant().clone();
        refit.setSource(VariantSource.REFIT);
        refit.setHullVariantId(UUID.randomUUID().toString());
        refit.addPermaMod(MercenaryCompanyConversion.ID);
        member.setVariant(refit, false, true);
        member.updateStats();
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(MercenaryCompanyConversion.CREDIT_COST);
        Global.getSector().getPlayerFleet().getFleetData().setSyncNeeded();
        CerberusCompanyStart.establishDepot(member, market);
        return null;
    }
}
