package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
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
        return MercenaryCompanyConversion.planetaryYard(market)
                && market.getFaction() != null && !market.getFaction().isHostileTo(CampaignBackend.playerFaction());
    }
    public static MarketAPI dockedMarket() {
        MarketAPI market = CampaignBackend.yardPort();
        return accessible(market) ? market : null;
    }
    /** The game's answer to {@link CampaignBackend#yardPort}, read from the open interaction dialog. */
    public static MarketAPI planetInDialog() {
        if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) return null;
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog == null || dialog.getInteractionTarget() == null || !(dialog.getInteractionTarget() instanceof PlanetAPI)) return null;
        return dialog.getInteractionTarget().getMarket();
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
        if (CampaignBackend.playerFleet() == null) return Strings.get("openingCompanyYardNoPlayerFleet");
        var fleet = CampaignBackend.playerFleet();
        return refusal(fleet.getFleetData().getMembersListCopy().contains(member),
                MercenaryCompanyConversion.compatible(member), member != null && member.isMothballed(),
                MercenaryCompanyConversion.installed(member), accessible(market) && market == dockedMarket(),
                Math.max(0, fleet.getCargo().getCredits().get()) + reservedCredits(member), price(member));
    }
    public static int reservedCredits(FleetMemberAPI member) {
        return member == null || Global.getSector() == null || MercenaryCompanyConversion.installed(member)
                ? 0 : FoundingReserve.available(CampaignBackend.savedData(), member.getId());
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
        FoundingReserve.consume(CampaignBackend.savedData(), member.getId(), reserved);
        int cash = cashCost(reserved);
        if (cash > 0) CampaignBackend.playerFleet().getCargo().getCredits().subtract(cash);
        CampaignBackend.playerFleet().getFleetData().setSyncNeeded();
        CerberusCompanyStart.conversionPaid(member, market);
        return null;
    }
}
