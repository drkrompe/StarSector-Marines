package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.tools.tilesetauthoring.SheetSlicer;
import com.dillon.starsectormarines.tools.tilesetauthoring.TilesetDocument;
import com.dillon.starsectormarines.tools.tilesetauthoring.TilesetExport;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each piece of {@code nature-tiles} must cut out of its own box, and that
 * the relief beside the shipped atlas is that atlas's relief.
 *
 * <p>Alpha is what this sheet is: the loader has no coordinates for it and finds
 * the twenty frames by scanning for gaps, so a silhouette that goes wrong does
 * not draw a slightly worse tile — it renumbers the frames and every
 * {@code nature.*} id resolves to a different picture.
 * {@link TileRegistryParityTest} pins the ids, layers, frame indices and cover
 * and stays green through all of that, because a tileset cannot see its own
 * atlas.
 *
 * <p>The statements are shapes rather than totals. A total is blind to every way
 * a silhouette can be wrong, because the errors are signed and they cancel — so
 * what is asserted is structure: a field is a filled rectangle, a plant is not
 * its own bounding box, and a rock cluster is exactly the stones it was drawn
 * with. That last one is the check this sheet needs most: its rocks are dark
 * grey drawn against black, so the threshold that correctly keys grass eats
 * whole pebbles, and an eroded scatter is invisible in a coverage figure.
 *
 * <p>The boxes come from the authoring document rather than from a table here.
 * The document is what says which pixels are which piece, and a second copy of
 * that would be a coordinate to keep in step.
 */
class NatureTilesAlphaTest {

    private static final String TILESET = "data/tilesets/nature-tiles.tileset.json";
    private static final Path TILESETS = Paths.get("art-source", "tilesets");
    private static final Path RAW_SHEET = TILESETS.resolve("nature-tiles.raw.png");
    private static final Path DOCUMENT = TILESETS.resolve("nature-tiles.tileset-authoring.json");

    /** Frame order is the sheet's only address, so the ids are listed in it. */
    private static final List<String> FRAMES = List.of(
            "nature.grass-1", "nature.grass-2", "nature.grass-3", "nature.grass-4",
            "nature.grass-5", "nature.grass-6", "nature.grass-7", "nature.grass-8",
            "nature.dirt-1", "nature.dirt-2", "nature.dirt-3", "nature.dirt-4",
            "nature.dirt-5", "nature.dirt-6", "nature.dirt-7", "nature.dirt-8",
            "nature.sand", "nature.water-1", "nature.water-2",
            "nature.shrub-1", "nature.shrub-2", "nature.tuft-1", "nature.tuft-2",
            "nature.shrub-3", "nature.tuft-3",
            "nature.rock-small-1", "nature.rock-small-2",
            "nature.rock-medium-1", "nature.rock-medium-2",
            "nature.rock-large-1", "nature.rock-large-2", "nature.rock-large-3");

    /**
     * How many separate bodies each piece is drawn as.
     *
     * <p>Authored rather than measured, the way a void is: whether a prop is one
     * stone or three is a judgement about the object, and it is the judgement a
     * key can silently overrule in either direction. Too high a threshold
     * dissolves a pebble and the scatter loses a stone; too low a one admits a
     * speck of matte and it gains one. Neither moves a coverage figure enough to
     * notice — the smallest stone here is two percent of its piece.
     */
    private static final Map<String, Integer> BODIES = new LinkedHashMap<>();

    static {
        for (String id : FRAMES) BODIES.put(id, 1);
        BODIES.put("nature.rock-small-1", 3);
        BODIES.put("nature.rock-small-2", 3);
        BODIES.put("nature.rock-medium-2", 2);
    }

