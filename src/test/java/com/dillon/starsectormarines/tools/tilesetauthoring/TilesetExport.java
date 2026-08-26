package com.dillon.starsectormarines.tools.tilesetauthoring;

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
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Packs authored pieces into an atlas the game can address, and writes the
 * tileset that describes it.
 *
 * <p><b>Each piece is stretched to fill its footprint.</b> A piece is not placed
 * at whatever size it happens to be drawn — it is resampled to exactly the cells
 * it was authored to occupy, because the footprint is a statement about how much
 * deck the thing covers, not about the art's pixel dimensions. A console
 * authored two cells long stretches across two cells whether it was drawn wide
 * or square, and that is intended rather than tolerated.
 *
 * <p>The atlas is addressed in cells of {@code cellPx}, which the tileset
 * declares and rendering honours, so a sheet drawn finer than the game's grid
 * keeps its detail instead of being resampled down to it.
 */
public final class TilesetExport {

    private TilesetExport() {}

    /** Cells across the atlas before wrapping to a new shelf. Keeps the sheet roughly square. */
    private static final int ATLAS_COLUMNS = 16;

    /** One authored piece: where it came from, what it is, and how much deck it covers. */
    public static final class Entry {
        public SheetSlicer.Piece piece;
        public String id;
        public int footprintX = 1;
        public int footprintY = 1;
        public String cover = "none";
        public boolean included = true;
        /** Assigned by {@link #pack}. */
        public int col;
        public int row;

        public Entry(SheetSlicer.Piece piece, String id) {
            this.piece = piece;
            this.id = id;
        }
    }

    /**
     * Shelf-pack the included entries, assigning each a cell origin.
     *
     * @return the atlas size in cells, as {@code {columns, rows}}
     */
    public static int[] pack(List<Entry> entries) {
        int cursorX = 0;
        int cursorY = 0;
        int shelfHeight = 0;
        int widest = 0;
        for (Entry entry : entries) {
            if (!entry.included) continue;
            if (cursorX + entry.footprintX > ATLAS_COLUMNS && cursorX > 0) {
                cursorX = 0;
                cursorY += shelfHeight;
                shelfHeight = 0;
            }
            entry.col = cursorX;
            entry.row = cursorY;
            cursorX += entry.footprintX;
            shelfHeight = Math.max(shelfHeight, entry.footprintY);
            widest = Math.max(widest, cursorX);
        }
        return new int[]{ Math.max(1, widest), Math.max(1, cursorY + shelfHeight) };
    }

    /** Draw every included entry into its packed slot, stretched to fill it. */
    public static BufferedImage atlas(BufferedImage source, List<Entry> entries, int cellPx) {
        int[] size = pack(entries);
        BufferedImage atlas = new BufferedImage(
                size[0] * cellPx, size[1] * cellPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (Entry entry : entries) {
            if (!entry.included) continue;
            SheetSlicer.Piece p = entry.piece;
            g.drawImage(
                    source.getSubimage(p.x(), p.y(), p.width(), p.height()),
                    entry.col * cellPx, entry.row * cellPx,
                    entry.footprintX * cellPx, entry.footprintY * cellPx, null);
        }
        g.dispose();
        return atlas;
    }

    /** The tileset document describing {@code sheetPath}'s doodads. */
    public static JSONObject tileset(String sheetPath, int cellPx, List<Entry> entries)
            throws JSONException {
        JSONArray doodads = new JSONArray();
        for (Entry entry : entries) {
            if (!entry.included) continue;
            JSONObject o = new JSONObject();
            o.put("id", entry.id);
            o.put("col", entry.col);
            o.put("row", entry.row);
            o.put("cover", entry.cover);
            if (entry.footprintX != 1 || entry.footprintY != 1) {
                o.put("footprintCells", new JSONArray().put(entry.footprintX).put(entry.footprintY));
            }
            doodads.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("sheet", sheetPath);
        root.put("cellPx", cellPx);
        root.put("doodads", doodads);
        return root;
    }

    /**
     * Write atlas and tileset together, each replaced atomically.
     *
     * <p>Both or neither: a tileset naming cells that the atlas beside it does
     * not contain is a startup crash, and half-written art is worse than none.
     */
    public static void write(BufferedImage atlas, JSONObject tileset,
                             Path atlasPath, Path tilesetPath)
            throws IOException, JSONException {
        Files.createDirectories(atlasPath.getParent());
        Files.createDirectories(tilesetPath.getParent());
        Path atlasTemp = atlasPath.resolveSibling(atlasPath.getFileName() + ".tmp");
        Path tilesetTemp = tilesetPath.resolveSibling(tilesetPath.getFileName() + ".tmp");
        try {
            ImageIO.write(atlas, "png", atlasTemp.toFile());
            Files.writeString(tilesetTemp, tileset.toString(2), StandardCharsets.UTF_8);
            Files.move(atlasTemp, atlasPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tilesetTemp, tilesetPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(atlasTemp);
            Files.deleteIfExists(tilesetTemp);
        }
    }
}
