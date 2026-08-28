package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each piece of {@code urban-tileset} must still cut out of its cell.
 *
 * <p>Alpha is the one property of this sheet that no other check can see. Ids,
 * cover and half-height all survive an export that packs every piece as an
 * opaque black-backed square — {@code UrbanTilesetContractTest} stays green
 * through it, the catalog loads, the map draws, and every crate is a black
 * tile. That is the failure this pins, and the numbers below are the
 * hand-maintained atlas's own, measured cell by cell before it was first
 * generated from {@code art-source/tilesets/urban-tileset.raw.png}.
 *
 * <p>Per piece rather than over the sheet, because the two atlases are packed
 * differently and only the same named piece cut from each is comparable. The
 * tolerance is wide on purpose: the source is resampled from a 123x125 raw cell
 * to a 32px one, so a silhouette's edge lands a fraction of a cell differently
 * and a tighter or looser cut of a few percent is a judgement rather than a
 * defect. What it still catches is the whole class of failure that matters — a
 * prop exporting solid, a doorway exporting solid, a floor tile exporting with
 * a transparent gutter round it, or a flood leaking through a dark seam and
 * punching a hole in the middle of a shelf.
 */
class UrbanTilesetAlphaTest {

    /**
     * How far a piece may sit from the opacity the hand-maintained sheet had.
     *
     * <p>The largest reviewed difference is 0.085 ({@code doodad.chest-2}, whose
     * generated cut is the tighter of the two); the failure this guards against
     * moves a piece by 0.24 to 0.88.
     */
    private static final double TOLERANCE = 0.10;

    private static final String SHEET = "graphics/tilesets/urban-tileset.png";
    private static final String TILESET = "data/tilesets/urban-tileset.tileset.json";
    private static final Path RAW_SHEET =
            Paths.get("art-source", "tilesets", "urban-tileset.raw.png");

    /** id -> fraction of its cell that is opaque. */
    private static final Map<String, Double> DOODADS = new LinkedHashMap<>();

    static {
        DOODADS.put("doodad.chair-south-yellow", 0.389);
        DOODADS.put("doodad.chair-south-green", 0.389);
        DOODADS.put("doodad.box", 0.764);
        DOODADS.put("doodad.crate", 0.764);
        DOODADS.put("doodad.door-closed", 1.000);
        DOODADS.put("doodad.desk-1", 0.648);
        DOODADS.put("doodad.chest-1", 0.725);
        DOODADS.put("doodad.chest-2", 0.725);
        DOODADS.put("doodad.shelf-empty", 0.969);
        DOODADS.put("doodad.shelf-1", 0.969);
        DOODADS.put("doodad.shelf-2", 0.969);
        DOODADS.put("doodad.shelf-3", 0.969);
        DOODADS.put("doodad.desk-2", 0.648);
        DOODADS.put("doodad.decal-rubble-1", 0.217);
        DOODADS.put("doodad.decal-rubble-2", 0.213);
        DOODADS.put("doodad.decal-rubble-3", 0.167);
        DOODADS.put("doodad.decal-rubble-4", 0.284);
        DOODADS.put("doodad.shelf-dam-1", 0.969);
        DOODADS.put("doodad.shelf-dam-2", 0.969);
        DOODADS.put("doodad.desk-dam", 0.610);
        DOODADS.put("doodad.box-dam", 0.739);
        DOODADS.put("doodad.chair-s-yellow-dam", 0.327);
        DOODADS.put("doodad.chair-s-green-dam", 0.316);
        DOODADS.put("doodad.fl-grate-1", 1.000);
        DOODADS.put("doodad.fl-striped-yellow", 1.000);
        DOODADS.put("doodad.fl-grate-2", 1.000);
    }

