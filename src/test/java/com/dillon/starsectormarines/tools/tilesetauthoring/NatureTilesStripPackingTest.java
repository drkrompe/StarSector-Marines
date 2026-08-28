package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code nature-tiles}' authoring document packs out of its keyed raw
 * sheet, asked of the real document rather than of a synthetic one.
 *
 * <p>The sheet is not exported yet — five of its twenty frames take their
 * picture from a tileable material rather than from this plate, and until that
 * is settled the shipped atlas has a producer this document is not. See
 * {@code nature-tiles-material-provenance.md}. What can be settled now is that
 * the document describes the sheet correctly, so that when the export does
 * happen the only open question is those five frames.
 *
 * <p>Two things are worth checking here and nowhere else. The first is the
 * binding: on a strip the frame index is the address, and the twenty ids are
 * live in {@code urban.mapping.json}, so a packing the loader splits or fuses
 * differently renames content the map already selects by. The second is the
 * sprite border, which is the difference between a ground field and a lattice
 * ruled over every cell of every outdoor map — and which nothing downstream can
 * see, because a lattice is a valid image of the right size.
 *
 * <p>Both write the picture they were judged on to
 * {@code build/tileset-authoring/}. The number is the assertion; the picture is
 * what a person checks it against, and it has to outlive the sitting it was
 * made in.
 */
class NatureTilesStripPackingTest {

    private static final Path TILESETS = Paths.get("art-source", "tilesets");
    private static final Path DOCUMENT = TILESETS.resolve("nature-tiles.tileset-authoring.json");
    private static final Path RAW_SHEET = TILESETS.resolve("nature-tiles.raw.png");
    private static final Path SHIPPED = Paths.get(
            "mod", "graphics", "tilesets", "nature-tiles.png");
    private static final Path SHIPPED_TILESET = Paths.get(
            "mod", "data", "tilesets", "nature-tiles.tileset.json");
    private static final Path REVIEW = Paths.get("build", "tileset-authoring");

    /** How much of a ground frame the renderer never draws. */
    private static final int INSET = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;

    /** Frame order is this sheet's only address, so the ids are listed in it. */
    private static final List<String> FRAMES = List.of(
            "nature.grass-1", "nature.grass-2", "nature.dirt-1", "nature.dirt-2",
            "nature.sand", "nature.water-1", "nature.water-2",
            "nature.shrub-1", "nature.shrub-2", "nature.tuft-1", "nature.tuft-2",
            "nature.shrub-3",
            "nature.rock-small-1", "nature.rock-small-2", "nature.rock-small-3",
            "nature.rock-medium-1", "nature.rock-medium-2",
            "nature.rock-large-1", "nature.rock-large-2", "nature.rock-large-3");

    @Test
    void theDocumentDescribesTwentyPiecesInTheOrderTheShippedTilesetPins() throws Exception {
        TilesetDocument document = TilesetDocument.read(DOCUMENT);
        assertTrue(document.isStrip(), "nature-tiles loads as an auto-strip; a document that "
                + "cannot say so would re-export it as a cell grid and rename every id");
        List<String> ids = new ArrayList<>();
        for (TilesetExport.Entry entry : document.entries) {
            if (entry.included) ids.add(entry.id);
        }
        assertEquals(FRAMES, ids);
    }

    /**
     * The document says everything the shipped tileset says.
     *
     * <p>This is the adoption, checked rather than asserted in prose: the sheet
     * was described by hand and the description has to match the file the game
     * loads today, field for field. A {@code cover} or a {@code passable} that
     * went missing here is a combat-behaviour change invisible in an id diff,
     * and a lost {@code validOn} silently changes where scatter puts things.
     *
     * <p>Stated as "nothing is lost" rather than "nothing differs", because the
     * document legitimately adds: the shipped tileset carries no descriptions
     * and the document carries one per piece, including the one recording that
     * frame twelve is a plant wearing a rock's id.
     */
    @Test
    void theDocumentSaysEverythingTheShippedTilesetSays() throws Exception {
        TilesetDocument document = TilesetDocument.read(DOCUMENT);
        JSONObject exported = TilesetExport.slicedTileset(
                document.resolvedOutputSheet(false), document.entries, document.strip);
        JSONObject shipped = new JSONObject(Files.readString(SHIPPED_TILESET));
        assertEquals(shipped.getJSONObject("slice").toString(),
                exported.getJSONObject("slice").toString(), "the slice settings would change");
        JSONArray were = shipped.getJSONArray("tiles");
        JSONArray now = exported.getJSONArray("tiles");
        assertEquals(were.length(), now.length());
        for (int i = 0; i < were.length(); i++) {
            JSONObject before = were.getJSONObject(i);
            JSONObject after = now.getJSONObject(i);
            for (Iterator<String> keys = before.keys(); keys.hasNext(); ) {
                String key = keys.next();
                assertTrue(after.has(key), before.getString("id") + " would lose its " + key);
                assertEquals(before.get(key).toString(), after.get(key).toString(),
                        before.getString("id") + " would change its " + key);
            }
        }
    }

