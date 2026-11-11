package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.ops.playrules.StandInMarket;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.CharacterDataAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One visit to a planet's interaction dialog in {@code playBattle}: the game's real {@code rules.csv} rows, matched
 * and run by the game's own rule lines ({@link PlayRules}), over a dialog this class plays. The mod's rule commands run
 * unchanged; game commands that need the running game are stood in for ({@link StandInMarket}), and the market's
 * state is a set of toggles rather than a campaign to arrange.
 *
 * <p>A stand-in sector is installed only while the rules run: for an option, the dialog opening, and the panel
 * closing. Everything else in {@code playBattle} keeps seeing no sector, as it always has.
 */
final class PlayMarket implements StandInMarket.State {

    /** What the menu does outside the dialog: host the panel a rule command opens, or leave for the worlds. */
    interface Host {
        void showPanel(CustomVisualDialogDelegate delegate);

        void leave();
    }

    /** One option as the option panel holds it. */
    record Choice(String id, String text, boolean enabled, String tooltip, String confirm) {
    }

    /** The market's state, which the menu's toggles set and a reopen keeps. */
    static final class Toggles {
        boolean station;
        boolean defenders;
        boolean hostile;
        boolean cooldown;
        boolean nexerelin;
        /** An uncolonized world's ruins have already been explored. */
        boolean explored;
        /** A hostile fleet nearby is tracking the player. */
        boolean hostileFleet;
    }

    /**
     * Vanilla's rows for exploring a world's ruins and the salvage they hand over to, through to the post-salvage row
     * that marks the ruins explored. The commands that read the running game are stood in for ({@link StandInMarket}).
     */
    private static final Set<String> SALVAGE_ROWS = Set.of("sal_noDefenders", "sal_checkSpecialNoneFound",
            "sal_showRatingAndCost", "sal_optionLeave", "sal_optionSalvageEnabled", "sal_optionCheckAccidents",
            "sal_salvageOptionSelected");

    private static final Charset GAME_CSV = Charset.forName("windows-1252");
    private static final String LEAVE = "playLeave";

    private final PlanetAPI world;
    private final Toggles toggles;
    private final Host host;
    private final PlayRules rules;
    private final List<String> paragraphs = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private final LinkedHashMap<String, Choice> options = new LinkedHashMap<>();
    private final Map<String, MemoryAPI> memoryMap = new HashMap<>();
    private final MemoryAPI global;
    private final MemoryAPI player;
    private final List<Object> listeners = new ArrayList<>();
    private final Set<String> unknownSectorCalls = new HashSet<>();
    private final PlanetAPI planet;
    private final InteractionDialogAPI dialog;
    private final SectorAPI sector;
    private String pending;

    PlayMarket(PlanetAPI world, Toggles toggles, Host host) {
        PlayRuleLines.boot(StandInMarket.class.getPackageName());
        this.global = PlayRuleLines.memory();
        this.player = PlayRuleLines.memory();
        this.world = world;
        this.toggles = toggles;
        this.host = host;
        this.rules = new PlayRules(sources(toggles.nexerelin), this::traced);
        MemoryAPI entity = PlayRuleLines.memory();
        MemoryAPI market = PlayRuleLines.memory();
        MemoryAPI faction = PlayRuleLines.memory();
        MarketAPI port = world.getMarket();
        faction.set("$id", port.getFactionId());
        faction.set("$isNeutralFaction", false);
        MarketAPI wrappedMarket = wrap(MarketAPI.class, port, market);
        this.planet = wrapPlanet(world, entity, wrappedMarket);
        memoryMap.put(MemKeys.LOCAL, entity);
        memoryMap.put(MemKeys.ENTITY, entity);
        memoryMap.put(MemKeys.MARKET, market);
        memoryMap.put(MemKeys.FACTION, faction);
        memoryMap.put(MemKeys.GLOBAL, global);
        memoryMap.put(MemKeys.PLAYER, player);
        this.dialog = dialog();
        this.sector = sector();
        traced("rules loaded: " + rules.size() + (toggles.nexerelin ? ", with Nexerelin's" : ", without Nexerelin"));
    }

