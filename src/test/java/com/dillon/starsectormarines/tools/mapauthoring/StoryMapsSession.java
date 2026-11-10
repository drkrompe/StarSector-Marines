package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidOwnedMap;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid.Kind;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapAcceptance;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Maps page's domain, shared by the workbench page and the headless tools: a story map's one owned seed
 * ({@code story-map-pools.md}), generated once on its home biome with its patch, drawn as every world, checked by the
 * mission's own acceptance ({@link StoryMapAcceptance}) and saved back into its pool file. The page edits the home
 * entry's patch (pins, ruin strokes, placed structures); a Colony Breach patch's lift, bores and holdouts are kept and
 * shown as they are.
 */
public final class StoryMapsSession implements AutoCloseable {

    /** The biome a story map's owned seed is laid out on and every other world is dressed from (owner: ARID). */
    public static final SurfacePalette HOME = SurfacePalette.ARID;

    /** A story map's home entry: its place among the home biome's entries and the entry as it is saved. */
    public record Opened(StoryMap map, int index, StoryMapPool.Entry entry) { }

    /** A plan place a pin can move: its name and where it grew from. */
    public record Place(String name, int x, int y) { }

    /** A structure the layout laid, by name, with its two corners: what a placement can name. */
    public record LaidStructure(String name, int x0, int y0, int x1, int y1) { }

    /**
     * The entry generated on its home biome: its battle (null when the setup refused the map, {@code failure} saying
     * why), the places a pin can move and the structures a placement can name.
     */
    public record Stood(StoryMapPool.Entry entry, BattleSimulation sim, List<Place> places, List<LaidStructure> structures,
                        String failure) { }

    private final Path projectRoot;
    private final MapsRender render;
    private final HeadlessStrings strings;
    private Stood stood;
    private MapReskin reskin;

    public StoryMapsSession(Path projectRoot) throws Exception {
        this.projectRoot = projectRoot;
        DiskRegistries.installMapGeneration(projectRoot);
        TileRegistryTestInstaller.install(projectRoot);
        strings = HeadlessBattles.install(projectRoot);
        render = new MapsRender(projectRoot.resolve("mod"));
    }

    /** Every story map, by pool id. */
    public static List<String> maps() {
        List<String> out = new ArrayList<>();
        for (StoryMap m : StoryMap.values()) out.add(m.id);
        return out;
    }

    public static StoryMap mapOf(String id) {
        for (StoryMap m : StoryMap.values()) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException("no story map " + id + "; known: " + maps());
    }

    /** The map's home entry as saved: the home biome's story entry, else its first. */
    public Opened open(String id) throws Exception {
        StoryMap map = mapOf(id);
        StoryMapPool pool = StoryMapPool.read(map, new JSONObject(Files.readString(poolFile(projectRoot, map))));
        List<StoryMapPool.Entry> entries = pool.entries(HOME);
        int index = 0;
        for (int k = 0; k < entries.size(); k++) if (entries.get(k).story()) index = k;
        return new Opened(map, index, entries.get(index));
    }

    /** The entry with {@code patch} in place of its own. */
    public static StoryMapPool.Entry withPatch(StoryMapPool.Entry e, MapPatch patch) {
        return new StoryMapPool.Entry(e.seed(), e.note(), patch, e.story());
    }

    /** Generates {@code entry} on the home biome, unless it is the entry already standing. */
    public synchronized Stood stand(StoryMap map, StoryMapPool.Entry entry) {
        if (stood != null && stood.entry().equals(entry)) return stood;
        dropStood();
        try {
            stood = generate(map, entry);
            reskin = new MapReskin(stood.sim(), HOME);
        } catch (IllegalStateException | IllegalArgumentException refused) {
            stood = new Stood(entry, null, List.of(), List.of(), sentence(refused));
        }
        return stood;
    }

