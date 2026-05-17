package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.mapgen.MapResult;
// Compound lives in the bsp sub-package (same as this test).
// DistrictMap lives in the bsp sub-package (same as this test).
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dev tool dressed as a test. Generates a small batch of maps from
 * {@link BspCityGenerator} and renders each {@link MapResult} to a PNG so a
 * human can eyeball the segmentation shape, the block-kind labels, and the
 * overlays (walls / doodads / POIs / spawn anchors) without launching the
 * game. Also asserts basic connectivity — every walkable cell must be
 * reachable from any other walkable cell via 4-neighbor traversal.
 *
 * <p>Output: {@code build/map-previews/seed-NNNN.png} (one per seed) plus
 * a 3×2 contact sheet at {@code build/map-previews/contact.png}. Run via:
 * <pre>
 *   gradlew :test --tests "*BspMapPreviewTest*"
 * </pre>
 * Re-run after editing fillers to see the visual delta.
 *
 * <p>Color scheme is per-{@link GroundKind} (not the actual sprite art —
 * that needs full atlas blits) plus a small overlay set:
 * <ul>
 *   <li>walls = black</li>
 *   <li>vehicles = dark blue</li>
 *   <li>doorways = light gold dot</li>
 *   <li>doodads = white pixel</li>
 *   <li>POIs = magenta rect outline</li>
 *   <li>marine spawn = green diamond, defender spawn = red diamond</li>
 * </ul>
 */
public class BspMapPreviewTest {

    private static final int GRID_W = 80;
    private static final int GRID_H = 80;
    private static final int CELL_PX = 8;
    private static final long[] SEEDS = { 1L, 42L, 100L, 777L, 1234L, 9999L };

    private static final Path OUT_DIR = Paths.get("build/map-previews");

    /** Color per GroundKind — picked to be visually distinct on screen. */
    private static final Map<GroundKind, Color> GROUND_COLORS = new EnumMap<>(GroundKind.class);
    static {
        GROUND_COLORS.put(GroundKind.INDOOR,    new Color(230, 215, 180));
        GROUND_COLORS.put(GroundKind.STREET,    new Color(110, 110, 110));
        GROUND_COLORS.put(GroundKind.COURTYARD, new Color( 60,  70,  95));
        GROUND_COLORS.put(GroundKind.GRASS,     new Color( 90, 150,  70));
        GROUND_COLORS.put(GroundKind.DIRT,      new Color(130, 100,  70));
        GROUND_COLORS.put(GroundKind.STONE,     new Color(170, 170, 170));
        GROUND_COLORS.put(GroundKind.SAND,      new Color(220, 200, 145));
        GROUND_COLORS.put(GroundKind.SNOW,      new Color(240, 240, 240));
        GROUND_COLORS.put(GroundKind.WATER,     new Color( 70, 110, 170));
        GROUND_COLORS.put(GroundKind.TILE,      new Color(200, 180, 155));
        GROUND_COLORS.put(GroundKind.STRIPED,   new Color(200, 190, 100));
        GROUND_COLORS.put(GroundKind.LZ_MARKER, new Color(255, 220,  50));
        GROUND_COLORS.put(GroundKind.RUBBLE,    new Color(120,  90,  80));
    }

    /** District-theme accent colors used in the boundary overlay. Picked to read as colored highlights against any ground. */
    private static final Map<MapDistrictTheme, Color> DISTRICT_COLORS = new EnumMap<>(MapDistrictTheme.class);
    static {
        DISTRICT_COLORS.put(MapDistrictTheme.RESIDENTIAL, new Color(150, 220, 150));
        DISTRICT_COLORS.put(MapDistrictTheme.INDUSTRIAL, new Color(220, 150,  80));
        DISTRICT_COLORS.put(MapDistrictTheme.CIVIC,      new Color(140, 200, 255));
        DISTRICT_COLORS.put(MapDistrictTheme.MIXED,      new Color(200, 200, 220));
        DISTRICT_COLORS.put(MapDistrictTheme.WATERFRONT, new Color(120, 180, 255));
        DISTRICT_COLORS.put(MapDistrictTheme.OUTSKIRTS,  new Color(180, 160, 130));
    }

    @Test
    void renderPreviewBatch() throws Exception {
        Files.createDirectories(OUT_DIR);
        BspCityGenerator gen = new BspCityGenerator();

        BufferedImage[] perSeed = new BufferedImage[SEEDS.length];
        List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long seed = SEEDS[i];
            MapResult map = gen.generate(GRID_W, GRID_H, seed);
            BufferedImage img = renderMap(map, seed, gen.getLastDistrictMap(), gen.getLastCompounds());
            perSeed[i] = img;
            Path out = OUT_DIR.resolve(String.format("seed-%04d.png", (int) seed));
            ImageIO.write(img, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
            try {
                assertConnected(map, seed);
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }

        BufferedImage contact = composeContactSheet(perSeed, 3);
        Path contactPath = OUT_DIR.resolve("contact.png");
        ImageIO.write(contact, "PNG", contactPath.toFile());
        System.out.println("  wrote " + contactPath.toAbsolutePath());
    }

    /** Walks the walkable subgraph from one seed cell; fails if any walkable cell is unreached. */
    private static void assertConnected(MapResult map, long seed) {
        NavigationGrid grid = map.grid;
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[][] visited = new boolean[w][h];

        int startX = -1, startY = -1;
        outer:
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (grid.isWalkable(x, y)) { startX = x; startY = y; break outer; }
            }
        }
        if (startX < 0) return; // pathological empty map; nothing to verify

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        visited[startX][startY] = true;
        int reached = 1;
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int[][] nbrs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                if (visited[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                visited[nx][ny] = true;
                reached++;
                stack.push(new int[]{nx, ny});
            }
        }

