package com.dillon.starsectormarines.battle.sprites;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.map.WallMasks;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingCommercialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingIndustrialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingResidentialFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Dev preview for the three building {@link BlockFiller} implementations
 * ({@link BuildingResidentialFiller}, {@link BuildingCommercialFiller},
 * {@link BuildingIndustrialFiller}). For each, generates a small batch of
 * standalone buildings at different sizes / seeds and renders them through
 * the real tile art so the carve + scatter passes can be reviewed without
 * having to launch the full {@link com.dillon.starsectormarines.battle.mapgen.bsp.BspCityGenerator}
 * map.
 *
 * <p>Rendering shares the same {@link TileSink}-backed path the in-game
 * renderer uses — wall autotile, interior floor autotile, DOOR_OPEN overlay,
 * STRIPED/TILE accent grounds, and doodad scatter all route through the
 * same picker + inset constants as {@link com.dillon.starsectormarines.ops.BattleScreen}.
 *
 * <p>Output: {@code build/zone-previews/buildings-<kind>.png} — a 2×2 panel
 * showing four buildings at different sizes. Iterate via:
 * <pre>
 *   gradlew :test --tests "*BuildingZonePreviewTest*"
 * </pre>
 *
 * <p>Each panel pairs the carved structure with a label tracking the variant
 * label, seed, and POI/doodad counts so the eyeball-review step has a quick
 * reality check on the carve outputs.
 */
public class BuildingZonePreviewTest {

    private static final Path URBAN_SHEET  = Paths.get("mod/graphics/tilesets/urban-tileset.png");
    private static final Path ROAD_SHEET   = Paths.get("mod/graphics/tilesets/urban-tileset-2.png");
    private static final Path FLOORS_SHEET = Paths.get("mod/graphics/tilesets/Floors_Tiles.png");
    private static final Path OUT_DIR      = Paths.get("build/zone-previews");

    /** 1.5x upscale of the 32px source — large enough to read interior partitions and doodad scatter at a glance. */
    private static final int DISPLAY_CELL_PX = 48;
    /** Road frame thickness on each side of the building leaf. Two cells gives enough STREET around the carve to read its perimeter against. */
    private static final int MARGIN_CELLS = 2;

    private static final Color STREET_FILL      = new Color(0x40, 0x46, 0x52);
    private static final Color WALL_CENTER_FILL = new Color(0x18, 0x18, 0x1C);
    private static final Color LABEL_BG         = new Color(0, 0, 0, 200);
    private static final Color LABEL_FG         = new Color(0xE0, 0xE8, 0xF4);

    /**
     * Building dimensions + seeds chosen to exercise the carve logic broadly:
     * <ul>
     *   <li>{@code 5x5}: just above {@link com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingResidentialFiller}'s
     *       hollow threshold; eligible for a single doorway but not interior partitions.</li>
     *   <li>{@code 9x6}: width &ge; multi-room threshold — eligible for a vertical partition wall.</li>
     *   <li>{@code 6x9}: height &ge; multi-room threshold — eligible for a horizontal partition wall.</li>
     *   <li>{@code 12x8}: both axes large — two-doorway likely + partition + densest doodad scatter.</li>
     * </ul>
     */
    private static final BuildingVariant[] VARIANTS = {
            new BuildingVariant("tiny 5x5",    5, 5,   1L),
            new BuildingVariant("wide 9x6",    9, 6,  42L),
            new BuildingVariant("tall 6x9",    6, 9, 100L),
            new BuildingVariant("large 12x8", 12, 8, 777L),
    };

    /**
     * Renders the clean wall 3×3 (cols 3..5, rows 0..2) and damaged wall 3×3
     * (cols 3..5, rows 4..6) from {@code urban-tileset.png} at 8× zoom, each
     * cell annotated with the perimeter direction {@link TileManifest#pickWallTile}
     * routes there. Lets us see at a glance whether the source art is
     * directional (top-cap on (4, 2), left-cap on (5, 1), etc.) or whether
     * the mid-edge cells were drawn symmetric — answers the "do we need to
     * re-art or just mirror at render time" question.
     */
    @Test
    void renderWallBlockGallery() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage urban = ImageIO.read(Files.newInputStream(URBAN_SHEET));
        assertNotNull(urban, "failed to load " + URBAN_SHEET);