    /**
     * The rows the planet menu stands in for: the mod's raid, invade and ruins rows, vanilla's Take Hostile Action rows
     * (and the main menu row offering them), vanilla's ruins exploration and the salvage it hands over to, Nexerelin's replacements when it is toggled on, and {@code playBattle}'s own Leave.
     */
    private static List<PlayRules.Source> sources(boolean nexerelin) {
        Path core = Path.of(System.getProperty("starsectorDir"), "starsector-core", "data", "campaign", "rules.csv");
        List<PlayRules.Source> sources = new ArrayList<>();
        sources.add(new PlayRules.Source("mod", Path.of("mod/data/campaign/rules.csv"), StandardCharsets.UTF_8,
                id -> id.startsWith("smRaid") || id.startsWith("smNexInvade") || id.startsWith("smRuins")));
        sources.add(new PlayRules.Source("vanilla", core, GAME_CSV,
                id -> id.equals("marketOptHostile") || id.startsWith("marketHostile") || id.startsWith("salRuins")
                        || SALVAGE_ROWS.contains(id)));
        if (nexerelin) {
            sources.add(new PlayRules.Source("nex", nexerelinRules(), GAME_CSV,
                    id -> id.startsWith("nex_marketHostile")));
        }
        sources.add(new PlayRules.Source("play", Path.of("src/test/resources/play/rules.csv"), StandardCharsets.UTF_8,
                id -> true));
        return sources;
    }

    static Path nexerelinRules() {
        return Path.of(System.getProperty("starsectorDir"), "mods", "Nexerelin", "data", "campaign", "rules.csv");
    }

    static boolean nexerelinAvailable() {
        return Files.isRegularFile(nexerelinRules());
    }

    // ---------- what the menu shows and does ----------

    PlanetAPI world() {
        return world;
    }

    /** An uncolonized world with ruins: the ruins rows apply here, the raid rows never. */
    boolean ruinsSite() {
        return world.getMarket() != null && world.getMarket().isPlanetConditionMarketOnly();
    }

    Toggles toggles() {
        return toggles;
    }

    List<String> paragraphs() {
        return paragraphs;
    }

    List<String> trace() {
        return trace;
    }

    List<Choice> choices() {
        return new ArrayList<>(options.values());
    }

    /** The confirmation awaiting an answer, or null. */
    Choice pending() {
        return pending != null ? options.get(pending) : null;
    }

    void open() {
        inScope(() -> {
            paragraphs.add("Your fleet holds orbit over " + world.getName() + ".");
            if (ruinsSite()) {
                // What vanilla's memory update puts on an uncolonized, surveyed world's market.
                MemoryAPI market = memoryMap.get(MemKeys.MARKET);
                market.set("$isSurveyed", true);
                market.set("$isPlanetConditionMarketOnly", true);
                market.set("$hasRuins", true);
                market.set("$ruinsExplored", toggles.explored);
                for (MarketConditionAPI condition : world.getMarket().getConditions()) {
                    market.set("$mc:" + condition.getId(), true);
                }
                traced("an uncolonized ruins world; Explore the ruins offers the ruins fork");
            } else {
                memoryMap.get(MemKeys.LOCAL).set("$hasMarket", true);
            }
            memoryMap.get(MemKeys.LOCAL).set("$menuState", "main");
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
        });
    }

    /** The player picks an option; one with a confirmation waits for {@link #answer}. */
    void select(String id) {
        Choice choice = options.get(id);
        if (choice == null || !choice.enabled()) return;
        if (choice.confirm() != null) {
            pending = id;
            return;
        }
        choose(choice);
    }

    void answer(boolean yes) {
        Choice choice = pending();
        pending = null;
        if (yes && choice != null) choose(choice);
    }

    private void choose(Choice choice) {
        paragraphs.add("> " + choice.text());
        if (LEAVE.equals(choice.id())) {
            host.leave();
            return;
        }
        inScope(() -> {
            MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
            local.set("$option", choice.id());
            local.expire("$option", 0);
            if (!FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected")) {
                paragraphs.add("[playBattle] No rule matched option " + choice.id() + ".");
            }
        });
    }

    /** The panel a rule command opened has closed: its own close callback runs with the rules in place. */
    void panelClosed(CustomVisualDialogDelegate delegate) {
        inScope(() -> delegate.reportDismissed(0));
    }

