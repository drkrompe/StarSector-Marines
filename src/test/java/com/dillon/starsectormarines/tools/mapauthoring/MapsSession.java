package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapCatalog;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapJson;
import com.dillon.starsectormarines.battle.world.gen.OwnedMapRecipe;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.json.JSONObject;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Maps page's domain, shared by the workbench page and the headless tools so both open, build, check, draw and
 * save an owned map the same way ({@code owned-maps.md}). A map is built for a world by the mission's own recipe
 * ({@link OwnedMapRecipe}), so what the page draws is what the battle plays.
 */
public final class MapsSession implements AutoCloseable {

    /** The nature seed every editor build draws from: the owned-map look suite's, so the two show the same map. */
    public static final long SEED = 8101;

    /**
     * A map built for one world: the finished map (null when the recipe refused it), how each settlement adapted, and
     * every check that failed (empty when all pass).
     */
    public record Built(MapWorld world, MapResult map, Map<String, BiomeSkin.Adaptation> adaptations, List<String> problems) { }

    private final Path projectRoot;
    private final MapsRender render;
    private final HeadlessStrings strings;
    private RoadPlan installedRoads;
    private String installedRoadsPath;

    public MapsSession(Path projectRoot) throws Exception {
        this.projectRoot = projectRoot;
        DiskRegistries.installMapGeneration(projectRoot);
        strings = HeadlessBattles.install(projectRoot);
        render = new MapsRender(projectRoot.resolve("mod"));
    }

    /** The owned maps there are to open. */
    public static List<String> maps() { return OwnedMapCatalog.BUILTINS; }

    /** An owned map as it is saved, with the road document it names. */
    public MapDocument open(String id) {
        OwnedMap map = OwnedMapCatalog.map(id);
        return new MapDocument(map, map.roads() == null ? null : RoadCatalog.plan(map.roads()));
    }

    /** {@code doc} built for {@code world} by the mission's recipe and checked. A refused map comes back with no map. */
    public synchronized Built build(MapDocument doc, MapWorld world) {
        List<String> problems = new ArrayList<>();
        try {
            installRoads(doc);
        } catch (Exception e) {
            problems.add("The roads cannot be laid: " + e.getMessage());
            return new Built(world, null, Map.of(), problems);
        }
        OwnedMapRecipe.Built built;
        try {
            built = OwnedMapRecipe.build(doc.map(), world.profile(), SEED);
        } catch (RuntimeException e) {
            problems.add(sentence(e));
            return new Built(world, null, Map.of(), problems);
        }
        MapResult map;
        try {
            map = OwnedMapRecipe.result(built);
        } catch (RuntimeException e) {
            problems.add(sentence(e));
            map = new MapResult(built.context().grid, built.context().topology, doc.map().origin().x(), doc.map().origin().y(),
                    doc.map().objective().x(), doc.map().objective().y(), built.context().pois, built.context().doodads);
        }
        problems.addAll(MapChecks.run(map.grid, doc.map(), doc.roads()));
        return new Built(world, map, new LinkedHashMap<>(built.adaptations()), problems);
    }

    /** The recipe's refusal as a sentence: it names the map, the part and the cell. */
    private static String sentence(RuntimeException e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.endsWith(".") ? m : m + ".";
    }

    /** The recipe reads roads from the catalog, so an edited road document is installed in its place for the build. */
    private void installRoads(MapDocument doc) throws Exception {
        String path = doc.map().roads();
        if (path == null || doc.roads() == null) return;
        if (doc.roads().equals(installedRoads) && path.equals(installedRoadsPath)) return;
        RoadCatalog.validate(path, doc.roads());
        Path mod = projectRoot.resolve("mod");
        JSONObject edited = doc.roads().json();
        RoadCatalog.loadBuiltins(p -> p.equals(path) ? edited : new JSONObject(Files.readString(mod.resolve(p))));
        installedRoads = doc.roads();
        installedRoadsPath = path;
    }

    /** The built map as it plays, whole, in the look preset, and how its cells land on the picture. */
    public MapsRender.Frame frame(Built built) throws Exception {
        return render.whole(built.map(), built.world().profile().surface(), MapsRender.WIDTH, MapsRender.HEIGHT);
    }

