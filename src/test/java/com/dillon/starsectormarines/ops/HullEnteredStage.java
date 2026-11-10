package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
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
 * A stand-in for the hull entered (`art-source/fallen-hull`, `armour` and `fittings`), staged over a slice of the real
 * hull in a look-preset frame until the ground lays its compartments: three compartments along the hull's south side
 * (a launch bay, a survey lab, the forge's stores) behind its outer armour, with a hatch, a viewport and a breach in
 * the armour, the generic ship hold deck and bulkheads inside, and the hull's own fittings. Every piece is the shipped
 * tileset's cell, laid at its cell size. Not a map the game lays; the frame round it is the battle's.
 */
final class HullEnteredStage {

    /**
     * The stage, north row first; its last two rows are the hull's outer armour. {@code #} armour, {@code %} armour
     * torn open, {@code H} hatch, {@code P} viewport, {@code b} ship bulkhead, {@code d} bulkhead door, {@code .} deck.
     */
    static final String[] LAYOUT = {
            "bbbbbbbdbbbbbbbbbbbbbbdbbbbbbbbbbbbb",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "b..............d...........d.......b",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "b..............b...........b.......b",
            "####H#######PP####PP#######%%%%#####",
            "####H#######################%%%#####",
    };
    static final int COLS = LAYOUT[0].length();
    static final int ROWS = LAYOUT.length;
    /** The bay's columns: the compartment the card shows boarded under the roofed hull. */
    static final int BAY_COLS = 16;

    /** The hull's fittings on the deck: id, south-west cell (stage x, y from the south row). */
    static final List<Object[]> FITTINGS = List.of(
            new Object[]{"hangar-cradle", 5, 5},
            new Object[]{"rib-v", 3, -1}, new Object[]{"rib-v", 12, -1},
            new Object[]{"conduit-h", 1, 11}, new Object[]{"conduit-h", 2, 11}, new Object[]{"conduit-h", 3, 11},
            new Object[]{"conduit-h", 4, 11}, new Object[]{"conduit-h", 5, 11}, new Object[]{"conduit-junction", 6, 11},
            new Object[]{"conduit-h", 8, 11}, new Object[]{"conduit-h", 9, 11}, new Object[]{"conduit-h", 10, 11},
            new Object[]{"conduit-h", 11, 11}, new Object[]{"conduit-h", 12, 11}, new Object[]{"conduit-h", 13, 11},
            new Object[]{"survey-rack-h", 17, 11}, new Object[]{"survey-rack-h", 20, 11},
            new Object[]{"survey-rack-h", 23, 11}, new Object[]{"survey-console", 18, 7},
            new Object[]{"survey-console", 21, 7}, new Object[]{"sensor-drum", 23, 5},
            new Object[]{"crushed-bulkhead-a", 17, 3}, new Object[]{"crushed-bulkhead-b", 20, 2},
            new Object[]{"survey-rack-v", 26, 4},
            new Object[]{"forge-hopper", 29, 10}, new Object[]{"forge-coolant", 32, 9},
            new Object[]{"forge-cables", 29, 7}, new Object[]{"forge-cables", 31, 7},
            new Object[]{"breach-plating", 28, 3}, new Object[]{"breach-scatter", 31, 3},
            new Object[]{"breach-scatter", 32, 4}, new Object[]{"breach-scatter", 30, 5},
            // the breach's torn lip on the ground outside it, the hull at the image's top
            new Object[]{"breach-edge", 27, -1}, new Object[]{"breach-edge", 28, -1},
            new Object[]{"breach-edge", 29, -1}, new Object[]{"breach-edge", 30, -1},
            new Object[]{"breach-edge", 31, -1});

    /**
     * A stand-in for the debris shower on open ground: its craters, scorch and skid first, then the metal over them,
     * id and centre cell offset from the ground's middle. The pieces are the tileset's, laid at their cell size.
     */
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

    private final BufferedImage hull, bulkheads, interior, hold;
    private final Map<String, int[]> doodads = new HashMap<>();

    HullEnteredStage(Path modRoot) throws IOException, JSONException {
        hull = ImageIO.read(modRoot.resolve("graphics/tilesets/fallen-hull.png").toFile());
        bulkheads = ImageIO.read(modRoot.resolve("graphics/tilesets/ship-bulkheads.png").toFile());
        interior = ImageIO.read(modRoot.resolve("graphics/tilesets/ship-interior-materials.png").toFile());
        hold = ImageIO.read(modRoot.resolve("graphics/tilesets/ship-hold.png").toFile());
        JSONObject doc = new JSONObject(Files.readString(modRoot.resolve("data/tilesets/fallen-hull.tileset.json"),
                StandardCharsets.UTF_8));
        JSONArray list = doc.getJSONArray("doodads");
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.getJSONObject(i);
            JSONArray fp = d.getJSONArray("footprintCells");
            doodads.put(d.getString("id"), new int[]{d.getInt("col"), d.getInt("row"), fp.getInt(0), fp.getInt(1)});
        }
    }

    /**
     * Where the stage stands: its south-west cell, its armour rows on the hull's south armour, {@code back} cells west
     * of the forge at {@code (fx, fy)}. The hull's south edge is the first cell south of the forge under no building
     * and no wall.
     */
    static int[] origin(BattleSimulation sim, float fx, float fy, int back) {
        CellTopology t = sim.getTopology();
        int x = Math.round(fx) - back;
        int y = Math.round(fy);
        while (y > 0 && (t.getBuildingId(x, y) != 0 || t.isWall(x, y))) y--;
        return new int[]{x - COLS / 2, y + 1};
    }

    /** A look frame centred on the stage, {@code cells} wide, the stage's columns up to {@code toCol} laid over it. */
    BufferedImage frame(Path modRoot, BattleSimulation sim, int[] at, float cells, boolean roofs, int toCol,
                        float centreCol) throws IOException {
        float cx = at[0] + centreCol, cy = at[1] + ROWS / 2f;
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(modRoot, 1920, 1080, cx, cy, cells);
        if (!roofs) renderer = renderer.withoutRoofs();
        BufferedImage shot = renderer.render(sim, "");
        BufferedImage img = new BufferedImage(shot.getWidth(), shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(shot, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        lay(g, renderer.cameraOf(sim), img.getHeight(), at, toCol);
        g.dispose();
        return img;
    }

    private void lay(Graphics2D g, BattleCamera cam, int h, int[] at, int toCol) {
        // Deck under every cell of the stage, then walls, openings and breaches, then the fittings.
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < toCol; c++) {
                int deck = 4 + Math.floorMod(c * 7 + r * 3, 3);    // ship-hold.plate-worn
                cell(g, cam, h, hold, deck, 0, at[0] + c, at[1] + ROWS - 1 - r, 1, 1);
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < toCol; c++) {
                char k = LAYOUT[r].charAt(c);
                int x = at[0] + c, y = at[1] + ROWS - 1 - r;
                int mask = mask(r, c);
                boolean eastWest = solid(r, c - 1) || solid(r, c + 1);
                int facing = eastWest ? 0 : 1;
                switch (k) {
                    case '#' -> cell(g, cam, h, hull, mask % 4, mask / 4, x, y, 1, 1);
                    case '%' -> cell(g, cam, h, hull, 12 + mask % 4, mask / 4, x, y, 1, 1);
                    case 'H' -> cell(g, cam, h, hull, 12 + facing % 2, 4 + facing / 2, x, y, 1, 1);
                    case 'P' -> cell(g, cam, h, hull, 14 + facing % 2, 4 + facing / 2, x, y, 1, 1);
                    case 'b' -> cell(g, cam, h, bulkheads, mask % 4, mask / 4, x, y, 1, 1);
                    case 'd' -> cell(g, cam, h, interior, 2 + facing % 2, facing / 2, x, y, 1, 1);
                    default -> { }
                }
            }
        }
        for (Object[] f : FITTINGS) {
            String id = "fallen-hull." + f[0];
            int[] d = doodads.get(id);
            if (d == null) throw new IllegalStateException("no " + id + " in the fallen-hull tileset");
            int sx = (Integer) f[1], sy = (Integer) f[2];
            if (sy == -1 && "fallen-hull.breach-edge".equals(id)) {
                if (sx < toCol) cell(g, cam, h, hull, d[0], d[1], at[0] + sx, at[1] - 1, d[2], d[3]);
                continue;
            }
            if (sy < 0) {
                // a rib running the compartment's depth, one cell at a time
                for (int yy = 2; yy < ROWS - 1; yy++) {
                    if (sx < toCol) cell(g, cam, h, hull, d[0], d[1], at[0] + sx, at[1] + yy, d[2], d[3]);
                }
                continue;
            }
            if (sx + d[2] > toCol) continue;
            cell(g, cam, h, hull, d[0], d[1], at[0] + sx, at[1] + sy, d[2], d[3]);
        }
    }

    /** The debris shower stand-in centred on {@code (cx, cy)}, {@code cells} wide, in a roofs-on look frame. */
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
            String id = "fallen-hull.shower-shard-" + (char) ('a' + i % 3);
            int[] d = doodads.get(id);
            cell(g, cam, img.getHeight(), hull, d[0], d[1], cx + rnd.nextInt(30) - 15, cy + rnd.nextInt(14) - 7, 1, 1);
        }
        for (Object[] f : SHOWER) {
            String id = "fallen-hull." + f[0];
            int[] d = doodads.get(id);
            if (d == null) throw new IllegalStateException("no " + id + " in the fallen-hull tileset");
            int x = cx + (Integer) f[1] - d[2] / 2, y = cy + (Integer) f[2] - d[3] / 2;
            cell(g, cam, img.getHeight(), hull, d[0], d[1], x, y, d[2], d[3]);
        }
        g.dispose();
        return img;
    }

    /** Wall-faces mask (N=1 E=2 S=4 W=8) of stage cell (r, c): a side is exposed where no wall of any kind joins it. */
    private static int mask(int r, int c) {
        int m = 0;
        if (!solid(r - 1, c)) m |= 1;
        if (!solid(r, c + 1)) m |= 2;
        if (!solid(r + 1, c)) m |= 4;
        if (!solid(r, c - 1)) m |= 8;
        return m;
    }

    /** A wall, opening or breach root; beyond the stage the hull's armour carries on east and west and south. */
    private static boolean solid(int r, int c) {
        if (r >= ROWS) return false;
        if (r < 0) return true;
        if (c < 0 || c >= COLS) return r >= ROWS - 2;
        return LAYOUT[r].charAt(c) != '.';
    }

    /** A sheet's cells (col, row; w x h) laid with their south-west corner on map cell (x, y). */
    private static void cell(Graphics2D g, BattleCamera cam, int h, BufferedImage sheet, int col, int row, int x,
                             int y, int w, int hh) {
        int left = Math.round(cam.cellToScreenX(x));
        int right = Math.round(cam.cellToScreenX(x + w));
        int top = h - Math.round(cam.cellToScreenY(y + hh));
        int bottom = h - Math.round(cam.cellToScreenY(y));
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(sheet, left, top, right, bottom, col * F, row * F, (col + w) * F, (row + hh) * F, null);
    }
}
