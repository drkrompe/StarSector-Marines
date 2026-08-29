package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The variant pool, which is the second shape a block can have.
 *
 * <p>An autotile answers "which cell for this neighbour mask" and therefore
 * occupies a fixed patch addressed as an origin plus a layout offset. A pool
 * answers "any of these", is picked by hashing the cell's coordinate, and has
 * no geometry — so it is written as an explicit list of cells.
 *
 * <p>The exporter could not express one until now, which is exactly why the two
 * variant-pool sheets in this project were the last still produced by a script
 * rather than from an authoring document.
 */
public class TilesetVariantPoolExportTest {

    private static final int CELL = 16;

    /** A plate of {@code count} distinct solid cells in a row. */
    private static BufferedImage plate(int count) {
        BufferedImage image = new BufferedImage(count * CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        for (int i = 0; i < count; i++) {
            g.setColor(new Color(20 + i * 40, 60, 120));
            g.fillRect(i * CELL, 0, CELL, CELL);
        }
        g.dispose();
        return image;
    }

    private static List<TilesetExport.Entry> pool(String blockId, int count) {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        List<String> slots = BlockSlots.pool(count);
        for (int i = 0; i < count; i++) {
            TilesetExport.Entry entry = new TilesetExport.Entry(
                    new SheetSlicer.Piece(i * CELL, 0, CELL, CELL), "piece-" + i);
            entry.blockId = blockId;
            entry.slot = slots.get(i);
            entries.add(entry);
        }
        return entries;
    }

    /** A pool with no layout writes its cells; there is no origin to write. */
    @Test
    void aPoolIsWrittenAsCellsRatherThanAnOriginAndLayout() throws Exception {
        List<TilesetExport.Entry> entries = pool("water.water", 3);
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec("water.water", null, null));

        JSONObject tileset = TilesetExport.tileset("graphics/tilesets/w.png", CELL,
                entries, blocks);
        JSONObject block = tileset.getJSONArray("blocks").getJSONObject(0);

        assertEquals("water.water", block.getString("id"));
        assertTrue(block.has("cells"), "a pool is addressed by an explicit cell list");
        assertFalse(block.has("origin"), "a pool has no origin: it has no geometry to offset from");
        assertFalse(block.has("layout"), "a pool resolves by hash, not by a neighbour mask");
        assertEquals(3, block.getJSONArray("cells").length());
    }

    /** The run is laid in slot order, so v1 really is the pool's first cell. */
    @Test
    void aPoolsCellsLieInSlotOrder() {
        List<TilesetExport.Entry> entries = pool("floors.grass", 3);
        // Authored back to front: document order must not decide the run.
        List<TilesetExport.Entry> shuffled =
                List.of(entries.get(2), entries.get(0), entries.get(1));
        TilesetExport.pack(shuffled,
                List.of(new TilesetExport.BlockSpec("floors.grass", null, null)));

        assertEquals(0, entries.get(0).col, "v1 is the first cell of the run");
        assertEquals(1, entries.get(1).col);
        assertEquals(2, entries.get(2).col);
        for (TilesetExport.Entry entry : entries) assertEquals(0, entry.row);
    }

    /** A pool reserves one cell per variant, not a 3x3 patch. */
    @Test
    void aPoolReservesOnlyAsManyCellsAsItHasVariants() {
        List<TilesetExport.Entry> entries = pool("floors.grass", 4);
        TilesetExport.Packing packing = TilesetExport.pack(entries,
                List.of(new TilesetExport.BlockSpec("floors.grass", null, null)));
        assertEquals(4, packing.columns());
        assertEquals(1, packing.rows());
    }

    /** What the exporter writes is what the game's own loader reads back. */
    @Test
    void theGameLoadsThePoolTheExporterWrote() throws Exception {
        List<TilesetExport.Entry> entries = pool("water.water", 3);
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec("water.water", null, null));
        JSONObject tileset = TilesetExport.tileset("graphics/tilesets/w.png", CELL,
                entries, blocks);

        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);

        var block = registry.block("water.water");
        assertNotNull(block, "the exported pool must load");
        assertTrue(block.isVariantPool(), "it must load as a pool, not as an autotile");
        assertEquals(3, block.cells.length);
    }

    /**
     * Reducing a piece by more than half averages every source pixel instead of
     * sampling four of them. A single bilinear step at 3x reads four of every
     * nine and discards the rest, which is visible as softening and aliasing on
     * a ground tile cut from a large plate.
     */
    @Test
    void aBigReductionAveragesTheWholePieceRatherThanSamplingIt() {
        // Half black, half white in 2px columns: a single bilinear step to 1/4
        // can land entirely on one phase, an averaging one cannot.
        BufferedImage striped = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = striped.createGraphics();
        for (int x = 0; x < 32; x += 4) {
            g.setColor(Color.WHITE);
            g.fillRect(x, 0, 2, 32);
            g.setColor(Color.BLACK);
            g.fillRect(x + 2, 0, 2, 32);
        }
        g.dispose();

        BufferedImage small = TilesetExport.reduced(striped, 8, 8);
        assertTrue(small.getWidth() <= 16,
                "a 4x reduction should have been halved at least once, not drawn in one step");

        int mid = new Color(small.getRGB(small.getWidth() / 2, small.getHeight() / 2)).getRed();
        assertTrue(mid > 60 && mid < 195,
                "averaging a black-and-white stripe should land mid-grey, not on one phase; got "
                        + mid);
    }

    /** Enlargement and small reductions pass straight through rather than round twice. */
    @Test
    void aPieceThatIsNotBeingShrunkIsLeftAlone() {
        BufferedImage piece = plate(1);
        assertEquals(piece, TilesetExport.reduced(piece, CELL, CELL));
        assertEquals(piece, TilesetExport.reduced(piece, CELL * 2, CELL * 2));
    }
}