    /**
     * Block id -> the opacity every cell its layout can resolve to must have.
     *
     * <p>Every cell of a 3x3 patch, because a tiled surface with a transparent
     * border draws as a grid of holes and one perforated cell is enough to show
     * it. The enclosed case a wall block resolves to {@code null} is not a cell
     * and is not measured — that is what {@code fillRgb} is for.
     */
    private static final Map<String, Double> BLOCKS = new LinkedHashMap<>();

    static {
        BLOCKS.put("urban.wall", 1.000);
        BLOCKS.put("urban.floor", 1.000);
        BLOCKS.put("urban.rubble", 1.000);
        // The slim overhead bar of an open doorway: almost all of it is the gap.
        BLOCKS.put("urban.door-open", 0.125);
    }

    /**
     * {@code "<block> <dcol>,<drow>"} -> opacity, where one cell differs from
     * the rest of its patch.
     *
     * <p>A wall block's middle cell is empty art: {@code wall-3x3} only paints
     * {@code fillRgb} for the fully-enclosed mask, so a wall stub with no wall
     * neighbours still resolves to the middle and draws nothing. That hole is
     * the sheet as it has always been, not something an export introduced.
     */
    private static final Map<String, Double> BLOCK_CELLS = Map.of("urban.wall 1,1", 0.000);

    /**
     * Piece -> a rectangle {@code {x0, y0, x1, y1}} of its cell, exclusive of
     * {@code x1/y1}, that must be see-through.
     *
     * <p>These are the voids a piece is drawn around: the gap a chair stands
     * either side of, the space under a desk top, the opening below a doorway's
     * lintel. They are the half of a silhouette that total opacity cannot see —
     * plug one and the piece's own cut-out edge gets a little tighter somewhere
     * else, the two cancel, and the figure stays inside tolerance while the
     * chair draws with a black brick between its legs. Measured from the
     * hand-maintained atlas, and every rectangle here is one it left fully
     * transparent.
     *
     * <p>Each sits at least two pixels clear of the art around it, so no
     * resampling of an outline can reach into one; a couple of stray pixels is
     * the whole budget, and a filled pocket is an order of magnitude more.
     */
    private static final Map<String, int[]> VOIDS = new LinkedHashMap<>();

    static {
        // Between the legs, under the seat.
        VOIDS.put("doodad.chair-south-yellow", new int[]{14, 27, 18, 30});
        VOIDS.put("doodad.chair-south-green", new int[]{14, 27, 18, 30});
        VOIDS.put("doodad.chair-s-yellow-dam", new int[]{14, 27, 18, 30});
        VOIDS.put("doodad.chair-s-green-dam", new int[]{14, 27, 18, 30});
        // Under the desk top, between its pedestals.
        VOIDS.put("doodad.desk-1", new int[]{11, 25, 21, 29});
        VOIDS.put("doodad.desk-2", new int[]{11, 25, 21, 29});
        VOIDS.put("doodad.desk-dam", new int[]{12, 24, 20, 30});
        // The doorway itself: an open door is a hole with a lintel over it.
        VOIDS.put("urban.door-open/only", new int[]{0, 8, 32, 32});
    }

    /** A void may hold this many opaque pixels before it is a filled pocket. */
    private static final int VOID_BUDGET = 2;

    @Test
    void theVoidsAPieceIsDrawnAroundStayOpen() throws Exception {
        TileRegistry registry = loadTilesets();
        BufferedImage atlas = readSheet();
        for (Map.Entry<String, int[]> expected : VOIDS.entrySet()) {
            int[] cell = placeOf(registry, expected.getKey());
            int[] box = expected.getValue();
            int opaque = 0;
            for (int y = box[1]; y < box[3]; y++) {
                for (int x = box[0]; x < box[2]; x++) {
                    if ((atlas.getRGB(cell[0] * cellPx() + x, cell[1] * cellPx() + y) >>> 24)
                            >= 128) {
                        opaque++;
                    }
                }
            }
            assertTrue(opaque <= VOID_BUDGET, expected.getKey() + " has " + opaque
                    + " opaque pixels in the void at [" + box[0] + "," + box[1] + ".."
                    + box[2] + "," + box[3] + "), which the sheet leaves open");
        }
    }

