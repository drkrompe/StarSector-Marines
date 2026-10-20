package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.start.SecondWindKeeper;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.CompanyStoryDialogDelegate;
import com.dillon.starsectormarines.ops.MarineOpsDialogDelegate;
import com.dillon.starsectormarines.ops.SecondWindBuyBackScreen;
import com.dillon.starsectormarines.ops.MarineOpsDialogSize;
import com.dillon.starsectormarines.ops.CorvusWaylineCampaign;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.AgreusArcCampaign;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Rule command referenced from {@code mod/data/campaign/rules.csv} by its simple class
 * name ({@code MarineOpsCMD}). Lives in {@code com.fs.starfarer.api.impl.campaign.rulecmd}
 * because that's one of the five packages Starsector scans when resolving a rulecmd
 * by simple name — mods can't register additional scan packages, so by convention any
 * mod-defined command sits in the same namespace as vanilla ones.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code MarineOpsCMD gate} — while the Second Wind is lost, turn the option into her buy-back
 *       (or disable it where no yard can sell her); while she is stored, disable it with Castle's
 *       instructions as its tooltip ({@link SecondWindKeeper}).</li>
 *   <li>{@code MarineOpsCMD open} — swap the current dialog area for the Marine Ops
 *       custom dialog (full-canvas, 3D render via FBO, our own input pipeline).</li>
 * </ul>
 */
public class MarineOpsCMD extends BaseCommandPlugin {

    private static final Logger LOG = Global.getLogger(MarineOpsCMD.class);
    /** The planet-menu option id, from {@code rules.csv}. */
    private static final String OPTION = "marineOpsOpen";

    @Override
    public boolean execute(String ruleId,
                           InteractionDialogAPI dialog,
                           List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;

        String sub = params.get(0).getString(memoryMap);
        if ("storySite".equals(sub)) {
            return dialog.getInteractionTarget() instanceof PlanetAPI planet
                    && (CorvusWaylineCampaign.atSite(planet) || ShepherdRecoveryCampaign.atSite(planet) || AgreusArcCampaign.atSite(planet));
        }
        if ("gate".equals(sub)) {
            if (!dialog.getOptionPanel().hasOption(OPTION)) return true;
            // While she is lost, the option is the way to get her back, wherever a yard can sell her.
            if (SecondWindKeeper.loss() != null) {
                if (SecondWindKeeper.buyBackOffered(market(dialog))) {
                    dialog.getOptionPanel().setOptionText(Strings.get("secondWindBuyBackOption"), OPTION);
                    dialog.getOptionPanel().setTooltip(OPTION, Strings.get("secondWindBuyBackOptionTooltip"));
                    return true;
                }
                dialog.getOptionPanel().setEnabled(OPTION, false);
                dialog.getOptionPanel().setTooltip(OPTION, Strings.get("secondWindBuyBackNoYard"));
                return true;
            }
            // The option stays listed so the player can see why it is closed.
            String away = SecondWindKeeper.awayInstructions();
            if (away != null) {
                dialog.getOptionPanel().setEnabled(OPTION, false);
                dialog.getOptionPanel().setTooltip(OPTION, away);
            }
            return true;
        }
        if ("open".equals(sub)) {
            if (CompanyYardCMD.openRequired(dialog, memoryMap)) return true;
            if (SecondWindKeeper.loss() != null && SecondWindKeeper.buyBackOffered(market(dialog))) {
                openBuyBack(dialog, memoryMap);
                return true;
            }
            String away = SecondWindKeeper.awayInstructions();
            if (away != null) {
                dialog.getTextPanel().addPara(away);
                return true;
            }
            float w = MarineOpsDialogSize.requestedWidth();
            float h = MarineOpsDialogSize.requestedHeight();
            LOG.info(String.format("MarineOpsCMD: opening custom dialog at %.0fx%.0f", w, h));

            // Collapse parent dialog chrome so the custom dialog isn't competing for space.
            // The delegate restores these on cancel.
            dialog.hideTextPanel();
            dialog.hideVisualPanel();

            SectorEntityToken target = dialog.getInteractionTarget();
            PlanetAPI planet = target instanceof PlanetAPI ? (PlanetAPI) target : null;
            dialog.showCustomVisualDialog(w, h, new MarineOpsDialogDelegate(dialog, planet));
            return true;
        }
        LOG.warn("MarineOpsCMD: unknown sub-command '" + sub + "'");
        return false;
    }

    /** The buy-back storybook over the port menu; the menu is rebuilt behind it so the option reads true again. */
    private static void openBuyBack(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = market(dialog);
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight(),
                CompanyStoryDialogDelegate.storybook(new SecondWindBuyBackScreen(market == null ? null : market.getName(),
                        () -> Global.getSector().getCampaignUI().addMessage(Strings.get("secondWindKeepBoughtBack"),
                                Misc.getHighlightColor())), () -> {
                    dialog.showTextPanel();
                    dialog.showVisualPanel();
                    FireBest.fire(null, dialog, memoryMap, "OpenInteractionDialog");
                }));
    }

    private static MarketAPI market(InteractionDialogAPI dialog) {
        return dialog.getInteractionTarget() == null ? null : dialog.getInteractionTarget().getMarket();
    }
}
