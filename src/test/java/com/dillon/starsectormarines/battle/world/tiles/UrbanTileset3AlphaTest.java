package com.dillon.starsectormarines.battle.world.tiles;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each frame of {@code urban-tileset-3} must cut out of its own box, and
 * that the relief beside it is this atlas's relief.
 *
 * <p>Alpha is what this strip is: the loader has no coordinates for it and finds
 * the seven frames by scanning for gaps in the alpha channel, so a silhouette
 * that goes wrong here does not draw a slightly worse tile — it renumbers the
 * frames and every {@code urban3.*} id resolves to a different picture.
 * {@link TileRegistryParityTest} pins the ids, layers and frame indices and
 * stays green through all of that, because a tileset cannot see its own atlas.
 *
 * <p>The statements below are shapes rather than totals. A total is blind to
 * every way a silhouette can be wrong — a plugged void and a tighter outline are
 * opposite-signed errors in one number and they cancel — so what is asserted is
 * structure: a paving surface has no holes in it, a manhole cover is round, the
 * bench stands on two legs, and nothing floats beside any of them.
 *
 * <p>Unlike {@code UrbanTilesetAlphaTest}, these are not measured from the sheet
 * being replaced. That sheet's alpha came from dilating a brightness mask, which
 * squared off the culvert's rim and filled the gap under the bench, so it is not
 * the authority on what these pieces are drawn as. The authority is the raw art,
 * and these are read off it.
 */
class UrbanTileset3AlphaTest {

    private static final String TILESET = "data/tilesets/urban-tileset-3.tileset.json";
    private static final Path RAW_SHEET =
            Paths.get("art-source", "tilesets", "urban-tileset-3.raw.png");

    /** Frame order is the sheet's only address, so the ids are listed in it. */
    private static final List<String> FRAMES = List.of(
            "urban3.street-square",
            "urban3.street-irregular",
            "urban3.sidewalk",
            "urban3.sidewalk-corner",
            "urban3.culvert",
            "urban3.bench-s",
            "urban3.bench-e");

    /** How much of a frame the renderer never draws. */
    private static final int GROUND_INSET = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;

