package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The colony's farmland and its roads, as generated: Colony Breach's map at
 * tick zero on the canonical seed and two others, whole map, the country band,
 * and play-zoom views of the fields, of the town's edge where a road leaves it,
 * and of a stretch of country away from the landing (`precincts.md`, open-field
 * landing; `mapgen-nouns.md`, rural ground). Look evidence, 1920x1080.
 *
 * <p>{@code -Dsnapshot.ruralMapgen.seeds=7117,3,11} picks the seeds.
 */
public final class RuralMapgenSnapshotSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;

    @Override public String id() { return "rural-mapgen"; }

    @Override public String label() { return "Rural mapgen: colony farmland, country roads and plots"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String token : System.getProperty("snapshot.ruralMapgen.seeds", "7117,3,11").split(",")) {
            long seed = Long.parseLong(token.trim());
            String tag = "seed" + seed + "-";
            try (BattleSimulation sim = colony(seed)) {
                Country country = Country.of(sim);
                out.add(new SnapshotArtifact(tag + "01-whole-map.png",
                        whole.render(sim, "colony seed " + seed + ", whole map")));
                near.refocus(sim.getGrid().getWidth() / 2f, country.bandMidY(), 220f);
                out.add(new SnapshotArtifact(tag + "02-country-band.png",
                        near.render(sim, "seed " + seed + ", the country band, 220 cells across")));
                near.refocus(country.fieldX + 0.5f, country.fieldY + 0.5f, PLAY_ZOOM_CELLS);
                out.add(new SnapshotArtifact(tag + "03-play-zoom-field.png",
                        near.render(sim, "seed " + seed + ", the landing field at play zoom, 32 px a cell")));
                int[] exit = country.roadLeavingTown(sim.getTopology());
                near.refocus(exit[0] + 0.5f, exit[1] + 0.5f, PLAY_ZOOM_CELLS);
                out.add(new SnapshotArtifact(tag + "04-play-zoom-town-edge.png",
                        near.render(sim, "seed " + seed + ", where a road leaves town, play zoom")));
                int[] away = country.awayFromField(sim.getGrid().getWidth());
                near.refocus(away[0] + 0.5f, away[1] + 0.5f, PLAY_ZOOM_CELLS);
                out.add(new SnapshotArtifact(tag + "05-play-zoom-country.png",
                        near.render(sim, "seed " + seed + ", open country away from the landing, play zoom")));
                int[] farm = country.farmstead(sim.getTopology());
                if (farm != null) {
                    near.refocus(farm[0] + 0.5f, farm[1] + 0.5f, PLAY_ZOOM_CELLS);
                    out.add(new SnapshotArtifact(tag + "06-play-zoom-farmstead.png",
                            near.render(sim, "seed " + seed + ", a farmstead on its track, play zoom")));
                }
            }
        }
        return out;
    }

    /** Where the field lies and which edge its country runs along, read off the pads. */
    private record Country(int fieldX, int fieldY, boolean north, int depth, int height) {
        static Country of(BattleSimulation sim) {
            List<LandingPad> pads = sim.getMarineLandingPads();
            int sx = 0, sy = 0;
            for (LandingPad pad : pads) { sx += pad.centerX; sy += pad.centerY; }
            int n = Math.max(1, pads.size());
            int height = sim.getGrid().getHeight();
            int fx = sx / n, fy = sy / n;
            boolean north = fy < height / 2;
            int edgeDistance = north ? fy : height - 1 - fy;
            // The field's country: the disc's own depth, its radius and the margin.
            return new Country(fx, fy, north, edgeDistance + 40 + 8, height);
        }

        float bandMidY() { return north ? depth / 2f : height - depth / 2f; }

        /** A street cell on the town's side of the country edge, nearest the field's column. */
        int[] roadLeavingTown(CellTopology topology) {
            int y = north ? depth : height - 1 - depth;
            int best = -1;
            for (int x = 0; x < topology.getWidth(); x++) {
                GroundKind kind = topology.getGroundKind(x, y);
                if (kind != GroundKind.STREET) continue;
                if (best < 0 || Math.abs(x - fieldX) < Math.abs(best - fieldX)) best = x;
            }
            return new int[]{best < 0 ? fieldX : best, y};
        }

        /** The middle of the first building standing in the country, or {@code null}. */
        int[] farmstead(CellTopology topology) {
            int lo = north ? 0 : height - depth, hi = north ? depth - 1 : height - 1;
            int x0 = Integer.MAX_VALUE, x1 = -1, y0 = 0, y1 = 0;
            for (int x = 0; x < topology.getWidth(); x++) {
                for (int y = lo; y <= hi; y++) {
                    if (!topology.isWall(x, y)) continue;
                    if (x1 >= 0 && x > x1 + 40) return new int[]{(x0 + x1) / 2, (y0 + y1) / 2};
                    if (x < x0) { x0 = x; y0 = y; }
                    x1 = x; y1 = y;
                }
            }
            return x1 < 0 ? null : new int[]{(x0 + x1) / 2, (y0 + y1) / 2};
        }

        /** The country half way between field and town, well along the edge from the field. */
        int[] awayFromField(int width) {
            int x = fieldX < width / 2 ? Math.min(width - 40, fieldX + 110) : Math.max(40, fieldX - 110);
            return new int[]{x, (int) bandMidY()};
        }
    }

    private static BattleSimulation colony(long seed) throws Exception {
        String json;
        try (InputStream stream = RuralMapgenSnapshotSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONObject object = new JSONObject(json);
        object.put("seed", seed);
        BattleFixture parsed = BattleFixtureJson.fromJson(object);
        return (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
    }
}
