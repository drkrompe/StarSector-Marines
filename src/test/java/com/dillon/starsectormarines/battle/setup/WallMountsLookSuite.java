package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The props hung on building walls (shop awnings, loading canopies, wall lamps) against the faces they hang from
 * ({@code WallMounts}), on Colony Breach colonies, whose main streets carry the shop awnings. Each awning picked is
 * shot close, 30 cells across with the awning off the frame's centre where its face leans most
 * ({@code <seed>-<n>-close.png}), and in a pan of three frames sliding along its wall, 40 cells across
 * ({@code -pan-a/b/c.png}). Look preset; nothing is played. Opt-in.
 *
 * <p>{@code -Dsnapshot.mounts.count=4} is how many awnings to shoot, at most two a map.
 */
public final class WallMountsLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final float CLOSE = 30f, PAN = 40f;
    private static final int SEEDS_SCANNED = 12;

    @Override public String id() { return "wall-mounts-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Awnings and wall lamps on the faces they hang from, close and in a pan"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        int count = Integer.getInteger("snapshot.mounts.count", 4);
        List<SnapshotArtifact> out = new ArrayList<>();
        int shot = 0;
        for (long seed = 1; seed <= SEEDS_SCANNED && shot < count; seed++) {
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                List<Doodad> picked = pick(sim, Math.min(2, count - shot));
                System.out.printf(Locale.ROOT, "[wall-mounts-look] seed %d: picked %d%n", seed, picked.size());
                if (picked.isEmpty()) continue;
                BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                        picked.get(0).cellX, picked.get(0).cellY, PAN);
                for (Doodad d : picked) {
                    shot++;
                    int[] wall = wallStep(d.quarterTurns);
                    float cx = d.cellX + d.footprintCellsX * .5f, cy = d.cellY + d.footprintCellsY * .5f;
                    // Off to the street side and along the wall, so the face it hangs on leans well into view.
                    float ax = wall[1] != 0 ? 1 : 0, ay = wall[0] != 0 ? 1 : 0;
                    String tag = "seed" + seed + "-" + shot + "-";
                    System.out.printf(Locale.ROOT, "  %d: %s at (%.1f,%.1f), wall (%d,%d)%n", shot,
                            d.definitionId(), cx, cy, wall[0], wall[1]);
                    look.refocus(cx - wall[0] * 6 + ax * 7, cy - wall[1] * 6 + ay * 7, CLOSE);
                    out.add(new SnapshotArtifact(tag + "close.png", look.render(sim, "")));
                    String[] step = {"a", "b", "c"};
                    for (int k = 0; k < 3; k++) {
                        float along = (k - 1) * 10f;
                        look.refocus(cx - wall[0] * 8 + ax * along, cy - wall[1] * 8 + ay * along, PAN);
                        out.add(new SnapshotArtifact(tag + "pan-" + step[k] + ".png", look.render(sim, "")));
                    }
                }
            }
        }
        return out;
    }

    private static boolean hangs(String id) {
        return id != null && (id.startsWith("doodad.street-awning-") || id.startsWith("doodad.street-lamp-bracket-"));
    }

    /** The grid step from a mount toward its wall: its frame's north, turned by its quarter turns. */
    private static int[] wallStep(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, -1};
            default -> new int[]{1, 0};
        };
    }

    /**
     * Up to {@code count} mounts, each at least 30 cells from the last picked and clear of the map's edge: shop
     * awnings first, then loading canopies and lamps.
     */
    private static List<Doodad> pick(BattleSimulation sim, int count) {
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        List<Doodad> picked = new ArrayList<>();
        List<Doodad> ordered = new ArrayList<>(sim.getDoodads());
        ordered.sort(Comparator.comparing((Doodad d) -> String.valueOf(d.definitionId()).endsWith("-steel")));
        for (Doodad d : ordered) {
            if (picked.size() >= count) break;
            if (!hangs(d.definitionId())) continue;
            if (d.cellX < 34 || d.cellY < 20 || d.cellX > w - 34 || d.cellY > h - 20) continue;
            boolean far = true;
            for (Doodad p : picked) far &= Math.abs(p.cellX - d.cellX) + Math.abs(p.cellY - d.cellY) > 30;
            if (far) picked.add(d);
        }
        return picked;
    }
}