    /**
     * Piece -> how much opaque area may sit detached from its main body.
     *
     * <p>A prop is one object. Anything opaque that is not joined to it draws as
     * dirt floating beside it, and the two ways this sheet produces such dirt
     * are a background speck the key admitted and a sliver of the neighbouring
     * cell left along the cut. Neither shows up in a total, because both are
     * smaller than the resampling noise on the outline they are competing with.
     *
     * <p>The default is four pixels. The rubble decals are the real exception —
     * they are drawn as several separate stones, so the hand-maintained atlas
     * has 52 to 162 pixels of genuine second and third body, and their budget is
     * that with room to resample.
     */
    private static final int STRAY_BUDGET = 4;

    private static final Map<String, Integer> SCATTERED = Map.of(
            "doodad.decal-rubble-1", 90,
            "doodad.decal-rubble-2", 160,
            "doodad.decal-rubble-3", 70,
            "doodad.decal-rubble-4", 200);

    @Test
    void nothingFloatsBesideAPiece() throws Exception {
        TileRegistry registry = loadTilesets();
        BufferedImage atlas = readSheet();
        for (String id : DOODADS.keySet()) {
            int[] cell = placeOf(registry, id);
            boolean[][] opaque = silhouette(atlas, cellPx(), cell[0], cell[1]);
            int stray = detachedArea(opaque);
            int budget = SCATTERED.getOrDefault(id, STRAY_BUDGET);
            assertTrue(stray <= budget, id + " has " + stray
                    + " pixels of opaque art detached from its body (budget " + budget
                    + "), which draws as dirt floating beside the piece");
        }
    }

