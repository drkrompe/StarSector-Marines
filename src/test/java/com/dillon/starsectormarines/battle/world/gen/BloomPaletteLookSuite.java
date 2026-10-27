package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
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
import java.util.List;
import java.util.Locale;

/**
 * The Rav Bloom maps on one world's palette, through the look preset: Colony Breach, the Cordon's Line and Road, and
 * the heart's Broad Front and The Burn, each whole, then its densest farmland at site and play zoom, and the town's
 * edge where buildings meet fields, and where a desert basin's rim grows dry scrub, its thickest stand. For the owner judging a world's look (Asharu, arid with poor farmland). Opt-in.
 *
 * <p>{@code -Dsnapshot.bloomPalette.palette=ARID} (the default) picks the palette;
 * {@code -Dsnapshot.bloomPalette.maps=breach,cordon-line,cordon-road,broad-front,the-burn} and
 * {@code -Dsnapshot.bloomPalette.seedCount=2} (how many of each map's seeds, 7117 and 3 for the colony and the
 * Cordon, 101 and 202 for the heart) narrow it. Each map prints its share of cultivated ground (grass and turned
 * earth) against the wild surface.
 */
public final class BloomPaletteLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float PLAY_ZOOM_CELLS = 60f, SITE_CELLS = 120f;
    private static final String COLONY_FIXTURE = "/battle-fixtures/colony-breach-v1.json";

    @Override public String id() { return "bloom-palette-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Rav Bloom maps on one palette: whole, farmland and town edge"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SurfacePalette palette = SurfacePalette.valueOf(
                System.getProperty("snapshot.bloomPalette.palette", "ARID").trim().toUpperCase(Locale.ROOT));
        String maps = System.getProperty("snapshot.bloomPalette.maps", "breach,cordon-line,cordon-road,broad-front,the-burn");
        int seedCount = Integer.getInteger("snapshot.bloomPalette.seedCount", 2);
        List<SnapshotArtifact> out = new ArrayList<>();
        String tone = palette.name().toLowerCase(Locale.ROOT);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String map : maps.split(",")) {
                map = map.trim();
                long[] seeds = switch (map) {
                    case "breach", "cordon-line", "cordon-road" -> new long[]{7117, 3};
                    default -> new long[]{101, 202};
                };
                for (int s = 0; s < Math.min(seedCount, seeds.length); s++) {
                    long seed = seeds[s];
                    try (BattleSimulation sim = build(map, palette, seed)) {
                        shots(context, out, sim, "look/" + tone + "-" + map + "-seed" + seed + "-");
                    }
                }
            }
        }
        return out;
    }

    private static BattleSimulation build(String map, SurfacePalette palette, long seed) throws Exception {
        return switch (map) {
            case "breach" -> colony(palette, seed);
            case "cordon-line", "cordon-road" -> {
                CordonVariant variant = map.endsWith("line") ? CordonVariant.LINE : CordonVariant.ROAD;
                yield CordonSetup.create(CordonSetup.map(variant, palette, seed), variant, List.of(),
                        CordonSequence.Timetable.STANDARD);
            }
            case "broad-front" -> BloomHeartSetup.country(Form.BROAD_FRONT, palette, seed).sim();
            case "the-burn" -> BloomHeartSetup.country(Form.THE_BURN, palette, seed).sim();
            default -> throw new IllegalArgumentException("Unknown map " + map);
        };
    }

    private static void shots(SnapshotContext context, List<SnapshotArtifact> out, BattleSimulation sim, String tag)
            throws Exception {
        CellTopology topology = sim.getTopology();
        int w = topology.getWidth(), h = topology.getHeight();
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                w / 2f, h / 2f, w);
        out.add(new SnapshotArtifact(tag + "01-whole-map.png", look.render(sim, "")));
        int[] farm = densest(topology, (int) PLAY_ZOOM_CELLS, false);
        if (farm != null) {
            look.refocus(farm[0], farm[1], SITE_CELLS);
            out.add(new SnapshotArtifact(tag + "02-farmland-site.png", look.render(sim, "")));
            look.refocus(farm[0], farm[1], PLAY_ZOOM_CELLS);
            out.add(new SnapshotArtifact(tag + "03-farmland-play-zoom.png", look.render(sim, "")));
        }
        int[] edge = densest(topology, (int) PLAY_ZOOM_CELLS, true);
        if (edge != null) {
            look.refocus(edge[0], edge[1], SITE_CELLS);
            out.add(new SnapshotArtifact(tag + "04-town-edge.png", look.render(sim, "")));
        }
        int[] scrub = densestScrub(sim, (int) PLAY_ZOOM_CELLS);
        if (scrub != null) {
            look.refocus(scrub[0], scrub[1], PLAY_ZOOM_CELLS);
            out.add(new SnapshotArtifact(tag + "05-rim-scrub.png", look.render(sim, "")));
        }
        int cultivated = 0;
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) if (cultivated(topology.getGroundKind(x, y))) cultivated++;
        System.out.printf(Locale.ROOT, "bloom-palette-look %s cultivated %d of %d cells (%.1f%%), farmland %s, edge %s%n",
                tag, cultivated, w * h, 100.0 * cultivated / (w * h),
                farm == null ? "none" : farm[0] + "," + farm[1], edge == null ? "none" : edge[0] + "," + edge[1]);
    }

    /** The middle of the {@code size}-cell square holding the most dry-country scrub, or null where there is none. */
    private static int[] densestScrub(BattleSimulation sim, int size) {
        CellTopology topology = sim.getTopology();
        int w = topology.getWidth(), h = topology.getHeight(), step = size / 4;
        int best = 0;
        int[] at = null;
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size <= h; y0 += step) {
                int count = 0;
                for (Doodad d : sim.getDoodads()) {
                    String id = d.definitionId();
                    if (id != null && id.endsWith(NaturalPropShape.DRY) && d.cellX >= x0 && d.cellX < x0 + size
                            && d.cellY >= y0 && d.cellY < y0 + size) count++;
                }
                if (count > best) {
                    best = count;
                    at = new int[]{x0 + size / 2, y0 + size / 2};
                }
            }
        }
        return at;
    }

    /** Grass or turned earth: what a field is laid as on every world (`RuralGround`). */
    private static boolean cultivated(GroundKind kind) {
        return kind == GroundKind.GRASS || kind == GroundKind.DIRT;
    }

    private static boolean built(CellTopology topology, int x, int y) {
        GroundKind kind = topology.getGroundKind(x, y);
        return topology.isWall(x, y) || kind == GroundKind.STREET || kind == GroundKind.SIDEWALK;
    }

    /**
     * The middle of the {@code size}-cell square with the most cultivated ground, or, for {@code edge}, the most of
     * both cultivated and built ground (the smaller of the two counts); null where the map has none.
     */
    private static int[] densest(CellTopology topology, int size, boolean edge) {
        int w = topology.getWidth(), h = topology.getHeight(), step = size / 4;
        int best = 0;
        int[] at = null;
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size <= h; y0 += step) {
                int fields = 0, town = 0;
                for (int x = x0; x < x0 + size; x++) {
                    for (int y = y0; y < y0 + size; y++) {
                        if (cultivated(topology.getGroundKind(x, y))) fields++;
                        else if (edge && built(topology, x, y)) town++;
                    }
                }
                int score = edge ? Math.min(fields, town) : fields;
                if (score > best) {
                    best = score;
                    at = new int[]{x0 + size / 2, y0 + size / 2};
                }
            }
        }
        return at;
    }

    private static BattleSimulation colony(SurfacePalette palette, long seed) throws Exception {
        String json;
        try (InputStream stream = BloomPaletteLookSuite.class.getResourceAsStream(COLONY_FIXTURE)) {
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
