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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Maps page's domain, shared by the workbench page and the headless tools: a story map's one owned seed
 * ({@code story-map-pools.md}), generated on its home palette with its patch, drawn as any world, checked by the
 * mission's own acceptance ({@link StoryMapAcceptance}) and saved back into its pool file. A world is drawn the way
 * the campaign builds it: the mission's setup lays the map out on the home palette and the planet skin re-dresses it
 * ({@code PlanetSkin}). The page edits the entry's patch (pins, ruin strokes, placed structures); a Colony Breach
 * patch's lift, bores and holdouts are kept and shown as they are.
 */
public final class StoryMapsSession implements AutoCloseable {

    /** A story map's pool as saved: its home palette and its owned entry. */
    public record Opened(StoryMap map, SurfacePalette home, StoryMapPool.Entry entry) { }

    /** A plan place a pin can move: its name and where it grew from. */
    public record Place(String name, int x, int y) { }

    /** A structure the layout laid, by name, with its two corners: what a placement can name. */
    public record LaidStructure(String name, int x0, int y0, int x1, int y1) { }

    /**
     * The entry generated for one world: its battle (null when the setup refused the map, {@code failure} saying why),
     * the places a pin can move and the structures a placement can name.
     */
    public record Stood(StoryMapPool.Entry entry, SurfacePalette world, BattleSimulation sim, List<Place> places,
                        List<LaidStructure> structures, String failure) { }

    private final Path projectRoot;
    private final MapsRender render;
    private final HeadlessStrings strings;
    private Stood stood;
    /** Frames of the standing entry on worlds other than home, each generated once. */
    private final Map<SurfacePalette, MapsRender.Frame> frames = new EnumMap<>(SurfacePalette.class);

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

    /** Every world a map is drawn as: each world palette the campaign skins a pooled map for. */
    public static List<SurfacePalette> worlds() {
        return List.of(SurfacePalette.values());
    }

    /** The map's pool as saved on disk under the project. */
    public Opened open(String id) throws Exception {
        StoryMap map = mapOf(id);
        StoryMapPool pool = StoryMapPool.read(map, new JSONObject(Files.readString(poolFile(projectRoot, map))));
        return new Opened(map, pool.home(), pool.owned());
    }

    /** The entry with {@code patch} in place of its own. */
    public static StoryMapPool.Entry withPatch(StoryMapPool.Entry e, MapPatch patch) {
        return new StoryMapPool.Entry(e.seed(), e.note(), patch, e.story());
    }

    /** Generates {@code entry} on its home palette, unless it is the entry already standing. */
    public synchronized Stood stand(Opened o, StoryMapPool.Entry entry) {
        if (stood != null && stood.entry().equals(entry)) return stood;
        dropStood();
        stood = generate(o.map(), entry, o.home());
        return stood;
    }

