package com.dillon.starsectormarines.ops.playrules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/**
 * Stands in for the game's salvage command, which reads the fleet and shows the loot screen. The planet menu walks the
 * same steps (cost, accidents, perform) and reports what the game would do, so the rules after it run as they would.
 */
public class SalvageEntity extends BaseCommandPlugin {

    static final String PERFORM = "salPerform";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        String sub = params.isEmpty() ? "" : params.get(0).getString(memoryMap);
        StandInMarket.State state = StandInMarket.state();
        switch (sub) {
            case "showCost" -> dialog.getTextPanel().addPara(
                    "[playBattle] The salvage team's rating and cost would show here.");
            case "checkAccidents" -> {
                state.report("SalvageEntity checkAccidents: the game would roll accidents here");
                dialog.getOptionPanel().clearOptions();
                dialog.getOptionPanel().addOption("Perform the salvage (stand-in)", PERFORM);
            }
            case "performSalvage" -> {
                state.report("SalvageEntity performSalvage: the game would roll the haul and show its loot screen");
                dialog.getTextPanel().addPara("[playBattle] The salvage team brings back vanilla's haul.");
                dialog.getOptionPanel().clearOptions();
            }
            case "canBeMadeRecoverable" -> {
                return false;
            }
            default -> state.report("SalvageEntity " + sub + ": not stood in for");
        }
        return true;
    }
}
