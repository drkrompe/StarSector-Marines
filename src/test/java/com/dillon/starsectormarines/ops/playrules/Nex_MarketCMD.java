package com.dillon.starsectormarines.ops.playrules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.Map;

/**
 * Stands in for Nexerelin's {@code Nex_MarketCMD}, which extends vanilla's and replaces its rows at {@code score:2}:
 * the same raid, labelled as Nexerelin's, with its Invade option on the defences menu.
 */
public class Nex_MarketCMD extends MarketCMD {

    @Override
    protected String who() {
        return "Nexerelin's Nex_MarketCMD";
    }

    @Override
    protected void addInvade(OptionPanelAPI options, boolean allowed, String planet) {
        options.addOption("Invade " + planet, "nex_mktInvade");
        if (!allowed) options.setEnabled("nex_mktInvade", false);
    }

    @Override
    protected boolean other(String command, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        // Nexerelin's Go back row clears its military-response tracker first; there is none here.
        if ("cleanupResponder".equals(command)) return true;
        if ("invadeMenu".equals(command)) {
            dialog.getTextPanel().addPara("[playBattle] Nexerelin's invasion is not stood in for yet.");
            showDefenses(dialog);
            return true;
        }
        return super.other(command, dialog, memoryMap);
    }
}
