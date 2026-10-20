package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.i18n.Strings;
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
        return refusal(owned, compatible, mothballed, installed, docked, credits, MercenaryCompanyConversion.CREDIT_COST);
    }
    public static String refusal(boolean owned, boolean compatible, boolean mothballed,
                                 boolean installed, boolean docked, float credits, int price) {
        if (!owned || !compatible) return Strings.get("openingCompanyYardSelectAnOwnedCerberusForTheConversion");
        if (mothballed) return Strings.get("openingCompanyYardReactivateTheCerberusBeforeOrderingYardWork");
        if (installed) return Strings.get("openingCompanyYardThisShipAlreadyHasTheMercenaryCompany");
        if (!docked) return Strings.get("openingCompanyYardVisitAnAccessiblePlanetWithAWorking");
        if (credits < price) return Strings.format("openingCompanyYardTheConversionRequiresCredits", price);
        return null;
    }
    public static String refusal(FleetMemberAPI member, MarketAPI market) {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return Strings.get("openingCompanyYardNoPlayerFleet");
        var fleet = Global.getSector().getPlayerFleet();
        return refusal(fleet.getFleetData().getMembersListCopy().contains(member),
                MercenaryCompanyConversion.compatible(member), member != null && member.isMothballed(),
                MercenaryCompanyConversion.installed(member), accessible(market) && market == dockedMarket(),
                Math.max(0, fleet.getCargo().getCredits().get()) + reservedCredits(member), price(member));
    }
    public static int reservedCredits(FleetMemberAPI member) {
        return member == null || Global.getSector() == null || MercenaryCompanyConversion.installed(member)
                ? 0 : FoundingReserve.available(Global.getSector().getPersistentData(), member.getId());
    }
    /** Credits the yard waives: the pressure test, when Castle's founding reserve pays. */
    public static int waived(int reserved) { return reserved > 0 ? MercenaryCompanyConversion.PRESSURE_TEST : 0; }
    /** What the order costs this ship, after any waiver. */
    public static int price(FleetMemberAPI member) {
        return MercenaryCompanyConversion.CREDIT_COST - waived(reservedCredits(member));
    }
    /** The share of the price the ship's account pays. */
    public static int cashCost(int reserved) {
        return Math.max(0, MercenaryCompanyConversion.CREDIT_COST - waived(reserved) - reserved);
    }
    public static int cashCost(FleetMemberAPI member) { return cashCost(reservedCredits(member)); }
    public static String install(FleetMemberAPI member, MarketAPI market) {
        String refused = refusal(member, market);
        if (refused != null) return refused;
        int reserved = reservedCredits(member);
        ShipVariantAPI refit = member.getVariant().clone();
        refit.setSource(VariantSource.REFIT);
        refit.setHullVariantId(UUID.randomUUID().toString());
        refit.addPermaMod(MercenaryCompanyConversion.ID);
        member.setVariant(refit, false, true);
        member.updateStats();
        FoundingReserve.consume(Global.getSector().getPersistentData(), member.getId(), reserved);
        int cash = cashCost(reserved);
        if (cash > 0) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cash);
        Global.getSector().getPlayerFleet().getFleetData().setSyncNeeded();
        CerberusCompanyStart.establishDepot(member, market);
        return null;
    }
}