    /** The short label each tile carries; documentation, and the first thing an export drops. */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("nature.grass-1", "grass-1");
        LABELS.put("nature.grass-2", "grass-2");
        LABELS.put("nature.grass-3", "grass-3");
        LABELS.put("nature.grass-4", "grass-4");
        LABELS.put("nature.grass-5", "grass-5");
        LABELS.put("nature.grass-6", "grass-6");
        LABELS.put("nature.grass-7", "grass-7");
        LABELS.put("nature.grass-8", "grass-8");
        LABELS.put("nature.dirt-1", "dirt-1");
        LABELS.put("nature.dirt-2", "dirt-2");
        LABELS.put("nature.dirt-3", "dirt-3");
        LABELS.put("nature.dirt-4", "dirt-4");
        LABELS.put("nature.dirt-5", "dirt-5");
        LABELS.put("nature.dirt-6", "dirt-6");
        LABELS.put("nature.dirt-7", "dirt-7");
        LABELS.put("nature.dirt-8", "dirt-8");
        LABELS.put("nature.sand", "sand");
        LABELS.put("nature.water-1", "water");
        LABELS.put("nature.water-2", "water-alt");
        LABELS.put("nature.shrub-1", "shrub");
        LABELS.put("nature.shrub-2", "shrub-alt");
        LABELS.put("nature.tuft-1", "grass-tuft");
        LABELS.put("nature.tuft-2", "grass-tuft-alt");
        LABELS.put("nature.shrub-3", "shrub-variant");
        LABELS.put("nature.tuft-3", "grass-tuft-tall");
        LABELS.put("nature.rock-small-1", "rocks-small");
        LABELS.put("nature.rock-small-2", "rocks-small-2");
        LABELS.put("nature.rock-medium-1", "rock-medium");
        LABELS.put("nature.rock-medium-2", "rock-medium-2");
        LABELS.put("nature.rock-large-1", "rock-large");
        LABELS.put("nature.rock-large-2", "rock-large-2");
        LABELS.put("nature.rock-large-3", "rock-large-3");
    }

    /**
     * The raw sheet carries its own alpha, which is what makes the atlas
     * re-derivable at all: an opaque plate has not recorded what is background
     * and what is art, and no authoring document can supply that.
     */
    @Test
    void theRawSheetCarriesItsOwnAlpha() throws Exception {
        BufferedImage raw = rawSheet();
        long clear = 0;
        for (int y = 0; y < raw.getHeight(); y++) {
            for (int x = 0; x < raw.getWidth(); x++) {
                if ((raw.getRGB(x, y) >>> 24) < 128) clear++;
            }
        }
        double fraction = clear / (double) (raw.getWidth() * raw.getHeight());
        assertTrue(fraction > 0.85 && fraction < 0.95, RAW_SHEET + " is "
                + Math.round(fraction * 100) + "% transparent, which is not a keyed background: "
                + "twenty pieces drawn in a band across this much canvas left it 92% clear");
    }

    /**
     * A field is a filled rectangle wherever it is drawn.
     *
     * <p>This is the ground half of "the voids a piece is drawn around must stay
     * open": a field is drawn around nothing, so every hole in one is a hole in
     * the ground, repeated in every cell the map paves with it. It is the shape a
     * flood down a dark seam destroys and the shape a total cannot see.
     */
    @Test
    void everyFieldIsSolidWhereItIsDrawn() throws Exception {
        BufferedImage raw = rawSheet();
        for (TilesetExport.Entry entry : document().entries) {
            if (!"ground".equals(entry.layer)) continue;
            SheetSlicer.Piece piece = entry.piece;
            int clear = 0;
            for (int y = 0; y < piece.height(); y++) {
                for (int x = 0; x < piece.width(); x++) {
                    if (!opaque(raw, piece.x() + x, piece.y() + y)) clear++;
                }
            }
            assertEquals(0, clear, entry.id + " has " + clear + " see-through pixels inside the "
                    + "rectangle it is drawn on, which paves the ground with holes");
        }
    }

    /**
     * A plant is not its own bounding box.
     *
     * <p>A prop exported as the rectangle it was drawn in is the failure the
     * alpha law exists for: it loads, it validates, it draws, and it draws a
     * black square on the grass. Every plant here is a rosette or a tuft with
     * sky in all four corners, which is the cheapest statement of that shape
     * that cannot be satisfied by a total.
     */
    @Test
    void everyPlantHasSkyInAllFourCornersOfItsBox() throws Exception {
        BufferedImage raw = rawSheet();
        for (TilesetExport.Entry entry : document().entries) {
            if (!"plant".equals(entry.layer)) continue;
            SheetSlicer.Piece piece = entry.piece;
            for (int[] corner : new int[][]{{0, 0}, {piece.width() - 5, 0},
                    {0, piece.height() - 5}, {piece.width() - 5, piece.height() - 5}}) {
                int opaque = 0;
                for (int y = 0; y < 5; y++) {
                    for (int x = 0; x < 5; x++) {
                        if (opaque(raw, piece.x() + corner[0] + x, piece.y() + corner[1] + y)) {
                            opaque++;
                        }
                    }
                }
                assertEquals(0, opaque, entry.id + " has " + opaque + " opaque pixels in the "
                        + "corner at " + corner[0] + "," + corner[1] + ", where a plant has sky");
            }
        }
    }

    /**
     * A prop is exactly the bodies it was drawn as.
     *
     * <p>Both directions matter and neither shows in a coverage figure. A rock
     * scatter that loses a pebble to an over-eager key still reads as a rock
     * scatter; one that gains a speck of admitted matte draws grit floating on
     * the grass beside it. The count is the one number that moves either way.
     */
    @Test
    void everyPieceIsExactlyTheBodiesItWasDrawnAs() throws Exception {
        BufferedImage raw = rawSheet();
        for (TilesetExport.Entry entry : document().entries) {
            SheetSlicer.Piece piece = entry.piece;
            boolean[][] opaque = new boolean[piece.height()][piece.width()];
            for (int y = 0; y < piece.height(); y++) {
                for (int x = 0; x < piece.width(); x++) {
                    opaque[y][x] = opaque(raw, piece.x() + x, piece.y() + y);
                }
            }
            assertEquals(BODIES.get(entry.id).intValue(), bodies(opaque), entry.id
                    + " is drawn as " + BODIES.get(entry.id) + " connected bodies but its alpha "
                    + "holds " + bodies(opaque) + ": the key has eaten one of them, or admitted "
                    + "dust that will draw beside it");
        }
    }

    /** Every 8-connected opaque body in a piece. */
    private static int bodies(boolean[][] opaque) {
        int height = opaque.length;
        int width = height == 0 ? 0 : opaque[0].length;
        boolean[][] seen = new boolean[height][width];
        int found = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque[y][x] || seen[y][x]) continue;
                found++;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{y, x});
                seen[y][x] = true;
                while (!stack.isEmpty()) {
                    int[] at = stack.pop();
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int ny = at[0] + dy;
                            int nx = at[1] + dx;
                            if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;
                            if (!opaque[ny][nx] || seen[ny][nx]) continue;
                            seen[ny][nx] = true;
                            stack.push(new int[]{ny, nx});
                        }
                    }
                }
            }
        }
        return found;
    }

    @Test
    void theShippedAtlasSlicesIntoTheFramesItsTilesPin() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        assertEquals(FRAMES.size(), frames.frames.length,
                "the strip no longer slices into the frames its tiles pin, so every id from the "
                        + "first difference on names a different picture");
        TileRegistry registry = registry();
        for (int frame = 0; frame < FRAMES.size(); frame++) {
            TileDef tile = registry.tile(FRAMES.get(frame));
            assertNotNull(tile, "nature-tiles no longer defines " + FRAMES.get(frame));
            assertEquals(frame, tile.frame, FRAMES.get(frame) + " moved to another frame");
        }
        for (int i = 1; i < frames.frames.length; i++) {
            SpriteSheetFrames.Frame previous = frames.frames[i - 1];
            int gap = frames.frames[i].x - (previous.x + previous.w);
            assertTrue(gap >= SpriteSheetSlicer.MIN_GAP, "frames " + (i - 1) + " and " + i
                    + " are " + gap + " columns apart, under the " + SpriteSheetSlicer.MIN_GAP
                    + " the loader splits on");
        }
    }

    /**
     * The short label a reader of the catalog sees beside each picture.
     *
     * <p>Documentation, which nothing reads at runtime, which is exactly why it
     * goes missing: an export with no field for it drops it and every other test
     * stays green.
     */
    @Test
    void everyTileKeepsTheNameItWasAuthoredWith() throws Exception {
        TileRegistry registry = registry();
        for (String id : FRAMES) {
            TileDef tile = registry.tile(id);
            assertNotNull(tile, "nature-tiles no longer defines " + id);
            assertEquals(LABELS.get(id), tile.name, id + " lost or changed its name");
        }
    }

    /**
     * What the tile-map derivation writes where a sheet is not a tile.
     *
     * <p>{@code AtlasTileMapDeriver} fills everything outside a cell with flat
     * height and a straight-up normal, so the sheets' non-flat region is exactly
     * the union of the frames they were derived from. Restated here rather than
     * shared, because that class is on the build-time tool classpath and must not
     * reach the shipped jar.
     */
    private static final int FLAT_HEIGHT = 0x808080;
    private static final int FLAT_NORMAL = 0x8080FF;

    /**
     * The relief beside the atlas is <em>this</em> atlas's relief.
     *
     * <p>{@code GroundMicroHeightSampler} finds {@code _height} and
     * {@code _normal} by naming convention and samples them at the coordinates it
     * sliced off the albedo. Nothing connects the three files, so re-packing the
     * albedo without re-deriving them leaves the parallax reading the relief of a
     * sheet that no longer exists — silently, because a stale companion is a
     * valid PNG of the right size and every other test passes.
     */
    @Test
    void theHeightAndNormalSheetsAreDerivedFromThisAtlasFrames() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        for (String suffix : List.of("_height", "_normal")) {
            BufferedImage companion = image("mod/graphics/tilesets/nature-tiles" + suffix + ".png");
            assertEquals(atlas.getWidth(), companion.getWidth(), suffix + " is a different width");
            assertEquals(atlas.getHeight(), companion.getHeight(), suffix + " is a different height");
            int flat = "_height".equals(suffix) ? FLAT_HEIGHT : FLAT_NORMAL;
            for (int x = 0; x < companion.getWidth(); x++) {
                boolean derived = false;
                for (int y = 0; y < companion.getHeight() && !derived; y++) {
                    derived = (companion.getRGB(x, y) & 0xFFFFFF) != flat;
                }
                assertEquals(insideAFrame(frames, x), derived, "column " + x + " of " + suffix
                        + " is " + (derived ? "derived but sits in a gutter of the albedo"
                        : "flat but sits inside one of the albedo's frames")
                        + ", so the relief is sampled at pixels this atlas does not have there"
                        + " — re-run gradlew.bat :asset-pipeline:deriveTileMaps");
            }
        }
    }

    private static boolean insideAFrame(SpriteSheetFrames frames, int x) {
        for (SpriteSheetFrames.Frame frame : frames.frames) {
            if (x >= frame.x && x < frame.x + frame.w) return true;
        }
        return false;
    }

    private static boolean opaque(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) >= 128;
    }

    private static TilesetDocument document() throws Exception {
        return TilesetDocument.read(DOCUMENT);
    }

    private static BufferedImage rawSheet() throws Exception {
        BufferedImage raw = ImageIO.read(RAW_SHEET.toFile());
        assertNotNull(raw, "cannot read " + RAW_SHEET);
        assertTrue(raw.getColorModel().hasAlpha(),
                RAW_SHEET + " is opaque; every frame would export as a black rectangle");
        return raw;
    }

    private static BufferedImage atlas() throws Exception {
        return image("mod/" + new JSONObject(read(TILESET)).getString("sheet"));
    }

    private static BufferedImage image(String path) throws Exception {
        String[] parts = path.split("/");
        BufferedImage image = ImageIO.read(Paths.get(parts[0],
                Arrays.copyOfRange(parts, 1, parts.length)).toFile());
        assertNotNull(image, "cannot read " + path);
        return image;
    }

    private static TileRegistry registry() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject(read(TILESET)));
        return registry;
    }

    private static String read(String modRelativePath) {
        Path path = Paths.get("mod", modRelativePath.split("/"));
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
