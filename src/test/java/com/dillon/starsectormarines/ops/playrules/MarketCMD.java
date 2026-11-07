package com.dillon.starsectormarines.ops.playrules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.util.Misc.Token;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Stands in for vanilla's {@code MarketCMD}, the command behind Take Hostile Action, which needs the whole running
 * game. It keeps vanilla's option ids, menus and raid record ({@code $MarketCMD_temp}, a real {@link TempData}), so the
 * rows and the mod's commands around it run unchanged; what the game decides from fleets and markets, the planet
 * menu's toggles decide instead ({@link StandInMarket}).
 *
 * <p>Its raid confirm reads the marine-loss stat through the game's own listener hook, as vanilla's does, so a
 * listener the mod arms around the confirm acts on it here exactly as in the game. It then reports, line by line, what
 * the game's command would commit, and fires {@code PostGroundRaid} as vanilla does.
 */
public class MarketCMD extends BaseCommandPlugin {

    /** Where the raid command keeps the raid being set up, on the market's memory. */
    static final String TEMP_KEY = "$MarketCMD_temp";
    /** Vanilla's average marine losses before modifiers, for a raid this size. UNTUNED: a stand-in figure. */
    static final float AVERAGE_LOSSES = 12f;
    /** Vanilla's raid effectiveness from attacker against defender strength. UNTUNED: a stand-in figure. */
    static final float RAID_EFFECTIVENESS = 0.55f;
    static final int MARINE_TOKENS = 4;

    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;

    /** Whose command this stands in for, in the report. */
    protected String who() {
        return "vanilla's MarketCMD";
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        String command = params.get(0).getString(memoryMap);
        switch (command) {
            case "showDefenses", "goBackToDefenses" -> showDefenses(dialog);
            case "engage" -> {
                dialog.getTextPanel().addPara("[playBattle] Engaging the defences is a fleet battle and is not stood"
                        + " in for. Turn the station and defenders toggles off to stand for having beaten them.");
                showDefenses(dialog);
            }
            case "raidMenu", "raidNeverMind" -> raidMenu();
            case "raidValuable" -> raidSetUp(RaidType.VALUABLE);
            case "raidDisrupt" -> raidSetUp(RaidType.DISRUPT);
            case "raidConfirm" -> raidConfirm();
            case "addContinueToRaidResultOption" -> {
                dialog.getOptionPanel().clearOptions();
                dialog.getOptionPanel().addOption("Continue", "mktRaidResult");
            }
            case "raidResult" -> {
                local().set("$menuState", "main", 0f);
                dialog.getTextPanel().addPara("The raid is over.");
                FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            }
            case "bombardMenu" -> {
                dialog.getTextPanel().addPara("[playBattle] Bombardment is not stood in for.");
                showDefenses(dialog);
            }
            default -> {
                return other(command, dialog, memoryMap);
            }
        }
        return true;
    }

    /** A command this stand-in does not know; a subclass may. */
    protected boolean other(String command, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        dialog.getTextPanel().addPara("[playBattle] " + who() + " " + command + " is not stood in for.");
        return false;
    }

    protected void addInvade(OptionPanelAPI options, boolean allowed, String planet) {
    }

