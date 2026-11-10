package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.raid.MarketRaidLedger;
import com.dillon.starsectormarines.campaign.raid.RaidOutcomeCommit;
import com.dillon.starsectormarines.campaign.raid.RaidForkSetting;
import com.dillon.starsectormarines.campaign.raid.RaidRoute;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.MarineOpsDialogDelegate;
import com.dillon.starsectormarines.ops.MarineOpsDialogSize;
import com.dillon.starsectormarines.ops.MarketRaidMissionFactory;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.ScreenId;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.util.Misc.Token;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

/**
 * Offers the player's choice of how their own raid on a market is resolved ({@code raid-resolution.md}): led as a
 * Marine Operations battle, or launched as the game would. Referenced from {@code rules.csv} by simple name.
 *
 * <p>It intercepts the raid's confirm ({@code mktRaidConfirm}) at a rule score of 3 ({@code score:2} on the option
 * line plus this command's condition line), one above Nexerelin's own row (2) and vanilla's (1). So the same rule
 * works with and without Nexerelin and never names a Nexerelin command. Both paths end in whichever raid command is
 * running (vanilla's {@code MarketCMD}, or Nexerelin's subclass of it): the selection is re-fired with a one-shot
 * bypass, and that command commits the raid. A led raid first adjusts the raid it commits ({@link RaidOutcomeCommit}).
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code intercept}: condition. This is a raid Marine Operations can fight and the save's
 *       {@link RaidForkSetting} is on. When false, the raid command's own row handles the confirm untouched.</li>
 *   <li>{@code fork}: shows the choice. It is made every raid; nothing is remembered.</li>
 *   <li>{@code marine}: opens the raid briefing.</li>
 *   <li>{@code standard}: hands the confirm back to the raid command.</li>
 * </ul>
 *
 * <p>The raid command's own gating stays authoritative: the raid exists, and its confirm is offered, only where the
 * game allows it, and its own "never mind" ({@code mktRaidNeverMind}) is reused as Back. The story-point secret raid
 * confirms inside the command rather than through a rule, so it always resolves the standard way.
 */
public class MarineRaidCMD extends BaseCommandPlugin {

    private static final Logger LOG = Global.getLogger(MarineRaidCMD.class);