    private void inScope(Runnable action) {
        SectorAPI before = Global.getSector();
        SettingsAPI settingsBefore = Global.getSettings();
        StandInMarket.current = this;
        Global.setSector(sector);
        Global.setSettings(withNexerelin(settingsBefore));
        try {
            action.run();
        } catch (RuntimeException failure) {
            failure.printStackTrace();
            paragraphs.add("[playBattle] " + failure);
            traced("FAILED: " + failure);
        } finally {
            Global.setSector(before);
            Global.setSettings(settingsBefore);
        }
    }

    /**
     * The settings with a mod manager that reports Nexerelin enabled exactly when its toggle is on, so the mod's own
     * Nexerelin checks (the invade fork, its settlement) see the same Nexerelin the loaded rows do.
     */
    private SettingsAPI withNexerelin(SettingsAPI settings) {
        ModManagerAPI underneath = settings.getModManager();
        ModManagerAPI mods = proxy(ModManagerAPI.class, (method, args) -> {
            if (method.getName().equals("isModEnabled") && "nexerelin".equals(args[0])) return toggles.nexerelin;
            return underneath != null ? delegate(method, underneath, args) : inert(method.getReturnType());
        });
        return proxy(SettingsAPI.class, (method, args) ->
                method.getName().equals("getModManager") ? mods : delegate(method, settings, args));
    }

    private void traced(String line) {
        trace.add(line);
        System.out.println("[playBattle rules] " + line);
    }

    // ---------- StandInMarket.State ----------

    @Override public boolean station() { return toggles.station; }

    @Override public boolean defenders() { return toggles.defenders; }

    @Override public boolean hostile() { return toggles.hostile; }

    @Override public boolean cooldown() { return toggles.cooldown; }

    @Override public void cooldown(boolean running) { toggles.cooldown = running; }

    @Override public boolean hostileFleet() { return toggles.hostileFleet; }

    @Override public void report(String line) { traced(line); }

    // ---------- the dialog the rules see ----------

    private InteractionDialogAPI dialog() {
        OptionPanelAPI optionPanel = proxy(OptionPanelAPI.class, this::option);
        TextPanelAPI textPanel = proxy(TextPanelAPI.class, (method, args) -> {
            String name = method.getName();
            if ((name.equals("addPara") || name.equals("addParagraph")) && args != null && args.length > 0
                    && args[0] instanceof String text) {
                paragraphs.add(text);
            }
            return inert(method.getReturnType());
        });
        Object visual = inert(VisualPanelAPI.class);
        Object plugin = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{InteractionDialogPlugin.class, RuleBasedDialog.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMemoryMap" -> memoryMap;
                    case "optionSelected" -> {
                        if (args[1] instanceof String id) select(id);
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> inert(method.getReturnType());
                });
        return proxy(InteractionDialogAPI.class, (method, args) -> switch (method.getName()) {
            case "getOptionPanel" -> optionPanel;
            case "getTextPanel" -> textPanel;
            case "getVisualPanel" -> visual;
            case "getInteractionTarget" -> planet;
            case "getPlugin" -> plugin;
            case "showCustomVisualDialog" -> {
                host.showPanel((CustomVisualDialogDelegate) args[2]);
                yield null;
            }
            case "dismiss", "dismissAsCancel" -> {
                host.leave();
                yield null;
            }
            default -> inert(method.getReturnType());
        });
    }