    /**
     * The packed strip slices back into the frames that were packed.
     *
     * <p>Nothing in the tileset can be checked against the atlas by reading it:
     * a tile names a frame index and the atlas is the only thing that says what
     * that resolves to. Two of this sheet's props are scatters — three separate
     * pebbles with daylight between them — so the packing has to keep the
     * daylight inside a piece narrower than the gap the loader splits on, and
     * the gutter between pieces wider. Neither is visible in either file.
     */
    @Test
    void theStripSlicesBackIntoTheTwentyPiecesThatWerePacked() throws Exception {
        TilesetDocument document = TilesetDocument.read(DOCUMENT);
        BufferedImage atlas = atlas(document);
        writeFrameComparison(atlas, document);
        TilesetExport.StripPacking packing =
                TilesetExport.packStrip(document.entries, document.strip);
        SpriteSheetFrames found = SpriteSheetSlicer.slice(atlas);
        assertEquals(FRAMES.size(), packing.frames().size());
        assertEquals(packing.frames().size(), found.frames.length,
                "the packed strip does not slice into the pieces it was packed from, so every "
                        + "id from the first difference on would name a different picture");
        for (int i = 0; i < found.frames.length; i++) {
            TilesetExport.Entry entry = packing.frames().get(i);
            assertTrue(found.frames[i].x >= entry.frameX
                            && found.frames[i].x + found.frames[i].w
                            <= entry.frameX + entry.frameWidth,
                    FRAMES.get(i) + " slices outside the frame it was packed into");
        }
    }

    /**
     * A ground field exports without the border its sprite was drawn with.
     *
     * <p>Every field on this plate is an isolated slab: a lit rim across the
     * top, a soil skirt under the foot, a darker column down each side.
     * Repeated across a map those rule a grid over the ground, one line per
     * cell — and nothing downstream can see it, because a lattice is a valid
     * image of the right size and the tileset describing it stays correct.
     *
     * <p>Both the failure and its removal are asserted, because a bound alone
     * cannot tell a treatment that works from a bound that is generous. The
     * sides and the ends are measured apart: the border is several pixels deeper
     * under the slab than beside it, which is why the depth is two numbers and
     * why it is authored per piece rather than once for the sheet — the seven
     * fields settled on six different pairs.
     */
    @Test
    void everyFieldExportsWithoutTheBorderItsSpriteWasDrawnWith() throws Exception {
        TilesetDocument document = TilesetDocument.read(DOCUMENT);
        BufferedImage treated = atlas(document);
        TilesetDocument bare = TilesetDocument.read(DOCUMENT);
        for (TilesetExport.Entry entry : bare.entries) {
            entry.spriteBorderX = 0;
            entry.spriteBorderY = 0;
        }
        BufferedImage untreated = atlas(bare);
        writeGroundTiling(treated, untreated, document);

        StringBuilder measured = new StringBuilder();
        double worstSideTreated = 0;
        double worstSideBare = 0;
        double worstEndTreated = 0;
        double worstEndBare = 0;
        for (int i = 0; i < document.entries.size(); i++) {
            TilesetExport.Entry entry = document.entries.get(i);
            if (entry.spriteBorderX == 0 && entry.spriteBorderY == 0) continue;
            double sides = drift(treated, entry, SIDES);
            double sidesBare = drift(untreated, bare.entries.get(i), SIDES);
            double ends = drift(treated, entry, ENDS);
            double endsBare = drift(untreated, bare.entries.get(i), ENDS);
            measured.append(System.lineSeparator()).append("  ").append(entry.id)
                    .append(": sides ").append(percent(sidesBare)).append(" -> ")
                    .append(percent(sides)).append(", ends ").append(percent(endsBare))
                    .append(" -> ").append(percent(ends));
            worstSideTreated = Math.max(worstSideTreated, sides);
            worstSideBare = Math.max(worstSideBare, sidesBare);
            worstEndTreated = Math.max(worstEndTreated, ends);
            worstEndBare = Math.max(worstEndBare, endsBare);
        }
        assertTrue(worstSideBare > 0.20 && worstEndBare > 0.40,
                "the fields no longer arrive with the border this treatment exists to remove, "
                        + "so the bounds below are not measuring anything:" + measured);
        assertTrue(worstSideTreated < 0.09, "a field still draws a side away from its middle, "
                + "which tiles as a line down every vertical join:" + measured);
        assertTrue(worstEndTreated < 0.10, "a field still draws an end away from its middle, "
                + "which tiles as a line across every horizontal join:" + measured);
    }