    static final String OPTION_MARINE = "smRaidMarine";
    static final String OPTION_STANDARD = "smRaidStandard";
    /** One-shot local flag that lets a re-fired confirm through to the raid command's own row. */
    static final String BYPASS_FLAG = "$smRaidBypass";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;
        String sub = params.get(0).getString(memoryMap);
        CampaignState state = state();
        switch (sub) {
            case "intercept":
                if (bypassed(memoryMap)) return false;
                return route(dialog, state) != RaidRoute.STANDARD;
            case "fork":
                showFork(dialog);
                return true;
            case "marine":
                return open(dialog);
            case "standard":
                return toStandard(dialog, memoryMap);
            default:
                LOG.warn("MarineRaidCMD: unknown sub-command " + sub);
                return false;
        }
    }

    /**
     * Where this raid goes. Supported: a raid for valuables or to disrupt an industry, on a planet's market. A
     * station has no surface to fight on; a raid on something that is not a market, or a mission's custom-only raid,
     * is not a fight Marine Operations can stand in for.
     */
    private static RaidRoute route(InteractionDialogAPI dialog, CampaignState state) {
        TempData temp = RaidOutcomeCommit.raidOn(planetMarket(dialog));
        boolean supported = state != null && temp != null && !temp.nonMarket
                && (temp.raidType == RaidType.VALUABLE || temp.raidType == RaidType.DISRUPT);
        return RaidRoute.choose(RaidForkSetting.asks(state), supported);
    }

    /** The industry's Raid site's briefing line, or null for an industry with no site or a raid that disrupts nothing. */
    private static String chokepoint(String planetName, int marketSize, String industryId, int days) {
        RaidSiteTemplate site = RaidSiteTemplate.forIndustry(industryId);
        if (site == null || days <= 0) return null;
        return Strings.format(site.briefingKey(), planetName, RaidSiteTemplate.feedersFor(marketSize), days);
    }

    private static MarketAPI planetMarket(InteractionDialogAPI dialog) {
        return dialog.getInteractionTarget() instanceof PlanetAPI planet ? planet.getMarket() : null;
    }

    /** Re-fires the confirm with the bypass set, so the raid command's own row takes it. */
    private static boolean toStandard(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
        if (local == null) return false;
        local.set(BYPASS_FLAG, true, 0f);
        local.set("$option", MarketCMD.RAID_CONFIRM, 0f);
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
        dialog.getTextPanel().addPara(Strings.get("marketRaidForkText"));
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption(Strings.get("marketRaidForkMarine"), OPTION_MARINE);
        options.addOption(Strings.get("marketRaidForkStandard"), OPTION_STANDARD);
        options.addOption(Strings.get("marketRaidForkBack"), MarketCMD.RAID_NEVER_MIND);
        options.setShortcut(MarketCMD.RAID_NEVER_MIND, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    /**
     * Opens Marine Operations straight at the raid briefing. When the panel closes, a fought battle commits the raid
     * through the raid command; backing out commits nothing and returns to the choice.
     */
    private static boolean open(InteractionDialogAPI dialog) {
        if (!(dialog.getInteractionTarget() instanceof PlanetAPI planet) || planet.getMarket() == null) return false;
        MarketAPI market = planet.getMarket();
        int serial = MarketRaidLedger.issue(state());
        TempData temp = RaidOutcomeCommit.raidOn(market);
        String industryId = RaidOutcomeCommit.targetIndustryId(temp);
        Mission mission = MarketRaidMissionFactory.create(
                serial, market.getId(), planet.getName(), market.getFactionId(), industryId,
                TargetProfileResolver.fromMarket(market).garrison(),
                chokepoint(planet.getName(), market.getSize(), industryId, RaidOutcomeCommit.disruptionDays(temp)));
        if (mission == null) return false;
        LOG.info("MarineRaidCMD: opening raid briefing " + mission.id);
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        dialog.showCustomVisualDialog(MarineOpsDialogSize.requestedWidth(), MarineOpsDialogSize.requestedHeight(),
                new MarineOpsDialogDelegate(planet, ctx -> {
                    ctx.bindOperation(mission);
                    ctx.goTo(ScreenId.BRIEFING);
                }, () -> {
                    dialog.showTextPanel();
                    dialog.showVisualPanel();
                    afterPanel(dialog, market, serial);
                }));
        return true;
    }

    /** Commits the fought raid through the raid command, or returns to the choice when no battle was fought. */
    private static void afterPanel(InteractionDialogAPI dialog, MarketAPI market, int serial) {
        MarketRaidLedger.Pending pending = MarketRaidLedger.take(state(), serial);
        if (pending == MarketRaidLedger.Pending.NONE) {
            showFork(dialog);
            return;
        }
        boolean won = pending == MarketRaidLedger.Pending.WON;
        TempData temp = RaidOutcomeCommit.raidOn(market);
        if (temp == null || !(dialog.getPlugin() instanceof RuleBasedDialog rules)) {
            LOG.warn("MarineRaidCMD: raid " + serial + " on " + market.getId() + " has no raid to commit");
            refreshMenu(dialog);
            return;
        }
        RaidOutcomeCommit.prepare(temp, won, MarketRaidLedger.recordedShare(state()));
        dialog.getTextPanel().addPara(Strings.get(won ? "marketRaidCommitWon" : "marketRaidCommitLost"));
        rules.updateMemory();
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        // Armed only around this one confirm: the cargo marines did not fight, so the command rolls no losses.
        listeners.addListener(RaidOutcomeCommit.LOSSES, true);
        try {
            toStandard(dialog, rules.getMemoryMap());
        } finally {
            listeners.removeListener(RaidOutcomeCommit.LOSSES);
        }
        LOG.info("MarineRaidCMD: raid " + serial + " on " + market.getId() + " committed as "
                + (won ? "won" : "lost"));
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
