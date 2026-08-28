package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exporting a sheet as a sliced strip rather than a cell grid.
 *
 * <p>The two shapes are alternatives. A grid sheet's tiles are addressed by
 * {@code (col, row)} on a stated cell size; a strip's are addressed by the frame
 * index the loader assigns when it scans the atlas for alpha gaps. Two shipped
 * sheets are the second kind, and until the exporter could write it, re-exporting
 * one turned it into the first — which loads, validates, and renames every id the
 * map selects by.
 */
class TilesetStripExportTest {

    private static final TilesetExport.StripSpec SPEC = TilesetExport.StripSpec.of(4);

    /** Three opaque blocks of different sizes with clear space between them. */
    private static BufferedImage sheet() {
        BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
        fill(image, 10, 10, 80, 120, 0xFFCC8844);
        fill(image, 150, 20, 40, 40, 0xFF4488CC);
        fill(image, 250, 30, 120, 60, 0xFF44CC88);
        return image;
    }

    private static void fill(BufferedImage image, int x0, int y0, int width, int height, int argb) {
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) image.setRGB(x, y, argb);
        }
    }

    private static List<TilesetExport.Entry> entries() {
        return List.of(
                entry(new SheetSlicer.Piece(10, 10, 80, 120), "strip.tall"),
                entry(new SheetSlicer.Piece(150, 20, 40, 40), "strip.small"),
                entry(new SheetSlicer.Piece(250, 30, 120, 60), "strip.wide"));
    }

    private static TilesetExport.Entry entry(SheetSlicer.Piece piece, String id) {
        return new TilesetExport.Entry(piece, id);
    }

    @Test
    void framesAreLaidLeftToRightAtTheAuthoredScale() {
        List<TilesetExport.Entry> entries = entries();
        TilesetExport.StripPacking packing = TilesetExport.packStrip(entries, SPEC);
        assertEquals(3, packing.frames().size());
        assertEquals(20, entries.get(0).frameWidth, "80 raw pixels at 1/4");
        assertEquals(30, entries.get(0).frameHeight);
        assertEquals(10, entries.get(1).frameWidth);
        assertEquals(30, entries.get(2).frameWidth);
        assertEquals(2, entries.get(0).frameX, "the margin comes first");
        assertEquals(2 + 20 + 8, entries.get(1).frameX, "then the gutter");
        assertEquals(2 + 20 + 8 + 10 + 8, entries.get(2).frameX);
        assertEquals(2 + 20 + 8 + 10 + 8 + 30 + 2, packing.width());
        assertEquals(30 + 4, packing.height(), "as tall as its tallest frame, plus the margins");
    }

    /**
     * The one property a strip has to have: the loader must find the frames back.
     *
     * <p>Nothing else in the export can check it. The tileset names frame indices
     * and the atlas is the only thing that says what those resolve to, so a
     * packing whose gutters the slicer walks straight through renumbers every id
     * after the first fused pair and leaves both files internally consistent.
     */
    @Test
    void thePackedStripSlicesBackIntoTheFramesThatWerePacked() {
        List<TilesetExport.Entry> entries = entries();
        TilesetExport.StripPacking packing = TilesetExport.packStrip(entries, SPEC);
        SpriteSheetFrames found = SpriteSheetSlicer.slice(
                TilesetExport.stripAtlas(sheet(), entries, SPEC));
        assertEquals(packing.frames().size(), found.frames.length);
        for (int i = 0; i < found.frames.length; i++) {
            assertEquals(entries.get(i).frameX, found.frames[i].x, "frame " + i + " starts elsewhere");
            assertEquals(entries.get(i).frameWidth, found.frames[i].w, "frame " + i + " is a different width");
        }
    }

    @Test
    void aGutterNarrowerThanTheGapTheLoaderSplitsOnIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new TilesetExport.StripSpec("auto-strip", 16, 4, 4, 3, 2));
        assertTrue(refused.getMessage().contains("separate"), refused.getMessage());
    }

    @Test
    void theTilesetPinsFrameIndicesInDocumentOrderAndCarriesEveryAuthoredField() throws Exception {
        List<TilesetExport.Entry> entries = entries();
        entries.get(0).label = "tall";
        entries.get(0).note = "the tall one";
        entries.get(1).layer = "overlay";
        entries.get(1).cover = "light";
        entries.get(1).passable = false;
        entries.get(1).validOn = List.of("layer:ground");
        JSONObject tileset = TilesetExport.slicedTileset("graphics/tilesets/x.png", entries, SPEC);

        assertEquals("auto-strip", tileset.getJSONObject("slice").getString("mode"));
        assertEquals(16, tileset.getJSONObject("slice").getInt("alphaThreshold"));
        assertEquals(4, tileset.getJSONObject("slice").getInt("minGap"));
        assertFalse(tileset.has("cellPx"), "a strip has no grid to state a cell size for");
        assertFalse(tileset.has("doodads"), "a strip's pieces are frames, not props at coordinates");

        JSONArray tiles = tileset.getJSONArray("tiles");
        assertEquals(3, tiles.length());
        for (int frame = 0; frame < tiles.length(); frame++) {
            assertEquals(frame, tiles.getJSONObject(frame).getInt("frame"));
            assertEquals(entries.get(frame).id, tiles.getJSONObject(frame).getString("id"));
        }
        assertEquals("tall", tiles.getJSONObject(0).getString("name"));
        assertEquals("the tall one", tiles.getJSONObject(0).getString("description"));
        assertEquals("ground", tiles.getJSONObject(0).getString("layer"));
        assertEquals("overlay", tiles.getJSONObject(1).getString("layer"));
        assertEquals("light", tiles.getJSONObject(1).getString("cover"));
        assertFalse(tiles.getJSONObject(1).getBoolean("passable"));
        assertEquals("layer:ground", tiles.getJSONObject(1).getJSONArray("validOn").getString(0));
    }

    /** An excluded piece is not on the sheet, so it does not hold a frame index open. */
    @Test
    void anExcludedPieceRenumbersTheFramesAfterIt() throws Exception {
        List<TilesetExport.Entry> entries = entries();
        entries.get(0).included = false;
        JSONArray tiles = TilesetExport.slicedTileset("graphics/tilesets/x.png", entries, SPEC)
                .getJSONArray("tiles");
        assertEquals(2, tiles.length());
        assertEquals("strip.small", tiles.getJSONObject(0).getString("id"));
        assertEquals(0, tiles.getJSONObject(0).getInt("frame"));
        assertEquals("strip.wide", tiles.getJSONObject(1).getString("id"));
        assertEquals(1, tiles.getJSONObject(1).getInt("frame"));
    }

    /**
     * The document has to be able to say everything the tileset says.
     *
     * <p>A field only one of them has is where a re-export drops something
     * silently: a description that vanishes reads as no change at all, and a
     * layer that reverts to the default turns an overlay into ground.
     */
    @Test
    void aStripDocumentRoundTripsItsShapeAndEveryPerTileField() throws Exception {
        TilesetDocument document = new TilesetDocument();
        document.sheet = "art-source/tilesets/x.raw.png";
        document.sheetName = "x";
        document.strip = SPEC;
        TilesetExport.Entry tile = entry(new SheetSlicer.Piece(1, 2, 30, 40), "x.bench");
        tile.layer = "overlay";
        tile.label = "bench";
        tile.note = "bench facing south";
        tile.cover = "light";
        tile.passable = false;
        tile.validOn = List.of("layer:ground", "!x.water");
        document.entries.add(tile);

        TilesetExport.Entry field = entry(new SheetSlicer.Piece(40, 2, 30, 40), "x.grass");
        field.spriteBorderX = 3;
        field.spriteBorderY = 6;
        document.entries.add(field);

        TilesetDocument read = TilesetDocument.fromJson(document.toJson());
        assertTrue(read.isStrip());
        assertEquals(SPEC, read.strip);
        TilesetExport.Entry back = read.entries.get(0);
        assertEquals("overlay", back.layer);
        assertEquals("bench", back.label);
        assertEquals("bench facing south", back.note);
        assertEquals("light", back.cover);
        assertFalse(back.passable);
        assertEquals(List.of("layer:ground", "!x.water"), back.validOn);
        assertEquals(0, back.spriteBorderX, "a piece drawn without a border keeps none");
        assertEquals(3, read.entries.get(1).spriteBorderX);
        assertEquals(6, read.entries.get(1).spriteBorderY);
    }

    /**
     * A field sprite's own border is not part of the surface it paves.
     *
     * <p>The generated ground frames arrive as slabs — a lit rim, a shadowed
     * skirt, a darker column down each side. Repeat one across a map and those
     * rule a dark lattice over the ground, one line per cell, which is not a
     * subtle artefact and which no id, frame count or opacity figure can see.
     *
     * <p>The frame below is that failure in miniature: a flat interior inside a
     * dark border. Without the treatment its edge column is the border's colour
     * and its neighbour in a tiling is the same colour again, so the two make a
     * double-width dark line down the join. With it, every edge reads as
     * interior and there is no join to see.
     */
    @Test
    void aFieldsOwnBorderIsReplacedByItsInteriorSoTheFieldTiles() {
        int raw = 4;
        BufferedImage source = new BufferedImage(80, 160, BufferedImage.TYPE_INT_ARGB);
        fill(source, 8, 8, 64, 128, 0xFF202020);
        fill(source, 8 + 3 * raw, 8 + 6 * raw, 64 - 6 * raw, 128 - 12 * raw, 0xFFA0A0A0);
        SheetSlicer.Piece piece = new SheetSlicer.Piece(8, 8, 64, 128);

        TilesetExport.Entry untreated = entry(piece, "x.field");
        BufferedImage plain = TilesetExport.stripAtlas(source, List.of(untreated), SPEC);
        assertEquals(0x202020, plain.getRGB(untreated.frameX, untreated.frameY) & 0xFFFFFF,
                "the miniature is meant to arrive with its border intact");

        TilesetExport.Entry treated = entry(piece, "x.field");
        treated.spriteBorderX = 3;
        treated.spriteBorderY = 6;
        BufferedImage exported = TilesetExport.stripAtlas(source, List.of(treated), SPEC);
        for (int y = 0; y < treated.frameHeight; y++) {
            for (int x = 0; x < treated.frameWidth; x++) {
                int argb = exported.getRGB(treated.frameX + x, treated.frameY + y);
                assertTrue((argb & 0xFF) >= 0x80, "the field is still dark at " + x + "," + y
                        + " (" + Integer.toHexString(argb) + "), so its border survived and "
                        + "tiles as a line down every join");
                assertEquals(0xFF, argb >>> 24, "the treatment moved alpha at " + x + "," + y
                        + ", which moves the frame boxes the loader finds");
            }
        }
    }

    /** A border with no interior left to mirror is a mistake, not a treatment. */
    @Test
    void aBorderWiderThanTheFrameIsRefused() {
        BufferedImage source = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        fill(source, 8, 8, 40, 40, 0xFF808080);
        TilesetExport.Entry entry = entry(new SheetSlicer.Piece(8, 8, 40, 40), "x.field");
        entry.spriteBorderX = 6;
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> TilesetExport.stripAtlas(source, List.of(entry), SPEC));
        assertTrue(refused.getMessage().contains("nothing"), refused.getMessage());
    }

    /**
     * A strip of ground goes with the tilesets, a strip of props with the
     * doodads — the distinction blocks draw for a grid sheet, asked of the shape
     * that has no blocks to ask with.
     */
    @Test
    void aStripOfGroundLandsWithTheTilesetsRatherThanTheDoodads() {
        TilesetDocument document = new TilesetDocument();
        document.sheetName = "x";
        document.strip = SPEC;
        TilesetExport.Entry prop = entry(new SheetSlicer.Piece(0, 0, 4, 4), "x.prop");
        prop.layer = "overlay";
        document.entries.add(prop);
        assertEquals("graphics/doodads/x.png", document.resolvedOutputSheet(false));

        TilesetExport.Entry ground = entry(new SheetSlicer.Piece(8, 0, 4, 4), "x.paving");
        document.entries.add(ground);
        assertEquals("graphics/tilesets/x.png", document.resolvedOutputSheet(false));
    }
}
