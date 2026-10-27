package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.dillon.starsectormarines.ops.CompanyShipDesignation;
import com.dillon.starsectormarines.ops.DialogHandOff;
import com.dillon.starsectormarines.ops.MarineOpsContext;
import com.dillon.starsectormarines.ops.MarineOpsDialogDelegate;
import com.dillon.starsectormarines.ops.MarineOpsDialogSize;
import com.dillon.starsectormarines.ops.CompanyStoryDialogDelegate;
import com.dillon.starsectormarines.ops.CompanyYardScreen;
import com.dillon.starsectormarines.ops.MenuTheme;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/** Reviewable planetary installation, reached from the ordinary planet menu. */
public final class CompanyYardCMD extends BaseCommandPlugin {
    private static final String PREFIX = "smYardShip_";
    private static final String SELECTED = "$smYardSelectedShip";

    @Override public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                                     Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;
        String action = params.get(0).getString(memoryMap);
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        String option = local.getString("$option");
        if ("selected".equals(action)) return option != null && option.startsWith(PREFIX);
        if ("available".equals(action)) return CompanyYard.dockedMarket() != null && !candidates().isEmpty();
        if ("show".equals(action)) {
            if (openRequired(dialog, memoryMap)) return true;
            portrait(dialog);
            dialog.getOptionPanel().clearOptions();
            dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDWhichCerberusShallTheYardConvertCommander"));
            for (FleetMemberAPI ship : candidates()) dialog.getOptionPanel().addOption(ship.getShipName(), PREFIX + ship.getId());
            dialog.getOptionPanel().addOption(Strings.get("openingCompanyYardCMDReturnToPort"), "smYardReturn");
        } else if ("review".equals(action)) {
            local.set(SELECTED, option.substring(PREFIX.length()));
            review(dialog, selected(local));
        } else if ("install".equals(action)) {
            FleetMemberAPI ship = selected(local);
            String failure = CompanyYard.install(ship, CompanyYard.dockedMarket());
            portrait(dialog);
            if (failure == null && secondWind(ship))
                dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDNessaSaysSheLlKeepTheOld"));
            dialog.getTextPanel().addPara(failure != null ? failure
                    : Strings.get("openingCompanyYardCMDTheYardHasFinishedEighteenMorePlaces"));
            if (failure == null && CerberusCompanyStart.depot() != null)
                dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDOurPrepaidPreparationStoresAreInThis"));
            dialog.getOptionPanel().clearOptions();
            dialog.getOptionPanel().addOption(Strings.get("openingCompanyYardCMDReturnToPort"), "smYardReturn");
        } else return false;
        return true;
    }
    /** The opening lesson goes straight to the real, reviewable yard order at port. */
    public static boolean openRequired(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        if (!CerberusCompanyStart.conversionRequired() || CompanyYard.dockedMarket() == null
                || dialog != Global.getSector().getCampaignUI().getCurrentInteractionDialog()) return false;
        FleetMemberAPI ship = CerberusCompanyStart.startingShip();
        if (ship == null) return false;
        memoryMap.get(MemKeys.LOCAL).set(SELECTED, ship.getId());
        // Keep the planet interaction alive: it is the yard/depot's docking authority.
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption(Strings.get("openingCompanyYardCMDReturnToPort"), "smYardReturn");
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight(),
                CompanyStoryDialogDelegate.storybook(new CompanyYardScreen(ship), () -> {
                    // The theme carries on over the port menu into Marine operations; undocking ends it.
                    MenuTheme.shared().holdWhileOpen(dialog);
                    // Left before paying: back to port, and the next visit opens the yard again.
                    if (CerberusCompanyStart.conversionRequired()) {
                        returnToPort(dialog, memoryMap);
                        return;
                    }
                    // Paid: the company moves aboard her now, and the walk carries on to Morgan.
                    CompanyShipDesignation.designateFoundingShip();
                    DialogHandOff.next(dialog, () -> openOperations(dialog, memoryMap),
                            () -> returnToPort(dialog, memoryMap));
                }));
        return true;
    }

    /** Marine operations at Morgan's induction, over the same port dialog; leaving it rebuilds the port menu. */
    private static void openOperations(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        PlanetAPI planet = dialog.getInteractionTarget() instanceof PlanetAPI docked ? docked : null;
        dialog.showCustomVisualDialog(MarineOpsDialogSize.requestedWidth(), MarineOpsDialogSize.requestedHeight(),
                new MarineOpsDialogDelegate(planet, MarineOpsContext::openOfficerInduction,
                        () -> returnToPort(dialog, memoryMap)));
    }

    private static void returnToPort(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        dialog.showTextPanel();
        dialog.showVisualPanel();
        FireBest.fire(null, dialog, memoryMap, "OpenInteractionDialog");
    }
    private static List<FleetMemberAPI> candidates() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return List.of();
        return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().stream()
                .filter(MercenaryCompanyConversion::compatible)
                .filter(m -> !MercenaryCompanyConversion.installed(m)).toList();
    }
    private static FleetMemberAPI selected(MemoryAPI memory) {
        String id = memory.getString(SELECTED);
        return candidates().stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }
    private static void portrait(InteractionDialogAPI dialog) {
        dialog.getTextPanel().addImage(CompanyManagementGuide.portrait());
        dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDCastleCompanyOfficer"));
    }
    /** Second Wind's own conversion is Nessa's job at whichever port the start chose; any other Cerberus gets the yard. */
    private static boolean secondWind(FleetMemberAPI ship) {
        return ship != null && ship.getId().equals(CerberusCompanyStart.startingShipId());
    }
    private static void review(InteractionDialogAPI dialog, FleetMemberAPI ship) {
        portrait(dialog);
        if (CerberusCompanyStart.conversionRequired()) {
            dialog.getTextPanel().addPara(secondWind(ship)
                    ? Strings.get("openingCompanyYardCMDNessaVennMetUsAtTheBerth")
                    : Strings.get("openingCompanyYardCMDTheYardCanMakeRoomForBunks"));
            // The player meets Zoey through her sister's yard.
            if (secondWind(ship)) dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDHerRacksAreHalfEmptyMostOf"));
        }
        dialog.getTextPanel().addPara(Strings.format("openingCompanyYardCMDMercenaryCompanyConversion", (ship == null ? "" : " - " + ship.getShipName())));
        dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDPermanentInstallationPersonnelCapacityCargoCapacityExpanded"));
        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        int reserved = CompanyYard.reservedCredits(ship);
        int cash = CompanyYard.cashCost(ship);
        dialog.getTextPanel().addPara(Strings.format("openingCompanyYardCMDYardCostCreditsCastleSReservedPayment",
                MercenaryCompanyConversion.CREDIT_COST, reserved, CompanyYard.waived(reserved), cash, credits,
                Math.max(0, cash - Math.max(0, credits))));
        if (reserved > 0) dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDIVeKeptThisPaymentAsideSince"));
        String failure = CompanyYard.refusal(ship, CompanyYard.dockedMarket());
        if (failure != null) dialog.getTextPanel().addPara(failure);
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption(reserved > 0 ? Strings.get("openingCompanyYardCMDInstallConversionUseReservedPayment")
                : Strings.format("openingCompanyYardCMDInstallConversionCredits", MercenaryCompanyConversion.CREDIT_COST), "smYardInstall");
        dialog.getOptionPanel().setEnabled("smYardInstall", failure == null);
        dialog.getOptionPanel().addOption(Strings.get("openingCompanyYardCMDReturnToPort"), "smYardReturn");
    }
}
