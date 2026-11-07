package com.dillon.starsectormarines.ops.playrules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/** Stands in for the game's command of the same name, which reads the running game: the planet menu has no visual. */
public class ShowDefaultVisual extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        return true;
    }
}
