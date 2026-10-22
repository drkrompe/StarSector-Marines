package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.Doodad;
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
 * The colony militia's roadblocks as set up: Colony Breach's map at tick zero,
 * every roadblock framed at play zoom with its team on the bags
 * ({@code colony-breach-nouns.md}, Roadblock). Look evidence, 1920x1080.
 *
 * <p>{@code -Dsnapshot.roadblocks.seeds=7117,3,11} picks the colony seeds.
 * {@code -Dbattle.colonyBreach.roadblockSpan=kerb} renders the kerb-to-kerb
 * control.
 */
public final class ColonyRoadblockSnapshotSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** 1920 px over sixty cells: 32 px a cell, the play zoom. */
    private static final float PLAY_ZOOM_CELLS = 60f;
    /** Hedgehogs closer than this belong to one roadblock. */
    private static final int SAME = 16;

    @Override public String id() { return "roadblocks"; }

    @Override public String label() { return "Colony roadblocks: militia lines across the streets into the shelter"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer near = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String token : System.getProperty("snapshot.roadblocks.seeds", "7117,3,11").split(",")) {
            long seed = Long.parseLong(token.trim());
            try (BattleSimulation sim = colony(seed)) {
                List<int[]> blocks = roadblocks(sim);
                out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed%d-00-whole-map.png", seed),
                        whole.render(sim, String.format(Locale.ROOT, "colony seed %d, %d roadblocks", seed, blocks.size()))));
                int n = 0;
                for (int[] b : blocks) {
                    n++;
                    near.refocus(b[0] + 0.5f, b[1] + 0.5f, PLAY_ZOOM_CELLS);
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed%d-%02d-roadblock-%d-%d.png", seed, n, b[0], b[1]),
                            near.render(sim, String.format(Locale.ROOT,
                                    "seed %d, roadblock %d at %d,%d, play zoom", seed, n, b[0], b[1]))));
                }
            }
        }
        return out;
    }

    /** Each roadblock's middle: hedgehogs and trucks gathered into the lines they stand in. */
    static List<int[]> roadblocks(BattleSimulation sim) {
        List<int[]> sums = new ArrayList<>();
        for (Doodad d : sim.getDoodads()) {
            String id = d.definitionId();
            if (!ColonyRoadblocks.HEDGEHOG.equals(id)) continue;
            int[] into = null;
            for (int[] s : sums) {
                if (Math.abs(s[0] / s[2] - d.cellX) < SAME && Math.abs(s[1] / s[2] - d.cellY) < SAME) into = s;
            }
            if (into == null) sums.add(new int[]{d.cellX, d.cellY, 1});
            else {
                into[0] += d.cellX;
                into[1] += d.cellY;
                into[2]++;
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int[] s : sums) out.add(new int[]{s[0] / s[2], s[1] / s[2]});
        return out;
    }

    static BattleSimulation colony(long seed) throws Exception {
        String json;
        try (InputStream stream = ColonyRoadblockSnapshotSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONObject object = new JSONObject(json);
        object.put("seed", seed);
        BattleFixture parsed = BattleFixtureJson.fromJson(object);
        return (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
    }
}
