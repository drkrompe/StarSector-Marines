package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.nexerelin.GroundCombatPreference;
import com.dillon.starsectormarines.campaign.nexerelin.GroundCombatPreferenceLedger;
import com.dillon.starsectormarines.campaign.nexerelin.InvasionRoute;
import com.dillon.starsectormarines.campaign.nexerelin.NexInvasionLedger;
import com.dillon.starsectormarines.campaign.nexerelin.Nexerelin;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.MarineOpsDialogDelegate;
import com.dillon.starsectormarines.ops.MarineOpsDialogSize;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.NexInvasionMissionFactory;
import com.dillon.starsectormarines.ops.ScreenId;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;
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
 * Offers the player's choice of ground combat when Nexerelin's Invade is selected
 * ({@code nex-invasion-conquest.md}, slice 1). Referenced from {@code rules.csv} by simple name.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code intercept} — condition: Nexerelin is enabled, this is a supported invasion, and the save's preference
 *       is not Nexerelin. When false, Nexerelin's own row handles Invade untouched.</li>
 *   <li>{@code fork} — under Ask, shows the choice; under Marine Operations, opens the briefing at once.</li>
 *   <li>{@code marine} / {@code marineAlways} — open the invasion briefing; the second also remembers the choice.</li>
 *   <li>{@code nex} / {@code nexAlways} — hand this Invade back to Nexerelin's own row; the second also remembers
 *       the choice. The selection is re-fired with a one-shot bypass, so this mod's rules never name a Nexerelin
 *       command and Nexerelin's row runs exactly as it would have.</li>
 *   <li>{@code resetAvailable} / {@code addReset} / {@code reset} — the market menu's way back to Ask.</li>
 * </ul>
 *
 * <p>Nexerelin's own gating stays authoritative: its Invade option only exists, enabled, where Nexerelin allows an
 * invasion, and its own Back option ({@code nex_mktInvadeGoBack}) is reused here.
 */
public class MarineInvasionCMD extends BaseCommandPlugin {

    private static final Logger LOG = Global.getLogger(MarineInvasionCMD.class);

    static final String OPTION_MARINE = "smInvadeMarine";
    static final String OPTION_MARINE_ALWAYS = "smInvadeMarineAlways";
    static final String OPTION_NEXERELIN = "smInvadeNex";
    static final String OPTION_NEXERELIN_ALWAYS = "smInvadeNexAlways";
    static final String OPTION_RESET = "smInvadeReset";
    /** Nexerelin's Invade option, which this intercepts. */
    static final String OPTION_NEXERELIN_INVADE = "nex_mktInvade";
    /** One-shot local flag that lets a re-fired Invade through to Nexerelin's own row. */
    static final String BYPASS_FLAG = "$smNexInvadeBypass";
    /** Nexerelin's own Back from its Invade menu, which returns to the market's defences. */
    static final String OPTION_NEXERELIN_BACK = "nex_mktInvadeGoBack";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;
        String sub = params.get(0).getString(memoryMap);
        CampaignState state = state();
        switch (sub) {
            case "intercept":
                if (bypassed(memoryMap)) return false;
                return route(dialog, state) != InvasionRoute.NEXERELIN;
            case "fork":
                if (route(dialog, state) == InvasionRoute.MARINE_OPERATIONS) return open(dialog);
                showFork(dialog);
                return true;
            case "marine":
                return open(dialog);
            case "marineAlways":
                GroundCombatPreferenceLedger.write(state, GroundCombatPreference.MARINE_OPERATIONS);
                return open(dialog);
            case "nex":
                return toNexerelin(dialog, memoryMap);
            case "nexAlways":
                GroundCombatPreferenceLedger.write(state, GroundCombatPreference.NEXERELIN);
                return toNexerelin(dialog, memoryMap);
            case "resetAvailable":
                return Nexerelin.isEnabled()
                        && GroundCombatPreferenceLedger.read(state) != GroundCombatPreference.ASK;
            case "addReset":
                dialog.getOptionPanel().addOption(Strings.get("nexInvadeResetOption"), OPTION_RESET);
                return true;
            case "reset":
                GroundCombatPreferenceLedger.write(state, GroundCombatPreference.ASK);
                dialog.getTextPanel().addPara(Strings.get("nexInvadeResetDone"));
                refreshMenu(dialog);
                return true;
            default:
                LOG.warn("MarineInvasionCMD: unknown sub-command '" + sub + "'");
                return false;
        }
    }

    /**
     * Where this Invade goes. The alpha supports only the player's own invasion of a planet's market: a station has
     * no planet to fight on, so it is always Nexerelin's.
     */
    private static InvasionRoute route(InteractionDialogAPI dialog, CampaignState state) {
        boolean supported = dialog.getInteractionTarget() instanceof PlanetAPI planet
                && planet.getMarket() != null && state != null;
        return InvasionRoute.choose(GroundCombatPreferenceLedger.read(state), Nexerelin.isEnabled(), supported);
    }

    /** Re-fires the Invade selection with the bypass set, so Nexerelin's own row takes it. */
    private static boolean toNexerelin(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
        if (local == null) return false;
        local.set(BYPASS_FLAG, true, 0f);
        local.set("$option", OPTION_NEXERELIN_INVADE, 0f);
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
        dialog.getTextPanel().addPara(Strings.get("nexInvadeForkText"));
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption(Strings.get("nexInvadeForkMarine"), OPTION_MARINE);
        options.addOption(Strings.get("nexInvadeForkNexerelin"), OPTION_NEXERELIN);
        options.addOption(Strings.get("nexInvadeForkMarineAlways"), OPTION_MARINE_ALWAYS);
        options.addOption(Strings.get("nexInvadeForkNexerelinAlways"), OPTION_NEXERELIN_ALWAYS);
        options.addOption(Strings.get("nexInvadeForkBack"), OPTION_NEXERELIN_BACK);
        options.setShortcut(OPTION_NEXERELIN_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    /** Opens Marine Operations straight at the invasion briefing. Backing out commits nothing. */
    private static boolean open(InteractionDialogAPI dialog) {
        if (!(dialog.getInteractionTarget() instanceof PlanetAPI planet) || planet.getMarket() == null) return false;
        MarketAPI market = planet.getMarket();
        Mission mission = NexInvasionMissionFactory.create(
                NexInvasionLedger.issue(state()), market.getId(), planet.getName(),
                market.getFactionId(), TargetProfileResolver.fromMarket(market).garrison());
        if (mission == null) return false;
        LOG.info("MarineInvasionCMD: opening invasion briefing " + mission.id);
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(MarineOpsDialogSize.requestedWidth(), MarineOpsDialogSize.requestedHeight(),
                new MarineOpsDialogDelegate(planet, ctx -> {
                    ctx.setSelectedMission(mission);
                    ctx.goTo(ScreenId.BRIEFING);
                }, () -> {
                    dialog.showTextPanel();
                    dialog.showVisualPanel();
                    // The market may have changed hands while the panel was up, so rebuild the menu from scratch.
                    refreshMenu(dialog);
                }));
        return true;
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
