package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.fs.starfarer.api.campaign.PlanetAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code playBattle}'s campaign map: the worlds {@link PlayCompany} stands in, each with who has work there now, so the
 * story can be followed from one to the next. Picking one docks there and opens Marine Operations, as a flight and a
 * planet menu would. Beside them are the run's saves ({@link PlaySaves}) and a way to start the campaign over. Not a screen of the mod; its markup lives with the test tree and never ships.
 */
final class PlayWorldMenu extends MissionFlowMlxScreen {

    private static final String ROOT = "world-menu";
    private static final List<String> COMPONENTS = List.of("play/world-menu.mlx");
    /** What each stockpile button adds; the markup's labels say the same. */
    private static final int GRANT = 20_000;

    private final Consumer<PlanetAPI> pick;
    private final Runnable quit;
    private final Consumer<String> load;
    private final Runnable save;
    private final Runnable startOver;
    private final Consumer<Runnable> grant;

    PlayWorldMenu(Consumer<PlanetAPI> pick, Runnable quit, Consumer<String> load, Runnable save, Runnable startOver,
                  Consumer<Runnable> grant) {
        super(ROOT, COMPONENTS);
        this.pick = pick;
        this.quit = quit;
        this.load = load;
        this.save = save;
        this.startOver = startOver;
        this.grant = grant;
    }

    @Override protected List<String> requiredElementIds() {
        return List.of("world-menu-root", "world-menu-quit");
    }

    @Override protected Map<String, Object> props() {
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (PlanetAPI world : PlayCompany.worlds()) {
            String id = "world-" + world.getId();
            Map<String, Object> row = new HashMap<>();
            row.put("id", id);
            row.put("globeId", id + "-globe");
            row.put("copyId", id + "-copy");
            row.put("nameId", id + "-name");
            row.put("placeId", id + "-place");
            row.put("workId", id + "-work");
            row.put("name", world.getName());
            row.put("place", world.getStarSystem().getName() + (world == PlayCompany.docked() ? " / docked here" : ""));
            row.put("work", work(world));
            row.put("action", (Runnable) () -> pick.accept(world));
            worlds.add(row);
        }
        Map<String, Object> props = new HashMap<>();
        props.put("worlds", worlds);
        props.put("quitAction", quit);
        props.put("saveAction", save);
        props.put("newAction", startOver);
        props.put("stockpile", PlayCompany.stockpile());
        props.put("creditsAction", (Runnable) () -> grant.accept(() -> PlayCompany.addCredits(GRANT)));
        props.put("marinesAction", (Runnable) () -> grant.accept(() -> PlayCompany.addMarines(GRANT)));
        List<Map<String, Object>> saves = new ArrayList<>();
        for (String name : PlaySaves.names()) {
            String id = "save-" + name;
            Map<String, Object> row = new HashMap<>();
            row.put("id", id);
            row.put("nameId", id + "-name");
            row.put("whenId", id + "-when");
            row.put("name", name);
            row.put("when", PlaySaves.when(name));
            row.put("action", (Runnable) () -> load.accept(name));
            saves.add(row);
        }
        props.put("saves", saves);
        return props;
    }

    /** Whoever has work on this world, asked as its mission board would ask. */
    private static String work(PlanetAPI world) {
        List<String> offers = new ArrayList<>();
        MarineOpsContext board = new MarineOpsContext(world);
        for (Client client : board.clients) {
            int contracts = board.getMissionsFor(client).size();
            if (contracts > 0) offers.add(client.displayName + ": " + contracts + (contracts == 1 ? " contract" : " contracts"));
        }
        return offers.isEmpty() ? "No work here now" : String.join("  /  ", offers);
    }

    @Override protected void onDocumentBuilt(MarkupInstance instance, UiDocument built) {
        for (PlanetAPI world : PlayCompany.worlds()) {
            built.canvases().set(instance.requireElement("world-" + world.getId() + "-globe"),
                    PlanetGlobeCanvas.forLocation(world.getName()));
        }
    }
}