    /** The picture with the page's marks and the check results over it. */
    public BufferedImage picture(Built built, MapDocument doc, String selected) throws Exception {
        MapsRender.Frame frame = frame(built);
        Graphics2D g = frame.image().createGraphics();
        MapsRender.mark(g, frame, doc, built, selected);
        MapsRender.problems(g, frame.image().getHeight(), built.problems());
        g.dispose();
        return frame.image();
    }

    /** The same map built for every world, side by side, each labelled with its drop style and adaptations. */
    public BufferedImage contactSheet(MapDocument doc) throws Exception {
        List<MapsRender.Tile> tiles = new ArrayList<>();
        for (MapWorld world : MapWorld.values()) {
            Built built = build(doc, world);
            BufferedImage image = built.map() == null ? new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB) : crop(frame(built));
            String drop = doc.map().drops().isEmpty() ? "no drop" : world.dropStyle(doc.map().drops().get(0).role());
            List<String> towns = new ArrayList<>();
            for (BiomeSkin.Adaptation a : built.adaptations().values()) if (!towns.contains(MapWorld.words(a))) towns.add(MapWorld.words(a));
            tiles.add(new MapsRender.Tile(world, image, drop, towns.isEmpty() ? "no settlement" : String.join(", ", towns),
                    built.problems().size()));
        }
        return MapsRender.contactSheet(doc.map().id() + " · the same map on every world", tiles);
    }

    /** Every world's check failures, each named by its world; empty when the map holds on all of them. */
    public List<String> checkEveryWorld(MapDocument doc) {
        List<String> out = new ArrayList<>();
        for (MapWorld world : MapWorld.values()) {
            for (String p : build(doc, world).problems()) out.add(world.label() + ": " + p);
        }
        return out;
    }

    /**
     * Saves {@code doc} under {@code root}'s mod folder, the map to {@code data/world/maps/<id>.map.json} and its roads
     * to the road document it names, each replaced atomically. Refused, writing nothing, while a check fails on any
     * world or a document would not read back as written.
     */
    public List<Path> save(MapDocument doc, Path root) throws Exception {
        List<String> problems = checkEveryWorld(doc);
        if (!problems.isEmpty()) throw new IllegalStateException("Not saved: " + problems.size() + " check(s) fail. " + problems.get(0));
        JSONObject map = OwnedMapJson.write(doc.map());
        if (!OwnedMapJson.read(new JSONObject(map.toString())).equals(doc.map())) {
            throw new IllegalStateException("Not saved: the map would not read back as it was written.");
        }
        String roadText = null;
        if (doc.roads() != null && doc.map().roads() != null) {
            RoadCatalog.validate(doc.map().roads(), doc.roads());
            JSONObject roads = doc.roads().json();
            if (!RoadPlan.read(new JSONObject(roads.toString())).equals(doc.roads())) {
                throw new IllegalStateException("Not saved: the roads would not read back as they were written.");
            }
            roadText = roads.toString(2);
        }
        List<Path> written = new ArrayList<>();
        Path mod = root.resolve("mod");
        written.add(write(mod.resolve(OwnedMapCatalog.path(doc.map().id())), map.toString(2)));
        if (roadText != null) written.add(write(mod.resolve(doc.map().roads()), roadText));
        return written;
    }

    private static Path write(Path target, String text) throws Exception {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "map-", ".tmp");
        try {
            Files.writeString(temp, text + "\n");
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    /** Just the map's own cells of a frame, copied so the tile does not hold the whole frame. */
    static BufferedImage crop(MapsRender.Frame f) {
        int x0 = Math.max(0, Math.round(f.sx(0))), y0 = Math.max(0, Math.round(f.sy(f.mapH())));
        int x1 = Math.min(f.image().getWidth(), Math.round(f.sx(f.mapW()))), y1 = Math.min(f.image().getHeight(), Math.round(f.sy(0)));
        BufferedImage view = f.image().getSubimage(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        BufferedImage out = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(view, 0, 0, null);
        g.dispose();
        return out;
    }

    @Override
    public void close() {
        if (strings != null) strings.close();
    }
}
