package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
