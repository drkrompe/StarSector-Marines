package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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
 * City street junctions as generated: on Colony Breach's town, every place where
 * the finished asphalt runs off in three or four directions, framed close
 * (`mapgen-nouns.md`, City streets). Look evidence for how a junction's
 * pavement is joined, 1920x1080.
 *
 * <p>{@code -Dsnapshot.cityJunctions.seeds=7117,3} picks the colony seeds and
 * {@code .count} how many junctions a seed shows; {@code .cells} the framing.
 */
public final class CityJunctionSnapshotSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Asphalt this many cells long counts as a street leaving the junction. */
    private static final int ARM = 6;
    /** Junctions closer than this to one already framed are the same junction. */
    private static final int APART = 24;

    @Override public String id() { return "city-junctions"; }

    @Override public String label() { return "City junctions: how street pavement joins at T and cross junctions"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        float cells = Float.parseFloat(System.getProperty("snapshot.cityJunctions.cells", "40"));
        int count = Integer.getInteger("snapshot.cityJunctions.count", 12);
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, cells);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String token : System.getProperty("snapshot.cityJunctions.seeds", "7117,3").split(",")) {
            long seed = Long.parseLong(token.trim());
            try (BattleSimulation sim = colony(seed)) {
                List<int[]> junctions = junctions(sim.getTopology(), count);
                int n = 0;
                for (int[] j : junctions) {
                    n++;
                    near.refocus(j[0] + 0.5f, j[1] + 0.5f, cells);
                    String name = String.format(Locale.ROOT, "seed%d-%02d-%s-%d-%d.png", seed, n,
                            j[2] == 4 ? "cross" : "tee", j[0], j[1]);
                    out.add(new SnapshotArtifact(name, near.render(sim, String.format(Locale.ROOT,
                            "seed %d, %s junction at %d,%d, %.0f cells across", seed,
                            j[2] == 4 ? "cross" : "T", j[0], j[1], cells))));
                }
            }
        }
        return out;
    }

    /** Junction centres, three- and four-way, spread over the map, in scan order. */
    static List<int[]> junctions(CellTopology topology, int count) {
        RoadSurface surface = topology.roadSurface();
        List<int[]> found = new ArrayList<>();
        if (surface == null) return found;
        int w = topology.getWidth(), h = topology.getHeight();
        boolean[][] asphalt = new boolean[w][h];
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            RoadSurface.Patch p = surface.patch(x, y, Material.ASPHALT.ordinal());
            asphalt[x][y] = p != null && p.area() > .99;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<int[]> tees = new ArrayList<>(), crosses = new ArrayList<>();
        for (int y = ARM; y < h - ARM; y++) for (int x = ARM; x < w - ARM; x++) {
            if (!asphalt[x][y]) continue;
            int arms = 0;
            for (int[] d : dirs) {
                boolean run = true;
                for (int k = 1; k <= ARM && run; k++) run = asphalt[x + d[0] * k][y + d[1] * k];
                if (run) arms++;
            }
            if (arms < 3) continue;
            // The middle of the junction: asphalt across every diagonal too would be open paving.
            int[] j = {x, y, arms};
            if (spaced(j, arms == 4 ? crosses : tees)) (arms == 4 ? crosses : tees).add(j);
        }
        for (int i = 0; found.size() < count && (i < tees.size() || i < crosses.size()); i++) {
            if (i < tees.size() && spaced(tees.get(i), found)) found.add(tees.get(i));
            if (i < crosses.size() && found.size() < count && spaced(crosses.get(i), found)) found.add(crosses.get(i));
        }
        return found;
    }

    private static boolean spaced(int[] j, List<int[]> taken) {
        for (int[] t : taken) if (Math.abs(t[0] - j[0]) < APART && Math.abs(t[1] - j[1]) < APART) return false;
        return true;
    }

    private static BattleSimulation colony(long seed) throws Exception {
        String json;
        try (InputStream stream = CityJunctionSnapshotSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONObject object = new JSONObject(json);
        object.put("seed", seed);
        BattleFixture parsed = BattleFixtureJson.fromJson(object);
        return (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
    }
}
