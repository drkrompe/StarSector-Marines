package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionLedger;
import com.dillon.starsectormarines.campaign.ruins.RuinsForkSetting;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsRoute;
import com.dillon.starsectormarines.campaign.ruins.SectorRuinsOccupants;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.MarineOpsDialogDelegate;
import com.dillon.starsectormarines.ops.MarineOpsDialogSize;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.RuinsExpeditionMissionFactory;
import com.dillon.starsectormarines.ops.ScreenId;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

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
 *   <li>{@code intercept}: condition. The site is one an expedition can fight on and the save's
 *       {@link RuinsForkSetting} is on. When false, vanilla's row handles the selection untouched.</li>
 *   <li>{@code fork}: shows the choice. It is made every time; nothing is remembered.</li>
 *   <li>{@code marine}: opens the expedition.</li>
 *   <li>{@code standard}: hands the selection back to vanilla's salvage team.</li>
 *   <li>{@code back}: returns to the planet menu with nothing done.</li>
 * </ul>
 *
 * <p>Choosing the company opens the expedition's briefing. When the Marine Operations panel closes, a won expedition
 * re-fires Explore the ruins through the bypass, so vanilla's own salvage gives the haul and marks the ruins explored;
 * a lost one leaves the ruins unexplored; backing out of the briefing returns to the choice ({@link
 * RuinsExpeditionLedger}).
 *
 * <p>The battle is still a stand-in ({@code RuinsExpeditionMissionFactory}). Until the expedition's own mission
 * exists ({@code ruins-expedition-battle.md}), the fork is offered only in a development build
 * ({@link DevConfig#DEV_MODE}). A player's install never sees it, so Explore the ruins is unchanged for them.
 */
public class MarineRuinsCMD extends BaseCommandPlugin {

    private static final Logger LOG = Global.getLogger(MarineRuinsCMD.class);

    /** The vanilla option this intercepts. */
    static final String OPTION_EXPLORE = "salExplore";
    static final String OPTION_MARINE = "smRuinsMarine";
    static final String OPTION_STANDARD = "smRuinsStandard";
    static final String OPTION_BACK = "smRuinsBack";
    /** One-shot local flag that lets a re-fired selection through to vanilla's own row. */
    static final String BYPASS_FLAG = "$smRuinsBypass";

    /**
     * Whether the expedition's own battle exists. False while it is a stand-in; while false, only a development build
     * shows the fork. The slice that adds the battle removes this and the development gate together.
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
                showFork(dialog);
                return true;
            case "marine":
                return open(dialog);
            case "standard":
                return toStandard(dialog, memoryMap);
            case "back":
                refreshMenu(dialog);
                return true;
            default:
                LOG.warn("MarineRuinsCMD: unknown sub-command " + sub);
                return false;
        }
    }

    /**
     * Whether this build offers the fork at all: always once the expedition exists, before that only in development.
     * The Settings page shows the fork's key only then.
     */
    public static boolean offered() {
        return EXPEDITION_BUILT || DevConfig.DEV_MODE;
    }

    private static RuinsRoute route(InteractionDialogAPI dialog, CampaignState state) {
        boolean supported = state != null && offered() && supported(dialog);
        return RuinsRoute.choose(RuinsForkSetting.asks(state), supported);
    }

    /** An uncolonized world with a ruins condition, not a gas giant. */
    private static boolean supported(InteractionDialogAPI dialog) {
        if (!(dialog.getInteractionTarget() instanceof PlanetAPI planet)) return false;
        MarketAPI market = planet.getMarket();
        if (market == null) return false;
        return RuinsRoute.supported(grade(market), planet.isGasGiant(), market.isPlanetConditionMarketOnly());
    }

    private static RuinsGrade grade(MarketAPI market) {
        return SectorRuinsOccupants.gradeOf(market);
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
        options.addOption(Strings.get("ruinsForkBack"), OPTION_BACK);
        options.setShortcut(OPTION_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    /**
     * Opens Marine Operations straight at the expedition briefing. When the panel closes, a fought battle commits
     * through vanilla's salvage or leaves the ruins as they were; backing out commits nothing and returns to the choice.
     */
    private static boolean open(InteractionDialogAPI dialog) {
        if (!(dialog.getInteractionTarget() instanceof PlanetAPI planet) || planet.getMarket() == null) return false;
        MarketAPI market = planet.getMarket();
        int serial = RuinsExpeditionLedger.issue(state());
        Mission mission = RuinsExpeditionMissionFactory.create(serial, market.getId(), planet.getName(), grade(market),
                SectorRuinsOccupants.of(market));
        if (mission == null) return false;
        LOG.info("MarineRuinsCMD: opening expedition briefing " + mission.id + " (" + mission.ruinsOccupant + ")");
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(MarineOpsDialogSize.requestedWidth(), MarineOpsDialogSize.requestedHeight(),
                new MarineOpsDialogDelegate(planet, ctx -> {
                    ctx.setSelectedMission(mission);
                    ctx.goTo(ScreenId.BRIEFING);
                }, () -> {
                    dialog.showTextPanel();
                    dialog.showVisualPanel();
                    afterPanel(dialog, market, serial);
                }));
        return true;
    }

    /**
     * Hands a won expedition's ruins to vanilla's salvage, leaves a pulled-out or lost one's as they were (the vault
     * sealed, the site unexplored), or returns to the choice.
     */
    private static void afterPanel(InteractionDialogAPI dialog, MarketAPI market, int serial) {
        RuinsExpeditionLedger.Pending pending = RuinsExpeditionLedger.take(state(), serial);
        if (pending == RuinsExpeditionLedger.Pending.NONE) {
            showFork(dialog);
            return;
        }
        boolean won = pending == RuinsExpeditionLedger.Pending.WON;
        dialog.getTextPanel().addPara(Strings.get(switch (pending) {
            case WON -> "ruinsExpeditionCommitWon";
            case PULLED_OUT -> "ruinsExpeditionCommitPulledOut";
            default -> "ruinsExpeditionCommitLost";
        }));
        // The planet menu again, so vanilla's ruins row seeds its salvage as on arrival.
        refreshMenu(dialog);
        if (won && dialog.getPlugin() instanceof RuleBasedDialog rules) {
            dialog.getOptionPanel().clearOptions();
            toStandard(dialog, rules.getMemoryMap());
        }
        LOG.info("MarineRuinsCMD: expedition " + serial + " on " + market.getId() + " committed as " + pending);
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
