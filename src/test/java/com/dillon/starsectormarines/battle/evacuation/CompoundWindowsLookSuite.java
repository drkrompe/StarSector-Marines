package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
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
 * The windows in a compound's freestanding walls, as Colony Breach's Asharu lays them (pool seed 38 on arid), stood
 * up with their walls: the colony at tick zero, framed through the look preset where its walls hold the most windows,
 * at play zoom and close. {@code -Dsnapshot.compoundWindows.seeds} (default {@code 38/arid}) picks the colonies, each
 * {@code seed/biome}. Opt-in.
 */
public final class CompoundWindowsLookSuite implements SnapshotSuite {

    private static final String FIXTURE = "/battle-fixtures/colony-breach-v1.json";
    private static final float PLAY = 60f, CLOSE = 22f;

    @Override public String id() { return "compound-windows-look"; }

    @Override public String label() { return "Windows in a compound's walls (Asharu), at play zoom and close"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            List<SnapshotArtifact> out = new ArrayList<>();
            for (String token : System.getProperty("snapshot.compoundWindows.seeds", "38/arid").split(",")) {
                String[] seedAndBiome = token.trim().split("/");
                long seed = Long.parseLong(seedAndBiome[0]);
                String biome = seedAndBiome.length > 1 ? seedAndBiome[1].toUpperCase(Locale.ROOT) : null;
                try (BattleSimulation sim = HeadlessBattles.fixture(colony(seed, biome))) {
                    List<float[]> spots = windows(sim.getTopology(), 2);
                    System.out.printf("    [compound-windows] seed %d: %d frames%n", seed, spots.size());
                    for (int i = 0; i < spots.size(); i++) {
                        float[] at = spots.get(i);
                        String name = String.format(Locale.ROOT, "s%d-%d", seed, i + 1);
                        out.add(new SnapshotArtifact(name + "-play.png", BattleReviewFrameRenderer.look(
                                context.modRoot(), LookReview.WIDTH, LookReview.HEIGHT, at[0], at[1], PLAY)
                                .render(sim, "")));
                        out.add(new SnapshotArtifact(name + "-close.png", BattleReviewFrameRenderer.look(
                                context.modRoot(), LookReview.WIDTH, LookReview.HEIGHT, at[0], at[1], CLOSE)
                                .render(sim, "")));
                    }
                }
            }
            return out;
        }
    }

    /**
     * Up to {@code count} frame centres, each where the freestanding walls hold the most windows within a close frame,
     * the next at least a play frame from the last.
     */
    static List<float[]> windows(CellTopology ground, int count) {
        int w = ground.getWidth(), h = ground.getHeight();
        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (ground.isWall(x, y) && ground.isWindow(x, y) && RoofCover.owner(ground, x, y) == 0) {
                    cells.add(new int[]{x, y});
                }
            }
        }
        List<float[]> out = new ArrayList<>();
        while (out.size() < count) {
            int best = -1, bestCount = 0;
            for (int i = 0; i < cells.size(); i++) {
                int[] c = cells.get(i);
                boolean taken = false;
                for (float[] o : out) taken |= Math.abs(o[0] - c[0]) < PLAY / 2 && Math.abs(o[1] - c[1]) < PLAY / 3;
                if (taken) continue;
                int n = 0;
                for (int[] d : cells) if (Math.abs(d[0] - c[0]) < 8 && Math.abs(d[1] - c[1]) < 5) n++;
                if (n > bestCount) { bestCount = n; best = i; }
            }
            if (best < 0) break;
            out.add(new float[]{cells.get(best)[0] + .5f, cells.get(best)[1] + .5f});
        }
        return out;
    }

    private static JSONObject colony(long seed, String biome) throws Exception {
        try (InputStream stream = CompoundWindowsLookSuite.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Missing fixture " + FIXTURE);
            JSONObject fixture = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .put("seed", seed);
            if (biome != null) fixture.getJSONObject("targetProfile").put("surface", biome);
            return fixture;
        }
    }
}