    /** The left and right edges: where the slab has a border and no shading ramp. */
    private static final boolean SIDES = true;
    /** The top and bottom edges: where the border and the ramp are on top of each other. */
    private static final boolean ENDS = false;

    /**
     * The worse of two opposite drawn edges, as a fraction of the frame's middle.
     *
     * <p>Per edge and never as one ring. A slab's rim is lit and its skirt is
     * shadowed, so the two are opposite-signed and a ring average cancels them
     * to almost nothing — the same trap a per-piece opacity figure is. What
     * ruins a tiling is one edge sitting away from the surface, whichever way.
     *
     * <p>The band starts at the renderer's inset, because the pixels inside that
     * are the ones a neighbouring cell is drawn against.
     */
    private static double drift(BufferedImage atlas, TilesetExport.Entry entry, boolean sides) {
        double middle = band(atlas, entry, entry.frameHeight / 3, entry.frameHeight * 2 / 3,
                entry.frameWidth / 3, entry.frameWidth * 2 / 3);
        int width = entry.frameWidth;
        int height = entry.frameHeight;
        int[][] edges = sides
                ? new int[][]{
                        {INSET, height - INSET, INSET, INSET + 2},
                        {INSET, height - INSET, width - INSET - 2, width - INSET}}
                : new int[][]{
                        {INSET, INSET + 2, INSET, width - INSET},
                        {height - INSET - 2, height - INSET, INSET, width - INSET}};
        double worst = 0;
        for (int[] edge : edges) {
            double value = band(atlas, entry, edge[0], edge[1], edge[2], edge[3]);
            worst = Math.max(worst, Math.abs(value - middle) / Math.max(1, middle));
        }
        return worst;
    }

