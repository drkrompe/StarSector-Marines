package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.raid.MarketRaidLedger;
import com.dillon.starsectormarines.campaign.raid.LedRaidReward;
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
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.util.Misc;
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
 *   <li>{@code menuIntercept} / {@code menu}: the raid menu ({@code mktRaid}). The raid command builds it, then any
 *       kind of raid it refused for want of marines is offered again, since the company can lead it
 *       ({@code raid-resolution.md}, law 1).</li>
 *   <li>{@code shortIntercept <valuable|disrupt>} / {@code ledOnly <option>}: picking a kind of raid the fleet's
 *       marines are too few for marks the raid as one only the company can carry out, and hands the pick to the raid
 *       command. Its choice then offers only Lead the Raid.</li>
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
    /** One-shot local flag that lets a re-fired selection through to the raid command's own row. */
    static final String BYPASS_FLAG = "$smRaidBypass";
    /** Local flag: this raid is one the fleet's marines are too few for, so it can only be led. */
    static final String LED_ONLY_FLAG = "$smRaidLedOnly";

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
                if (ledOnly(local(memoryMap))) {
                    showFork(dialog);
                    return true;
                }
                return toStandard(dialog, memoryMap);
            case "menuIntercept":
                if (bypassed(memoryMap)) return false;
                return RaidForkSetting.asks(state) && planetMarket(dialog) != null;
            case "menu":
                return menu(dialog, memoryMap);
            case "shortIntercept": {
                if (bypassed(memoryMap) || !RaidForkSetting.asks(state) || params.size() < 2) return false;
                TempData temp = RaidOutcomeCommit.raidOn(planetMarket(dialog));
                return temp != null && !temp.nonMarket && LedRaidReward.shortOfMarines(temp.raidMult,
                        "disrupt".equals(params.get(1).getString(memoryMap)));
            }
            case "ledOnly": {
                MemoryAPI local = local(memoryMap);
                if (local == null || params.size() < 2) return false;
                local.set(LED_ONLY_FLAG, true, 0f);
                return refire(dialog, memoryMap, params.get(1).getString(memoryMap));
            }
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
        return refire(dialog, memoryMap, MarketCMD.RAID_CONFIRM);
    }

    /** Re-fires the selection of {@code option} with the bypass set, so the raid command's own row takes it. */
    private static boolean refire(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, String option) {
        MemoryAPI local = local(memoryMap);
        if (local == null || option == null) return false;
        local.set(BYPASS_FLAG, true, 0f);
        local.set("$option", option, 0f);
        return FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected");
    }

    /**
     * The raid menu as the raid command builds it, with any kind of raid it refused for want of marines offered again:
     * the company's battle stands in for them ({@code raid-resolution.md}, law 1). Nothing else of the command's gate
     * changes: a raid the market menu would not open (enemy fleets near, the raid cooldown) never reaches here.
     */
    private static boolean menu(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = local(memoryMap);
        if (local != null) local.unset(LED_ONLY_FLAG);
        boolean fired = refire(dialog, memoryMap, MarketCMD.RAID);
        TempData temp = RaidOutcomeCommit.raidOn(planetMarket(dialog));
        if (!fired || temp == null) return fired;
        boolean valuables = LedRaidReward.shortOfMarines(temp.raidMult, false);
        boolean disrupt = LedRaidReward.shortOfMarines(temp.raidMult, true);
        if (!valuables && !disrupt) return true;
        OptionPanelAPI options = dialog.getOptionPanel();
        String lead = Strings.get("marketRaidMenuShort");
        if (valuables && options.hasOption(MarketCMD.RAID_VALUABLE)) {
            options.setEnabled(MarketCMD.RAID_VALUABLE, true);
            options.setTooltip(MarketCMD.RAID_VALUABLE, lead);
        }
        if (disrupt && options.hasOption(MarketCMD.RAID_DISRUPT)) {
            options.setEnabled(MarketCMD.RAID_DISRUPT, true);
            options.setTooltip(MarketCMD.RAID_DISRUPT, lead);
            options.setTooltipHighlights(MarketCMD.RAID_DISRUPT);
        }
        dialog.getTextPanel().addPara(lead);
        return true;
    }

    private static MemoryAPI local(Map<String, MemoryAPI> memoryMap) {
        return memoryMap != null ? memoryMap.get(MemKeys.LOCAL) : null;
    }

    private static MemoryAPI local(InteractionDialogAPI dialog) {
        return dialog.getPlugin() instanceof RuleBasedDialog rules ? local(rules.getMemoryMap()) : null;
    }

    private static boolean ledOnly(MemoryAPI local) {
        return local != null && local.getBoolean(LED_ONLY_FLAG);
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
        comparePayouts(dialog);
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption(Strings.get("marketRaidForkMarine"), OPTION_MARINE);
        options.addOption(Strings.get("marketRaidForkStandard"), OPTION_STANDARD);
        if (ledOnly(local(dialog))) {
            options.setEnabled(OPTION_STANDARD, false);
            options.setTooltip(OPTION_STANDARD, Strings.get("marketRaidForkShort"));
        }
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

    /**
     * The two raids side by side in the raid command's own terms ({@code raid-resolution.md}, law 9): the loot it
     * projects, or the days it says the target goes down, ordered and led, and what defends the market. Ordered is
     * what the command already told the player; led is the same reckoning at the won led raid's size. A raid the
     * fleet's marines are too few for says so instead of an ordered figure.
     */
    private static void comparePayouts(InteractionDialogAPI dialog) {
        MarketAPI market = planetMarket(dialog);
        TempData temp = RaidOutcomeCommit.raidOn(market);
        if (temp == null || temp.objectives == null) return;
        boolean disrupting = temp.raidType == RaidType.DISRUPT;
        List<GroundRaidObjectivePlugin> objectives = temp.objectives;
        int[] assigned = new int[objectives.size()];
        for (int i = 0; i < assigned.length; i++) {
            GroundRaidObjectivePlugin objective = objectives.get(i);
            assigned[i] = objective == null ? 0 : Math.max(0, objective.getMarinesAssigned());
        }
        RaidSiteTemplate.Security security = RaidSiteTemplate.Security.of(TargetProfileResolver.fromMarket(market));
        int[] led = LedRaidReward.ledAssignment(assigned, LedRaidReward.premium(security), disrupting);
        boolean ordered = !ledOnly(local(dialog));
        if (disrupting) {
            int at = -1;
            for (int i = 0; i < assigned.length && at < 0; i++) {
                if (assigned[i] > 0 && objectives.get(i) != null) at = i;
            }
            if (at < 0) return;
            GroundRaidObjectivePlugin target = objectives.get(at);
            String name = target.getNameOverride() != null ? target.getNameOverride() : target.getName();
            if (ordered) dialog.getTextPanel().addPara(Strings.format("marketRaidForkOrderedDays", name,
                    target.getDisruptionDaysSort(assigned[at])));
            dialog.getTextPanel().addPara(Strings.format("marketRaidForkLedDays", name,
                    target.getDisruptionDaysSort(led[at])));
        } else {
            if (ordered) dialog.getTextPanel().addPara(Strings.format("marketRaidForkOrderedLoot",
                    Misc.getDGSCredits(creditsAt(objectives, assigned))));
            dialog.getTextPanel().addPara(Strings.format("marketRaidForkLedLoot",
                    Misc.getDGSCredits(creditsAt(objectives, led))));
        }
        if (!ordered) dialog.getTextPanel().addPara(Strings.get("marketRaidForkShort"));
        dialog.getTextPanel().addPara(Strings.format("marketRaidForkLedFaces",
                Strings.get("marketRaidSecurity" + security.name())));
    }

    /**
     * The value the raid command projects for {@code objectives} with {@code tokens} marine tokens on each, as its own
     * plan reports it; each objective's assignment is put back as it was.
     */
    private static float creditsAt(List<GroundRaidObjectivePlugin> objectives, int[] tokens) {
        float value = 0f;
        for (int i = 0; i < objectives.size(); i++) {
            GroundRaidObjectivePlugin objective = objectives.get(i);
            if (objective == null || tokens[i] <= 0) continue;
            int was = objective.getMarinesAssigned();
            objective.setMarinesAssigned(tokens[i]);
            try {
                value += objective.getProjectedCreditsValue();
            } finally {
                objective.setMarinesAssigned(was);
            }
        }
        return value;
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
        RaidOutcomeCommit.prepare(temp, won, MarketRaidLedger.recordedShare(state()),
                LedRaidReward.premium(RaidSiteTemplate.Security.of(TargetProfileResolver.fromMarket(market))));
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
