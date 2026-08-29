package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.Graphics2DTileSink;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileSink;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Pictures of a block, for choosing between blocks.
 *
 * <p>A list of ids is the wrong instrument for picking art. {@code urban.wall}
 * and {@code road.embankment} are both walls and are nothing alike to look at —
 * one is masonry and one is a sandbag embankment — and no amount of naming
 * carries that. What tells them apart is seeing them.
 *
 * <p>Two pictures, for two questions. The <b>patch</b> is the block's own cells
 * as the atlas holds them, which answers "which art is this". The <b>room</b>
 * draws the block the way the game resolves it, three cells on a side, which is
 * the only view that answers "is this wall inside out" — every cell of such a
 * room has a distinct neighbour mask, so the nine drawn cells are exactly the
 * nine cases the layout can produce.
 */
public final class BlockPreview {

    private static final Color CHECKER_DARK = new Color(0x2A, 0x2E, 0x34);
    private static final Color CHECKER_LIGHT = new Color(0x35, 0x3A, 0x42);
    private static final int CHECKER_PX = 8;

    private final Path projectRoot;
    private final Map<String, BufferedImage> atlases = new HashMap<>();
    private final Map<String, BufferedImage> thumbnails = new HashMap<>();

    public BlockPreview(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * The block's own cells, scaled to fit a {@code size} square.
     *
     * <p>Cached: a list cell renderer is asked for this on every repaint, and
     * decoding an atlas per row per paint would stall the window.
     */
    public BufferedImage patch(GridBlockDef block, int size) {
        if (block == null) return null;
        String key = block.id + "@" + size;
        if (thumbnails.containsKey(key)) return thumbnails.get(key);
        BufferedImage made = drawPatch(block, size);
        thumbnails.put(key, made);
        return made;
    }

    private BufferedImage drawPatch(GridBlockDef block, int size) {
        BufferedImage atlas = atlas(block.sheetPath);
        if (atlas == null) return null;

        int[][] cells = cellsOf(block);
        int columns = 0;
        int rows = 0;
        for (int[] cell : cells) {
            columns = Math.max(columns, cell[0] - cells[0][0] + 1);
            rows = Math.max(rows, cell[1] - cells[0][1] + 1);
        }
        columns = Math.max(1, columns);
        rows = Math.max(1, rows);

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        checker(g, size);
        // Nearest neighbour: this is pixel art being magnified, and a smoothed
        // thumbnail of a tile says less about the tile than a blocky one does.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int scale = Math.max(1, Math.min(size / (columns * block.cellPx),
                size / (rows * block.cellPx)));
        int drawnWidth = columns * block.cellPx * scale;
        int drawnHeight = rows * block.cellPx * scale;
        if (drawnWidth > size || drawnHeight > size) {
            double fit = Math.min(size / (double) drawnWidth, size / (double) drawnHeight);
            drawnWidth = (int) (drawnWidth * fit);
            drawnHeight = (int) (drawnHeight * fit);
        }
        int left = (size - drawnWidth) / 2;
        int top = (size - drawnHeight) / 2;
        int cellDrawn = Math.max(1, drawnWidth / columns);

        for (int[] cell : cells) {
            int sx = cell[0] * block.cellPx;
            int sy = cell[1] * block.cellPx;
            if (sx + block.cellPx > atlas.getWidth() || sy + block.cellPx > atlas.getHeight()) {
                continue;
            }
            int dx = left + (cell[0] - cells[0][0]) * cellDrawn;
            int dy = top + (cell[1] - cells[0][1]) * cellDrawn;
            g.drawImage(atlas.getSubimage(sx, sy, block.cellPx, block.cellPx),
                    dx, dy, cellDrawn, cellDrawn, null);
        }
        g.dispose();
        return out;
    }

    /**
     * The block drawn as a room three cells on a side — the view that shows a
     * mirrored wall, which a patch of the same nine cells cannot.
     */
    public BufferedImage room(GridBlockDef block, int screenCellPx) {
        if (block == null) return null;
        BufferedImage atlas = atlas(block.sheetPath);
        if (atlas == null) return null;

        int span = TilesetPreview.ROOM_SPAN;
        BufferedImage out = new BufferedImage(span * screenCellPx, span * screenCellPx,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        checker(g, Math.max(out.getWidth(), out.getHeight()));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        TileSink sink = new Graphics2DTileSink(g, atlas);
        FixedGridTileDrawer drawer = new FixedGridTileDrawer(block.cellPx);
        TilesetPreview.drawBlockRoom(g, sink, drawer, block, 0, 0, screenCellPx);
        g.dispose();
        return out;
    }

    /** Which cells the block occupies: a pool's list, or its layout's patch. */
    private static int[][] cellsOf(GridBlockDef block) {
        if (block.isVariantPool()) return block.cells;
        int span = block.layout == null ? 1 : block.layout.span();
        int[][] cells = new int[span * span][];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = new int[]{block.originCol + i % span, block.originRow + i / span};
        }
        return cells;
    }

    private BufferedImage atlas(String sheetPath) {
        if (atlases.containsKey(sheetPath)) return atlases.get(sheetPath);
        BufferedImage read = null;
        Path file = projectRoot.resolve("mod");
        for (String part : sheetPath.split("/")) file = file.resolve(part);
        try {
            if (Files.isRegularFile(file)) read = ImageIO.read(file.toFile());
        } catch (IOException unreadable) {
            // A sheet that will not decode is the export's problem to report.
            // Here it simply has no picture, and the list says so in words.
            read = null;
        }
        atlases.put(sheetPath, read);
        return read;
    }

    private static void checker(Graphics2D g, int size) {
        for (int y = 0; y < size; y += CHECKER_PX) {
            for (int x = 0; x < size; x += CHECKER_PX) {
                g.setColor(((x / CHECKER_PX) + (y / CHECKER_PX)) % 2 == 0
                        ? CHECKER_DARK : CHECKER_LIGHT);
                g.fillRect(x, y, CHECKER_PX, CHECKER_PX);
            }
        }
    }
}
