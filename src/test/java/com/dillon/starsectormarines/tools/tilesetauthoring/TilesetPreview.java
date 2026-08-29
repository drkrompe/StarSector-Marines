package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.Graphics2DTileSink;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileSink;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
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
 * <p>That matters most for the two things a contact sheet cannot show. The first
 * is whether a footprint is right: a piece is stretched into the cells it claims,
 * so a console authored one cell wide when it wants two reads as squashed here
 * and nowhere earlier in the pipeline.
 *
 * <p>The second is whether a wall is <b>inside out</b>. A block's cells are
 * chosen by {@code GridLayout} from a mask meaning "the exterior is on this
 * side", and a set of pieces assigned to the mirrored slots still loads, still
 * resolves and is still opaque — nothing downstream can object. So each block is
 * drawn here as the smallest room that exercises every one of its cases at once,
 * and a mirrored wall is visible as a room turned outside in.
 */
public final class TilesetPreview {

    private TilesetPreview() {}

    private static final Color VOID = new Color(0x0d, 0x11, 0x17);
    private static final Color DECK = new Color(0x2b, 0x33, 0x3d);
    private static final Color DECK_LINE = new Color(0x00, 0x00, 0x00, 46);
    private static final Color BULKHEAD = new Color(0x4a, 0x53, 0x5e);
    private static final Color CAPTION = new Color(0xc8, 0xd2, 0xdc);

    /** Cells of clear deck kept around the furnished area, so the room has walls. */
    private static final int MARGIN_CELLS = 1;
    /** Cells across the compartment before the ranks wrap. */
    private static final int ROOM_CELLS_ACROSS = 18;
    /** Height of a block's caption strip, in pixels. */
    private static final int CAPTION_PX = 16;

    /**
     * Lay the sheet's blocks and pieces out and draw them.
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

        List<GridBlockDef> blocks = new ArrayList<>();
        JSONArray blockArray = tileset.optJSONArray("blocks");
        for (int i = 0; blockArray != null && i < blockArray.length(); i++) {
            GridBlockDef def = registry.block(blockArray.getJSONObject(i).getString("id"));
            if (def != null) blocks.add(def);
        }

        List<DoodadDef> defs = new ArrayList<>();
        JSONArray doodadArray = tileset.getJSONArray("doodads");
        for (int i = 0; i < doodadArray.length(); i++) {
            DoodadDef def = registry.doodad(doodadArray.getJSONObject(i).getString("id"));
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
        int across = Math.max(1, Math.max(widest, blocks.size() * (ROOM_SPAN + 1)))
                + MARGIN_CELLS * 2;
        int down = cursorY + shelf + MARGIN_CELLS * 2;
        int blockStripPx = blocks.isEmpty() ? 0 : ROOM_SPAN * screenCellPx + CAPTION_PX;

        BufferedImage image = new BufferedImage(
                Math.max(1, across * screenCellPx),
                Math.max(1, down * screenCellPx + blockStripPx), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(VOID);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        TileSink sink = new Graphics2DTileSink(g, atlas);
        FixedGridTileDrawer drawer = new FixedGridTileDrawer(cellPx);

        int blockX = MARGIN_CELLS * screenCellPx;
        for (GridBlockDef block : blocks) {
            drawBlockRoom(g, sink, drawer, block, blockX, 0, screenCellPx);
            blockX += (ROOM_SPAN + 1) * screenCellPx;
        }

        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                boolean wall = x == 0 || y == 0 || x == across - 1 || y == down - 1;
                g.setColor(wall ? BULKHEAD : DECK);
                g.fillRect(x * screenCellPx, blockStripPx + y * screenCellPx,
                        screenCellPx, screenCellPx);
                g.setColor(DECK_LINE);
                g.drawRect(x * screenCellPx, blockStripPx + y * screenCellPx,
                        screenCellPx, screenCellPx);
            }
        }

        // Same drawer the game uses, at the sheet's own cell size rather than
        // the engine's, which is what lets a finer sheet keep its detail.
        for (int[] slot : placed) {
            DoodadDef def = defs.get(slot[0]);
            float w = def.footprintCellsX * (float) screenCellPx;
            float h = def.footprintCellsY * (float) screenCellPx;
            float cx = (slot[1] + MARGIN_CELLS) * screenCellPx + w / 2f;
            float cy = blockStripPx + (slot[2] + MARGIN_CELLS) * screenCellPx + h / 2f;
            drawer.drawSpan(sink, new TileManifest.TileFrame(def.col, def.row),
                    def.footprintCellsX, def.footprintCellsY,
                    cx, cy, w, h, 1f, FixedGridTileDrawer.OVERLAY_INSET_PX);
        }
        g.dispose();
        return image;
    }

    /** Cells across the smallest room that exercises every case of a 3x3 layout. */
    static final int ROOM_SPAN = 3;

    /**
     * Draw one block as a room three cells on a side.
     *
     * <p>Every cell of such a room has a distinct mask — four corners, four
     * edges, and an enclosed middle — so the nine drawn cells are exactly the
     * nine cases the layout can produce. The mask is stated the way the layout
     * reads it: the exterior lies beyond whichever sides of the room this cell
     * sits on.
     */
    static void drawBlockRoom(Graphics2D g, TileSink sink, FixedGridTileDrawer drawer,
                                      GridBlockDef block, int originPx, int topPx, int screenCellPx) {
        for (int row = 0; row < ROOM_SPAN; row++) {
            for (int col = 0; col < ROOM_SPAN; col++) {
                int x = originPx + col * screenCellPx;
                int y = topPx + row * screenCellPx;
                int[] cell = block.resolve(row == 0, row == ROOM_SPAN - 1,
                        col == ROOM_SPAN - 1, col == 0, col, row);
                if (cell == null) {
                    // The layout has no art for this case and defers to the fill.
                    g.setColor(block.fillRgb == null ? VOID : new Color(block.fillRgb));
                    g.fillRect(x, y, screenCellPx, screenCellPx);
                    continue;
                }
                drawer.draw(sink, new TileManifest.TileFrame(cell[0], cell[1]),
                        x + screenCellPx / 2f, y + screenCellPx / 2f,
                        screenCellPx, screenCellPx, 1f, FixedGridTileDrawer.OVERLAY_INSET_PX);
            }
        }
        g.setColor(CAPTION);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
        g.drawString(block.id, originPx, topPx + ROOM_SPAN * screenCellPx + CAPTION_PX - 4);
    }
}