    @Test
    void theLoaderFindsExactlySevenFramesInTheOrderTheTilesetPins() throws Exception {
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas());
        assertEquals(FRAMES.size(), frames.frames.length,
                "the strip no longer slices into the frames its tiles pin, so every id from "
                        + "the first difference on names a different picture");
        TileRegistry registry = registry();
        for (int frame = 0; frame < FRAMES.size(); frame++) {
            TileDef tile = registry.tile(FRAMES.get(frame));
            assertNotNull(tile, "urban-tileset-3 no longer defines " + FRAMES.get(frame));
            assertEquals(frame, tile.frame, FRAMES.get(frame) + " moved to another frame");
        }
        for (int i = 1; i < frames.frames.length; i++) {
            SpriteSheetFrames.Frame previous = frames.frames[i - 1];
            assertTrue(frames.frames[i].x > previous.x + previous.w,
                    "frame " + i + " overlaps the one before it");
        }
    }

    /**
     * A paving surface is a filled rectangle wherever it is drawn.
     *
     * <p>This is the ground half of "the voids a piece is drawn around must stay
     * open": a street tile is drawn around nothing, so every hole in one is a
     * hole in the road. It is the shape a flood leaking down a dark grout line
     * destroys, and the shape a total cannot see — the four slabs of the
     * sidewalk keyed apart at their joints still reads as 98% opaque.
     *
     * <p>Asserted inside the inset the renderer crops, because that is the part
     * that is drawn; the outer ring carries the art's own rounded corners and
     * never reaches the screen.
     */
    @Test
    void everyPavingFrameIsSolidWhereItIsDrawn() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        TileRegistry registry = registry();
        for (int frame = 0; frame < FRAMES.size(); frame++) {
            TileDef tile = registry.tile(FRAMES.get(frame));
            if (!tile.isGround()) continue;
            SpriteSheetFrames.Frame box = frames.frames[frame];
            int clear = 0;
            for (int y = GROUND_INSET; y < box.h - GROUND_INSET; y++) {
                for (int x = GROUND_INSET; x < box.w - GROUND_INSET; x++) {
                    if (!opaque(atlas, box.x + x, box.y + y)) clear++;
                }
            }
            assertEquals(0, clear, FRAMES.get(frame) + " has " + clear
                    + " see-through pixels inside the part of it that is drawn, which paves the "
                    + "street with holes");
        }
    }

    /**
     * The culvert is a disc.
     *
     * <p>A prop that exports as its own bounding box is the failure the alpha
     * law exists for: it loads, it validates, it draws, and it draws a black
     * square on the road. The manhole cover is the piece on this sheet that says
     * so most clearly — it fills the middle of its frame and touches the middle
     * of each side, and every corner of it is sky.
     */
    @Test
    void theCulvertIsARoundCoverRatherThanItsOwnBoundingBox() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames.Frame box = SpriteSheetSlicer.slice(atlas).frames[
                FRAMES.indexOf("urban3.culvert")];
        for (int[] corner : new int[][]{{0, 0}, {box.w - 4, 0}, {0, box.h - 4}, {box.w - 4, box.h - 4}}) {
            int opaque = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (opaque(atlas, box.x + corner[0] + x, box.y + corner[1] + y)) opaque++;
                }
            }
            assertEquals(0, opaque, "urban3.culvert has " + opaque + " opaque pixels in the corner "
                    + "at " + corner[0] + "," + corner[1] + ", where a round cover has none");
        }
        assertTrue(opaque(atlas, box.x + box.w / 2, box.y + box.h / 2), "urban3.culvert is hollow");
        for (int[] edge : new int[][]{{box.w / 2, 1}, {box.w / 2, box.h - 2},
                {1, box.h / 2}, {box.w - 2, box.h / 2}}) {
            assertTrue(opaque(atlas, box.x + edge[0], box.y + edge[1]),
                    "urban3.culvert does not reach the middle of one of its own sides, so its "
                            + "silhouette is not the cover that was drawn");
        }
    }

    /**
     * The void this sheet is actually drawn around: the gap between the side-on
     * bench's two legs.
     *
     * <p>It is the one place on the strip where daylight passes through a piece,
     * so it is the one rectangle that can be stated. Plugging it puts a brick
     * under the bench, and the opacity figure does not move enough to notice.
     * Read off the raw art rather than off the atlas being replaced, which
     * filled it.
     */
    @Test
    void theGapBetweenTheBenchesLegsStaysOpen() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames.Frame box = SpriteSheetSlicer.slice(atlas).frames[
                FRAMES.indexOf("urban3.bench-e")];
        int opaque = 0;
        for (int y = box.h - 2; y < box.h; y++) {
            for (int x = box.w / 3; x < 2 * box.w / 3; x++) {
                if (opaque(atlas, box.x + x, box.y + y)) opaque++;
            }
        }
        assertTrue(opaque <= 2, "urban3.bench-e has " + opaque + " opaque pixels between its legs, "
                + "where the bench is drawn standing on two of them");
    }

    /**
     * A piece is one object.
     *
     * <p>Anything opaque not joined to it draws as dirt floating beside it, and
     * the two ways this sheet produces such dirt are a background speck the key
     * admitted and a sliver of the neighbouring piece the band split left
     * behind. Neither shows in a total: both are smaller than the resampling
     * noise on the outline they compete with. Every frame here is one connected
     * body, so the budget is two pixels rather than a per-piece allowance.
     */
    @Test
    void nothingFloatsBesideAPiece() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        for (int frame = 0; frame < FRAMES.size(); frame++) {
            SpriteSheetFrames.Frame box = frames.frames[frame];
            boolean[][] opaque = new boolean[box.h][box.w];
            for (int y = 0; y < box.h; y++) {
                for (int x = 0; x < box.w; x++) {
                    opaque[y][x] = opaque(atlas, box.x + x, box.y + y);
                }
            }
            int stray = detachedArea(opaque);
            assertTrue(stray <= 2, FRAMES.get(frame) + " has " + stray
                    + " pixels of opaque art detached from its body, which draws as dirt beside it");
        }
    }

    /** Opaque area outside the largest connected body. */
    private static int detachedArea(boolean[][] opaque) {
        int height = opaque.length;
        int width = height == 0 ? 0 : opaque[0].length;
        boolean[][] seen = new boolean[height][width];
        int total = 0;
        int largest = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque[y][x] || seen[y][x]) continue;
                int area = 0;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{y, x});
                seen[y][x] = true;
                while (!stack.isEmpty()) {
                    int[] at = stack.pop();
                    area++;
                    for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int ny = at[0] + step[0];
                        int nx = at[1] + step[1];
                        if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;
                        if (!opaque[ny][nx] || seen[ny][nx]) continue;
                        seen[ny][nx] = true;
                        stack.push(new int[]{ny, nx});
                    }
                }
                total += area;
                largest = Math.max(largest, area);
            }
        }
        return total - largest;
    }

    /**
     * The raw sheet carries its own alpha, which is what makes this atlas
     * re-derivable at all: an opaque plate has not recorded what is background
     * and what is art, and no authoring document can supply that.
     */
    @Test
    void theRawSheetCarriesItsOwnAlpha() throws Exception {
        BufferedImage raw = ImageIO.read(RAW_SHEET.toFile());
        assertNotNull(raw, "cannot read " + RAW_SHEET);
        assertTrue(raw.getColorModel().hasAlpha(),
                RAW_SHEET + " is opaque; every frame would export as a black square");
        long clear = 0;
        for (int y = 0; y < raw.getHeight(); y++) {
            for (int x = 0; x < raw.getWidth(); x++) {
                if ((raw.getRGB(x, y) >>> 24) < 128) clear++;
            }
        }
        double fraction = clear / (double) (raw.getWidth() * raw.getHeight());
        assertTrue(fraction > 0.70 && fraction < 0.90,
                RAW_SHEET + " is " + Math.round(fraction * 100) + "% transparent, which is not a "
                        + "keyed background: seven pieces on this much canvas left it 81% clear");
    }

    /**
     * The short label and the free-text description each tile carries.
     *
     * <p>They are documentation and nothing reads them at runtime, which is
     * exactly why they go missing: an export with no field for them drops them
     * and every other test stays green. Losing the sentence that says a bench
     * faces south is the same class of loss as dropping how high it stops a
     * shot.
     */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("urban3.street-square", "street-square");
        LABELS.put("urban3.street-irregular", "street-irregular");
        LABELS.put("urban3.sidewalk", "sidewalk");
        LABELS.put("urban3.sidewalk-corner", "sidewalk-corner");
        LABELS.put("urban3.culvert", "culvert");
        LABELS.put("urban3.bench-s", "bench-s");
        LABELS.put("urban3.bench-e", "bench-e");
    }

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("urban3.street-square",
                "uniform square paver — primary road surface");
        DESCRIPTIONS.put("urban3.street-irregular",
                "irregular cobbled paver — alt road surface "
                        + "(use one or the other per road, mixing reads as patchy)");
        DESCRIPTIONS.put("urban3.sidewalk", "plain sidewalk slab — straight runs");
        DESCRIPTIONS.put("urban3.sidewalk-corner",
                "alt sidewalk slab — corners (two perpendicular non-sidewalk neighbors)");
        DESCRIPTIONS.put("urban3.culvert", "drain culvert doodad — placed at curb edge");
        DESCRIPTIONS.put("urban3.bench-s",
                "bench facing south — back of seat read from north sidewalk");
        DESCRIPTIONS.put("urban3.bench-e",
                "bench facing east — mirror at render time for a west-facing variant");
    }

    @Test
    void everyTileKeepsTheNameAndDescriptionItWasAuthoredWith() throws Exception {
        TileRegistry registry = registry();
        for (String id : FRAMES) {
            TileDef tile = registry.tile(id);
            assertNotNull(tile, "urban-tileset-3 no longer defines " + id);
            assertEquals(LABELS.get(id), tile.name, id + " lost or changed its name");
            assertEquals(DESCRIPTIONS.get(id), tile.description,
                    id + " lost or changed its description");
        }
    }

    /**
     * What the tile-map derivation writes where a sheet is not a tile.
     *
     * <p>{@code AtlasTileMapDeriver} fills everything outside a cell with flat
     * height and a straight-up normal, so the sheets' non-flat region is exactly
     * the union of the frames they were derived from. Restated here rather than
     * shared, because that class is on the build-time tool classpath and must
     * not reach the shipped jar.
     */
    private static final int FLAT_HEIGHT = 0x808080;
    private static final int FLAT_NORMAL = 0x8080FF;

    /**
     * The relief beside the atlas is <em>this</em> atlas's relief.
     *
     * <p>{@code GroundMicroHeightSampler} finds {@code _height} and {@code
     * _normal} by naming convention and samples them at the coordinates it
     * sliced off the albedo. Nothing connects the three files, so re-packing the
     * albedo without re-deriving them leaves the parallax reading the relief of
     * a sheet that no longer exists — silently, because a stale companion is a
     * valid PNG of the right size and every other test passes.
     *
     * <p>The derivation leaves the gutters flat, so the companions state their
     * own frame boxes and those must be the albedo's. That is a shape the fix
     * for a stale sheet cannot fake: nothing but re-running
     * {@code gradlew.bat :asset-pipeline:deriveTileMaps} moves them.
     */
    @Test
    void theHeightAndNormalSheetsAreDerivedFromThisAtlasFrames() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        for (String suffix : List.of("_height", "_normal")) {
            BufferedImage companion = image("mod/graphics/tilesets/urban-tileset-3" + suffix + ".png");
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

    /** Sanity: an atlas the loader cannot split into frames is not a strip at all. */
    @Test
    void theStripKeepsAGutterWideEnoughToSplitOn() throws Exception {
        BufferedImage atlas = atlas();
        SpriteSheetFrames frames = SpriteSheetSlicer.slice(atlas);
        for (int i = 1; i < frames.frames.length; i++) {
            SpriteSheetFrames.Frame previous = frames.frames[i - 1];
            int gap = frames.frames[i].x - (previous.x + previous.w);
            assertFalse(gap < SpriteSheetSlicer.MIN_GAP,
                    "frames " + (i - 1) + " and " + i + " are " + gap + " columns apart, under the "
                            + SpriteSheetSlicer.MIN_GAP + " the loader splits on");
        }
    }

    private static boolean opaque(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) >= 128;
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
