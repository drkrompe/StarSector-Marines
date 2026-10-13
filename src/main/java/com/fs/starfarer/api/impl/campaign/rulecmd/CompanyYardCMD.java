package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.CompanyYard;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.ops.CompanyManagementGuide;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
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
            if (failure == null && personalYard(ship))
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
        review(dialog, ship);
        return true;
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
    private static boolean personalYard(FleetMemberAPI ship) {
        var market = CompanyYard.dockedMarket();
        return ship != null && ship.getId().equals(CerberusCompanyStart.startingShipId())
                && market != null && "jangala".equals(market.getPlanetEntity().getId());
    }
    private static void review(InteractionDialogAPI dialog, FleetMemberAPI ship) {
        portrait(dialog);
        if (CerberusCompanyStart.conversionRequired())
            dialog.getTextPanel().addPara(personalYard(ship)
                    ? Strings.get("openingCompanyYardCMDNessaVennMetUsAtTheBerth")
                    : Strings.get("openingCompanyYardCMDTheYardCanMakeRoomForBunks"));
        dialog.getTextPanel().addPara(Strings.format("openingCompanyYardCMDMercenaryCompanyConversion", (ship == null ? "" : " - " + ship.getShipName())));
        dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDPermanentInstallationPersonnelCapacityCargoCapacityExpanded"));
        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        int reserved = CompanyYard.reservedCredits(ship);
        int cash = CompanyYard.cashCost(ship);
        dialog.getTextPanel().addPara(Strings.format("openingCompanyYardCMDYardCostCreditsCastleSReservedPayment", reserved, cash, credits, Math.max(0, cash - Math.max(0, credits))));
        if (reserved > 0) dialog.getTextPanel().addPara(Strings.get("openingCompanyYardCMDIVeKeptThisPaymentAsideSince"));
        String failure = CompanyYard.refusal(ship, CompanyYard.dockedMarket());
        if (failure != null) dialog.getTextPanel().addPara(failure);
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption(reserved > 0 ? Strings.get("openingCompanyYardCMDInstallConversionUseReservedPayment")
                : Strings.get("openingCompanyYardCMDInstallConversionCredits"), "smYardInstall");
        dialog.getOptionPanel().setEnabled("smYardInstall", failure == null);
        dialog.getOptionPanel().addOption(Strings.get("openingCompanyYardCMDReturnToPort"), "smYardReturn");
    }
}