    protected void showDefenses(InteractionDialogAPI dialog) {
        StandInMarket.State state = StandInMarket.state();
        String planet = dialog.getInteractionTarget().getName();
        TextPanelAPI text = dialog.getTextPanel();
        if (state.station()) {
            text.addPara("A star fortress dominates the orbit and prevents any hostile action, aside from a quick"
                    + " raid, unless it is dealt with.");
        }
        if (state.defenders()) {
            text.addPara("The defending ships present are" + (state.station() ? ", with the support of the station,"
                    : "") + " sufficient to prevent raiding as well.");
        }
        if (!state.station() && !state.defenders()) text.addPara("Nothing in orbit stands in your way.");
        if (state.cooldown()) text.addPara("Your forces need time to prepare for another raid.");
        boolean raid = !state.defenders() && !state.cooldown();
        boolean heavy = !state.station() && !state.defenders();
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Engage the " + (state.station() ? "star fortress and its defenders" : "defenders"),
                "mktEngage");
        if (!state.station() && !state.defenders()) options.setEnabled("mktEngage", false);
        options.addOption("Launch a raid against " + planet, "mktRaid");
        if (!raid) options.setEnabled("mktRaid", false);
        options.addOption("Consider an orbital bombardment of " + planet, "mktBombard");
        if (!heavy) options.setEnabled("mktBombard", false);
        addInvade(options, heavy, planet);
        options.addOption("Go back", "mktGoBack");
    }

    private void raidMenu() {
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("You consider what a raid could achieve.");
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Raid for valuables", "mktRaidValuable");
        options.addOption("Raid to disrupt an industry", "mktRaidDisrupt");
        options.addOption("Go back", "mktRaidGoBack");
    }

    /** What vanilla's objective dialog leaves behind: the raid record, with its objectives assigned. */
    private void raidSetUp(RaidType type) {
        MarketAPI market = market();
        TempData temp = new TempData();
        temp.raidType = type;
        temp.raidMult = RAID_EFFECTIVENESS;
        temp.canRaid = true;
        Industry target = type == RaidType.DISRUPT ? industry(market) : null;
        temp.target = target;
        temp.objectives.add(objective(type == RaidType.DISRUPT ? "Disrupt " + target.getCurrentName()
                : "Valuables", target, MARINE_TOKENS));
        market.getMemoryWithoutUpdate().set(TEMP_KEY, temp, 0f);

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("Objective: " + temp.objectives.get(0).getName() + ", " + MARINE_TOKENS
                + " marine tokens assigned. Raid effectiveness " + Math.round(RAID_EFFECTIVENESS * 100f) + "%.");
        text.addPara("Expected marine losses: " + Math.round(lossesStat(temp).getModifiedValue()) + ".");
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Launch raid", "mktRaidConfirm");
        options.addOption("Never mind", "mktRaidNeverMind");
        if (!StandInMarket.state().hostile()) {
            options.addOptionConfirmation("mktRaidConfirm", "The " + market.getFaction().getDisplayName()
                    + " are not currently hostile, and you have been positively identified. Are you sure you want"
                    + " to engage in open hostilities?", "Yes", "Never mind");
        }
    }

    /** Vanilla's confirm, reported rather than applied: what the game's command would commit for this raid record. */
    private void raidConfirm() {
        MarketAPI market = market();
        Object stored = market.getMemoryWithoutUpdate().get(TEMP_KEY);
        if (!(stored instanceof TempData temp) || temp.raidType == null) {
            raidMenu();
            return;
        }
        StandInMarket.State state = StandInMarket.state();
        state.report("COMMIT by " + who() + " on " + market.getName() + ":");
        state.report("  hostile timeout, military response, raid cooldown");
        state.cooldown(true);
        float tokens = 0f;
        for (GroundRaidObjectivePlugin objective : temp.objectives) tokens += objective.getMarinesAssigned();
        state.report("  stability penalty from " + tokens + " assigned tokens" + (tokens <= 0f ? " (none)" : ""));
        if (!state.hostile()) state.report("  reputation hit with " + market.getFaction().getDisplayName());
        MutableStat losses = lossesStat(temp);
        int lost = Math.round(losses.getModifiedValue());
        state.report("  marine losses " + lost + mods(losses));
        if (temp.objectives.isEmpty()) {
            state.report("  objectives achieved: none, no loot, nothing disrupted");
        } else {
            for (GroundRaidObjectivePlugin objective : temp.objectives) {
                state.report("  objective achieved: " + objective.getName() + " at effectiveness "
                        + Math.round(temp.raidMult * 100f) + "%");
            }
        }
        dialog.getTextPanel().addPara(lost <= 0 ? "Your forces have not suffered any casualties."
                : "Your forces have suffered " + lost + " casualties during the raid.");
        FireBest.fire(null, dialog, memoryMap, "PostGroundRaid");
    }

    /** Vanilla's marine-loss stat: its base, then whatever the listeners on the game's own hook make of it. */
    private MutableStat lossesStat(TempData temp) {
        MutableStat stat = new MutableStat(AVERAGE_LOSSES);
        ListenerUtil.modifyMarineLossesStatPreRaid(market(), temp.objectives, stat);
        return stat;
    }

    private static String mods(MutableStat stat) {
        StringBuilder out = new StringBuilder();
        for (StatMod mod : stat.getMultMods().values()) {
            out.append(out.length() == 0 ? " (" : ", ").append(mod.desc != null ? mod.desc : mod.source)
                    .append(" x").append(mod.value);
        }
        return out.length() == 0 ? "" : out.append(")").toString();
    }

    private MarketAPI market() {
        return dialog.getInteractionTarget().getMarket();
    }

    private MemoryAPI local() {
        return memoryMap.get(MemKeys.LOCAL);
    }

    private static Industry industry(MarketAPI market) {
        return (Industry) Proxy.newProxyInstance(Industry.class.getClassLoader(), new Class<?>[]{Industry.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> "heavyindustry";
                    case "getCurrentName", "getNameForModifier" -> "Heavy Industry";
                    case "getMarket" -> market;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "stand-in Heavy Industry";
                    default -> zero(method.getReturnType());
                });
    }

    private static GroundRaidObjectivePlugin objective(String name, Industry source, int tokens) {
        return (GroundRaidObjectivePlugin) Proxy.newProxyInstance(GroundRaidObjectivePlugin.class.getClassLoader(),
                new Class<?>[]{GroundRaidObjectivePlugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getSource" -> source;
                    case "getMarinesAssigned", "getMarinesRequired" -> tokens;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "stand-in objective " + name;
                    default -> zero(method.getReturnType());
                });
    }

    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        return null;
    }
}