    /** The option panel: what the rules add, enable, explain and ask to confirm. */
    private Object option(Method method, Object[] args) {
        String name = method.getName();
        switch (name) {
            case "addOption" -> {
                String id = String.valueOf(args[1]);
                String tooltip = args.length > 2 && args[args.length - 1] instanceof String text ? text : null;
                options.put(id, new Choice(id, (String) args[0], true, tooltip, null));
            }
            case "clearOptions" -> options.clear();
            case "removeOption" -> options.remove(String.valueOf(args[0]));
            case "setEnabled" -> options.computeIfPresent(String.valueOf(args[0]),
                    (id, c) -> new Choice(id, c.text(), (Boolean) args[1], c.tooltip(), c.confirm()));
            case "setTooltip" -> options.computeIfPresent(String.valueOf(args[0]),
                    (id, c) -> new Choice(id, c.text(), c.enabled(), (String) args[1], c.confirm()));
            case "setOptionText" -> options.computeIfPresent(String.valueOf(args[1]),
                    (id, c) -> new Choice(id, (String) args[0], c.enabled(), c.tooltip(), c.confirm()));
            case "addOptionConfirmation" -> options.computeIfPresent(String.valueOf(args[0]),
                    (id, c) -> new Choice(id, c.text(), c.enabled(), c.tooltip(),
                            args.length > 1 && args[1] instanceof String text ? text : "Are you sure?"));
            case "hasOption" -> {
                return options.containsKey(String.valueOf(args[0]));
            }
            case "hasOptions" -> {
                return !options.isEmpty();
            }
            case "isOptionEnabled" -> {
                Choice choice = options.get(String.valueOf(args[0]));
                return choice != null && choice.enabled();
            }
            default -> {
            }
        }
        return inert(method.getReturnType());
    }

    // ---------- the sector the rules see ----------

    private SectorAPI sector() {
        ListenerManagerAPI listenerManager = proxy(ListenerManagerAPI.class, (method, args) -> {
            switch (method.getName()) {
                case "addListener" -> {
                    if (!listeners.contains(args[0])) listeners.add(args[0]);
                }
                case "removeListener" -> listeners.remove(args[0]);
                case "removeListenerOfClass" -> listeners.removeIf(((Class<?>) args[0])::isInstance);
                case "hasListener" -> {
                    return listeners.contains(args[0]);
                }
                case "hasListenerOfClass" -> {
                    return listeners.stream().anyMatch(((Class<?>) args[0])::isInstance);
                }
                case "getListeners" -> {
                    List<Object> matching = new ArrayList<>();
                    for (Object listener : listeners) {
                        if (((Class<?>) args[0]).isInstance(listener)) matching.add(listener);
                    }
                    return matching;
                }
                default -> {
                }
            }
            return inert(method.getReturnType());
        });
        CharacterDataAPI character = proxy(CharacterDataAPI.class, (method, args) -> switch (method.getName()) {
            case "getMemory", "getMemoryWithoutUpdate" -> player;
            default -> inert(method.getReturnType());
        });
        return proxy(SectorAPI.class, (method, args) -> switch (method.getName()) {
            case "getRules" -> rules;
            case "getListenerManager" -> listenerManager;
            case "getMemory", "getMemoryWithoutUpdate" -> global;
            case "getCharacterData" -> character;
            case "getPlayerFleet" -> {
                CampaignFleetAPI fleet = CampaignBackend.playerFleet();
                yield fleet;
            }
            default -> {
                if (unknownSectorCalls.add(method.getName())) traced("stand-in sector: " + method.getName()
                        + " is not answered");
                yield inert(method.getReturnType());
            }
        });
    }

    // ---------- the planet and market the rules see ----------

    /** The world as the dialog targets it, with the game's own memory on it and on its market. */
    private PlanetAPI wrapPlanet(PlanetAPI original, MemoryAPI entity, MarketAPI market) {
        return (PlanetAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PlanetAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMemory", "getMemoryWithoutUpdate" -> entity;
                    case "getMarket" -> market;
                    default -> delegate(method, original, args);
                });
    }

    private <T> T wrap(Class<T> type, T original, MemoryAPI memory) {
        return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMemory", "getMemoryWithoutUpdate" -> memory;
                    default -> delegate(method, original, args);
                }));
    }

    private static Object delegate(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    // ---------- proxies ----------

    private interface Handler {
        Object handle(Method method, Object[] args) throws Throwable;
    }

    private <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "playBattle " + type.getSimpleName();
                    default -> handler.handle(method, args);
                }));
    }

    /** A value that does nothing: zero, false, an empty list, or a proxy whose every call does nothing. */
    private Object inert(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        if (type.isAssignableFrom(ArrayList.class)) return new ArrayList<>();
        if (type.isAssignableFrom(HashMap.class)) return new HashMap<>();
        if (type.isInterface()) return proxy(type, (method, args) -> inert(method.getReturnType()));
        return null;
    }
}
