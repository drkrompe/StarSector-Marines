package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.creep.CreepVegetationSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fire on real ground (`fire-nouns.md`, Look): the canonical colony on a verdant
 * world, its grass, fields, roads and pavement drawn through the production
 * ground layers, with a fire line lit down its west edge in a fresh wind. Only
 * the fire field is advanced. The play-zoom window is the one where burnt
 * ground, unburnt grass, road and pavement all meet, chosen from a first burn,
 * and the second burn is drawn there as the front crosses it and once it has
 * passed; the densest grove as its crowns catch; and the whole map mid-burn and
 * after. Flame scale and scorch tone follow {@code battle.render.fireFlames} and
 * {@code battle.render.fireScorch}, so the look options are this suite rendered
 * once per choice.
 */
public final class FireColonySnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    /** Cells across at play zoom: 32 px a cell. */
    private static final float PLAY_CELLS = 60f;
    private static final int WIN_W = 60, WIN_H = 34;
    /** Cells across close up: 96 px a cell. */
    private static final float CLOSE_CELLS = 20f;
    private static final int MAX_TICKS = 30 * 240;

    @Override public String id() { return "fire-colony"; }

    @Override public String label() { return "Fire on the green colony's real ground: burnt grass against road, pavement and fields"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        // A renderer first: it installs the tile and doodad registries the colony is built from.
        BattleReviewFrameRenderer whole = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H);
        // First burn: where everything meets, and when the front crossed it.
        int[] ignite;
        int endTick;
        int w, h;
        int[] window, grove, edge;
        try (BattleSimulation sim = CreepVegetationSnapshotSuite.verdantColony()) {
            w = sim.getGrid().getWidth();
            h = sim.getGrid().getHeight();
            ignite = new int[w * h];
            Arrays.fill(ignite, -1);
            endTick = burn(sim, MAX_TICKS, ignite);
            window = bestWindow(sim, w, h);
            edge = edgeSpot(sim, w, h, window);
            grove = groveCentre(sim, ignite, w);
        }
        int frontTick = medianIgnite(ignite, w, window[0], window[1], WIN_W, WIN_H);
        int groveTick = grove == null ? -1 : medianIgnite(ignite, w, grove[0] - 4, grove[1] - 4, 8, 8);

        List<SnapshotArtifact> out = new ArrayList<>();
        float wx = window[0] + WIN_W / 2f, wy = window[1] + WIN_H / 2f;
        BattleReviewFrameRenderer play = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, wx, wy, PLAY_CELLS);
        BattleReviewFrameRenderer groveView = grove == null ? null
                : new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H, grove[0] + 0.5f, grove[1] + 0.5f, PLAY_CELLS);
        // Each frame at its tick, in tick order.
        List<Shot> shots = new ArrayList<>();
        shots.add(new Shot(frontTick + 60, "01-front-on-ground.png", play, "front crossing grass, road and pavement"));
        if (groveView != null && groveTick >= 0) shots.add(new Shot(groveTick + 90, "03-grove-catching.png", groveView, "densest grove as its crowns catch"));
        shots.add(new Shot(endTick / 2, "04-whole-map-mid.png", whole, "whole map, mid-burn"));
        shots.add(new Shot(endTick + 30, "02-burnt-ground.png", play, "burnt ground against grass, road and pavement"));
        shots.add(new Shot(endTick + 30, "05-whole-map-after.png", whole, "whole map, burnt out"));
        BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                edge[0] + 0.5f, edge[1] + 0.5f, CLOSE_CELLS);
        shots.add(new Shot(endTick + 30, "06-edge-close.png", close, "close: where the burn stopped, unburnt grass beyond"));
        if (groveView != null && groveTick >= 0) {
            BattleReviewFrameRenderer groveClose = new BattleReviewFrameRenderer(context.modRoot(), FRAME_W, FRAME_H,
                    grove[0] + 0.5f, grove[1] + 0.5f, CLOSE_CELLS);
            shots.add(new Shot(endTick + 30, "07-grove-close.png", groveClose, "close: burnt ground under the grove"));
        }
        shots.sort((a, b) -> Integer.compare(a.tick, b.tick));
        try (BattleSimulation sim = CreepVegetationSnapshotSuite.verdantColony()) {
            FireField fire = light(sim);
            int tick = 0;
            for (Shot shot : shots) {
                for (; tick < shot.tick; tick++) fire.tick(tick);
                out.add(new SnapshotArtifact(shot.file, shot.renderer.render(sim, caption(shot.view, tick))));
            }
        }
        out.sort((a, b) -> a.relativePath().compareTo(b.relativePath()));
        return out;
    }

    private record Shot(int tick, String file, BattleReviewFrameRenderer renderer, String view) { }

    private static FireField light(BattleSimulation sim) {
        FireField fire = sim.fireField();
        fire.setWind(1f, 0.15f, 0.6f);
        for (int y = 0; y < sim.getGrid().getHeight(); y++) fire.addHeat(2, y, 3000);
        return fire;
    }

    /** Burns until the fire is out or the cap; records each cell's ignition tick; returns the last tick. */
    private static int burn(BattleSimulation sim, int maxTicks, int[] ignite) {
        FireField fire = light(sim);
        int tick = 0;
        for (; tick < maxTicks; tick++) {
            fire.tick(tick);
            if (tick % FireField.STEP_TICKS != 0) continue;
            for (int i = 0, n = fire.burningCount(); i < n; i++) {
                int idx = fire.burningCell(i);
                if (ignite[idx] < 0) ignite[idx] = tick;
            }
            if (tick > 300 && !fire.isActive()) break;
        }
        return tick;
    }

    private static boolean paved(GroundKind k) {
        return k == GroundKind.SIDEWALK || k == GroundKind.COURTYARD || k == GroundKind.TILE || k == GroundKind.BRICK;
    }

    /** The window whose scarcest of burnt, unburnt grass, road and pavement is most plentiful. */
    private static int[] bestWindow(BattleSimulation sim, int w, int h) {
        FireField fire = sim.fireField();
        CellTopology topo = sim.getTopology();
        int[][] sums = new int[4][(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                GroundKind k = topo.getGroundKind(x, y);
                int[] v = {
                        fire.stateAt(idx) == FireField.BURNT ? 1 : 0,
                        fire.stateAt(idx) == FireField.UNBURNT && k == GroundKind.GRASS ? 1 : 0,
                        k == GroundKind.STREET ? 1 : 0,
                        paved(k) ? 1 : 0};
                for (int c = 0; c < 4; c++) {
                    int[] s = sums[c];
                    s[(y + 1) * (w + 1) + x + 1] = v[c] + s[y * (w + 1) + x + 1] + s[(y + 1) * (w + 1) + x] - s[y * (w + 1) + x];
                }
            }
        }
        // Wanted share of the window for each: burnt and grass a third, road and pavement a tenth.
        float[] want = {0.33f, 0.33f, 0.1f, 0.1f};
        float best = -1f;
        int[] at = {0, 0};
        for (int y = 0; y + WIN_H <= h; y += 4) {
            for (int x = 0; x + WIN_W <= w; x += 4) {
                float score = Float.MAX_VALUE;
                for (int c = 0; c < 4; c++) {
                    int[] s = sums[c];
                    int n = s[(y + WIN_H) * (w + 1) + x + WIN_W] - s[y * (w + 1) + x + WIN_W] - s[(y + WIN_H) * (w + 1) + x] + s[y * (w + 1) + x];
                    score = Math.min(score, Math.min(1f, n / (float) (WIN_W * WIN_H) / want[c]));
                }
                if (score > best) {
                    best = score;
                    at = new int[]{x, y};
                }
            }
        }
        return at;
    }

    /** The centre of the burnt tree with the most burnt trees round it: where the front ran into a grove. */
    /** The burnt cell nearest the window's centre with unburnt grass a few cells off: the far side of a firebreak. */
    private static int[] edgeSpot(BattleSimulation sim, int w, int h, int[] window) {
        FireField fire = sim.fireField();
        CellTopology topo = sim.getTopology();
        int cx = window[0] + WIN_W / 2, cy = window[1] + WIN_H / 2;
        int[] best = {cx, cy};
        int bestD = Integer.MAX_VALUE;
        for (int y = 2; y < h - 2; y++) {
            for (int x = 2; x < w - 2; x++) {
                if (fire.stateAt(y * w + x) != FireField.BURNT) continue;
                boolean grass = false;
                for (int dy = -6; dy <= 6 && !grass; dy++) {
                    for (int dx = -6; dx <= 6; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        if (topo.getGroundKind(nx, ny) == GroundKind.GRASS && fire.stateAt(ny * w + nx) == FireField.UNBURNT) {
                            grass = true;
                            break;
                        }
                    }
                }
                if (!grass) continue;
                int d = Math.abs(x - cx) + Math.abs(y - cy);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    private static int[] groveCentre(BattleSimulation sim, int[] ignite, int w) {
        List<Doodad> trees = new ArrayList<>();
        for (Doodad d : sim.getDoodads()) {
            String id = d.definitionId();
            if (id == null || !id.startsWith("forest.")) continue;
            int cx = d.cellX + d.footprintCellsX / 2, cy = d.cellY + d.footprintCellsY / 2;
            int idx = cy * w + cx;
            if (idx >= 0 && idx < ignite.length && ignite[idx] >= 0) trees.add(d);
        }
        int[] best = null;
        int bestCount = -1;
        for (Doodad d : trees) {
            int count = 0;
            for (Doodad o : trees) if (Math.abs(o.cellX - d.cellX) <= 14 && Math.abs(o.cellY - d.cellY) <= 14) count++;
            if (count > bestCount) {
                bestCount = count;
                best = new int[]{d.cellX + d.footprintCellsX / 2, d.cellY + d.footprintCellsY / 2};
            }
        }
        return best;
    }

    private static int medianIgnite(int[] ignite, int w, int x0, int y0, int ww, int wh) {
        List<Integer> ticks = new ArrayList<>();
        int h = ignite.length / w;
        for (int y = Math.max(0, y0); y < Math.min(h, y0 + wh); y++)
            for (int x = Math.max(0, x0); x < Math.min(w, x0 + ww); x++)
                if (ignite[y * w + x] >= 0) ticks.add(ignite[y * w + x]);
        if (ticks.isEmpty()) return -1;
        ticks.sort(null);
        return ticks.get(ticks.size() / 2);
    }

    private static String caption(String view, int tick) {
        return String.format(Locale.ROOT, "fire, green colony | %s | %d:%02d | flames %s, scorch %s", view,
                tick / 1800, (tick % 1800) / 30,
                System.getProperty("battle.render.fireFlames", "restrained"), System.getProperty("battle.render.fireScorch", "char"));
    }
}
