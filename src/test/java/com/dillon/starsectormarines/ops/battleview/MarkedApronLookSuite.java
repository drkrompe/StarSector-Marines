package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Marked apron ({@code STRIPED} ground, {@code apron.marked}) where colony maps put it: per seed, the
 * close look-preset frame holding the most apron that draws as itself (no surface override, no wall).
 * Opt-in: a look review of the ground art, not part of {@code all}.
 */
public final class MarkedApronLookSuite implements SnapshotSuite {

    static final int SEEDS = 3;
    static final int FRAME_W = 30, FRAME_H = 17;

    @Override public String id() { return "marked-apron-look"; }

    @Override public String label() { return "Marked apron ground on colony maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                int[] best = densest(sim.getTopology());
                System.out.printf(Locale.ROOT, "[%s] seed %d: %d apron cells in the frame at %d,%d%n",
                        id(), seed, best[2], best[0], best[1]);
                if (best[2] == 0) continue;
                out.add(new SnapshotArtifact("seed" + seed + "-apron.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), 1920, 1080, best[0] + FRAME_W / 2f, best[1] + FRAME_H / 2f, FRAME_W)
                        .withoutRoofs().render(sim, "seed" + seed)));
            }
        }
        return out;
    }

    /** {@code {x, y, count}} of the frame-sized window holding the most bare apron. */
    private static int[] densest(CellTopology topology) {
        int w = topology.getWidth(), h = topology.getHeight();
        int[][] sum = new int[h + 1][w + 1];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean apron = topology.getGroundKind(x, y) == CellTopology.GroundKind.STRIPED
                    && topology.getSurface(x, y) == 0 && !topology.isWall(x, y);
            sum[y + 1][x + 1] = sum[y][x + 1] + sum[y + 1][x] - sum[y][x] + (apron ? 1 : 0);
        }
        int[] best = {0, 0, 0};
        for (int y = 0; y + FRAME_H <= h; y++) for (int x = 0; x + FRAME_W <= w; x++) {
            int n = sum[y + FRAME_H][x + FRAME_W] - sum[y][x + FRAME_W] - sum[y + FRAME_H][x] + sum[y][x];
            if (n > best[2]) best = new int[]{x, y, n};
        }
        return best;
    }
}