    /** The map as the campaign builds it for a world of {@code world}: laid out on home, skinned for the world. */
    private static Stood generate(StoryMap map, StoryMapPool.Entry entry, SurfacePalette world) {
        try {
            if (RaidOwnedMap.isRaid(map)) {
                RaidOwnedMap.Ground ground = RaidOwnedMap.Ground.of(map, StoryMapPools.pool(map).layoutFor(world));
                return new Stood(entry, world, BattleSetup.createPooledRaid(ground, world, entry.seed(),
                        map != StoryMap.RAID, List.of()), List.of(), List.of(), null);
            }
            if (map == StoryMap.COLONY_BREACH) {
                return new Stood(entry, world, BattleSetup.createPooledColonyBreach(world, entry, List.of()), List.of(), List.of(), null);
            }
            if (BloomHeartSetup.formOf(map) != null) {
                BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), world,
                        BloomHeartSetup.densityOf(map), entry);
                return new Stood(entry, world, built.sim(), places(built.plan()), structures(built.map().layout), null);
            }
            CordonVariant variant = CordonSetup.variantOf(map);
            CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), world, entry);
            BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD);
            return new Stood(entry, world, sim, places(cordon.plan()), structures(cordon.map().layout), null);
        } catch (IllegalStateException | IllegalArgumentException refused) {
            return new Stood(entry, world, null, List.of(), List.of(), sentence(refused));
        }
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

    /**
     * The entry drawn whole as {@code world}, 1920x1080: the standing home map itself, or the entry built again for
     * that world as the campaign builds it (once per entry). Null when the map was refused.
     */
    public synchronized MapsRender.Frame frame(Opened o, StoryMapPool.Entry entry, SurfacePalette world) throws Exception {
        Stood home = stand(o, entry);
        if (home.sim() == null) return null;
        if (world == o.home()) return render.whole(home.sim(), MapsRender.WIDTH, MapsRender.HEIGHT);
        MapsRender.Frame cached = frames.get(world);
        if (cached != null) return cached;
        Stood dressed = generate(o.map(), entry, world);
        if (dressed.sim() == null) throw new IllegalStateException(dressed.failure());
        try (BattleSimulation sim = dressed.sim()) {
            MapsRender.Frame f = render.whole(sim, MapsRender.WIDTH, MapsRender.HEIGHT);
            frames.put(world, f);
            return f;
        }
    }

    /**
     * Every check {@code entry} must pass before it is saved: its patch inside the map, and the mission's acceptance
     * on the home palette ({@link StoryMapAcceptance}, the regeneration check's own). Empty when all pass.
     */
    public List<String> check(Opened o, StoryMapPool.Entry entry) {
        List<String> problems = new ArrayList<>();
        Stood s = stand(o, entry);
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
            StoryMapAcceptance.check(o.map(), o.home(), entry);
        } catch (Exception | AssertionError e) {
            problems.add(sentence(e));
        }
        return problems;
    }

    private static void outside(List<String> problems, String what, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) problems.add(what + " at " + x + "," + y + " is off the " + w + "x" + h + " map.");
    }

    /**
     * Saves {@code entry} as {@code o}'s owned entry in its pool file under {@code root}/mod, after every check
     * passes: the file is written in the pool files' own form (so only the edited entry's lines change), read back to
     * the same pool, then replaced atomically.
     */
    public Path save(Opened o, StoryMapPool.Entry entry, Path root) throws Exception {
        List<String> problems = check(o, entry);
        if (!problems.isEmpty()) throw new IllegalStateException("Not saved: " + String.join(" ", problems));
        Path target = poolFile(root, o.map());
        String out = PoolEntryText.file(o.map(), o.home(), entry);
        StoryMapPool back = StoryMapPool.read(o.map(), new JSONObject(out));
        if (back.home() != o.home() || !back.owned().equals(entry)) {
            throw new IllegalStateException("Not saved: the pool file does not read back as the page holds it.");
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), o.map().id + "-", ".pool.json.tmp");
        Files.writeString(temp, out);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** The entry as every world, side by side. */
    public BufferedImage contactSheet(Opened o, StoryMapPool.Entry entry) throws Exception {
        Stood s = stand(o, entry);
        if (s.sim() == null) throw new IllegalStateException(s.failure());
        List<MapsRender.Tile> tiles = new ArrayList<>();
        for (SurfacePalette world : worlds()) {
            MapsRender.Frame f = frame(o, entry, world);
            tiles.add(new MapsRender.Tile(label(world), f.image(),
                    world == o.home() ? "the home layout" : "laid out on " + label(o.home()).toLowerCase(Locale.ROOT) + ", skinned",
                    "settlements: " + habitat(world)));
        }
        return MapsRender.contactSheet(o.map().id + " · seed " + entry.seed() + " · one map, every world", tiles);
    }

    public static String label(SurfacePalette p) {
        String n = p.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** How a world of {@code p} builds its settlements, as the campaign reads a world of that palette. */
    static String habitat(SurfacePalette p) {
        return switch (BiomeSkin.of(BloomHeartSetup.world(p)).habitat()) {
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
        frames.clear();
    }

    @Override
    public synchronized void close() {
        dropStood();
        strings.close();
    }
}
