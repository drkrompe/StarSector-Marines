package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/**
 * The Second Wind start's words on the new-game screen, read from the catalog so a translation overlay reaches
 * them: {@code tooltip} sets the hover text of the start's choice, {@code story} writes the paragraph shown once it
 * is picked. Rules.csv text columns are literal and would sit outside {@link Strings}.
 */
public final class CerberusStartTextCMD extends BaseCommandPlugin {
    static final String START_OPTION = "smCerberusStart";

    @Override public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                                     Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;
        switch (params.get(0).getString(memoryMap)) {
            case "tooltip" -> dialog.getOptionPanel().setTooltip(START_OPTION, Strings.get("openingCerberusStartTooltip"));
            case "story" -> dialog.getTextPanel().addPara(Strings.get("openingCerberusStartStory"));
            default -> { return false; }
        }
        return true;
    }
}
