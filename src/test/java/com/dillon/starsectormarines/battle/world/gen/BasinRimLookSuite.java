package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A mock-up of a green basin in a desert world (Asharu, the owner's question): each Rav Bloom map generated twice
 * from one seed, on VERDANT and on ARID, and the arid map's wild ground and natural props spliced into the verdant
 * map's rim through a noisy elliptical basin, with a dithered band where the woods thin out. Cultivated fields are the
 * same on both palettes, so they stand in either. Only the drawn picture is spliced: the verdant map's walls, water
 * and nav stay as generated. Snapshot code; shipped mapgen is untouched. Opt-in.
 *
 * <p>{@code -Dsnapshot.basinRim.maps=breach,cordon-line,cordon-road,broad-front,the-burn} narrows it; each map prints
 * how much of it is rim and how many cells its two layouts disagree on.
 */
public final class BasinRimLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float PLAY_ZOOM_CELLS = 60f, SITE_CELLS = 120f;
    private static final String COLONY_FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    /** Width of the transition band in basin distance, and the noise that makes the basin's edge ragged. */
    private static final float BAND = 0.2f, RAGGED = 0.12f, NOISE_CELLS = 30f, PATCH_CELLS = 9f;
    private static final Set<GroundKind> WILD = EnumSet.of(GroundKind.GRASS, GroundKind.DIRT, GroundKind.SAND,
            GroundKind.STONE, GroundKind.RUBBLE, GroundKind.WATER, GroundKind.SNOW);

    @Override public String id() { return "basin-rim-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Rav Bloom maps as a green basin with an arid rim (mock-up)"; }

    /** Half-axes of the basin floor as shares of the map's half-width and half-height: the heart's is the whole valley. */
    private record Basin(float rx, float ry) { }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String maps = System.getProperty("snapshot.basinRim.maps", "breach,cordon-line,cordon-road,broad-front,the-burn");
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String map : maps.split(",")) {
                map = map.trim();
                long seed = map.startsWith("breach") || map.startsWith("cordon") ? 7117 : 202;
                Basin basin = map.startsWith("breach") ? new Basin(0.66f, 0.6f)
                        : map.startsWith("cordon") ? new Basin(0.74f, 0.68f) : new Basin(1.12f, 1.15f);
                try (BattleSimulation green = build(map, SurfacePalette.VERDANT, seed);
                     BattleSimulation dry = build(map, SurfacePalette.ARID, seed)) {
                    float[] rim = splice(green, dry, basin, seed);
                    shots(context, out, green, rim, "look/basin-" + map + "-seed" + seed + "-");
                }
            }
        }
        return out;
    }

    /**
     * Puts the arid map's wild ground and natural props on the green map wherever the basin says rim, and returns
     * each cell's rim share (0 floor, 1 rim) for framing.
     */
    private static float[] splice(BattleSimulation green, BattleSimulation dry, Basin basin, long seed) {
        CellTopology g = green.getTopology(), d = dry.getTopology();
        int w = g.getWidth(), h = g.getHeight();
        float[] rim = new float[w * h];
        int rimCells = 0, disagree = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float share = rimShare(x, y, w, h, basin, seed);
                rim[y * w + x] = share;
                if (g.isWall(x, y) != d.isWall(x, y)) disagree++;
                if (!rimHere(x, y, share, seed)) continue;
                rimCells++;
                GroundKind mine = g.getGroundKind(x, y), theirs = d.getGroundKind(x, y);
                if (!WILD.contains(mine) || !WILD.contains(theirs) || g.isWall(x, y)) continue;
                g.setGroundKind(x, y, theirs);
                g.setSurface(x, y, g.surfaceIndex(d.getSurfaceId(x, y)));
                // The ground's own scatter (tufts, ferns, shrubs) is the dry map's too.
                g.setNatureOverlayIndex(x, y, d.getNatureOverlayIndex(x, y));
            }
        }
        // Woods and stones: the green map's give way where the rim takes the cell, the dry map's stand there.
        green.getDoodads().removeIf(p -> natural(p) && rimHere(p.cellX, p.cellY, rim[clamp(p.cellY, h) * w + clamp(p.cellX, w)], seed));
        for (Doodad p : dry.getDoodads()) {
            if (!natural(p) || p.cellX < 0 || p.cellY < 0 || p.cellX >= w || p.cellY >= h) continue;
            if (rimHere(p.cellX, p.cellY, rim[p.cellY * w + p.cellX], seed) && !g.isWall(p.cellX, p.cellY)) green.getDoodads().add(p);
        }
        System.out.printf(Locale.ROOT, "basin-rim-look rim %.1f%% of %dx%d, layouts disagree on %d wall cells%n",
                100.0 * rimCells / (w * h), w, h, disagree);
        return rim;
    }

    private static int clamp(int v, int n) {
        return Math.max(0, Math.min(n - 1, v));
    }

    /** Trees, undergrowth and field stones: what a palette puts on wild ground. */
    private static boolean natural(Doodad p) {
        String sheet = p.sheetPath == null ? "" : p.sheetPath;
        return sheet.contains("forest") || sheet.contains("rock") || sheet.contains("nature") || sheet.contains("shrub")
                || sheet.contains("stone") || sheet.contains("grass");
    }

    /** How far into the rim a cell is: 0 on the floor, 1 past the band, ragged by a slow noise. */
    private static float rimShare(int x, int y, int w, int h, Basin basin, long seed) {
        float nx = (x + 0.5f - w / 2f) / (basin.rx() * w / 2f), ny = (y + 0.5f - h / 2f) / (basin.ry() * h / 2f);
        float dist = (float) Math.sqrt(nx * nx + ny * ny) + RAGGED * (2f * noise(x / NOISE_CELLS, y / NOISE_CELLS, seed) - 1f);
        return Math.max(0f, Math.min(1f, (dist - (1f - BAND / 2f)) / BAND));
    }

    /** Whether a cell is rim: in the band, patches of rim grow from the outside in. */
    private static boolean rimHere(int x, int y, float share, long seed) {
        if (share <= 0f) return false;
        if (share >= 1f) return true;
        return noise(x / PATCH_CELLS, y / PATCH_CELLS, seed ^ 0x5A5A5A5AL) < share;
    }

    /** Smooth value noise in [0, 1]. */
    private static float noise(float x, float y, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = lattice(x0, y0, seed), b = lattice(x0 + 1, y0, seed);
        float c = lattice(x0, y0 + 1, seed), e = lattice(x0 + 1, y0 + 1, seed);
        return (a + (b - a) * fx) + ((c + (e - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float lattice(int x, int y, long seed) {
        long z = seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1L << 24);
    }

    private static void shots(SnapshotContext context, List<SnapshotArtifact> out, BattleSimulation sim, float[] rim,
                              String tag) throws Exception {
        int w = sim.getTopology().getWidth(), h = sim.getTopology().getHeight();
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                w / 2f, h / 2f, w);
        out.add(new SnapshotArtifact(tag + "01-whole-map.png", look.render(sim, "")));
        int[] band = mostBand(sim.getTopology(), rim, (int) SITE_CELLS / 2);
        if (band == null) return;
        look.refocus(band[0], band[1], SITE_CELLS);
        out.add(new SnapshotArtifact(tag + "02-transition-site.png", look.render(sim, "")));
        look.refocus(band[0], band[1], PLAY_ZOOM_CELLS);
        out.add(new SnapshotArtifact(tag + "03-transition-play-zoom.png", look.render(sim, "")));
    }

    /** The middle of the square with the most open ground inside the band, or null with none. */
    private static int[] mostBand(CellTopology topology, float[] rim, int size) {
        int w = topology.getWidth(), h = topology.getHeight(), step = size / 4, best = 0;
        int[] at = null;
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size <= h; y0 += step) {
                int n = 0;
                for (int x = x0; x < x0 + size; x++) {
                    for (int y = y0; y < y0 + size; y++) {
                        float s = rim[y * w + x];
                        if (s > 0.15f && s < 0.85f && WILD.contains(topology.getGroundKind(x, y))) n++;
                    }
                }
                if (n > best) {
                    best = n;
                    at = new int[]{x0 + size / 2, y0 + size / 2};
                }
            }
        }
        return at;
    }

    /**
     * The map on one palette. Pooled maps are picked per palette, so both palettes take the verdant pool's entry
     * where there is one, to share a layout.
     */
    private static BattleSimulation build(String map, SurfacePalette palette, long seed) throws Exception {
        return switch (map) {
            case "breach" -> colony(palette, seed);
            case "cordon-line", "cordon-road" -> {
                CordonVariant variant = map.endsWith("line") ? CordonVariant.LINE : CordonVariant.ROAD;
                StoryMapPool.Entry entry = StoryMapPools.pool(CordonSetup.storyMap(variant))
                        .entry(SurfacePalette.VERDANT, seed);
                CordonSetup.CordonMap cordon = entry == null ? CordonSetup.rolled(variant, CordonPlan.Density.RURAL, palette, seed)
                        : CordonSetup.map(variant, palette, entry);
                yield CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD);
            }
            case "broad-front", "the-burn" -> {
                Form form = map.equals("the-burn") ? Form.THE_BURN : Form.BROAD_FRONT;
                StoryMapPool.Entry entry = StoryMapPools.pool(BloomHeartSetup.storyMap(form, Density.RURAL))
                        .entry(SurfacePalette.VERDANT, seed);
                yield (entry == null ? BloomHeartSetup.country(form, palette, seed)
                        : BloomHeartSetup.country(form, palette, entry)).sim();
            }
            default -> throw new IllegalArgumentException("Unknown map " + map);
        };
    }

    private static BattleSimulation colony(SurfacePalette palette, long seed) throws Exception {
        String json;
        try (InputStream stream = BasinRimLookSuite.class.getResourceAsStream(COLONY_FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + COLONY_FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONObject object = new JSONObject(json);
        object.put("seed", seed);
        object.getJSONObject("targetProfile").put("surface", palette.name());
        BattleFixture parsed = BattleFixtureJson.fromJson(object);
        return (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
    }
}
