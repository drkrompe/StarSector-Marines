package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.Graphics2DTileSink;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileSink;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a compartment furnished from a tileset that has not been written to
 * disk yet.
 *
 * <p>Nothing about checking art needs the game running. The atlas is packed in
 * memory, the tileset is ingested into a real {@link TileRegistry} — so a
 * document that would not load fails here rather than at startup — and the
 * pieces are drawn through the same {@link FixedGridTileDrawer} and
 * {@link TileSink} the game draws tiles through. What comes out is what a room
 * furnished with this sheet looks like, at the size a cell actually occupies on
 * screen.
 *
 * <p>That matters most for the one thing a contact sheet cannot show: whether a
 * footprint is right. A piece is stretched into the cells it claims, so a
 * console authored one cell wide when it wants two reads as squashed here and
 * nowhere earlier in the pipeline.
 */
public final class TilesetPreview {

    private TilesetPreview() {}

    private static final Color VOID = new Color(0x0d, 0x11, 0x17);
    private static final Color DECK = new Color(0x2b, 0x33, 0x3d);
    private static final Color DECK_LINE = new Color(0x00, 0x00, 0x00, 46);
    private static final Color BULKHEAD = new Color(0x4a, 0x53, 0x5e);

    /** Cells of clear deck kept around the furnished area, so the room has walls. */
    private static final int MARGIN_CELLS = 1;
    /** Cells across the compartment before the ranks wrap. */
    private static final int ROOM_CELLS_ACROSS = 18;

    /**
     * Lay the sheet's pieces out in a compartment and draw it.
     *
     * @param atlas the packed sheet, as {@link TilesetExport#atlas} produced it
     * @param tileset the document describing that atlas; ingested to prove it loads
     * @param cellPx source cell size of the atlas
     * @param screenCellPx how large one deck cell is drawn, which is the whole point
     * @throws JSONException if the tileset would not load, which is the useful failure
     */
    public static BufferedImage render(BufferedImage atlas, JSONObject tileset,
                                       int cellPx, int screenCellPx) throws JSONException {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);
        registry.validateReferences();

        List<DoodadDef> defs = new ArrayList<>();
        for (int i = 0; i < tileset.getJSONArray("doodads").length(); i++) {
            String id = tileset.getJSONArray("doodads").getJSONObject(i).getString("id");
            DoodadDef def = registry.doodad(id);
            if (def != null) defs.add(def);
        }

        List<int[]> placed = new ArrayList<>();
        int cursorX = 0;
        int cursorY = 0;
        int shelf = 0;
        int widest = 0;
        for (int i = 0; i < defs.size(); i++) {
            DoodadDef def = defs.get(i);
            if (cursorX + def.footprintCellsX > ROOM_CELLS_ACROSS && cursorX > 0) {
                cursorX = 0;
                cursorY += shelf + 1;
                shelf = 0;
            }
            placed.add(new int[]{ i, cursorX, cursorY });
            cursorX += def.footprintCellsX + 1;
            widest = Math.max(widest, cursorX);
            shelf = Math.max(shelf, def.footprintCellsY);
        }
        int across = Math.max(1, widest) + MARGIN_CELLS * 2;
        int down = cursorY + shelf + MARGIN_CELLS * 2;

        BufferedImage image = new BufferedImage(
                Math.max(1, across * screenCellPx), Math.max(1, down * screenCellPx),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(VOID);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                boolean wall = x == 0 || y == 0 || x == across - 1 || y == down - 1;
                g.setColor(wall ? BULKHEAD : DECK);
                g.fillRect(x * screenCellPx, y * screenCellPx, screenCellPx, screenCellPx);
                g.setColor(DECK_LINE);
                g.drawRect(x * screenCellPx, y * screenCellPx, screenCellPx, screenCellPx);
            }
        }

        // Same drawer the game uses, at the sheet's own cell size rather than
        // the engine's, which is what lets a finer sheet keep its detail.
        TileSink sink = new Graphics2DTileSink(g, atlas);
        FixedGridTileDrawer drawer = new FixedGridTileDrawer(cellPx);
        for (int[] slot : placed) {
            DoodadDef def = defs.get(slot[0]);
            float w = def.footprintCellsX * (float) screenCellPx;
            float h = def.footprintCellsY * (float) screenCellPx;
            float cx = (slot[1] + MARGIN_CELLS) * screenCellPx + w / 2f;
            float cy = (slot[2] + MARGIN_CELLS) * screenCellPx + h / 2f;
            drawer.drawSpan(sink, new TileManifest.TileFrame(def.col, def.row),
                    def.footprintCellsX, def.footprintCellsY,
                    cx, cy, w, h, 1f, FixedGridTileDrawer.OVERLAY_INSET_PX);
        }
        g.dispose();
        return image;
    }
}
