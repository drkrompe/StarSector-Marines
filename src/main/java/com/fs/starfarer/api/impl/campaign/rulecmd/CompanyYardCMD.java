package com.fs.starfarer.api.impl.campaign.rulecmd;

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
            dialog.getTextPanel().addPara("Which Cerberus shall the yard convert, Commander?");
            for (FleetMemberAPI ship : candidates()) dialog.getOptionPanel().addOption(ship.getShipName(), PREFIX + ship.getId());
            dialog.getOptionPanel().addOption("Return to port", "smYardReturn");
        } else if ("review".equals(action)) {
            local.set(SELECTED, option.substring(PREFIX.length()));
            review(dialog, selected(local));
        } else if ("install".equals(action)) {
            FleetMemberAPI ship = selected(local);
            String failure = CompanyYard.install(ship, CompanyYard.dockedMarket());
            portrait(dialog);
            if (failure == null && personalYard(ship))
                dialog.getTextPanel().addPara("Nessa says she'll keep the old pressure seals on her wall. Apparently I have a reputation to maintain. "
                        + "She stayed for the pressure test herself. Didn't put that on the bill.");
            dialog.getTextPanel().addPara(failure != null ? failure
                    : "The yard has finished. Eighteen more places to sleep, a proper command fit, and a workshop gantry. "
                    + "We gave up twenty-five units of hold for them. This is permanent work.");
            if (failure == null && CerberusCompanyStart.depot() != null)
                dialog.getTextPanel().addPara("Our prepaid preparation stores are in this planet's depot. Stay docked and open Marine operations: "
                        + "designate the converted Cerberus, then follow my lesson. Workshop bills include depot stock here; they cannot draw it while we are away.");
            dialog.getOptionPanel().clearOptions();
            dialog.getOptionPanel().addOption("Return to port", "smYardReturn");
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
        dialog.getTextPanel().addPara("CASTLE / COMPANY OFFICER");
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
                    ? "Nessa Venn met us at the berth. Took one look at the patch under the collar and asked if I'd done it myself. "
                    + "I told her I'd supervised. She remembers what that means. Here's her estimate, Captain. She's kept to her word."
                    : "The yard can make room for bunks and a workshop. I've asked them to leave the sound compartments alone. "
                    + "Have a look at the estimate, Captain. We need to get this right.");
        dialog.getTextPanel().addPara("Mercenary Company Conversion" + (ship == null ? "" : " - " + ship.getShipName()));
        dialog.getTextPanel().addPara("Permanent installation: +18 personnel capacity, -25 cargo capacity, expanded barracks, "
                + "company command eligibility and one workshop gantry. The existing single boat berth remains; the hullmod includes no boat or mech.");
        int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        int reserved = CompanyYard.reservedCredits(ship);
        int cash = CompanyYard.cashCost(ship);
        dialog.getTextPanel().addPara("Yard cost: 5,000 credits / Castle's reserved payment: " + reserved
                + " / Due from fleet: " + cash + " / Fleet funds: " + credits + " / Short by: "
                + Math.max(0, cash - Math.max(0, credits)));
        if (reserved > 0) dialog.getTextPanel().addPara("I've kept this payment aside since we left the Claim. "
                + "We won't have to touch what's left in the ship's account. The shuttle parts and first squad's stores are paid for too.");
        String failure = CompanyYard.refusal(ship, CompanyYard.dockedMarket());
        if (failure != null) dialog.getTextPanel().addPara(failure);
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption(reserved > 0 ? "Install conversion - use reserved payment"
                : "Install conversion - 5,000 credits", "smYardInstall");
        dialog.getOptionPanel().setEnabled("smYardInstall", failure == null);
        dialog.getOptionPanel().addOption("Return to port", "smYardReturn");
    }
}
