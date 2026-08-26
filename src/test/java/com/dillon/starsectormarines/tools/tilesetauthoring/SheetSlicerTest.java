package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.dillon.starsectormarines.tools.authoring.AuthoringPageProvider;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Infrastructure checks for slicing a raw art sheet and packing it into a tileset. */
class SheetSlicerTest {

    private static final int RESIDUE = 20;   // what a soft key leaves across empty space
    private static final int ART = 250;

    /** A sheet with three opaque blocks, separated by background that is faintly non-zero. */
    private static BufferedImage sheet() {
        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, (RESIDUE << 24) | 0x202020);
            }
        }
        fill(image, 10, 10, 40, 40);     // row 0, left
        fill(image, 70, 10, 60, 40);     // row 0, right, wider
        fill(image, 10, 70, 40, 40);     // row 1
        return image;
    }

    private static void fill(BufferedImage image, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                image.setRGB(x + dx, y + dy, (ART << 24) | 0xC0C0C0);
            }
        }
    }

    @Test
    void backgroundResidueDoesNotFuseTheSheet() {
        List<SheetSlicer.Piece> pieces = SheetSlicer.slice(sheet(), SheetSlicer.DEFAULT_ALPHA_MIN, 100);
        assertEquals(3, pieces.size(), "faint background alpha should not merge the pieces");

        // Reading order: across the first row, then down.
        assertEquals(10, pieces.get(0).x());
        assertEquals(70, pieces.get(1).x());
        assertEquals(70, pieces.get(2).y());
    }

    @Test
    void aThresholdBelowTheResidueSwallowsEverything() {
        List<SheetSlicer.Piece> pieces = SheetSlicer.slice(sheet(), 1, 100);
        assertEquals(1, pieces.size(),
                "keying on any non-zero alpha should fuse the sheet, which is why the threshold exists");
    }

    @Test
    void aFusedPlateSplitsIntoItsTiles() {
        SheetSlicer.Piece fused = new SheetSlicer.Piece(0, 0, 208, 104);
        List<SheetSlicer.Piece> parts = SheetSlicer.splitOnGrid(fused, 104);
        assertEquals(2, parts.size());
        assertEquals(0, parts.get(0).x());
        assertEquals(104, parts.get(1).x());
    }

    @Test
    void providerIsDiscoverableFromRootTestServices() {
        boolean found = ServiceLoader.load(AuthoringPageProvider.class).stream()
                .anyMatch(provider -> provider.type() == TilesetAuthoringPageProvider.class);
        assertTrue(found, "Tilesets must be a visible authoring workbench page");
    }

    @Test
    void piecesPackIntoTheirAuthoredFootprints() throws Exception {
        BufferedImage source = sheet();
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (SheetSlicer.Piece piece : SheetSlicer.slice(source, SheetSlicer.DEFAULT_ALPHA_MIN, 100)) {
            entries.add(new TilesetExport.Entry(piece, "doodad.test." + entries.size()));
        }
        entries.get(1).footprintX = 2;     // a piece that covers two cells of deck

        int[] size = TilesetExport.pack(entries);
        assertEquals(4, size[0], "three pieces, one of them two cells wide");
        assertEquals(1, size[1]);
        assertEquals(0, entries.get(0).col);
        assertEquals(1, entries.get(1).col);
        assertEquals(3, entries.get(2).col, "the two-cell piece should advance the cursor by two");

        BufferedImage atlas = TilesetExport.atlas(source, entries, 64);
        assertEquals(4 * 64, atlas.getWidth());
        assertEquals(64, atlas.getHeight());
        // Stretched to fill, so the far corner of the two-cell slot is covered.
        assertTrue((atlas.getRGB(2 * 64 - 2, 32) >>> 24) > 128,
                "a two-cell piece should be stretched across both cells");

        JSONObject tileset = TilesetExport.tileset("graphics/doodads/test.png", 64, entries);
        assertEquals(64, tileset.getInt("cellPx"));
        assertEquals(3, tileset.getJSONArray("doodads").length());
        assertEquals(2, tileset.getJSONArray("doodads").getJSONObject(1)
                .getJSONArray("footprintCells").getInt(0));
    }
}