    /** Mean brightness of one rectangle of a frame, over its opaque pixels. */
    private static double band(BufferedImage atlas, TilesetExport.Entry entry,
                               int top, int bottom, int left, int right) {
        long sum = 0;
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int argb = atlas.getRGB(entry.frameX + x, entry.frameY + y);
                if (argb >>> 24 < 128) continue;
                sum += ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
                count += 3;
            }
        }
        return count == 0 ? 0 : sum / (double) count;
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    private static BufferedImage atlas(TilesetDocument document) throws IOException {
        return TilesetExport.stripAtlas(rawSheet(), document.entries, document.strip);
    }

    private static BufferedImage rawSheet() throws IOException {
        BufferedImage raw = ImageIO.read(RAW_SHEET.toFile());
        assertNotNull(raw, "cannot read " + RAW_SHEET);
        assertTrue(raw.getColorModel().hasAlpha(), RAW_SHEET + " carries no keyed alpha, so "
                + "every frame would export as the black rectangle it was drawn on");
        return raw;
    }

    // ---- review artifacts ----------------------------------------------------

    private static final int PAD = 4;
    private static final int MAGENTA = 0xFFFF00FF;
    private static final int PAPER = 0xFF202020;

    /** Every frame, shipped above exported, over magenta. */
    private static void writeFrameComparison(BufferedImage exported, TilesetDocument document)
            throws IOException {
        BufferedImage shipped = ImageIO.read(SHIPPED.toFile());
        assertNotNull(shipped, "cannot read " + SHIPPED);
        SpriteSheetFrames were = SpriteSheetSlicer.slice(shipped);
        assertEquals(FRAMES.size(), were.frames.length, "the shipped atlas no longer holds the "
                + "frames this comparison is against");
        TilesetExport.packStrip(document.entries, document.strip);

        List<BufferedImage> top = new ArrayList<>();
        List<BufferedImage> bottom = new ArrayList<>();
        for (int i = 0; i < FRAMES.size(); i++) {
            SpriteSheetFrames.Frame was = were.frames[i];
            top.add(crop(shipped, was.x, was.y, was.w, was.h));
            TilesetExport.Entry entry = document.entries.get(i);
            bottom.add(crop(exported, entry.frameX, entry.frameY,
                    entry.frameWidth, entry.frameHeight));
        }
        write(stack(List.of(row(top), row(bottom))), 6, "nature-tiles-shipped-vs-exported.png");
    }

    /** Each field tiled three by three: shipped, exported untreated, exported. */
    private static void writeGroundTiling(BufferedImage treated, BufferedImage untreated,
                                          TilesetDocument document) throws IOException {
        BufferedImage shipped = ImageIO.read(SHIPPED.toFile());
        assertNotNull(shipped, "cannot read " + SHIPPED);
        SpriteSheetFrames were = SpriteSheetSlicer.slice(shipped);
        List<BufferedImage> rows = new ArrayList<>();
        for (int i = 0; i < document.entries.size(); i++) {
            TilesetExport.Entry entry = document.entries.get(i);
            if (!"ground".equals(entry.layer)) continue;
            SpriteSheetFrames.Frame was = were.frames[i];
            rows.add(row(List.of(
                    tiled(shipped, was.x, was.y, was.w, was.h),
                    tiled(untreated, entry.frameX, entry.frameY,
                            entry.frameWidth, entry.frameHeight),
                    tiled(treated, entry.frameX, entry.frameY,
                            entry.frameWidth, entry.frameHeight))));
        }
        write(stack(rows), 2, "nature-tiles-tiling.png");
    }

    /** One frame's drawn part repeated three by three, as the map would lay it. */
    private static BufferedImage tiled(BufferedImage atlas, int x, int y, int w, int h) {
        BufferedImage cell = crop(atlas, x + INSET, y + INSET, w - 2 * INSET, h - 2 * INSET);
        BufferedImage out = new BufferedImage(cell.getWidth() * 3, cell.getHeight() * 3,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        for (int ty = 0; ty < 3; ty++) {
            for (int tx = 0; tx < 3; tx++) {
                g.drawImage(cell, tx * cell.getWidth(), ty * cell.getHeight(), null);
            }
        }
        g.dispose();
        return out;
    }

    /** One frame over magenta, magnified, so a silhouette can be looked at. */
    private static BufferedImage crop(BufferedImage atlas, int x, int y, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int argb = atlas.getRGB(x + dx, y + dy);
                out.setRGB(dx, dy, (argb >>> 24) < 128 ? MAGENTA : argb);
            }
        }
        return out;
    }

    private static BufferedImage row(List<BufferedImage> cells) {
        int height = 0;
        int width = PAD;
        for (BufferedImage cell : cells) {
            height = Math.max(height, cell.getHeight());
            width += cell.getWidth() + PAD;
        }
        BufferedImage out = blank(width, height + 2 * PAD);
        Graphics2D g = out.createGraphics();
        int cursor = PAD;
        for (BufferedImage cell : cells) {
            g.drawImage(cell, cursor, PAD, null);
            cursor += cell.getWidth() + PAD;
        }
        g.dispose();
        return out;
    }

    private static BufferedImage stack(List<BufferedImage> rows) {
        int width = 0;
        int height = 0;
        for (BufferedImage strip : rows) {
            width = Math.max(width, strip.getWidth());
            height += strip.getHeight();
        }
        BufferedImage out = blank(width, height);
        Graphics2D g = out.createGraphics();
        int cursor = 0;
        for (BufferedImage strip : rows) {
            g.drawImage(strip, 0, cursor, null);
            cursor += strip.getHeight();
        }
        g.dispose();
        return out;
    }

    private static BufferedImage blank(int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(PAPER));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return out;
    }

    private static void write(BufferedImage sheet, int magnify, String name) throws IOException {
        BufferedImage big = new BufferedImage(sheet.getWidth() * magnify,
                sheet.getHeight() * magnify, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(sheet, 0, 0, big.getWidth(), big.getHeight(), null);
        g.dispose();
        Files.createDirectories(REVIEW);
        ImageIO.write(big, "png", REVIEW.resolve(name).toFile());
    }
}