        // 32px source × 8 zoom = 256px per cell.
        int zoom = 8;
        int cellPx = TileManifest.TILE_SIZE * zoom;
        int gap = 24;
        int labelH = 36;
        int cols = 3;
        int rows = 3;
        int blockW = cols * cellPx + (cols + 1) * gap;
        int blockH = rows * cellPx + (rows + 1) * gap + labelH;
        // Two blocks side by side: clean on the left, damaged on the right.
        int imgW = blockW * 2 + gap;
        int imgH = blockH + 40;

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // NEAREST so the source pixels stay pixel-art readable at 8×.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x12, 0x15, 0x1B));
        g.fillRect(0, 0, imgW, imgH);

        drawWallBlock(g, urban, 0,       0, cellPx, gap, 3, 0, "Clean wall 3×3 (cols 3..5, rows 0..2)");
        drawWallBlock(g, urban, blockW + gap, 0, cellPx, gap, 3, 4, "Damaged wall 3×3 (cols 3..5, rows 4..6)");

        g.dispose();
        Path out = OUT_DIR.resolve("wall-blocks-debug.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Stamps a 3×3 wall block at zoom with per-cell direction annotations. */
    private static void drawWallBlock(Graphics2D g, BufferedImage sheet, int x0, int y0,
                                       int cellPx, int gap, int srcColOrigin, int srcRowOrigin,
                                       String heading) {
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(heading, x0 + gap, y0 + 24);

        // Label per cell — the source 3×3 is laid out spatially (top of
        // source = N, bottom = S, left = W, right = E). pickWallTile now
        // matches this convention:
        //   col 0 picked when wWall (west neighbor blocked)  → west-perimeter wall
        //   col 2 picked when eWall                          → east-perimeter wall
        //   row 0 picked when nWall                          → north-perimeter wall
        //   row 2 picked when sWall                          → south-perimeter wall
        // The four source-corner cells are picked for the perimeter cells
        // adjacent to a building's convex corners — they paint the L-bracket
        // joining two perpendicular walls (e.g. (3, 0) marks where the north
        // wall meets the west wall on a north-perimeter cell at the NW
        // corner). The building's convex corner cell itself has all 4
        // neighbors as wall-or-OOB → null → WALL_CENTER_FILL solid quad.
        String[][] labels = new String[][] {
                { "NW corner",  "N edge",  "NE corner" }, // row 0
                { "W edge",     "<center>","E edge"     }, // row 1
                { "SW corner",  "S edge",  "SE corner" }, // row 2
        };

        int gridY = y0 + 36;
        for (int dr = 0; dr < 3; dr++) {
            for (int dc = 0; dc < 3; dc++) {
                int cx = x0 + gap + dc * (cellPx + gap);
                int cy = gridY + gap + dr * (cellPx + gap);
                int srcX = (srcColOrigin + dc) * TileManifest.TILE_SIZE;
                int srcY = (srcRowOrigin + dr) * TileManifest.TILE_SIZE;
                g.setColor(new Color(0x22, 0x28, 0x32));
                g.fillRect(cx, cy, cellPx, cellPx);
                g.drawImage(sheet,
                        cx, cy, cx + cellPx, cy + cellPx,
                        srcX, srcY, srcX + TileManifest.TILE_SIZE, srcY + TileManifest.TILE_SIZE,
                        null);
                g.setColor(LABEL_FG);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                String coordTxt = String.format("(%d, %d)", srcColOrigin + dc, srcRowOrigin + dr);
                g.drawString(coordTxt,          cx + 6, cy + 16);
                g.drawString(labels[dr][dc],    cx + 6, cy + cellPx - 8);
            }
        }
    }

    /**
     * Renders every catalog-named floor-style cell across all three
     * tilesheets at 6× zoom so we can pick a better commercial floor than
     * the current {@code fl-tile-1..5} cluster (which reads more like
     * sidewalk slab / roof tile than polished commercial flooring).
     * Groups by sheet, labels each cell with its catalog name and coord.
     */
    @Test
    void renderFloorCandidatesGallery() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage urban  = ImageIO.read(Files.newInputStream(URBAN_SHEET));
        BufferedImage road   = ImageIO.read(Files.newInputStream(ROAD_SHEET));
        BufferedImage floors = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        assertNotNull(urban,  "failed to load " + URBAN_SHEET);
        assertNotNull(road,   "failed to load " + ROAD_SHEET);
        assertNotNull(floors, "failed to load " + FLOORS_SHEET);

        // Candidates grouped by source sheet. Center cells only — for
        // autotile blocks we sample the (1, 1) center so the comparison
        // isn't muddied by edge variants.
        FloorCandidate[] candidates = new FloorCandidate[] {
                // urban-tileset.png (32px cells) — clean indoor floor center + damaged.
                new FloorCandidate(urban,  32, 1, 1, "fl (INDOOR center)",              "urban"),
                new FloorCandidate(urban,  32, 1, 5, "fl-dam (damaged INDOOR center)",  "urban"),
                new FloorCandidate(urban,  32, 0, 3, "fl-grate-1",                       "urban"),
                new FloorCandidate(urban,  32, 1, 3, "fl-striped-yellow (decal)",        "urban"),
                new FloorCandidate(urban,  32, 2, 3, "fl-grate-2",                       "urban"),
                // urban-tileset-2.png (32px cells) — road-sheet floor variants.
                new FloorCandidate(road,   32, 1, 1, "courtyard center (COURTYARD)",     "road"),
                new FloorCandidate(road,   32, 7, 1, "fl-striped center (STRIPED)",      "road"),
                new FloorCandidate(road,   32, 9, 0, "fl-1",                              "road"),
                new FloorCandidate(road,   32,11, 0, "fl-2",                              "road"),
                new FloorCandidate(road,   32,11, 1, "fl-3",                              "road"),
                new FloorCandidate(road,   32,13, 1, "road center (STREET)",              "road"),
                new FloorCandidate(road,   32,11, 2, "grate-1",                           "road"),
                new FloorCandidate(road,   32,16, 2, "grate-2",                           "road"),
                // Floors_Tiles.png (16px cells) — the current TILE pool + outdoor centers.
                new FloorCandidate(floors, 16,17, 1, "fl-tile-1 (current TILE)",          "floors"),
                new FloorCandidate(floors, 16,16, 2, "fl-tile-2 (current TILE)",          "floors"),
                new FloorCandidate(floors, 16,17, 2, "fl-tile-3 (current TILE)",          "floors"),
                new FloorCandidate(floors, 16,18, 2, "fl-tile-4 (current TILE)",          "floors"),
                new FloorCandidate(floors, 16,17, 3, "fl-tile-5 (current TILE)",          "floors"),
                new FloorCandidate(floors, 16, 2,10, "grass-2 center",                    "floors"),
                new FloorCandidate(floors, 16, 7,10, "stone-2 center",                    "floors"),
                new FloorCandidate(floors, 16,12,10, "dirt-2 center",                     "floors"),
                new FloorCandidate(floors, 16, 2,14, "snow-3 center",                     "floors"),
                new FloorCandidate(floors, 16, 7,14, "sand-3 center",                     "floors"),
        };

        int displayCell = 160;        // 5x of 32px source; 10x of 16px source.
        int gap = 18;
        int labelH = 36;
        int cols = 5;
        int rows = (candidates.length + cols - 1) / cols;
        int imgW = cols * (displayCell + gap) + gap;
        int imgH = rows * (displayCell + labelH + gap) + gap + 40;

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x12, 0x15, 0x1B));
        g.fillRect(0, 0, imgW, imgH);

        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("Floor candidates — pick a better commercial floor than fl-tile-* (current bricked TILE)",
                gap, 28);

        for (int i = 0; i < candidates.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int dstX = gap + col * (displayCell + gap);
            int dstY = 40 + gap + row * (displayCell + labelH + gap);
            FloorCandidate c = candidates[i];
            g.setColor(new Color(0x22, 0x28, 0x32));
            g.fillRect(dstX, dstY, displayCell, displayCell);
            int srcX = c.col * c.srcCellPx;
            int srcY = c.row * c.srcCellPx;
            g.drawImage(c.sheet,
                    dstX, dstY, dstX + displayCell, dstY + displayCell,
                    srcX, srcY, srcX + c.srcCellPx, srcY + c.srcCellPx,
                    null);
            g.setColor(LABEL_FG);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(c.name, dstX, dstY + displayCell + 16);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(0x90, 0xa0, 0xb0));
            g.drawString(String.format("%s · (%d, %d)", c.sheetTag, c.col, c.row),
                    dstX, dstY + displayCell + 30);
        }

        g.dispose();
        Path out = OUT_DIR.resolve("floor-candidates-gallery.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Single floor-candidate sample: which sheet, where on it, and what the catalog calls it. */
    private static final class FloorCandidate {
        final BufferedImage sheet;
        final int srcCellPx;
        final int col;
        final int row;
        final String name;
        final String sheetTag;
        FloorCandidate(BufferedImage sheet, int srcCellPx, int col, int row, String name, String sheetTag) {
            this.sheet = sheet;
            this.srcCellPx = srcCellPx;
            this.col = col;
            this.row = row;
            this.name = name;
            this.sheetTag = sheetTag;
        }
    }

    /**
     * Stamps the five {@code fl-tile-1..5} catalog coords on BOTH the road
     * sheet (where {@link TileManifest#pickTileGroundTile} currently indexes
     * via {@link TileManifest#ROAD_SHEET}) and the floors sheet (where
     * {@code mod/data/tilesets/Floors_Tiles.catalog.json} says the art
     * actually lives). The road sheet is only 17×3 cells at 32px source, so
     * coords (17,1), (17,2), (18,2), (17,3) all read off-sheet there; the
     * floors sheet is 25×26 cells at 16px source, where the same coords are
     * well in-bounds. Side-by-side at 8× makes the sheet-mismatch bug
     * (transparent stamps → beige underpaint workaround) obvious.
     */
    @Test
    void renderFlTileGallery() throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage road   = ImageIO.read(Files.newInputStream(ROAD_SHEET));
        BufferedImage floors = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        assertNotNull(road,   "failed to load " + ROAD_SHEET);
        assertNotNull(floors, "failed to load " + FLOORS_SHEET);

        // Catalog coords for fl-tile-1..5, copied straight from
        // mod/data/tilesets/Floors_Tiles.catalog.json. Matches
        // TileManifest.FL_TILE_VARIANTS.
        int[][] coords = {
                {17, 1}, // fl-tile-1
                {16, 2}, // fl-tile-2
                {17, 2}, // fl-tile-3
                {18, 2}, // fl-tile-4
                {17, 3}, // fl-tile-5
        };

        int displayCell = 192;          // common display size so both sheets render at equal cells.
        int gap = 24;
        int labelH = 28;
        int rowHeaderW = 220;
        int cols = coords.length;
        int sheetRowH = displayCell + gap + labelH;
        int blockW = rowHeaderW + cols * (displayCell + gap) + gap;
        int blockH = 60 + sheetRowH * 2 + gap;

        BufferedImage img = new BufferedImage(blockW, blockH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x12, 0x15, 0x1B));
        g.fillRect(0, 0, blockW, blockH);

        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("fl-tile-1..5 — road sheet (current pickTileGroundTile target) vs floors sheet (catalog origin)",
                gap, 32);

        int roadCellPx = TileManifest.TILE_SIZE;          // 32
        int floorsCellPx = TileManifest.TILE_SIZE / 2;    // 16, per Floors_Tiles cell size
        int roadCols = road.getWidth() / roadCellPx;
        int roadRows = road.getHeight() / roadCellPx;
        int floorsCols = floors.getWidth() / floorsCellPx;
        int floorsRows = floors.getHeight() / floorsCellPx;

        // Row 1: road sheet (urban-tileset-2.png) — what's actually being indexed today.
        drawSheetRow(g, road, "ROAD_SHEET\n(17×3 cells @ 32px)", coords, roadCellPx,
                roadCols, roadRows, 0, 60, rowHeaderW, gap, displayCell, labelH);
        // Row 2: floors sheet (Floors_Tiles.png) — what the catalog says these coords map to.
        drawSheetRow(g, floors, "FLOORS_SHEET\n(25×26 cells @ 16px)", coords, floorsCellPx,
                floorsCols, floorsRows, 0, 60 + sheetRowH, rowHeaderW, gap, displayCell, labelH);

        g.dispose();
        Path out = OUT_DIR.resolve("fl-tile-gallery.png");
        ImageIO.write(img, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    /** Stamps one sheet's view of the fl-tile coords as a single row. Cells that read off-sheet render as a red "OOB" placeholder. */
    private static void drawSheetRow(Graphics2D g, BufferedImage sheet, String header,
                                     int[][] coords, int srcCellPx, int sheetCols, int sheetRows,
                                     int x0, int y0, int rowHeaderW, int gap, int displayCell, int labelH) {
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        int line = 0;
        for (String s : header.split("\n")) {
            g.drawString(s, x0 + gap, y0 + 24 + line * 18);
            line++;
        }

        int cx = x0 + rowHeaderW;
        int cy = y0;
        for (int i = 0; i < coords.length; i++) {
            int col = coords[i][0];
            int row = coords[i][1];
            int dstX = cx + i * (displayCell + gap);
            int dstY = cy + gap;
            g.setColor(new Color(0x22, 0x28, 0x32));
            g.fillRect(dstX, dstY, displayCell, displayCell);

            boolean inBounds = col < sheetCols && row < sheetRows;
            if (inBounds) {
                int srcX = col * srcCellPx;
                int srcY = row * srcCellPx;
                g.drawImage(sheet,
                        dstX, dstY, dstX + displayCell, dstY + displayCell,
                        srcX, srcY, srcX + srcCellPx, srcY + srcCellPx,
                        null);
            } else {
                g.setColor(new Color(0x88, 0x18, 0x18));
                g.fillRect(dstX, dstY, displayCell, displayCell);
                g.setColor(LABEL_FG);
                g.setFont(new Font("SansSerif", Font.BOLD, 22));
                String msg = "OOB";
                int msgW = g.getFontMetrics().stringWidth(msg);
                g.drawString(msg, dstX + (displayCell - msgW) / 2, dstY + displayCell / 2 + 8);
            }

            g.setColor(LABEL_FG);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(String.format("fl-tile-%d (%d, %d)", i + 1, col, row),
                    dstX + 6, dstY + displayCell + 18);
        }
    }

    @Test
    void renderResidentialVariants() throws Exception {
        renderBuildingBatch(new BuildingResidentialFiller(), "residential");
    }

    @Test
    void renderCommercialVariants() throws Exception {
        renderBuildingBatch(new BuildingCommercialFiller(), "commercial");
    }

    @Test
    void renderIndustrialVariants() throws Exception {
        renderBuildingBatch(new BuildingIndustrialFiller(), "industrial");
    }

    private void renderBuildingBatch(BlockFiller filler, String kindLabel) throws Exception {
        Files.createDirectories(OUT_DIR);
        BufferedImage urban  = ImageIO.read(Files.newInputStream(URBAN_SHEET));
        BufferedImage road   = ImageIO.read(Files.newInputStream(ROAD_SHEET));
        BufferedImage floors = ImageIO.read(Files.newInputStream(FLOORS_SHEET));
        assertNotNull(urban,  "failed to load " + URBAN_SHEET);
        assertNotNull(road,   "failed to load " + ROAD_SHEET);
        assertNotNull(floors, "failed to load " + FLOORS_SHEET);

        BufferedImage[] panels = new BufferedImage[VARIANTS.length];
        for (int i = 0; i < VARIANTS.length; i++) {
            panels[i] = renderVariant(filler, VARIANTS[i], urban, road, floors, kindLabel);
        }

        BufferedImage sheet = composeContactSheet(panels, 2);
        Path out = OUT_DIR.resolve(String.format("buildings-%s.png", kindLabel));
        ImageIO.write(sheet, "PNG", out.toFile());
        System.out.println("  wrote " + out.toAbsolutePath());
    }

    private static BufferedImage renderVariant(BlockFiller filler, BuildingVariant variant,
                                               BufferedImage urban, BufferedImage road,
                                               BufferedImage floors, String kindLabel) {
        int gridW = variant.leafW + MARGIN_CELLS * 2;
        int gridH = variant.leafH + MARGIN_CELLS * 2;
        int leafLeft   = MARGIN_CELLS;
        int leafTop    = MARGIN_CELLS;
        int leafRight  = leafLeft + variant.leafW - 1;
        int leafBottom = leafTop  + variant.leafH - 1;

        NavigationGrid grid = new NavigationGrid(gridW, gridH);
        CellTopology topology = new CellTopology(gridW, gridH);
        // Filler contract: leaf cells start as STREET ground + walkable. The
        // road frame outside the leaf keeps STREET; the filler overwrites the
        // interior with its configured ground kind and punches holes in the
        // perimeter walkability.
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }

        BlockLeaf leaf = new BlockLeaf(leafLeft, leafTop, leafRight, leafBottom, false);
        List<PointOfInterest> pois = new ArrayList<>();
        List<Doodad> doodads = new ArrayList<>();
        filler.fill(leaf, grid, topology, pois, doodads, new Random(variant.seed));

        // Sync WALL tags from nav walkability — matches the orchestrator's
        // post-fill pass. The wall picker reads {@code topology.isWall}, not
        // {@code grid.isWalkable}, so this must run before rendering.
        topology.tagDefaultWalls(grid);

        String label = String.format("%s · %s · seed=%d · POIs=%d · doodads=%d",
                kindLabel, variant.label, variant.seed, pois.size(), doodads.size());
        return renderScene(grid, topology, doodads, urban, road, floors, label);
    }

    private static BufferedImage renderScene(NavigationGrid grid, CellTopology topology,
                                             List<Doodad> doodads,
                                             BufferedImage urban, BufferedImage road,
                                             BufferedImage floors,
                                             String label) {
        int gridW = grid.getWidth();
        int gridH = grid.getHeight();
        int imgW = gridW * DISPLAY_CELL_PX;
        int imgH = gridH * DISPLAY_CELL_PX + 28;

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = configureGraphics(img);

        FixedGridTileDrawer drawer       = new FixedGridTileDrawer(TileManifest.TILE_SIZE);
        FixedGridTileDrawer floorsDrawer = new FixedGridTileDrawer(TileManifest.FLOORS_TILE_SIZE);
        TileSink urbanSink  = new Graphics2DTileSink(g, urban);
        TileSink roadSink   = new Graphics2DTileSink(g, road);
        TileSink floorsSink = new Graphics2DTileSink(g, floors);

        // Pass 1: STREET cells get a flat gray underneath (the preview
        // doesn't render the road autotile). Interior cells aren't
        // underpainted — every routed picker stamps a fully opaque source
        // cell, so a missing stamp here means a real bug, not a transparent
        // source-pixel quirk to be papered over.
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                if (topology.isWall(x, y)) continue;
                if (topology.getGroundKind(x, y) != GroundKind.STREET) continue;
                g.setColor(STREET_FILL);
                g.fillRect(x * DISPLAY_CELL_PX, (gridH - 1 - y) * DISPLAY_CELL_PX,
                        DISPLAY_CELL_PX, DISPLAY_CELL_PX);
            }
        }

        // Pass 2: per-cell floor for non-wall cells. Mirrors the dispatch in
        // BattleScreen.renderTiledFloorsAndWalls — INDOOR/RUBBLE on the urban
        // sheet, STRIPED/TILE on the road sheet, with the same inset constants.
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                if (topology.isWall(x, y)) continue;
                boolean nWall = isInBoundsWall(topology, x, y + 1);
                boolean sWall = isInBoundsWall(topology, x, y - 1);
                boolean eWall = isInBoundsWall(topology, x + 1, y);
                boolean wWall = isInBoundsWall(topology, x - 1, y);

                GroundKind kind = topology.getGroundKind(x, y);
                switch (kind) {
                    case INDOOR: {
                        TileManifest.TileFrame f = TileManifest.pickFloorTile(nWall, sWall, eWall, wWall);
                        stampCell(drawer, urbanSink, f, x, y, gridH, drawer.defaultGroundInsetPx());
                        break;
                    }
                    case STRIPED: {
                        TileManifest.TileFrame f = TileManifest.pickStripedTile(nWall, sWall, eWall, wWall);
                        stampCell(drawer, roadSink, f, x, y, gridH, drawer.defaultGroundInsetPx());
                        break;
                    }
                    case TILE: {
                        // Polished commercial floor (fl-2) on ROAD_SHEET —
                        // mirrors the BattleScreen.TILE dispatch through
                        // drawRoadTile.
                        TileManifest.TileFrame f = TileManifest.pickTileGroundTile(x, y);
                        stampCell(drawer, roadSink, f, x, y, gridH, drawer.defaultGroundInsetPx());
                        break;
                    }
                    case SIDEWALK: {
                        // Outdoor sidewalk / paved pavement (fl-tile-1..5)
                        // on FLOORS_SHEET — per-cell hash variant pool.
                        TileManifest.TileFrame f = TileManifest.pickSidewalkTile(x, y);
                        stampCell(floorsDrawer, floorsSink, f, x, y, gridH, floorsDrawer.defaultGroundInsetPx());
                        break;
                    }
                    case RUBBLE: {
                        TileManifest.TileFrame f = TileManifest.pickRubbleTile(nWall, sWall, eWall, wWall);
                        stampCell(drawer, urbanSink, f, x, y, gridH, drawer.defaultGroundInsetPx());
                        break;
                    }
                    case STREET:
                        // Already painted as a flat fill in pass 1.
                        break;
                    default:
                        // Other GroundKinds (GRASS/DIRT/WATER/etc.) aren't
                        // emitted by the building fillers under test — guard
                        // surfaces a regression visibly.
                        g.setColor(Color.MAGENTA);
                        g.fillRect(x * DISPLAY_CELL_PX, (gridH - 1 - y) * DISPLAY_CELL_PX,
                                DISPLAY_CELL_PX, DISPLAY_CELL_PX);
                        break;
                }

                // Overhead door overlay — DOOR_OPEN is an overlay sprite, not
                // a tiling ground, so it passes inset=0 to keep the door's
                // edge pixels intact (same rule as in-game).
                if (grid.isDoorway(x, y) && !topology.isRubble(x, y)) {
                    stampCell(drawer, urbanSink, TileManifest.DOOR_OPEN,
                            x, y, gridH, FixedGridTileDrawer.OVERLAY_INSET_PX);
                }
            }
        }

        // Pass 3: walls. Wall direction is read straight from the per-cell
        // mask that BuildingShellCore stamped at carve time — no neighbor
        // query. Cells with mask=0 are interior partition walls (no
        // exterior face); pickWallTile resolves them to the empty center
        // cell, falling back to a solid quad below.
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                if (!topology.isWall(x, y)) continue;
                int mask = topology.getWallDirMask(x, y);
                TileManifest.TileFrame tile = WallMasks.pickTileFromMask(mask);
                if (tile == null) {
                    g.setColor(WALL_CENTER_FILL);
                    g.fillRect(x * DISPLAY_CELL_PX, (gridH - 1 - y) * DISPLAY_CELL_PX,
                            DISPLAY_CELL_PX, DISPLAY_CELL_PX);
                } else {
                    // Walls render with NO source inset — unlike ground autotiles,
                    // wall art keeps its directional cap strokes at the cell edge
                    // (e.g. a 1-2px horizontal line at the top of cell (4, 0)
                    // distinguishes a south-facing wall edge from a north one).
                    // Applying GROUND_INSET_PX_LARGE here crops those strokes
                    // away and makes opposite-edge walls read identically.
                    stampCell(drawer, urbanSink, tile, x, y, gridH,
                            FixedGridTileDrawer.OVERLAY_INSET_PX);
                }
            }
        }

        // Pass 4: doodads. No inset — these are standalone sprites whose
        // edge pixels are content. Source sheet is per-doodad (road sheet for
        // LZ-style props, urban sheet for everything the building fillers emit).
        for (Doodad d : doodads) {
            TileSink sink = d.fromRoadSheet ? roadSink : urbanSink;
            stampCell(drawer, sink, d.tile, d.cellX, d.cellY, gridH,
                    FixedGridTileDrawer.OVERLAY_INSET_PX);
        }

        drawLabel(g, gridW, gridH, label);
        g.dispose();
        return img;
    }

    private static void stampCell(FixedGridTileDrawer drawer, TileSink sink,
                                  TileManifest.TileFrame f, int gridX, int gridY, int gridH,
                                  int insetPx) {
        if (f == null) return;
        float dstCx = gridX * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        float dstCy = (gridH - 1 - gridY) * DISPLAY_CELL_PX + DISPLAY_CELL_PX / 2f;
        drawer.draw(sink, f, dstCx, dstCy, DISPLAY_CELL_PX, DISPLAY_CELL_PX, 1f, insetPx);
    }

    /** Floor-pass wall test: OOB is treated as open (not-wall) to match BattleScreen.isInBoundsWall. */
    private static boolean isInBoundsWall(CellTopology t, int x, int y) {
        if (!t.inBounds(x, y)) return false;
        return t.isWall(x, y);
    }

    private static Graphics2D configureGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        // BILINEAR matches Starsector's default GL texture filter.
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void drawLabel(Graphics2D g, int gridW, int gridH, String text) {
        int imgW = gridW * DISPLAY_CELL_PX;
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(LABEL_BG);
        g.fillRect(0, gridH * DISPLAY_CELL_PX, imgW, 28);
        g.setColor(LABEL_FG);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString(text, 8, gridH * DISPLAY_CELL_PX + 19);
    }

    private static BufferedImage composeContactSheet(BufferedImage[] panels, int cols) {
        int rows = (panels.length + cols - 1) / cols;
        int maxW = 0, maxH = 0;
        for (BufferedImage p : panels) {
            maxW = Math.max(maxW, p.getWidth());
            maxH = Math.max(maxH, p.getHeight());
        }
        int gap = 12;
        BufferedImage sheet = new BufferedImage(
                cols * maxW + (cols + 1) * gap,
                rows * maxH + (rows + 1) * gap,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x15, 0x18, 0x1F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < panels.length; i++) {
            int cx = i % cols;
            int cy = i / cols;
            // Bottom-align each panel inside its slot so labels stay readable
            // even when panels vary in height (5x5 is shorter than 12x8).
            int x = gap + cx * (maxW + gap);
            int y = gap + cy * (maxH + gap) + (maxH - panels[i].getHeight());
            g.drawImage(panels[i], x, y, null);
        }
        g.dispose();
        return sheet;
    }

    private static final class BuildingVariant {
        final String label;
        final int leafW;
        final int leafH;
        final long seed;

        BuildingVariant(String label, int leafW, int leafH, long seed) {
            this.label = label;
            this.leafW = leafW;
            this.leafH = leafH;
            this.seed  = seed;
        }
    }
}
