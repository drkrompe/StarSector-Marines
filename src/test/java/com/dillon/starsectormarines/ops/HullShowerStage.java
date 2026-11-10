package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A stand-in for the debris shower the fallen hull threw as it broke up (`art-source/fallen-hull`, {@code shower}), laid
 * over open ground in a look-preset frame until the ground strews it: craters, scorch and an impact skid first, then
 * shards, plates and chunks over them. Every piece is the shipped tileset's, laid at its cell size. Not a map the game
 * lays; the frame round it is the battle's.
 */
final class HullShowerStage {

    /** Id and centre cell offset from the ground's middle, decals first. */
    static final List<Object[]> SHOWER = List.of(
            new Object[]{"crater-large", -11, -5}, new Object[]{"crater-small", 7, 3},
            new Object[]{"scorch-splash", -3, 2}, new Object[]{"scorch-streak", 3, -5},
            new Object[]{"scorch-splash", 10, -3}, new Object[]{"crater-small", -6, 5},
            new Object[]{"shower-bell", -10, 2}, new Object[]{"shower-slab", 2, -1},
            new Object[]{"shower-mast", -1, -4}, new Object[]{"shower-girders", 9, 2},
            new Object[]{"shower-pod", -5, -1}, new Object[]{"shower-plate-b", 6, -3},
            new Object[]{"shower-plate-a", -7, 4}, new Object[]{"shower-plate-a", 12, 0},
            new Object[]{"shower-plate-b", -13, -1}, new Object[]{"shower-pod", 13, 4});

    private static final int F = 64;

    private final BufferedImage hull;
    private final Map<String, int[]> doodads = new HashMap<>();

    HullShowerStage(Path modRoot) throws IOException, JSONException {
        hull = ImageIO.read(modRoot.resolve("graphics/tilesets/fallen-hull.png").toFile());
        JSONObject doc = new JSONObject(Files.readString(modRoot.resolve("data/tilesets/fallen-hull.tileset.json"),
                StandardCharsets.UTF_8));
        JSONArray list = doc.getJSONArray("doodads");
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.getJSONObject(i);
            JSONArray fp = d.getJSONArray("footprintCells");
            doodads.put(d.getString("id"), new int[]{d.getInt("col"), d.getInt("row"), fp.getInt(0), fp.getInt(1)});
        }
    }

    /** The shower centred on {@code (cx, cy)}, {@code cells} wide, in a roofs-on look frame. */
    BufferedImage shower(Path modRoot, BattleSimulation sim, int cx, int cy, float cells) throws IOException {
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(modRoot, 1920, 1080, cx + .5f, cy + .5f,
                cells);
        BufferedImage shot = renderer.render(sim, "");
        BufferedImage img = new BufferedImage(shot.getWidth(), shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(shot, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        BattleCamera cam = renderer.cameraOf(sim);
        Random rnd = new Random(5);
        for (int i = 0; i < 18; i++) {
            int[] d = piece("shower-shard-" + (char) ('a' + i % 3));
            cell(g, cam, img.getHeight(), d, cx + rnd.nextInt(30) - 15, cy + rnd.nextInt(14) - 7);
        }
        for (Object[] f : SHOWER) {
            int[] d = piece((String) f[0]);
            cell(g, cam, img.getHeight(), d, cx + (Integer) f[1] - d[2] / 2, cy + (Integer) f[2] - d[3] / 2);
        }
        g.dispose();
        return img;
    }

    private int[] piece(String name) {
        int[] d = doodads.get("fallen-hull." + name);
        if (d == null) throw new IllegalStateException("no fallen-hull." + name + " in the fallen-hull tileset");
        return d;
    }

    /** A piece's cells laid with their south-west corner on map cell (x, y). */
    private void cell(Graphics2D g, BattleCamera cam, int h, int[] d, int x, int y) {
        int left = Math.round(cam.cellToScreenX(x));
        int right = Math.round(cam.cellToScreenX(x + d[2]));
        int top = h - Math.round(cam.cellToScreenY(y + d[3]));
        int bottom = h - Math.round(cam.cellToScreenY(y));
        g.drawImage(hull, left, top, right, bottom, d[0] * F, d[1] * F, (d[0] + d[2]) * F, (d[1] + d[3]) * F, null);
    }
}
