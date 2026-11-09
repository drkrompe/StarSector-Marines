package com.dillon.starsectormarines.ops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code playBattle}'s planet menu: a planet's interaction dialog ({@link PlayMarket}) on the left, as the game shows
 * it, and on the right the market state its toggles set and the trace of every rule match. Not a screen of the mod;
 * its markup lives with the test tree and never ships.
 *
 * <p>Every action is handed to the host, which runs it between input and the next tick and then rebuilds this
 * screen, never from inside the screen's own handler.
 */
final class PlayMarketMenu extends MissionFlowMlxScreen {

    private static final String ROOT = "market-menu";
    private static final List<String> COMPONENTS = List.of("play/market-menu.mlx");
    /** How much of the dialog and the trace is on screen: the newest, so the end is always in view. */
    private static final int PARAGRAPHS = 28;
    private static final int TRACE = 40;

    private final Consumer<Runnable> act;
    private final Runnable leave;
    private final Consumer<PlayMarket.Toggles> reopen;
    private PlayMarket market;

    PlayMarketMenu(Consumer<Runnable> act, Runnable leave, Consumer<PlayMarket.Toggles> reopen) {
        super(ROOT, COMPONENTS);
        this.act = act;
        this.leave = leave;
        this.reopen = reopen;
    }

    void show(PlayMarket market) {
        this.market = market;
    }

    void refresh() {
        rebuildDocument();
    }

    @Override protected List<String> requiredElementIds() {
        return List.of("market-menu-root", "market-menu-leave");
    }

    @Override protected Map<String, Object> props() {
        Map<String, Object> props = new HashMap<>();
        props.put("title", "PLAYBATTLE / PLANET MENU / " + market.world().getName().toUpperCase());
        props.put("copy", "The game's own rules.csv rows, matched by the game's own rule lines. Toggle the market's"
                + " state on the right.");
        props.put("leaveAction", leave);

        List<Map<String, Object>> paragraphs = new ArrayList<>();
        List<String> text = market.paragraphs();
        for (int i = Math.max(0, text.size() - PARAGRAPHS); i < text.size(); i++) {
            paragraphs.add(Map.of("id", "market-para-" + i, "text", text.get(i)));
        }
        props.put("paragraphs", paragraphs);

        List<Map<String, Object>> confirms = new ArrayList<>();
        PlayMarket.Choice pending = market.pending();
        if (pending != null) {
            Map<String, Object> confirm = new HashMap<>();
            confirm.put("id", "market-confirm");
            confirm.put("textId", "market-confirm-text");
            confirm.put("rowId", "market-confirm-row");
            confirm.put("yesId", "market-confirm-yes");
            confirm.put("noId", "market-confirm-no");
            confirm.put("text", pending.confirm());
            confirm.put("yesAction", (Runnable) () -> act.accept(() -> market.answer(true)));
            confirm.put("noAction", (Runnable) () -> act.accept(() -> market.answer(false)));
            confirms.add(confirm);
        }
        props.put("confirms", confirms);

        List<Map<String, Object>> choices = new ArrayList<>();
        int index = 0;
        for (PlayMarket.Choice choice : market.choices()) {
            index++;
            Map<String, Object> row = new HashMap<>();
            // Keyed by the option's own id, so a rebuilt menu never hands one option's button to another, and a
            // script presses an option by name: market-option-mktRaid.
            row.put("id", "market-option-" + choice.id().replaceAll("[^A-Za-z0-9_-]", "_"));
            String label = index + ". " + choice.text() + (choice.enabled() ? "" : "  (disabled)")
                    + (choice.tooltip() != null ? "  — " + choice.tooltip() : "");
            row.put("text", label);
            row.put("disabled", !choice.enabled() || pending != null);
            row.put("action", (Runnable) () -> act.accept(() -> market.select(choice.id())));
            choices.add(row);
        }
        props.put("choices", choices);

        PlayMarket.Toggles toggles = market.toggles();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (market.ruinsSite()) {
            rows.add(toggle("market-toggle-explored", "Ruins already explored", toggles.explored, false,
                    () -> toggles.explored = !toggles.explored));
            rows.add(toggle("market-toggle-hostile-fleet", "Hostile fleet nearby and tracking you",
                    toggles.hostileFleet, false, () -> toggles.hostileFleet = !toggles.hostileFleet));
        } else {
            rows.add(toggle("market-toggle-station", "Star fortress in orbit", toggles.station, false,
                    () -> toggles.station = !toggles.station));
            rows.add(toggle("market-toggle-defenders", "Defenders strong enough to stop a raid", toggles.defenders,
                    false, () -> toggles.defenders = !toggles.defenders));
            rows.add(toggle("market-toggle-hostile", "Faction already hostile (no confirmation)", toggles.hostile,
                    false, () -> toggles.hostile = !toggles.hostile));
            rows.add(toggle("market-toggle-cooldown", "Raid cooldown running", toggles.cooldown, false,
                    () -> toggles.cooldown = !toggles.cooldown));
        }
        boolean nex = PlayMarket.nexerelinAvailable();
        rows.add(toggle("market-toggle-nexerelin", "Nexerelin installed" + (nex ? "" : " (not found in mods)"),
                toggles.nexerelin, !nex, () -> toggles.nexerelin = !toggles.nexerelin));
        Map<String, Object> again = new HashMap<>();
        again.put("id", "market-toggle-reopen");
        again.put("label", "Reopen the dialog");
        again.put("disabled", false);
        again.put("action", (Runnable) () -> act.accept(() -> reopen.accept(toggles)));
        rows.add(again);
        props.put("toggles", rows);

        List<Map<String, Object>> trace = new ArrayList<>();
        List<String> lines = market.trace();
        for (int i = Math.max(0, lines.size() - TRACE); i < lines.size(); i++) {
            trace.add(Map.of("id", "market-trace-" + i, "text", lines.get(i)));
        }
        props.put("trace", trace);
        return props;
    }

    /** A toggle flips the state and reopens the dialog on it, as arriving at a market in that state would. */
    private Map<String, Object> toggle(String id, String label, boolean on, boolean disabled, Runnable flip) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("label", (on ? "[ON]  " : "[off] ") + label);
        row.put("disabled", disabled);
        PlayMarket.Toggles toggles = market.toggles();
        row.put("action", (Runnable) () -> act.accept(() -> {
            flip.run();
            reopen.accept(toggles);
        }));
        return row;
    }
}