        int totalWalkable = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (grid.isWalkable(x, y)) totalWalkable++;
            }
        }
        int finalReached = reached;
        int finalTotal = totalWalkable;
        if (reached != totalWalkable) {
            System.out.println("    FAIL seed=" + seed);
            int printed = 0;
            for (int y = 0; y < h && printed < 10; y++) {
                for (int x = 0; x < w && printed < 10; x++) {
                    if (grid.isWalkable(x, y) && !visited[x][y]) {
                        // Show what's around the unreached cell.
                        System.out.println("    unreached: " + x + "," + y
                                + " ground=" + map.topology.getGroundKind(x, y)
                                + " N=" + neighborState(map, x, y, 0, -1)
                                + " S=" + neighborState(map, x, y, 0, 1)
                                + " E=" + neighborState(map, x, y, 1, 0)
                                + " W=" + neighborState(map, x, y, -1, 0));
                        printed++;
                    }
                }
            }
        }
        assertTrue(reached == totalWalkable,
                () -> String.format("seed %d: walkable cells partitioned — reached %d of %d",
                        seed, finalReached, finalTotal));
    }

    private static String neighborState(MapResult map, int x, int y, int dx, int dy) {
        int nx = x + dx, ny = y + dy;
        if (nx < 0 || nx >= map.grid.getWidth() || ny < 0 || ny >= map.grid.getHeight()) return "OOB";
        return (map.grid.isWalkable(nx, ny) ? "w" : "W")
                + ":" + map.topology.getGroundKind(nx, ny);
    }

    private static BufferedImage renderMap(MapResult map, long seed, DistrictMap districts, List<Compound> compounds) {
        NavigationGrid grid = map.grid;
        CellTopology topo = map.topology;
        int w = grid.getWidth(), h = grid.getHeight();
        int imgW = w * CELL_PX;
        int imgH = h * CELL_PX + 24; // bottom strip for seed label + counts

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Pass 1 — ground fill per cell.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = GROUND_COLORS.getOrDefault(topo.getGroundKind(x, y), Color.MAGENTA);
                g.setColor(c);
                g.fillRect(x * CELL_PX, (h - 1 - y) * CELL_PX, CELL_PX, CELL_PX);
            }
        }

        // Pass 2 — walls (overlay black).
        g.setColor(Color.BLACK);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (topo.isWall(x, y)) {
                    g.fillRect(x * CELL_PX, (h - 1 - y) * CELL_PX, CELL_PX, CELL_PX);
                }
            }
        }

        // Pass 3 — vehicles (dark blue with border).
        g.setColor(new Color(30, 40, 80));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (topo.isVehicle(x, y)) {
                    g.fillRect(x * CELL_PX, (h - 1 - y) * CELL_PX, CELL_PX, CELL_PX);
                }
            }
        }

        // Pass 4 — doorways (light gold dot).
        g.setColor(new Color(255, 220, 100));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (grid.isDoorway(x, y)) {
                    int cx = x * CELL_PX + CELL_PX / 2;
                    int cy = (h - 1 - y) * CELL_PX + CELL_PX / 2;
                    g.fillOval(cx - 2, cy - 2, 4, 4);
                }
            }
        }

        // Pass 5 — doodads (white pixels).
        g.setColor(Color.WHITE);
        for (Doodad d : map.doodads) {
            int cx = d.cellX * CELL_PX + CELL_PX / 2;
            int cy = (h - 1 - d.cellY) * CELL_PX + CELL_PX / 2;
            g.fillRect(cx - 1, cy - 1, 2, 2);
        }

        // Pass 6 — POI outlines (magenta).
        g.setColor(new Color(220, 80, 220));
        g.setStroke(new BasicStroke(1f));
        for (PointOfInterest p : map.pointsOfInterest) {
            int x0 = p.left * CELL_PX;
            int y0 = (h - 1 - p.bottom) * CELL_PX;
            int width  = (p.right  - p.left + 1) * CELL_PX;
            int height = (p.bottom - p.top  + 1) * CELL_PX;
            g.drawRect(x0, y0, width - 1, height - 1);
        }

        // Pass 7 — district boundaries + theme labels. Drawn over the map but
        // under spawn markers so the partition shape stays readable.
        if (districts != null) {
            drawDistrictOverlay(g, districts, h, imgW);
        }

        // Pass 7b — compound bounding rects. A bright magenta outline traces
        // each compound's bounding box so the multi-leaf claim is visible at
        // a glance even when the wall ring blends with adjacent building art.
        if (compounds != null && !compounds.isEmpty()) {
            drawCompoundOverlay(g, compounds, h);
        }

        // Pass 8 — spawn anchors.
        drawDiamond(g, new Color(80, 230, 110), map.marineSpawnX,   map.marineSpawnY,   h);
        drawDiamond(g, new Color(240, 80,  80), map.defenderSpawnX, map.defenderSpawnY, h);

        // Pass 9 — bottom label strip.
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, h * CELL_PX, imgW, 24);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        String label = String.format("seed=%d  %dx%d  POIs=%d  doodads=%d",
                seed, w, h, map.pointsOfInterest.size(), map.doodads.size());
        g.drawString(label, 6, h * CELL_PX + 16);

        g.dispose();
        return img;
    }

    /**
     * Paint a thick translucent border around every district plus a small
     * theme glyph in the corner. Boundary color = theme accent so a coherent
     * cluster reads as one large bordered region.
     */
    private static void drawDistrictOverlay(Graphics2D g, DistrictMap districts, int gridH, int imgW) {
        int cellW = districts.districtCellWidth();
        int cellH = districts.districtCellHeight();
        g.setStroke(new BasicStroke(2f));
        for (int dx = 0; dx < districts.districtsX(); dx++) {
            for (int dy = 0; dy < districts.districtsY(); dy++) {
                MapDistrictTheme theme = districts.themeAtDistrict(dx, dy);
                Color base = DISTRICT_COLORS.getOrDefault(theme, Color.WHITE);
                Color line = new Color(base.getRed(), base.getGreen(), base.getBlue(), 200);

                int x0 = dx * cellW * CELL_PX;
                // Image-y is grid-y flipped — district at grid-y=0 lives at image-bottom.
                int y0 = (gridH - (dy + 1) * cellH) * CELL_PX;
                int w  = cellW * CELL_PX;
                int hPx = cellH * CELL_PX;

                g.setColor(line);
                g.drawRect(x0, y0, w - 1, hPx - 1);

                // Theme glyph: small filled square + first 3 letters of theme name.
                g.fillRect(x0 + 4, y0 + 4, 10, 10);
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.drawString(theme.name().substring(0, Math.min(3, theme.name().length())),
                        x0 + 4, y0 + 22);
            }
        }
    }

    /**
     * Draw a colored outline around each compound's union bounding rect plus
     * a kind glyph in the top-left corner of the rect. Reads as "this cluster
     * of leaves was claimed as one compound" without interfering with the
     * underlying ground / wall / doorway art.
     */
    private static void drawCompoundOverlay(Graphics2D g, List<Compound> compounds, int gridH) {
        g.setStroke(new BasicStroke(2f));
        for (Compound c : compounds) {
            int x0 = c.left * CELL_PX - 1;
            int y0 = (gridH - 1 - c.bottom) * CELL_PX - 1;
            int wPx = c.width()  * CELL_PX + 2;
            int hPx = c.height() * CELL_PX + 2;
            Color outline = new Color(255, 80, 200, 230);
            g.setColor(outline);
            g.drawRect(x0, y0, wPx, hPx);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(255, 255, 255, 240));
            g.drawString(glyphFor(c.kind), x0 + 4, y0 + 12);
        }
    }

    private static String glyphFor(com.dillon.starsectormarines.battle.mapgen.BlockKind kind) {
        switch (kind) {
            case MILITARY_BASE: return "MIL";
            case GATED_HOUSING: return "GH";
            case DENSE_QUARTER: return "DQ";
            default:            return kind.name().substring(0, Math.min(3, kind.name().length()));
        }
    }

    private static void drawDiamond(Graphics2D g, Color c, int cellX, int cellY, int gridH) {
        g.setColor(c);
        int cx = cellX * CELL_PX + CELL_PX / 2;
        int cy = (gridH - 1 - cellY) * CELL_PX + CELL_PX / 2;
        int r = CELL_PX;
        int[] xs = { cx, cx + r, cx, cx - r };
        int[] ys = { cy - r, cy, cy + r, cy };
        g.fillPolygon(xs, ys, 4);
        g.setColor(Color.BLACK);
        g.drawPolygon(xs, ys, 4);
    }

    private static BufferedImage composeContactSheet(BufferedImage[] tiles, int cols) {
        int rows = (tiles.length + cols - 1) / cols;
        int tileW = tiles[0].getWidth();
        int tileH = tiles[0].getHeight();
        int gap = 8;
        BufferedImage sheet = new BufferedImage(
                cols * tileW + (cols + 1) * gap,
                rows * tileH + (rows + 1) * gap,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(20, 25, 32));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < tiles.length; i++) {
            int cx = i % cols;
            int cy = i / cols;
            int x = gap + cx * (tileW + gap);
            int y = gap + cy * (tileH + gap);
            g.drawImage(tiles[i], x, y, null);
        }
        g.dispose();
        return sheet;
    }
}