    /** Opaque area outside the largest connected body. */
    private static int detachedArea(boolean[][] opaque) {
        int height = opaque.length;
        int width = height == 0 ? 0 : opaque[0].length;
        int[][] seen = new int[height][width];
        int total = 0;
        int largest = 0;
        int next = 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque[y][x] || seen[y][x] != 0) continue;
                int area = 0;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{y, x});
                seen[y][x] = next;
                while (!stack.isEmpty()) {
                    int[] at = stack.pop();
                    area++;
                    for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int ny = at[0] + step[0];
                        int nx = at[1] + step[1];
                        if (ny < 0 || ny >= height || nx < 0 || nx >= width) continue;
                        if (!opaque[ny][nx] || seen[ny][nx] != 0) continue;
                        seen[ny][nx] = next;
                        stack.push(new int[]{ny, nx});
                    }
                }
                next++;
                total += area;
                largest = Math.max(largest, area);
            }
        }
        return total - largest;
    }

    private static boolean[][] silhouette(BufferedImage atlas, int cellPx, int col, int row) {
        boolean[][] opaque = new boolean[cellPx][cellPx];
        for (int y = 0; y < cellPx; y++) {
            for (int x = 0; x < cellPx; x++) {
                opaque[y][x] =
                        (atlas.getRGB(col * cellPx + x, row * cellPx + y) >>> 24) >= 128;
            }
        }
        return opaque;
    }

    /** The packed cell a doodad id or a {@code block/slot} name landed in. */
    private static int[] placeOf(TileRegistry registry, String id) {
        int slash = id.indexOf('/');
        if (slash < 0) {
            DoodadDef def = registry.doodad(id);
            assertNotNull(def, "urban-tileset no longer defines " + id);
            return new int[]{def.col, def.row};
        }
        GridBlockDef block = registry.block(id.substring(0, slash));
        assertNotNull(block, "urban-tileset no longer defines block " + id);
        return new int[]{block.originCol, block.originRow};
    }

    @Test
    void everyDoodadKeepsTheSilhouetteTheShippedSheetHad() throws Exception {
        TileRegistry registry = loadTilesets();
        BufferedImage atlas = readSheet();
        for (Map.Entry<String, Double> expected : DOODADS.entrySet()) {
            DoodadDef def = registry.doodad(expected.getKey());
            assertNotNull(def, "urban-tileset no longer defines " + expected.getKey());
            assertEquals(SHEET, def.sheetPath, expected.getKey() + " moved to another sheet");
            assertEquals(expected.getValue(), opacity(atlas, cellPx(), def.col, def.row),
                    TOLERANCE, expected.getKey() + " no longer cuts its cell the way it did");
        }
    }

    @Test
    void everyBlockCellAnAutotileCanDrawKeepsItsOpacity() throws Exception {
        TileRegistry registry = loadTilesets();
        BufferedImage atlas = readSheet();
        for (Map.Entry<String, Double> expected : BLOCKS.entrySet()) {
            GridBlockDef block = registry.block(expected.getKey());
            assertNotNull(block, "urban-tileset no longer defines block " + expected.getKey());
            for (int[] cell : resolvableCells(block)) {
                String offset = expected.getKey() + " "
                        + (cell[0] - block.originCol) + "," + (cell[1] - block.originRow);
                double want = BLOCK_CELLS.getOrDefault(offset, expected.getValue());
                assertEquals(want, opacity(atlas, block.cellPx, cell[0], cell[1]),
                        TOLERANCE, offset + " no longer cuts its cell the way it did");
            }
        }
    }

    /**
     * The raw sheet carries its own alpha, which is what makes the shipped
     * atlas re-derivable at all: an opaque plate has not recorded what is
     * background and what is art, and no authoring document can supply that.
     */
    @Test
    void theRawSheetCarriesItsOwnAlpha() throws Exception {
        BufferedImage raw = ImageIO.read(RAW_SHEET.toFile());
        assertNotNull(raw, "cannot read " + RAW_SHEET);
        assertTrue(raw.getColorModel().hasAlpha(),
                RAW_SHEET + " is opaque; every piece would export as a black square");
        long clear = 0;
        for (int y = 0; y < raw.getHeight(); y++) {
            for (int x = 0; x < raw.getWidth(); x++) {
                if ((raw.getRGB(x, y) >>> 24) < 128) clear++;
            }
        }
        double fraction = clear / (double) (raw.getWidth() * raw.getHeight());
        assertTrue(fraction > 0.25 && fraction < 0.50,
                RAW_SHEET + " is " + Math.round(fraction * 100) + "% transparent, which is not a "
                        + "keyed background: the sheet was 37% transparent when it was keyed");
    }

    /** Every distinct cell {@code block}'s layout can produce, ignoring the fill case. */
    private static Set<int[]> resolvableCells(GridBlockDef block) {
        Map<Long, int[]> distinct = new LinkedHashMap<>();
        for (int mask = 0; mask < 16; mask++) {
            int[] cell = block.resolve((mask & 1) != 0, (mask & 2) != 0,
                    (mask & 4) != 0, (mask & 8) != 0);
            if (cell == null) continue;
            distinct.putIfAbsent(((long) cell[0] << 32) | cell[1], cell);
        }
        return new LinkedHashSet<>(distinct.values());
    }

    private static double opacity(BufferedImage atlas, int cellPx, int col, int row) {
        int opaque = 0;
        for (int y = 0; y < cellPx; y++) {
            for (int x = 0; x < cellPx; x++) {
                if ((atlas.getRGB(col * cellPx + x, row * cellPx + y) >>> 24) >= 128) opaque++;
            }
        }
        return opaque / (double) (cellPx * cellPx);
    }

    private static int cellPx() throws Exception {
        return new JSONObject(read(TILESET)).getInt("cellPx");
    }

    private static BufferedImage readSheet() throws Exception {
        BufferedImage atlas = ImageIO.read(Paths.get("mod", SHEET.split("/")).toFile());
        assertNotNull(atlas, "cannot read mod/" + SHEET);
        return atlas;
    }

    private static TileRegistry loadTilesets() throws Exception {
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
