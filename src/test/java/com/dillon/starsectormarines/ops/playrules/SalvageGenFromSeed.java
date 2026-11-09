package com.dillon.starsectormarines.ops.playrules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/**
 * Stands in for the game's command of the same name, which rolls the salvage spec's drops from the entity's seed: the
 * planet menu reports which spec vanilla would roll and leaves the haul to the game.
 */
public class SalvageGenFromSeed extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
        Object spec = local != null ? local.get("$salvageSpecId") : null;
        StandInMarket.state().report("SalvageGenFromSeed: vanilla would seed the " + spec + " salvage here");
        return true;
    }
}
