package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.StrandedDetachment;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

/**
 * Planet-menu pickup of a stranded detachment — see {@code contracts-nouns.md} (stranded detachments).
 * No battle runs: reaching the market is the whole risk, and the domain release goes
 * through {@link StrandedDetachment#pickup}.
 *
 * <ul>
 *   <li>{@code available} — whether a detachment is waiting on this market.</li>
 *   <li>{@code addOption} — adds the pickup to the market menu.</li>
 *   <li>{@code pickup} — collects every detachment waiting here and rebuilds the menu.</li>
 * </ul>
 */
public class StrandedDetachmentCMD extends BaseCommandPlugin {

    static final String OPTION_PICKUP = "smStrandedPickup";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;
        CampaignState state = state();
        MarketAPI market = market(dialog);
        if (state == null || market == null) return false;
        List<Long> stranded = StrandedDetachment.strandedAt(state, market.getId());
        switch (params.get(0).getString(memoryMap)) {
            case "available":
                return !stranded.isEmpty();
            case "addOption":
                dialog.getOptionPanel().addOption(Strings.get("strandedDetachmentOption"),
                        OPTION_PICKUP);
                return true;
            case "pickup":
                return pickup(dialog, state, stranded, market);
            default:
                return false;
        }
    }

    private static boolean pickup(InteractionDialogAPI dialog, CampaignState state,
                                  List<Long> stranded, MarketAPI market) {
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script != null ? script.roster() : null;
        if (roster == null) return false;
        int day = CampaignClock.day();
        for (long contractId : stranded) {
            int recovered = StrandedDetachment.pickup(state, contractId, roster, day,
                    market.getName());
            if (recovered < 0) continue;
            dialog.getTextPanel().addPara(MessageFormat.format(
                    Strings.get("strandedDetachmentCollected"), recovered, market.getName()));
        }
        refreshMenu(dialog);
        return true;
    }

    private static MarketAPI market(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog.getInteractionTarget();
        return target != null ? target.getMarket() : null;
    }

    private static void refreshMenu(InteractionDialogAPI dialog) {
        if (dialog.getPlugin() instanceof RuleBasedDialog rules) {
            rules.updateMemory();
            FireAll.fire(null, dialog, rules.getMemoryMap(), "PopulateOptions");
        }
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }
}
