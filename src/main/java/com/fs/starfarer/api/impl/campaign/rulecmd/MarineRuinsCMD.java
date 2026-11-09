package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionPreference;
import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionPreferenceLedger;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsRoute;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Offers the player's choice of who explores a world's ruins ({@code ruins-expedition.md}): the company, as a Marine
 * Operations expedition, or vanilla's salvage team. Referenced from {@code rules.csv} by simple name.
 *
 * <p>It intercepts vanilla's Explore the ruins ({@code salExplore}) on a market with unexplored ruins. That option id
 * is shared with derelicts and debris fields, so the row also requires {@code $market.hasRuins}. The row scores 5
 * ({@code score:2} on the option line plus three condition lines), ahead of vanilla's own ruins row, which scores 1.
 * The standard path re-fires the selection with a one-shot bypass, so vanilla's salvage runs exactly as it would have.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code intercept}: condition. The site is one an expedition can fight on and the save's preference is not
 *       standard. When false, vanilla's row handles the selection untouched.</li>
 *   <li>{@code fork}: under Ask, shows the choice; under Marine Operations, opens the expedition at once.</li>
 *   <li>{@code marine} / {@code marineAlways}: open the expedition; the second also remembers the choice.</li>
 *   <li>{@code standard} / {@code standardAlways}: hand the selection back to vanilla's salvage team; the second also
 *       remembers the choice.</li>
 *   <li>{@code back}: return to the planet menu with nothing done.</li>
 *   <li>{@code resetAvailable} / {@code addReset} / {@code reset}: the planet menu's way back to Ask.</li>
 * </ul>
 *
 * <p>The expedition battle does not exist yet ({@code ruins-expedition-fork.md}, slices 2 and 3). Until it does, the
 * fork is offered only in a development build ({@link DevConfig#DEV_MODE}), and choosing the company says so and
 * returns to the choice. A player's install never sees the fork, so Explore the ruins is unchanged for them.
 */
public class MarineRuinsCMD extends BaseCommandPlugin {

    private static final Logger LOG = Global.getLogger(MarineRuinsCMD.class);

    /** The vanilla option this intercepts. */
    static final String OPTION_EXPLORE = "salExplore";
    static final String OPTION_MARINE = "smRuinsMarine";
    static final String OPTION_MARINE_ALWAYS = "smRuinsMarineAlways";
    static final String OPTION_STANDARD = "smRuinsStandard";
    static final String OPTION_STANDARD_ALWAYS = "smRuinsStandardAlways";
    static final String OPTION_BACK = "smRuinsBack";
    static final String OPTION_RESET = "smRuinsReset";
    /** One-shot local flag that lets a re-fired selection through to vanilla's own row. */
    static final String BYPASS_FLAG = "$smRuinsBypass";

    /**
     * Whether the expedition can be fought. False until its battle exists; while false, only a development build shows
     * the fork. The slice that adds the battle removes this and the development gate together.
     */
    static final boolean EXPEDITION_BUILT = false;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;
        String sub = params.get(0).getString(memoryMap);
        CampaignState state = state();
        switch (sub) {
            case "intercept":
                if (bypassed(memoryMap)) return false;
                return route(dialog, state) != RuinsRoute.STANDARD;
            case "fork":
                if (route(dialog, state) == RuinsRoute.MARINE_OPERATIONS) return open(dialog);
                showFork(dialog);
                return true;
            case "marine":
                return open(dialog);
            case "marineAlways":
                RuinsExpeditionPreferenceLedger.write(state, RuinsExpeditionPreference.MARINE_OPERATIONS);
                return open(dialog);
            case "standard":
                return toStandard(dialog, memoryMap);
            case "standardAlways":
                RuinsExpeditionPreferenceLedger.write(state, RuinsExpeditionPreference.STANDARD);
                return toStandard(dialog, memoryMap);
            case "back":
                refreshMenu(dialog);
                return true;
            case "resetAvailable":
                return offered() && supported(dialog)
                        && RuinsExpeditionPreferenceLedger.read(state) != RuinsExpeditionPreference.ASK;
            case "addReset":
                dialog.getOptionPanel().addOption(Strings.get("ruinsResetOption"), OPTION_RESET);
                return true;
            case "reset":
                RuinsExpeditionPreferenceLedger.write(state, RuinsExpeditionPreference.ASK);
                dialog.getTextPanel().addPara(Strings.get("ruinsResetDone"));
                refreshMenu(dialog);
                return true;
            default:
                LOG.warn("MarineRuinsCMD: unknown sub-command " + sub);
                return false;
        }
    }

    /** Whether this build offers the fork at all: always once the expedition exists, before that only in development. */
    private static boolean offered() {
        return EXPEDITION_BUILT || DevConfig.DEV_MODE;
    }

    private static RuinsRoute route(InteractionDialogAPI dialog, CampaignState state) {
        boolean supported = state != null && offered() && supported(dialog);
        return RuinsRoute.choose(RuinsExpeditionPreferenceLedger.read(state), supported);
    }

    /** An uncolonized world with a ruins condition, not a gas giant. */
    private static boolean supported(InteractionDialogAPI dialog) {
        if (!(dialog.getInteractionTarget() instanceof PlanetAPI planet)) return false;
        MarketAPI market = planet.getMarket();
        if (market == null) return false;
        return RuinsRoute.supported(grade(market), planet.isGasGiant(), market.isPlanetConditionMarketOnly());
    }

    private static RuinsGrade grade(MarketAPI market) {
        List<String> ids = new ArrayList<>();
        for (MarketConditionAPI condition : market.getConditions()) ids.add(condition.getId());
        return RuinsGrade.of(ids);
    }

    /** Re-fires Explore the ruins with the bypass set, so vanilla's own row sends the salvage team. */
    private static boolean toStandard(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
        if (local == null) return false;
        local.set(BYPASS_FLAG, true, 0f);
        local.set("$option", OPTION_EXPLORE, 0f);
        return FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected");
    }

    /** Whether the bypass is set; consuming it, so it lets exactly one selection through. */
    private static boolean bypassed(Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
        if (local == null || !local.getBoolean(BYPASS_FLAG)) return false;
        local.unset(BYPASS_FLAG);
        return true;
    }

    private static void showFork(InteractionDialogAPI dialog) {
        dialog.getTextPanel().addPara(Strings.get("ruinsForkText"));
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption(Strings.get("ruinsForkMarine"), OPTION_MARINE);
        options.addOption(Strings.get("ruinsForkStandard"), OPTION_STANDARD);
        options.addOption(Strings.get("ruinsForkMarineAlways"), OPTION_MARINE_ALWAYS);
        options.addOption(Strings.get("ruinsForkStandardAlways"), OPTION_STANDARD_ALWAYS);
        options.addOption(Strings.get("ruinsForkBack"), OPTION_BACK);
        options.setShortcut(OPTION_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    /** Opens the expedition. Its battle is not built yet, so for now this says so and returns to the choice. */
    private static boolean open(InteractionDialogAPI dialog) {
        LOG.info("MarineRuinsCMD: expedition chosen on " + dialog.getInteractionTarget().getId()
                + "; the expedition battle is not built yet");
        dialog.getTextPanel().addPara(Strings.get("ruinsExpeditionNotReady"));
        showFork(dialog);
        return true;
    }

    /** Rebuilds the planet menu from scratch. */
    private static void refreshMenu(InteractionDialogAPI dialog) {
        if (dialog.getPlugin() instanceof RuleBasedDialog rules) {
            dialog.getOptionPanel().clearOptions();
            rules.updateMemory();
            FireAll.fire(null, dialog, rules.getMemoryMap(), "PopulateOptions");
        }
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }
}