    private static Stood generate(StoryMap map, StoryMapPool.Entry entry) {
        if (RaidOwnedMap.isRaid(map)) {
            return new Stood(entry, BattleSetup.createPooledRaid(RaidOwnedMap.Ground.of(map, HOME), HOME, entry.seed(),
                    map != StoryMap.RAID, List.of()), List.of(), List.of(), null);
        }
        if (map == StoryMap.COLONY_BREACH) {
            return new Stood(entry, BattleSetup.createPooledColonyBreach(HOME, entry, List.of()), List.of(), List.of(), null);
        }
        if (BloomHeartSetup.formOf(map) != null) {
            BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), HOME,
                    BloomHeartSetup.densityOf(map), entry);
            return new Stood(entry, built.sim(), places(built.plan()), structures(built.map().layout), null);
        }
        CordonVariant variant = CordonSetup.variantOf(map);
        CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), HOME, entry);
        BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD);
        return new Stood(entry, sim, places(cordon.plan()), structures(cordon.map().layout), null);
    }

    private static List<Place> places(PrecinctPlan plan) {
        List<Place> out = new ArrayList<>();
        for (Precinct p : plan.precincts()) out.add(new Place(p.name(), p.seedX(), p.seedY()));
        return out;
    }

    private static List<LaidStructure> structures(LaidLayout layout) {
        List<LaidStructure> out = new ArrayList<>();
        if (layout == null) return out;
        for (Laid l : layout.pieces()) {
            if (l.kind() != Kind.STRUCTURE || !l.isLaid() || l.cells().size() < 2) continue;
            int[] a = l.cells().get(0), b = l.cells().get(1);
            out.add(new LaidStructure(l.name(), Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])));
        }
        return out;
    }

    /** The standing map dressed as {@code world} and drawn whole; null when the map was refused. */
    public synchronized MapsRender.Frame frame(MapWorld world, int width, int height) throws Exception {
        if (stood == null || stood.sim() == null) return null;
        reskin.apply(world);
        try {
            return render.whole(stood.sim(), width, height);
        } finally {
            reskin.restore();
        }
    }

    /**
     * Every check {@code entry} must pass before it is saved: its patch inside the map, and the mission's acceptance
     * on the home biome ({@link StoryMapAcceptance}, the regeneration check's own). Empty when all pass.
     */
    public List<String> check(StoryMap map, StoryMapPool.Entry entry) {
        List<String> problems = new ArrayList<>();
        Stood s = stand(map, entry);
        if (s.sim() == null) {
            problems.add(s.failure());
            return problems;
        }
        int w = s.sim().getGrid().getWidth(), h = s.sim().getGrid().getHeight();
        MapPatch p = entry.patch();
        for (MapPatch.Pin pin : p.pins()) outside(problems, "Pin " + pin.place(), pin.x(), pin.y(), w, h);
        for (int k = 0; k < p.ruin().size(); k++) {
            for (DamagePlan.Point pt : p.ruin().get(k).points()) outside(problems, "Ruin stroke " + (k + 1), (int) pt.x(), (int) pt.y(), w, h);
        }
        for (Structure st : p.place()) outside(problems, "Placed " + st.name(), st.x(), st.y(), w, h);
        if (!problems.isEmpty()) return problems;
        try {
            StoryMapAcceptance.check(map, HOME, entry);
        } catch (Exception | AssertionError e) {
            problems.add(sentence(e));
        }
        return problems;
    }

    private static void outside(List<String> problems, String what, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) problems.add(what + " at " + x + "," + y + " is off the " + w + "x" + h + " map.");
    }

    /**
     * Saves {@code entry} as the home entry of {@code opened}'s pool file under {@code root}/mod, after every check
     * passes: only that entry's text changes, the file is read back to the same entry, then replaced atomically.
     */
    public Path save(Opened opened, StoryMapPool.Entry entry, Path root) throws Exception {
        List<String> problems = check(opened.map(), entry);
        if (!problems.isEmpty()) throw new IllegalStateException("Not saved: " + String.join(" ", problems));
        Path source = poolFile(projectRoot, opened.map()), target = poolFile(root, opened.map());
        String text = Files.readString(Files.exists(target) ? target : source);
        String out = PoolEntryText.replace(text, HOME, opened.index(), PoolEntryText.entry(entry));
        StoryMapPool.Entry back = StoryMapPool.read(opened.map(), new JSONObject(out)).entries(HOME).get(opened.index());
        if (!back.equals(entry)) throw new IllegalStateException("Not saved: the pool file does not read back as the page holds it.");
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), opened.map().id + "-", ".pool.json.tmp");
        Files.writeString(temp, out);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** The standing map as every world, side by side. */
    public BufferedImage contactSheet(StoryMap map, StoryMapPool.Entry entry) throws Exception {
        Stood s = stand(map, entry);
        if (s.sim() == null) throw new IllegalStateException(s.failure());
        List<MapsRender.Tile> tiles = new ArrayList<>();
        for (MapWorld world : MapWorld.values()) {
            MapsRender.Frame f = frame(world, MapsRender.WIDTH, MapsRender.HEIGHT);
            BiomeSkin skin = world.skin();
            tiles.add(new MapsRender.Tile(world, f.image(),
                    "ground: " + skin.palette().name().toLowerCase(Locale.ROOT),
                    "settlements: " + habitat(skin)));
        }
        return MapsRender.contactSheet(map.id + " · seed " + entry.seed() + " (laid out on " + HOME.name().toLowerCase(Locale.ROOT)
                + ") · the same map on every world", tiles);
    }

    static String habitat(BiomeSkin skin) {
        return switch (skin.habitat()) {
            case OPEN -> "open";
            case INSULATED -> "insulated";
            case SEALED -> "sealed perimeter";
            case SHIELDED -> "shielded";
        };
    }

    public static Path poolFile(Path root, StoryMap map) { return root.resolve("mod").resolve(map.path()); }

    static String sentence(Throwable e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return Character.toUpperCase(m.charAt(0)) + m.substring(1) + (m.endsWith(".") ? "" : ".");
    }

    private void dropStood() {
        if (stood != null && stood.sim() != null) stood.sim().close();
        stood = null;
        reskin = null;
    }

    @Override
    public synchronized void close() {
        dropStood();
        strings.close();
    }
}
